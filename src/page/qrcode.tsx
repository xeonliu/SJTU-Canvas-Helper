import { Card, Image, List, Space } from "antd";
import BasicLayout from "../components/layout";
import CourseSelect from "../components/course_select";
import { useEffect, useState } from "react";
import { Course, QRCodeScanResult } from "../lib/model";
import useMessage from "antd/es/message/useMessage";
import { invoke } from "@tauri-apps/api";
import Meta from "antd/es/card/Meta";
import { Empty } from "antd/lib";

export function QRCodePage() {
    const [courses, setCourses] = useState<Course[]>([]);
    const [messageApi, contextHolder] = useMessage();
    const [operating, setOperating] = useState<boolean>(false);
    const [scanResults, setScanResults] = useState<QRCodeScanResult[]>([]);

    useEffect(() => {
        initCourses();
    }, []);

    useEffect(() => {
        if (operating) {
            messageApi.open({
                key: "operating",
                type: "loading",
                content: "正在读取中...请耐心等待😁"
            });
        } else {
            messageApi.destroy("operating");
        }
    }, [operating])

    const initCourses = async () => {
        try {
            let courses = await invoke("list_courses") as Course[];
            setCourses(courses);
        } catch (e) {
            messageApi.error(e as string);
        }
    }

    const handleGetQRCode = async (courseId: number) => {
        setOperating(true);
        try {
            let scanResults = await invoke("filter_course_qrcode_images", { courseId }) as QRCodeScanResult[];
            setScanResults(scanResults);
        } catch (e) {
            messageApi.error(`读取错误：${e}`);
        }
        setOperating(false);
    }

    const handleCourseSelect = async (courseId: number) => {
        if (courses.find(course => course.id === courseId)) {
            handleGetQRCode(courseId);
        }
    }

    return <BasicLayout>
        {contextHolder}
        <Space direction="vertical" style={{ width: "100%", overflow: "scroll" }} size={"large"}>
            <CourseSelect onChange={handleCourseSelect} disabled={operating} courses={courses} />
            {
                scanResults.length > 0 &&
                <List grid={{ gutter: 16, column: 2 }} style={{ width: "100%" }} dataSource={scanResults}
                    renderItem={scanResult => <List.Item>
                        <Card
                            hoverable
                            cover={<Image src={scanResult.file.url} />}
                        >
                            <Meta title={scanResult.file.display_name} />
                        </Card>

                    </List.Item>}>
                </List>
            }
            {scanResults.length === 0 && <Empty />}
        </Space>
    </BasicLayout>
}