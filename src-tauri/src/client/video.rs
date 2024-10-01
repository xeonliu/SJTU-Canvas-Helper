use std::{
    collections::HashMap,
    fs::File,
    io::Write,
    sync::Arc,
    time::{SystemTime, UNIX_EPOCH},
};

use base64::{engine::general_purpose::STANDARD, Engine};
use md5::{Digest, Md5};
use regex::Regex;
use reqwest::{
    cookie::CookieStore,
    header::{HeaderValue, ACCEPT, CONTENT_RANGE, RANGE, REFERER},
    redirect::Policy,
    Response, StatusCode,
};
use select::{
    document::Document,
    node::Node,
    predicate::{Attr, Name},
};
use serde::{de::DeserializeOwned, Serialize};
use tauri::Url;
use tokio::{sync::Mutex, task::JoinSet};

use super::{
    constants::{
        AUTH_URL, CANVAS_LOGIN_URL, EXPRESS_LOGIN_URL, MY_SJTU_URL, VIDEO_BASE_URL,
        VIDEO_LOGIN_URL, VIDEO_OAUTH_KEY_URL,
    },
    Client,
};
use crate::{
    client::constants::{
        OAUTH_PATH, OAUTH_RANDOM, OAUTH_RANDOM_P1, OAUTH_RANDOM_P1_VAL, OAUTH_RANDOM_P2,
        OAUTH_RANDOM_P2_VAL, VIDEO_INFO_URL,
    },
    error::{AppError, Result},
    model::{
        CanvasVideo, CanvasVideoResponse, GetCanvasVideoInfoResponse, ItemPage, ProgressPayload,
        Subject, VideoCourse, VideoInfo, VideoPlayInfo,
    },
    utils::{self, write_file_at_offset},
};

// Apis here are for course video
// We take references from: https://github.com/prcwcy/sjtu-canvas-video-download/blob/master/sjtu_canvas_video.py
impl Client {
    pub fn init_cookie(&self, cookie: &str) {
        self.jar
            .add_cookie_str(cookie, &Url::parse(VIDEO_BASE_URL).unwrap());
    }

    pub async fn get_uuid(&self) -> Result<Option<String>> {
        let resp = self.cli.get(MY_SJTU_URL).send().await?.error_for_status()?;
        let body = resp.text().await?;
        // let document = Document::from(body.as_str());
        let re = Regex::new(
            r#"uuid=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"#,
        )
        .unwrap();

        if let Some(captures) = re.captures(&body) {
            if let Some(uuid) = captures.get(1) {
                return Ok(Some(uuid.as_str().to_owned()));
            }
        }

        Ok(None)
    }

    pub async fn express_login(&self, uuid: &str) -> Result<Option<String>> {
        let url = format!("{}?uuid={}", EXPRESS_LOGIN_URL, uuid);
        self.cli.get(&url).send().await?.error_for_status()?;
        let domain = Url::parse(AUTH_URL).unwrap();
        if let Some(value) = self.jar.cookies(&domain) {
            if let Ok(cookies) = value.to_str() {
                let kvs = cookies.split(';');
                for kv in kvs {
                    let kv: Vec<_> = kv.trim().split('=').collect();
                    if kv.len() >= 2 && kv[0] == "JAAuthCookie" {
                        return Ok(Some(kv[1].to_owned()));
                    }
                }
            }
        }
        Ok(None)
    }

    pub async fn login_video_website(&self, cookie: &str) -> Result<Option<String>> {
        self.jar
            .add_cookie_str(cookie, &Url::parse(AUTH_URL).unwrap());
        let response = self.get_request(VIDEO_LOGIN_URL, None::<&str>).await?;
        let url = response.url();
        if let Some(domain) = url.domain() {
            if domain == "jaccount.sjtu.edu.cn" {
                return Err(AppError::LoginError);
            }
        }
        if let Some(cookies) = self.jar.cookies(&Url::parse(VIDEO_BASE_URL).unwrap()) {
            if let Ok(cookies) = cookies.to_str() {
                return Ok(Some(cookies.to_owned()));
            }
        }
        Ok(None)
    }

