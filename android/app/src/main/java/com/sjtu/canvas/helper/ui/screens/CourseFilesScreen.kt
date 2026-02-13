package com.sjtu.canvas.helper.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sjtu.canvas.helper.R
import com.sjtu.canvas.helper.data.model.CanvasCourseFile
import com.sjtu.canvas.helper.data.model.CanvasFolder
import com.sjtu.canvas.helper.ui.viewmodel.CourseFilesEvent
import com.sjtu.canvas.helper.ui.viewmodel.DownloadStatus
import com.sjtu.canvas.helper.ui.viewmodel.CourseFilesUiState
import com.sjtu.canvas.helper.ui.viewmodel.CourseFilesViewModel
import com.sjtu.canvas.helper.ui.components.ContextualActionBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseFilesScreen(
    courseId: Long,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: CourseFilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val selectedFileIds by viewModel.selectedFileIds.collectAsState()
    val isSelectionMode = selectedFileIds.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CourseFilesEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is CourseFilesEvent.OpenFile -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(event.uri, event.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                            snackbarHostState.showSnackbar(
                                "没有可用应用打开该文件"
                            )
                        }
                }
            }
        }
    }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.files_title)) }, navigationIcon = {
            IconButton(onClick = { onNavigateBack?.invoke() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
        }, actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        )
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        when (val state = uiState) {
            is CourseFilesUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is CourseFilesUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            is CourseFilesUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 面包屑导航栏与同步按钮
                    BreadcrumbNavigation(
                        folderPath = state.currentFolderPath,
                        onFolderClick = { folder ->
                            viewModel.clearSelection()
                            viewModel.navigateToFolder(folder.id)
                        },
                        onBackClick = {
                            viewModel.clearSelection()
                            viewModel.navigateBack()
                        },
                        canGoBack = state.currentFolderId != null && state.foldersMap[state.currentFolderId]?.parentFolderId != null,
                        isSyncing = isSyncing,
                        onSyncClick = { viewModel.syncAll() }
                    )

                    // 内容区域
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        // 显示子文件夹
                        if (state.childFolders.isNotEmpty()) {
                            items(state.childFolders) { folder ->
                                val folderSelectionKey = "folder:${folder.id}"
                                FolderItem(
                                    folder = folder,
                                    isSelected = folderSelectionKey in selectedFileIds,
                                    isSelectionMode = isSelectionMode,
                                    syncing = isSyncing,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleFileSelection(folderSelectionKey)
                                        } else {
                                            viewModel.clearSelection()
                                            viewModel.navigateToFolder(folder.id)
                                        }
                                    },
                                    onLongClick = { viewModel.selectFile(folderSelectionKey) },
                                    onSelectToggle = {
                                        viewModel.toggleFileSelection(
                                            folderSelectionKey
                                        )
                                    },
                                    onDownload = { viewModel.downloadFolder(folder) })
                            }
                        }

                        // 显示当前文件夹的文件
                        if (state.currentFiles.isEmpty() && state.childFolders.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("暂无文件或文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(state.currentFiles) { file ->
                                val fileSelectionKey = "file:${file.id}"
                                CourseFileRow(
                                    file = file,
                                    isSelected = fileSelectionKey in selectedFileIds || file.id.toString() in selectedFileIds,
                                    isSelectionMode = isSelectionMode,
                                    syncing = isSyncing,
                                    progress = progressMap[file.id],
                                    onSelectToggle = {
                                        viewModel.toggleFileSelection(fileSelectionKey)
                                    },
                                    onDownload = { viewModel.downloadSingle(file) },
                                    onOpen = { viewModel.openFile(file) },
                                    onLongClick = {
                                        viewModel.selectFile(fileSelectionKey)
                                    })
                            }
                        }
                    }

                    // Contextual Action Bar for multi-select file operations
                    ContextualActionBar(
                        isVisible = selectedFileIds.isNotEmpty(),
                        selectedCount = selectedFileIds.size,
                        onDownloadClick = {
                            viewModel.downloadSelectedEntities()
                        },
                        onCloseClick = {
                            viewModel.clearSelection()
                        })
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbNavigation(
    folderPath: List<CanvasFolder>,
    onFolderClick: (CanvasFolder) -> Unit,
    onBackClick: () -> Unit,
    canGoBack: Boolean,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            IconButton(
                onClick = onBackClick, modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回上级目录",
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // 路径区域 - 可水平滚动
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "路径:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            folderPath.forEachIndexed { index, folder ->
                Text(
                    text = folder.name ?: "未知",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onFolderClick(folder) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
                if (index < folderPath.size - 1) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 一键同步按钮
        FilledTonalButton(
            onClick = onSyncClick,
            enabled = !isSyncing,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isSyncing) "同步中" else "一键同步",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(
    folder: CanvasFolder,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    syncing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = onClick, onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "文件夹",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(270f)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = folder.name ?: "未知文件夹",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = folder.fullName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (isSelectionMode) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "打开",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(180f)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onDownload, enabled = !syncing) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("下载")
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "打开",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(180f)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseFileRow(
    file: CanvasCourseFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    syncing: Boolean,
    progress: com.sjtu.canvas.helper.ui.viewmodel.DownloadProgress?,
    onSelectToggle: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle()
                    } else {
                        onOpen()
                    }
                }, onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    file.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
            }
            if (progress != null) {
                if (progress.status == DownloadStatus.QUEUED) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (progress.status == DownloadStatus.DOWNLOADING || progress.status == DownloadStatus.COMPLETED) {
                    LinearProgressIndicator(
                        progress = { if (progress.finished) 1f else progress.ratio },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = when (progress.status) {
                        DownloadStatus.QUEUED -> "排队中"
                        DownloadStatus.DOWNLOADING -> {
                            if (progress.total > 0) {
                                "下载中：${formatSize(progress.processed)} / ${formatSize(progress.total)}"
                            } else {
                                "下载中：${formatSize(progress.processed)}"
                            }
                        }

                        DownloadStatus.COMPLETED -> "下载完成"
                        DownloadStatus.FAILED -> "下载失败：${progress.message ?: "未知错误"}"
                    }, style = MaterialTheme.typography.bodySmall, color = when (progress.status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDateTime(file.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = file.size?.let { formatSize(it) } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DownloadProgressIconButton(
                    syncing = syncing, progress = progress, onDownload = onDownload
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun DownloadProgressIconButton(
    syncing: Boolean,
    progress: com.sjtu.canvas.helper.ui.viewmodel.DownloadProgress?,
    onDownload: () -> Unit
) {
    Box(
        modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center
    ) {
        // 仅在下载中显示进度圆环，去掉静态背景圆
        if (progress != null && progress.status == DownloadStatus.DOWNLOADING) {
            CircularProgressIndicator(
                progress = { progress.ratio.coerceIn(0f, 1f) },
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = onDownload, enabled = !syncing, modifier = Modifier.size(30.dp)) {
            val isCompleted = progress?.status == DownloadStatus.COMPLETED
            Icon(
                imageVector = if (isCompleted) Icons.Default.Done else Icons.Default.Download,
                contentDescription = if (isCompleted) "已下载" else "下载",
                tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int, onDownloadClick: () -> Unit, onClearClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已选中 $selectedCount 个文件",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onDownloadClick) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("批量下载")
                }
                IconButton(onClick = onClearClick) {
                    Icon(Icons.Default.Close, contentDescription = "取消选择")
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1fMB", mb)
    val gb = mb / 1024.0
    return String.format("%.2fGB", gb)
}

private fun formatDateTime(isoString: String?): String {
    if (isoString == null) return ""
    return try {
        val instant = java.time.Instant.parse(isoString)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        ""
    }
}

private fun formatRelativeTime(millis: Long): String {
    return when {
        millis < 0 -> ""
        millis < 60_000 -> "刚刚"
        millis < 3600_000 -> "${millis / 60_000}分钟前"
        millis < 86400_000 -> "${millis / 3600_000}小时前"
        millis < 2592000_000 -> "${millis / 86400_000}天前"
        else -> "${millis / 2592000_000}个月前"
    }
}
