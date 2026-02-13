package com.sjtu.canvas.helper.ui.screens

import android.content.Intent
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
//import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.sjtu.canvas.helper.ui.viewmodel.CourseFilesUiState
import com.sjtu.canvas.helper.ui.viewmodel.CourseFilesViewModel

@Composable
fun CourseFilesScreen(
    courseId: Long,
    onNavigateBack: () -> Unit,
    viewModel: CourseFilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    // 多选状态
    var selectedFileIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedFolderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

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
                    runCatching { context.startActivity(intent) }
                        .onFailure { snackbarHostState.showSnackbar("没有可用应用打开该文件") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.files_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
                backgroundColor = MaterialTheme.colors.primarySurface,
                contentColor = contentColorFor(MaterialTheme.colors.primarySurface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
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
                    // 面包屑导航栏
                    BreadcrumbNavigation(
                        folderPath = state.currentFolderPath,
                        onFolderClick = { folder ->
                            // 进入新文件夹时清空选择
                            selectedFileIds = emptySet()
                            selectedFolderIds = emptySet()
                            viewModel.navigateToFolder(folder.id)
                        },
                        onBackClick = {
                            selectedFileIds = emptySet()
                            selectedFolderIds = emptySet()
                            viewModel.navigateBack()
                        },
                        canGoBack = state.currentFolderId != null && 
                                   state.foldersMap[state.currentFolderId]?.parentFolderId != null
                    )

                    // 内容区域
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Button(
                                onClick = { viewModel.syncAll() },
                                enabled = !isSyncing
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isSyncing) "同步中" else "一键同步")
                            }
                        }

                        // 显示子文件夹
                        if (state.childFolders.isNotEmpty()) {
                            items(state.childFolders) { folder ->
                                FolderItem(
                                    folder = folder,
                                    isSelected = folder.id in selectedFolderIds,
                                    onClick = { 
                                        selectedFileIds = emptySet()
                                        selectedFolderIds = emptySet()
                                        viewModel.navigateToFolder(folder.id) 
                                    },
                                    onLongClick = {
                                        if (folder.id in selectedFolderIds) {
                                            selectedFolderIds = selectedFolderIds - folder.id
                                        } else {
                                            selectedFolderIds = selectedFolderIds + folder.id
                                        }
                                    }
                                )
                            }
                        }

                        // 显示当前文件夹的文件
                        if (state.currentFiles.isEmpty() && state.childFolders.isEmpty()) {
                            item {
                                Text("暂无文件或文件夹")
                            }
                        } else {
                            items(state.currentFiles) { file ->
                                CourseFileRow(
                                    file = file,
                                    isSelected = file.id in selectedFileIds,
                                    syncing = isSyncing,
                                    progress = progressMap[file.id],
                                    onDownload = { viewModel.downloadSingle(file) },
                                    onOpen = { viewModel.openFile(file) },
                                    onLongClick = {
                                        if (file.id in selectedFileIds) {
                                            selectedFileIds = selectedFileIds - file.id
                                        } else {
                                            selectedFileIds = selectedFileIds + file.id
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 批量操作栏
                    if (selectedFileIds.isNotEmpty()) {
                        BatchActionBar(
                            selectedCount = selectedFileIds.size,
                            onDownloadClick = {
                                val filesToDownload = state.currentFiles.filter { it.id in selectedFileIds }
                                viewModel.downloadMultiple(filesToDownload)
                                selectedFileIds = emptySet()
                                selectedFolderIds = emptySet()
                            },
                            onClearClick = {
                                selectedFileIds = emptySet()
                                selectedFolderIds = emptySet()
                            }
                        )
                    }
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
    canGoBack: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface.copy(alpha = 0.9f))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回上级目录",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = "当前路径：",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
        
        // 面包屑路径显示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            folderPath.forEachIndexed { index, folder ->
                OutlinedButton(
                    onClick = { onFolderClick(folder) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = folder.name ?: "未知",
                        style = MaterialTheme.typography.caption
                    )
                }
                if (index < folderPath.size - 1) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(
    folder: CanvasFolder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = 1.dp,
        backgroundColor = if (isSelected) {
            MaterialTheme.colors.secondary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colors.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "文件夹",
                        tint = MaterialTheme.colors.secondary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(270f)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = folder.name ?: "未知文件夹",
                        style = MaterialTheme.typography.subtitle2
                    )
                    Text(
                        text = folder.fullName ?: "",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseFileRow(
    file: CanvasCourseFile,
    isSelected: Boolean,
    syncing: Boolean,
    progress: com.sjtu.canvas.helper.ui.viewmodel.DownloadProgress?,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        elevation = 1.dp,
        backgroundColor = if (isSelected) {
            MaterialTheme.colors.secondary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colors.surface
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }
                Text(
                    file.displayName,
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.weight(1f)
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { if (progress.finished) 1f else progress.ratio },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (progress.finished) {
                        "下载完成"
                    } else if (progress.total > 0) {
                        "下载中：${formatSize(progress.processed)} / ${formatSize(progress.total)}"
                    } else {
                        "下载中：${formatSize(progress.processed)}"
                    },
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.size?.let { formatSize(it) } ?: "",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDownload, enabled = !syncing) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("下载")
                    }
                    Button(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("打开")
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onDownloadClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.secondary.copy(alpha = 0.12f)),
        elevation = 8.dp
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
                style = MaterialTheme.typography.button,
                color = MaterialTheme.colors.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDownloadClick) {
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