    pub async fn login_canvas_website(&self, cookie: &str) -> Result<()> {
        self.jar
            .add_cookie_str(cookie, &Url::parse(AUTH_URL).unwrap());
        let response = self.get_request(CANVAS_LOGIN_URL, None::<&str>).await?;
        let url = response.url();
        if let Some(domain) = url.domain() {
            if domain == "jaccount.sjtu.edu.cn" {
                return Err(AppError::LoginError);
            }
        }
        Ok(())
    }

    pub async fn get_page_items<T: Serialize + DeserializeOwned>(
        &self,
        url: &str,
    ) -> Result<Vec<T>> {
        let mut page_index = 1;
        let mut all_items = vec![];

        loop {
            let paged_url = format!("{}pageSize=100&pageIndex={}", url, page_index);
            let item_page = self
                .get_json_with_cookie::<_, ItemPage<T>>(&paged_url, None::<&str>)
                .await?;
            all_items.extend(item_page.list);
            let page = &item_page.page;
            if page.page_count == 0 || page.page_next == page_index {
                break;
            }
            page_index += 1;
        }
        Ok(all_items)
    }

    pub async fn get_subjects(&self) -> Result<Vec<Subject>> {
        let url = format!(
            "{}/system/course/subject/findSubjectVodList?",
            VIDEO_BASE_URL
        );
        self.get_page_items(&url).await
    }

    async fn get_form_data_for_canvas_course_id(
        &self,
        course_id: i64,
    ) -> Result<Option<HashMap<String, String>>> {
        let url = format!(
            "https://oc.sjtu.edu.cn/courses/{}/external_tools/8199",
            course_id
        );
        let response = self.cli.get(&url).send().await?;
        let body = response.text().await?;
        let document = Document::from(body.as_str());
        // tracing::info!("resp: {:?}", body);
        let form = document
            .find(Attr("action", "https://courses.sjtu.edu.cn/lti/launch"))
            .next();

        if form.is_none() {
            return Ok(None);
        }
        let form = form.unwrap();

        let mut data = HashMap::new();
        for input in form.find(Name("input")) {
            if let Some(name) = input.attr("name") {
                if let Some(value) = input.attr("value") {
                    data.insert(name.to_owned(), value.to_owned());
                }
            }
        }
        Ok(Some(data))
    }

    async fn to_canvas_course_id(&self, course_id: i64) -> Result<Option<String>> {
        let data = match self.get_form_data_for_canvas_course_id(course_id).await? {
            Some(data) => data,
            None => return Ok(None),
        };

        // cancel redirection
        let client = reqwest::Client::builder()
            .redirect(Policy::none())
            .cookie_provider(self.jar.clone())
            .build()?;
        let resp = client
            .post("https://courses.sjtu.edu.cn/lti/launch")
            .form(&data)
            .send()
            .await?;

        let location_header = resp.headers().get("location");
        if location_header.is_none() {
            return Ok(None);
        }
        let location_header = location_header.unwrap();
        let canvas_course_id = location_header.to_str()?.split("?canvasCourseId=").nth(1);
        Ok(canvas_course_id.map(|id| id.to_owned()))
    }

    pub async fn get_canvas_videos(&self, course_id: i64) -> Result<Vec<CanvasVideo>> {
        let canvas_course_id = self.to_canvas_course_id(course_id).await?;
        if canvas_course_id.is_none() {
            return Ok(vec![]);
        }
        // tracing::info!("canvas_course_id: {:?}", canvas_course_id);
        let canvas_course_id = canvas_course_id.unwrap();
        let url = "https://courses.sjtu.edu.cn/lti/vodVideo/findVodVideoList";
        let mut data = HashMap::new();
        data.insert("pageIndex", "1");
        data.insert("pageSize", "1000");
        data.insert("canvasCourseId", canvas_course_id.as_str());

        let resp = self
            .post_form(url, None::<&str>, &data)
            .await?
            .error_for_status()?;
        let body = resp.bytes().await?;
        // tracing::info!("body: {}", String::from_utf8_lossy(&body.to_vec()));
        let resp = utils::parse_json::<CanvasVideoResponse>(&body)?;
        let videos = match resp.body {
            Some(body) => body.list,
            None => vec![],
        };
        Ok(videos)
    }

