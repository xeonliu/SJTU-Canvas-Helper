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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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

@OptIn(ExperimentalMaterial3Api::class)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                FilledTonalButton(
                    onClick = { onFolderClick(folder) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = folder.name ?: "未知",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (index < folderPath.size - 1) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
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
                        tint = MaterialTheme.colorScheme.secondary,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
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
                    style = MaterialTheme.typography.titleSmall,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.size?.let { formatSize(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onDownload, enabled = !syncing) {
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
