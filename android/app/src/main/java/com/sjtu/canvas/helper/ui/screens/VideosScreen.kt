package com.sjtu.canvas.helper.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sjtu.canvas.helper.R
import com.sjtu.canvas.helper.data.model.SjtuCanvasVideo
import com.sjtu.canvas.helper.data.model.SjtuVideoPlayInfo
import com.sjtu.canvas.helper.ui.viewmodel.SjtuVideosUiState
import com.sjtu.canvas.helper.ui.viewmodel.SjtuVideosViewModel
import com.sjtu.canvas.helper.ui.viewmodel.VideoLoginState
import com.sjtu.canvas.helper.ui.viewmodel.VideoLoginViewModel
import com.sjtu.canvas.helper.util.QrCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    courseId: Long,
    onNavigateBack: () -> Unit,
    videosViewModel: SjtuVideosViewModel = hiltViewModel(),
    loginViewModel: VideoLoginViewModel = hiltViewModel()
) {
    val loginState by loginViewModel.state.collectAsState()
    val uiState by videosViewModel.uiState.collectAsState()
    val selectedVideo by videosViewModel.selectedVideo.collectAsState()
    val videoInfo by videosViewModel.videoInfo.collectAsState()
    val primaryPlay by videosViewModel.primaryPlay.collectAsState()
    val secondaryPlay by videosViewModel.secondaryPlay.collectAsState()
    val subtitlePath by videosViewModel.subtitlePath.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var dualMode by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var selectorDialogOpen by remember { mutableStateOf(false) }

    var speed by remember { mutableFloatStateOf(1.0f) }
    var primaryMuted by remember { mutableStateOf(false) }
    var secondaryMuted by remember { mutableStateOf(false) }
    var primaryVolume by remember { mutableFloatStateOf(1.0f) }
    var secondaryVolume by remember { mutableFloatStateOf(0.0f) }
    var subtitleEnabled by remember { mutableStateOf(true) }
    // 使用单一的播放位置状态来确保主副屏同步
    var currentPosition by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    val immersivePlayerMode = fullscreen || isLandscape
    val showTranscriptPanel = !isLandscape && !fullscreen

    var transcriptLines by remember { mutableStateOf<List<TranscriptLine>>(emptyList()) }
    var activeTranscriptIndex by remember { mutableStateOf<Int?>(null) }
    var transcriptSeekPositionMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(subtitlePath) {
        transcriptLines = if (!subtitlePath.isNullOrBlank()) {
            parseWebVttTranscript(subtitlePath!!)
        } else {
            emptyList()
        }
        activeTranscriptIndex = null
        transcriptSeekPositionMs = null
    }

    LaunchedEffect(currentPosition, transcriptLines) {
        if (transcriptLines.isEmpty()) {
            activeTranscriptIndex = null
        } else {
            val idx = transcriptLines.indexOfLast { currentPosition >= it.startMs }
            activeTranscriptIndex = if (idx >= 0) idx else null
        }
    }

    // // 竖屏非全屏时强制关闭视频内嵌字幕，仅通过 Transcript 面板展示
    LaunchedEffect(showTranscriptPanel) {
        if (showTranscriptPanel && subtitleEnabled) {
            subtitleEnabled = false
        }
    }

    // 默认先尝试加载回放；若因未登录/权限失败，再提示登录
    LaunchedEffect(Unit) {
        videosViewModel.loadVideos()
    }

    LaunchedEffect(loginState) {
        if (loginState is VideoLoginState.LoggedIn) {
            videosViewModel.loadVideos()
        }
    }

    LaunchedEffect(dualMode, videoInfo) {
        if (dualMode && secondaryPlay == null) {
            val candidates = videoInfo?.videoPlayResponseVoList.orEmpty()
            val second = candidates.firstOrNull { it.id != primaryPlay?.id }
            if (second != null) {
                videosViewModel.toggleSecondary(second)
                secondaryVolume = 0.0f
                secondaryMuted = true
            }
        }
    }

    FullscreenSystemUiEffect(enabled = immersivePlayerMode)

    Scaffold(
        topBar = {
            if (!immersivePlayerMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.videos_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { videosViewModel.loadVideos() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (immersivePlayerMode) PaddingValues(0.dp) else paddingValues)
        ) {
            when (loginState) {
                is VideoLoginState.ShowQr,
                is VideoLoginState.Loading,
                is VideoLoginState.Error -> {
                    VideoLoginPanel(
                        state = loginState,
                        onStart = { loginViewModel.startQrLogin() },
                        onCancel = { loginViewModel.cancel() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                else -> Unit
            }

            when (val state = uiState) {
                is SjtuVideosUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is SjtuVideosUiState.Error -> {
                    val msg = state.message
                    val loginRelated = listOf("未登录", "登录", "jaccount", "权限", "授权").any { kw ->
                        msg.contains(kw, ignoreCase = true)
                    }
                    if (loginRelated) {
                        VideoLoginPanel(
                            state = when (loginState) {
                                is VideoLoginState.ShowQr,
                                is VideoLoginState.Loading,
                                is VideoLoginState.Error -> loginState
                                else -> VideoLoginState.Idle
                            },
                            onStart = { loginViewModel.startQrLogin() },
                            onCancel = { loginViewModel.cancel() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        ErrorPanel(
                            message = state.message,
                            onRetry = { videosViewModel.loadVideos() }
                        )
                    }
                }

                is SjtuVideosUiState.Success -> {
                    if (state.videos.isEmpty()) {
                        EmptyPanel(
                            hint = "暂无回放（可能未登录 SJTU 视频平台或该课程没有录像）",
                            onLogin = { loginViewModel.startQrLogin() },
                            onReload = { videosViewModel.loadVideos() }
                        )
                        return@Column
                    }

                    if (primaryPlay != null) {
                        PlayerArea(
                            primaryUrl = primaryPlay!!.rtmpUrlHdv,
                            secondaryUrl = if (dualMode) secondaryPlay?.rtmpUrlHdv else null,
                            subtitlePath = subtitlePath,
                            subtitleEnabled = subtitleEnabled,
                            speed = speed,
                            primaryMuted = primaryMuted,
                            secondaryMuted = secondaryMuted,
                            primaryVolume = primaryVolume,
                            secondaryVolume = secondaryVolume,
                            position = currentPosition,
                            isPlaying = isPlaying,
                            fullscreen = fullscreen,
                            expanded = immersivePlayerMode,
                            dualMode = dualMode,
                            onToggleFullscreen = { fullscreen = !fullscreen },
                            onOpenSelector = { selectorDialogOpen = true },
                            onDualModeChange = { dualMode = it },
                            onSwap = {
                                videosViewModel.swapPrimarySecondary()
                                val tmpMuted = primaryMuted
                                primaryMuted = secondaryMuted
                                secondaryMuted = tmpMuted
                                val tmpVol = primaryVolume
                                primaryVolume = secondaryVolume
                                secondaryVolume = tmpVol
                                // 交换时不再交换播放位置，因为两者应该同步
                            },
                            onPrimaryMuteChange = { primaryMuted = it },
                            onSecondaryMuteChange = { secondaryMuted = it },
                            onPrimaryVolumeChange = { primaryVolume = it },
                            onSecondaryVolumeChange = { secondaryVolume = it },
                            onSpeedChange = { speed = it },
                            onSubtitleEnabledChange = { subtitleEnabled = it },
                            onPositionChange = { currentPosition = it },
                            onPlayingChange = { isPlaying = it },
                            transcriptSeekPositionMs = transcriptSeekPositionMs,
                        )

                        if (showTranscriptPanel && transcriptLines.isNotEmpty()) {
                            TranscriptPanel(
                                lines = transcriptLines,
                                activeIndex = activeTranscriptIndex,
                                onLineClick = { line ->
                                    transcriptSeekPositionMs = line.startMs
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    if (primaryPlay == null) {
                        VideoSelectionList(
                            videos = state.videos,
                            selectedVideo = selectedVideo,
                            videoInfo = videoInfo,
                            primaryPlay = primaryPlay,
                            secondaryPlay = secondaryPlay,
                            onSelectVideo = { videosViewModel.selectVideo(it) },
                            onSetPrimary = { videosViewModel.setPrimary(it) },
                            onToggleSecondary = { videosViewModel.toggleSecondary(it) },
                        )
                    }
                }
            }
        }
    }

    if (selectorDialogOpen) {
        Dialog(
            onDismissRequest = { selectorDialogOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("选择视频与播放源", style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = { selectorDialogOpen = false }) {
                            Text("关闭")
                        }
                    }
                    HorizontalDivider()
                    VideoSelectionList(
                        videos = (uiState as? SjtuVideosUiState.Success)?.videos.orEmpty(),
                        selectedVideo = selectedVideo,
                        videoInfo = videoInfo,
                        primaryPlay = primaryPlay,
                        secondaryPlay = secondaryPlay,
                        onSelectVideo = {
                            videosViewModel.selectVideo(it)
                            selectorDialogOpen = false
                        },
                        onSetPrimary = { videosViewModel.setPrimary(it) },
                        onToggleSecondary = { videosViewModel.toggleSecondary(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenSystemUiEffect(enabled: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(enabled) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            WindowCompat.setDecorFitsSystemWindows(window, !enabled)
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.navigationBars())
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }

        onDispose {
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.navigationBars())
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}

@Composable
private fun VideoSelectionList(
    videos: List<SjtuCanvasVideo>,
    selectedVideo: SjtuCanvasVideo?,
    videoInfo: com.sjtu.canvas.helper.data.model.SjtuVideoInfo?,
    primaryPlay: SjtuVideoPlayInfo?,
    secondaryPlay: SjtuVideoPlayInfo?,
    onSelectVideo: (SjtuCanvasVideo) -> Unit,
    onSetPrimary: (SjtuVideoPlayInfo) -> Unit,
    onToggleSecondary: (SjtuVideoPlayInfo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(videos) { video ->
            CanvasVideoCard(
                video = video,
                selected = selectedVideo?.videoId == video.videoId,
                onSelect = { onSelectVideo(video) }
            )
        }

        item {
            if (videoInfo != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "播放源",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                videoInfo.videoPlayResponseVoList.forEach { play ->
                    PlaySourceRow(
                        play = play,
                        isPrimary = primaryPlay?.id == play.id,
                        isSecondary = secondaryPlay?.id == play.id,
                        onPrimary = { onSetPrimary(play) },
                        onSecondary = { onToggleSecondary(play) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun VideoLoginPanel(
    state: VideoLoginState,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SJTU 视频回放需要 jAccount 会话",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请用另一台设备或电脑扫描二维码登录（与桌面版一致）。登录成功后会自动刷新回放列表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (state) {
                is VideoLoginState.ShowQr -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    val img = remember(state.qrUrl) { QrCodeGenerator.generate(state.qrUrl) }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(bitmap = img, contentDescription = null, modifier = Modifier.size(220.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onCancel) {
                            Text("取消")
                        }
                    }
                }

                is VideoLoginState.Loading -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("正在获取二维码/等待扫码…")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(onClick = onCancel) {
                        Text("取消")
                    }
                }

                is VideoLoginState.Error -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart) { Text("重试") }
                        FilledTonalButton(onClick = onCancel) { Text("取消") }
                    }
                }

                VideoLoginState.Idle -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onStart) { Text("扫码登录") }
                }

                VideoLoginState.LoggedIn -> Unit
            }
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun EmptyPanel(hint: String, onLogin: () -> Unit, onReload: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hint, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReload) { Text("刷新") }
                FilledTonalButton(onClick = onLogin) { Text("扫码登录") }
            }
        }
    }
}

@Composable
private fun CanvasVideoCard(
    video: SjtuCanvasVideo,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(video.videoName, style = MaterialTheme.typography.titleMedium)
                val time = listOfNotNull(video.courseBeginTime, video.courseEndTime)
                    .joinToString(" - ")
                if (time.isNotBlank()) {
                    Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(onClick = onSelect) {
                Text(if (selected) "已选择" else "选择")
            }
        }
    }
}

@Composable
private fun PlaySourceRow(
    play: SjtuVideoPlayInfo,
    isPrimary: Boolean,
    isSecondary: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "源 #${play.id}")
                Text(
                    text = play.rtmpUrlHdv.take(64) + if (play.rtmpUrlHdv.length > 64) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onPrimary) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPrimary) "主" else "设主")
                }
                Spacer(modifier = Modifier.height(6.dp))
                FilledTonalButton(onClick = onSecondary) {
                    Icon(
                        imageVector = if (isSecondary) Icons.Default.Check else Icons.Default.Splitscreen,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSecondary) "同屏" else "加入")
                }
            }
        }
    }
}

@Composable
private fun PlayerArea(
    primaryUrl: String,
    secondaryUrl: String?,
    subtitlePath: String?,
    subtitleEnabled: Boolean,
    speed: Float,
    primaryMuted: Boolean,
    secondaryMuted: Boolean,
    primaryVolume: Float,
    secondaryVolume: Float,
    position: Long,
    isPlaying: Boolean,
    fullscreen: Boolean,
    expanded: Boolean = false,
    dualMode: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenSelector: (() -> Unit)? = null,
    onDualModeChange: ((Boolean) -> Unit)? = null,
    onSwap: () -> Unit,
    onPrimaryMuteChange: (Boolean) -> Unit,
    onSecondaryMuteChange: (Boolean) -> Unit,
    onPrimaryVolumeChange: (Float) -> Unit,
    onSecondaryVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSubtitleEnabledChange: (Boolean) -> Unit,
    onPositionChange: (Long) -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    transcriptSeekPositionMs: Long? = null,
) {
    var secondaryOffsetX by remember { mutableFloatStateOf(0f) }
    var secondaryOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .then(if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .padding(horizontal = 8.dp)
            .then(if (expanded) Modifier else Modifier.aspectRatio(16f / 9f))
    ) {
        SjtuVideoPlayerSurface(
            playUrl = primaryUrl,
            subtitlePath = subtitlePath,
            subtitleEnabled = subtitleEnabled,
            speed = speed,
            muted = primaryMuted,
            volume = primaryVolume,
            position = position,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize(),
            roleLabel = "主",
            onMuteChange = onPrimaryMuteChange,
            onVolumeChange = onPrimaryVolumeChange,
            onSpeedChange = onSpeedChange,
            onSubtitleEnabledChange = onSubtitleEnabledChange,
            onPositionChange = onPositionChange,
            onPlayingChange = onPlayingChange,
            onSwap = if (secondaryUrl != null) onSwap else null,
            onFullscreen = onToggleFullscreen,
            onOpenSelector = onOpenSelector,
            dualMode = dualMode,
            onDualModeChange = onDualModeChange,
            fullscreen = fullscreen,
            externalSeekToMs = transcriptSeekPositionMs,
        )

        if (secondaryUrl != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(20f)
                    .offset { IntOffset(secondaryOffsetX.toInt(), secondaryOffsetY.toInt()) }
                    .padding(10.dp)
                    .width(180.dp)
                    .aspectRatio(16f / 9f)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            secondaryOffsetX += dragAmount.x
                            secondaryOffsetY += dragAmount.y
                        }
                    }
            ) {
                SjtuVideoPlayerSurface(
                    playUrl = secondaryUrl,
                    subtitlePath = null,
                    subtitleEnabled = false,
                    speed = speed,
                    muted = secondaryMuted,
                    volume = secondaryVolume,
                    position = position, // Pass position for syncing
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize(),
                    roleLabel = "副",
                    onMuteChange = onSecondaryMuteChange,
                    onVolumeChange = onSecondaryVolumeChange,
                    onSpeedChange = onSpeedChange,
                    onSubtitleEnabledChange = { },
                    onPositionChange = { }, // Secondary does not report position
                    onPlayingChange = onPlayingChange,
                    onSwap = onSwap,
                    onFullscreen = null,
                    onOpenSelector = null,
                    dualMode = dualMode,
                    onDualModeChange = null,
                    compact = true,
                    fullscreen = fullscreen,
                )
            }
        }
    }
}