    pub async fn get_oauth_consumer_key(&self) -> Result<Option<String>> {
        let resp = self.get_request(VIDEO_OAUTH_KEY_URL, None::<&str>).await?;
        let body = resp.text().await?;
        let document = Document::from(body.as_str());

        let Some(meta) = document
            .find(Name("meta"))
            .find(|n: &Node| n.attr("id").unwrap_or_default() == "xForSecName")
        else {
            return Ok(None);
        };
        let Some(v) = meta.attr("vaule") else {
            return Ok(None);
        };
        let bytes = &STANDARD.decode(v)?;
        Ok(Some(format!("{}", String::from_utf8_lossy(bytes))))
    }

    pub async fn get_video_course(
        &self,
        subject_id: i64,
        tecl_id: i64,
    ) -> Result<Option<VideoCourse>> {
        let url = format!(
            "{}/system/resource/vodVideo/getCourseListBySubject?orderField=courTimes&subjectId={}&teclId={}&",
            VIDEO_BASE_URL, subject_id, tecl_id
        );
        let mut courses = self.get_page_items(&url).await?;
        Ok(courses.remove(0))
    }

    fn get_oauth_signature(
        &self,
        video_id: i64,
        oauth_nonce: &str,
        oauth_consumer_key: &str,
    ) -> String {
        let signature_string = format!("/app/system/resource/vodVideo/getvideoinfos?id={}&oauth-consumer-key={}&oauth-nonce={}&oauth-path={}&{}&playTypeHls=true",
        video_id, oauth_consumer_key, oauth_nonce, OAUTH_PATH, OAUTH_RANDOM);
        let md5 = Md5::digest(signature_string);
        format!("{:x}", md5)
    }

    fn get_oauth_nonce(&self) -> String {
        let now = SystemTime::now();
        let since_the_epoch = now.duration_since(UNIX_EPOCH).expect("Time went backwards");
        (since_the_epoch.as_nanos() / 1_000_000).to_string()
    }

    async fn download_video_partial(&self, url: &str, begin: u64, end: u64) -> Result<Response> {
        let range_value = HeaderValue::from_str(&format!("bytes={}-{}", begin, end)).unwrap();
        let response = self
            .cli
            .get(url)
            .header(RANGE, range_value)
            .header(REFERER, "https://courses.sjtu.edu.cn")
            .send()
            .await?;
        Ok(response)
    }

    async fn get_download_video_size(&self, url: &str) -> Result<u64> {
        let resp = self.download_video_partial(url, 0, 0).await?;
        let range = resp.headers().get(CONTENT_RANGE);
        if let Some(range) = range {
            let range = range.to_str()?;
            let parts: Vec<_> = range.split('/').collect();
            let size = if parts.len() == 2 {
                parts[1].parse().unwrap_or_default()
            } else {
                0
            };
            Ok(size)
        } else {
            Ok(0)
        }
    }