@Composable
private fun FullscreenPlayerDialog(
    primaryUrl: String,
    secondaryUrl: String?,
    subtitlePath: String?,
    subtitleEnabled: Boolean,
    speed: Float,
    primaryMuted: Boolean,
    secondaryMuted: Boolean,
    primaryVolume: Float,
    secondaryVolume: Float,
    position: Long,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onSwap: () -> Unit,
    onPrimaryMuteChange: (Boolean) -> Unit,
    onSecondaryMuteChange: (Boolean) -> Unit,
    onPrimaryVolumeChange: (Float) -> Unit,
    onSecondaryVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSubtitleEnabledChange: (Boolean) -> Unit,
    onPositionChange: (Long) -> Unit,
    onPlayingChange: (Boolean) -> Unit,
) {
    var secondaryOffsetX by remember { mutableFloatStateOf(0f) }
    var secondaryOffsetY by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val activity = context as? Activity

    // 进入全屏时设置横屏和隐藏系统UI
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        val window = activity?.window
        val decorView = window?.decorView

        // 设置横屏
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // 隐藏系统UI实现真正的全屏
        decorView?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        onDispose {
            // 恢复原来的屏幕方向
            if (originalOrientation != null) {
                activity?.requestedOrientation = originalOrientation
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            // 恢复系统UI
            decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SjtuVideoPlayerSurface(
                playUrl = primaryUrl,
                subtitlePath = subtitlePath,
                subtitleEnabled = subtitleEnabled,
                speed = speed,
                muted = primaryMuted,
                volume = primaryVolume,
                position = position,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
                roleLabel = "主",
                onMuteChange = onPrimaryMuteChange,
                onVolumeChange = onPrimaryVolumeChange,
                onSpeedChange = onSpeedChange,
                onSubtitleEnabledChange = onSubtitleEnabledChange,
                onPositionChange = onPositionChange,
                onPlayingChange = onPlayingChange,
                onSwap = if (secondaryUrl != null) onSwap else null,
                onFullscreen = onDismiss,
                dualMode = secondaryUrl != null,
                fullscreen = true,
            )

            if (secondaryUrl != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset { IntOffset(secondaryOffsetX.toInt(), secondaryOffsetY.toInt()) }
                        .padding(12.dp)
                        .width(240.dp)
                        .aspectRatio(16f / 9f)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                secondaryOffsetX += dragAmount.x
                                secondaryOffsetY += dragAmount.y
                            }
                        }
                ) {
                    SjtuVideoPlayerSurface(
                        playUrl = secondaryUrl,
                        subtitlePath = null,
                        subtitleEnabled = false,
                        speed = speed,
                        muted = secondaryMuted,
                        volume = secondaryVolume,
                        position = position, // Pass position for syncing
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize(),
                        roleLabel = "副",
                        onMuteChange = onSecondaryMuteChange,
                        onVolumeChange = onSecondaryVolumeChange,
                        onSpeedChange = onSpeedChange,
                        onSubtitleEnabledChange = { },
                        onPositionChange = { }, // Secondary does not report position
                        onPlayingChange = onPlayingChange,
                        onSwap = onSwap,
                        onFullscreen = null,
                        dualMode = true,
                        compact = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SjtuVideoPlayerSurface(
    playUrl: String,
    subtitlePath: String?,
    subtitleEnabled: Boolean,
    speed: Float,
    muted: Boolean,
    volume: Float,
    position: Long,
    isPlaying: Boolean,
    modifier: Modifier,
    roleLabel: String,
    onMuteChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSubtitleEnabledChange: (Boolean) -> Unit,
    onPositionChange: (Long) -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    onSwap: (() -> Unit)?,
    onFullscreen: (() -> Unit)?,
    onOpenSelector: (() -> Unit)? = null,
    dualMode: Boolean,
    onDualModeChange: ((Boolean) -> Unit)? = null,
    compact: Boolean = false,
    fullscreen: Boolean = false,
    externalSeekToMs: Long? = null,
) {
    val context = LocalContext.current
    var holdSpeedBoost by remember { mutableStateOf(false) }
    var draggingSeek by remember { mutableStateOf(false) }
    var dragSeekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var dragSeekDeltaMs by remember { mutableStateOf(0L) }
    val effectiveUrl = remember(playUrl) {
        playUrl
            .replace("http://courses.sjtu.edu.cn", "https://courses.sjtu.edu.cn")
            .replace("http://live.sjtu.edu.cn", "https://live.sjtu.edu.cn")
    }

    val httpFactory = remember {
        DefaultHttpDataSource.Factory().setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://courses.sjtu.edu.cn",
                "User-Agent" to "Mozilla/5.0"
            )
        )
    }
    val dataSourceFactory = remember(context, httpFactory) {
        DefaultDataSource.Factory(context, httpFactory)
    }
    val mediaSourceFactory = remember(dataSourceFactory) { DefaultMediaSourceFactory(dataSourceFactory) }

    val mediaItem = remember(effectiveUrl, subtitlePath) {
        val builder = MediaItem.Builder().setUri(effectiveUrl)
        if (!subtitlePath.isNullOrBlank()) {
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(
                        android.net.Uri.fromFile(java.io.File(subtitlePath))
                    )
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("zh")
                        .setRoleFlags(androidx.media3.common.C.ROLE_FLAG_SUBTITLE)
                        .setLabel("字幕")
                        .build()
                )
            )
        }
        builder.build()
    }

    val player = remember(effectiveUrl, mediaItem) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(mediaItem)
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitleEnabled)
                    .setPreferredTextLanguage(if (subtitleEnabled) "zh" else null)
                    .build()
                // 如果这是因为切换（url改变）导致重建，且有传入位置，则恢复位置
                if (position > 0) {
                    seekTo(position)
                }
                prepare()
                playWhenReady = true
            }
    }

    LaunchedEffect(speed, holdSpeedBoost) {
        val effectiveSpeed = if (holdSpeedBoost) kotlin.math.max(speed, 2.0f) else speed
        player.playbackParameters = PlaybackParameters(effectiveSpeed)
    }
    LaunchedEffect(muted, volume) {
        player.volume = if (muted) 0f else volume.coerceIn(0f, 1f)
    }
    LaunchedEffect(subtitleEnabled) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitleEnabled)
            .setPreferredTextLanguage(if (subtitleEnabled) "zh" else null)
            .build()
    }

    // 同步播放/暂停状态
    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    // 处理来自 Transcript 面板的外部跳转请求（仅主屏）
    var lastExternalSeekMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(externalSeekToMs) {
        if (roleLabel == "主" && externalSeekToMs != null && externalSeekToMs != lastExternalSeekMs) {
            lastExternalSeekMs = externalSeekToMs
            player.seekTo(externalSeekToMs)
            onPositionChange(externalSeekToMs)
        }
    }

    // 主屏使用listener和定期轮询双重机制报告播放位置
    LaunchedEffect(roleLabel, player) {
        if (roleLabel == "主") {
            val listener = object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // 用户手动拖动进度条或其他跳转操作，立即同步
                    onPositionChange(player.currentPosition)
                }
            }
            
            player.addListener(listener)
            
            try {
                // 定期轮询以确保连续同步
                while (true) {
                    kotlinx.coroutines.delay(200) // 从500ms降低到200ms以提高响应速度
                    onPositionChange(player.currentPosition)
                }
            } finally {
                player.removeListener(listener)
            }
        }
    }

    // 副屏监听position变化并同步
    LaunchedEffect(position) {
        if (roleLabel == "副" && position > 0) {
            val diff = kotlin.math.abs(player.currentPosition - position)
            // 差异大于500ms就同步，提高同步灵敏度
            if (diff > 500) {
                player.seekTo(position)
            }
        }
    }

    // 监听播放意图变化，避免缓冲时错误回写暂停
    DisposableEffect(player, roleLabel) {
        if (roleLabel != "主") {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    onPlayingChange(playWhenReady)
                }
            }
            player.addListener(listener)
            onPlayingChange(player.playWhenReady)
            onDispose { player.removeListener(listener) }
        }
    }

    DisposableEffect(player) {
        onDispose {
            holdSpeedBoost = false
            draggingSeek = false
            dragSeekPreviewMs = null
            dragSeekDeltaMs = 0L
            player.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
                val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
                PlayerView(ctx).apply {
                    // 紧凑模式（副屏）不展示自带控制栏，避免遮挡画面
                    useController = !compact
                    this.player = player
                    var longPressTriggered = false
                    var dragStarted = false
                    var downX = 0f
                    var downY = 0f
                    var basePositionMs = 0L
                    val longPressRunnable = Runnable {
                        if (dragStarted) return@Runnable
                        longPressTriggered = true
                        holdSpeedBoost = true
                    }

                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                longPressTriggered = false
                                dragStarted = false
                                downX = event.x
                                downY = event.y
                                basePositionMs = player.currentPosition
                                dragSeekPreviewMs = null
                                dragSeekDeltaMs = 0L
                                draggingSeek = false
                                removeCallbacks(longPressRunnable)
                                postDelayed(longPressRunnable, longPressTimeout)
                            }

                            MotionEvent.ACTION_MOVE -> {
                                if (roleLabel != "主") {
                                    return@setOnTouchListener false
                                }

                                val dx = event.x - downX
                                val dy = event.y - downY
                                if (!dragStarted && kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                    dragStarted = true
                                    draggingSeek = true
                                    holdSpeedBoost = false
                                    removeCallbacks(longPressRunnable)
                                    parent?.requestDisallowInterceptTouchEvent(true)
                                }

                                if (dragStarted) {
                                    val duration = player.duration
                                    val width = width.coerceAtLeast(1)
                                    val deltaMs = if (duration > 0 && duration != C.TIME_UNSET) {
                                        (dx / width) * duration.toFloat()
                                    } else {
                                        dx * 120f
                                    }
                                    val maxMs = if (duration > 0 && duration != C.TIME_UNSET) duration else Long.MAX_VALUE
                                    val targetMs = (basePositionMs + deltaMs.toLong()).coerceIn(0L, maxMs)
                                    dragSeekPreviewMs = targetMs
                                    dragSeekDeltaMs = targetMs - basePositionMs
                                    player.seekTo(targetMs)
                                    onPositionChange(targetMs)
                                    return@setOnTouchListener true
                                }
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                removeCallbacks(longPressRunnable)
                                if (dragStarted) {
                                    val targetMs = dragSeekPreviewMs ?: basePositionMs
                                    player.seekTo(targetMs)
                                    player.playWhenReady = true
                                    onPositionChange(targetMs)
                                    onPlayingChange(true)
                                    draggingSeek = false
                                    dragSeekPreviewMs = null
                                    dragSeekDeltaMs = 0L
                                    dragStarted = false
                                    longPressTriggered = false
                                    holdSpeedBoost = false
                                    parent?.requestDisallowInterceptTouchEvent(false)
                                    return@setOnTouchListener true
                                }

                                if (longPressTriggered) {
                                    holdSpeedBoost = false
                                    longPressTriggered = false
                                }
                                draggingSeek = false
                                dragSeekPreviewMs = null
                                dragSeekDeltaMs = 0L
                                parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                }
            },
            update = {
                it.player = player
                if (!isPlaying && holdSpeedBoost) {
                    holdSpeedBoost = false
                }
                if (!isPlaying && draggingSeek) {
                    draggingSeek = false
                    dragSeekPreviewMs = null
                    dragSeekDeltaMs = 0L
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (holdSpeedBoost) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "长按加速 2.0x",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        if (draggingSeek && dragSeekPreviewMs != null) {
            val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            val sign = if (dragSeekDeltaMs >= 0) "+" else "-"
            val deltaSeconds = kotlin.math.abs(dragSeekDeltaMs) / 1000
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = if (duration != null) {
                            "${formatPlaybackTime(dragSeekPreviewMs!!)} / ${formatPlaybackTime(duration)}"
                        } else {
                            formatPlaybackTime(dragSeekPreviewMs!!)
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "$sign${deltaSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        PlayerOverlayControls(
            roleLabel = roleLabel,
            speed = speed,
            muted = muted,
            volume = volume,
            subtitleEnabled = subtitleEnabled,
            subtitleAvailable = !subtitlePath.isNullOrBlank(),
            onSpeedChange = onSpeedChange,
            onMuteChange = onMuteChange,
            onVolumeChange = onVolumeChange,
            onSubtitleEnabledChange = onSubtitleEnabledChange,
            onSwap = onSwap,
            onFullscreen = onFullscreen,
            onOpenSelector = onOpenSelector,
            dualMode = dualMode,
            onDualModeChange = onDualModeChange,
            compact = compact,
            fullscreen = fullscreen,
        )
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// 简单的字幕行模型，用于 Transcript 面板
private data class TranscriptLine(
    val startMs: Long,
    val text: String,
)

// 解析本地 WebVTT 字幕文件为 TranscriptLine 列表（仅使用起始时间与文本）
private suspend fun parseWebVttTranscript(path: String): List<TranscriptLine> = withContext(Dispatchers.IO) {
    val file = File(path)
    if (!file.exists() || !file.isFile) return@withContext emptyList()

    val lines = file.readLines()
    val result = mutableListOf<TranscriptLine>()

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        if (line.isEmpty() || line.equals("WEBVTT", ignoreCase = true)) {
            i++
            continue
        }

        // 可能存在 cue id，跳过到时间轴行
        var timeLine = line
        if (!timeLine.contains("-->") && i + 1 < lines.size) {
            timeLine = lines[i + 1].trim()
            i++
        }

        if (!timeLine.contains("-->")) {
            i++
            continue
        }

        val parts = timeLine.split("-->")
        val startPart = parts.getOrNull(0)?.trim().orEmpty()
        val startMs = parseVttTimeToMs(startPart)
        if (startMs == null) {
            i++
            continue
        }

        // 收集接下来的文本行，直到空行或新时间块
        val textBuilder = StringBuilder()
        i++
        while (i < lines.size) {
            val t = lines[i]
            if (t.isBlank()) break
            if (t.contains("-->")) break
            if (textBuilder.isNotEmpty()) textBuilder.append('\n')
            textBuilder.append(t.trim())
            i++
        }

        val text = textBuilder.toString().trim()
        if (text.isNotEmpty()) {
            result.add(TranscriptLine(startMs = startMs, text = text))
        }
    }

    result.sortedBy { it.startMs }
}

private fun parseVttTimeToMs(raw: String): Long? {
    // 支持 mm:ss.xxx 或 hh:mm:ss.xxx
    val mainAndMs = raw.split('.', limit = 2)
    val timePart = mainAndMs.getOrNull(0)?.trim().orEmpty()
    val msPart = mainAndMs.getOrNull(1)?.takeWhile { it.isDigit() } ?: "0"

    val segments = timePart.split(':')
    if (segments.size !in 2..3) return null

    return try {
        val hours: Long
        val minutes: Long
        val seconds: Long
        if (segments.size == 3) {
            hours = segments[0].toLong()
            minutes = segments[1].toLong()
            seconds = segments[2].toLong()
        } else {
            hours = 0
            minutes = segments[0].toLong()
            seconds = segments[1].toLong()
        }
        val millis = msPart.padEnd(3, '0').take(3).toLong()
        ((hours * 3600 + minutes * 60 + seconds) * 1000) + millis
    } catch (_: NumberFormatException) {
        null
    }
}

@Composable
private fun TranscriptPanel(
    lines: List<TranscriptLine>,
    activeIndex: Int?,
    onLineClick: (TranscriptLine) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex, lines.size) {
        if (activeIndex != null && activeIndex in lines.indices) {
            listState.animateScrollToItem(activeIndex.coerceAtMost(lines.lastIndex))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = "字幕 Transcript",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeIndex
                Surface(
                    tonalElevation = if (isActive) 2.dp else 0.dp,
                    shadowElevation = if (isActive) 1.dp else 0.dp,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onLineClick(line)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatPlaybackTime(line.startMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.width(60.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 4
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PlayerOverlayControls(
    roleLabel: String,
    speed: Float,
    muted: Boolean,
    volume: Float,
    subtitleEnabled: Boolean,
    subtitleAvailable: Boolean,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSubtitleEnabledChange: (Boolean) -> Unit,
    onSwap: (() -> Unit)?,
    onFullscreen: (() -> Unit)?,
    onOpenSelector: (() -> Unit)?,
    dualMode: Boolean,
    onDualModeChange: ((Boolean) -> Unit)?,
    compact: Boolean,
    fullscreen: Boolean,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(roleLabel, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (onSwap != null) {
            IconButton(onClick = onSwap, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null)
            }
        }

        IconButton(
            onClick = { onMuteChange(!muted) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = null
            )
        }

        Box {
            IconButton(onClick = { speedOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Speed, contentDescription = null)
            }

            DropdownMenu(expanded = speedOpen, onDismissRequest = { speedOpen = false }) {
                DropdownMenuItem(
                    text = { Text("倍速") },
                    onClick = {}
                )
                listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { v ->
                    DropdownMenuItem(
                        text = { Text("倍速 ${v}x") },
                        onClick = {
                            onSpeedChange(v)
                            speedOpen = false
                        },
                        trailingIcon = {
                            if (speed == v) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }


        }

        Box {
            IconButton(onClick = { settingsOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null)
            }
            DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                if (onOpenSelector != null) {
                    DropdownMenuItem(
                        text = { Text("选择视频/播放源") },
                        onClick = {
                            onOpenSelector()
                            settingsOpen = false
                        }
                    )
                }

                if (onDualModeChange != null) {
                    DropdownMenuItem(
                        text = { Text("同屏播放") },
                        onClick = {},
                        trailingIcon = {
                            Switch(
                                checked = dualMode,
                                onCheckedChange = { onDualModeChange(it) }
                            )
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("字幕") },
                    onClick = {},
                    trailingIcon = {
                        Switch(
                            checked = subtitleEnabled,
                            onCheckedChange = { onSubtitleEnabledChange(it) },
                            enabled = subtitleAvailable
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("音量") },
                    onClick = {}
                )
                DropdownMenuItem(
                    text = {
                        Slider(
                            value = volume.coerceIn(0f, 1f),
                            onValueChange = { onVolumeChange(it) },
                            valueRange = 0f..1f
                        )
                    },
                    onClick = {}
                )
            }
        }

        if (onFullscreen != null && !compact) {
            IconButton(onClick = onFullscreen, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = null
                )
            }
        }
    }
}