    pub async fn download_video<F: Fn(ProgressPayload) + Send + 'static>(
        self: Arc<Self>,
        video: &VideoPlayInfo,
        save_path: &str,
        progress_handler: F,
    ) -> Result<()> {
        let output_file = Arc::new(Mutex::new(File::create(save_path)?));
        let url = &video.rtmp_url_hdv;
        let size = self.get_download_video_size(url).await?;
        let payload = ProgressPayload {
            uuid: video.id.to_string(),
            processed: 0,
            total: size,
        };
        progress_handler(payload.clone());

        let progress_handler = Arc::new(Mutex::new(progress_handler));
        let payload = Arc::new(Mutex::new(payload));

        let nproc = num_cpus::get();
        tracing::info!("nproc: {}", nproc);
        let chunk_size = size / nproc as u64;
        let mut tasks = JoinSet::new();
        for i in 0..nproc {
            let begin = i as u64 * chunk_size;
            let end = if i == nproc - 1 {
                size
            } else {
                (i + 1) as u64 * chunk_size - 1
            };
            let self_clone = self.clone();
            let save_path = save_path.to_owned();
            let output_file = output_file.clone();
            let url = url.clone();
            let payload = payload.clone();
            let progress_handler = progress_handler.clone();
            tasks.spawn(async move {
                let response = self_clone.download_video_partial(&url, begin, end).await?;
                let status = response.status();
                if !(status == StatusCode::OK || status == StatusCode::PARTIAL_CONTENT) {
                    tracing::error!("status not ok: {}", status);
                    return Err(AppError::VideoDownloadError(save_path));
                }
                let bytes = response.bytes().await?;
                let read_bytes = bytes.len() as u64;
                tracing::info!("read_bytes: {:?}", read_bytes);
                {
                    let mut file = output_file.lock().await;
                    write_file_at_offset(file.by_ref(), &bytes, begin)?;
                    // release lock automatically after scope release
                }

                let mut payload_guard = payload.lock().await;
                payload_guard.processed += read_bytes;
                progress_handler.lock().await(payload_guard.clone());
                Ok(())
            });
        }
        while let Some(result) = tasks.join_next().await {
            result??;
        }
        tracing::info!("Successfully downloaded video to {}", save_path);
        Ok(())
    }

    pub async fn get_canvas_video_info(&self, video_id: &str) -> Result<VideoInfo> {
        let mut form_data = HashMap::new();
        let url = "https://courses.sjtu.edu.cn/lti/vodVideo/getVodVideoInfos";
        form_data.insert("playTypeHls", "true");
        form_data.insert("id", video_id);
        form_data.insert("isAudit", "true");
        let resp = self
            .post_form(url, None::<&str>, &form_data)
            .await?
            .error_for_status()?;
        let bytes = resp.bytes().await?;
        let resp = utils::parse_json::<GetCanvasVideoInfoResponse>(&bytes)?;
        Ok(resp.body)
    }

    pub async fn get_video_info(
        &self,
        video_id: i64,
        oauth_consumer_key: &str,
    ) -> Result<VideoInfo> {
        let mut form_data = HashMap::new();
        let oauth_nonce = self.get_oauth_nonce();
        let oauth_signature = self.get_oauth_signature(video_id, &oauth_nonce, oauth_consumer_key);

        tracing::debug!("oauth_nonce: {}", oauth_nonce);
        tracing::debug!("oauth_signature: {}", oauth_signature);
        tracing::debug!("oauth_consumer_key: {}", oauth_consumer_key);
        tracing::debug!("video_id: {}", video_id);

        let video_id_str = video_id.to_string();
        form_data.insert("playTypeHls", "true");
        form_data.insert("id", &video_id_str);
        form_data.insert(OAUTH_RANDOM_P1, OAUTH_RANDOM_P1_VAL);
        form_data.insert(OAUTH_RANDOM_P2, OAUTH_RANDOM_P2_VAL);

        let response = self
            .cli
            .post(VIDEO_INFO_URL)
            .form(&form_data)
            .header(ACCEPT, "application/json")
            .header("oauth-consumer-key", oauth_consumer_key)
            .header("oauth-nonce", oauth_nonce)
            .header("oauth-path", OAUTH_PATH)
            .header("oauth-signature", oauth_signature)
            .send()
            .await?
            .error_for_status()?;
        let bytes = response.bytes().await?;
        let video = utils::parse_json(&bytes)?;
        Ok(video)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_get_uuid() -> Result<()> {
        let cli = Client::new();
        let uuid = cli.get_uuid().await?;
        assert!(uuid.is_some());
        let uuid: String = uuid.unwrap();
        assert!(!uuid.is_empty());
        Ok(())
    }

    #[test]
    fn test_get_oauth_signature() -> Result<()> {
        let cli = Client::new();
        let oauth_nonce = "1709784720392";
        let id = 3601811;
        let oauth_consumer_key = "DADD2CA9923D5E31331C4B79B39A1E4B";
        assert_eq!(
            "2b499a5303048d6522118e79711c5ee0",
            cli.get_oauth_signature(id, oauth_nonce, oauth_consumer_key)
        );
        Ok(())
    }
}
