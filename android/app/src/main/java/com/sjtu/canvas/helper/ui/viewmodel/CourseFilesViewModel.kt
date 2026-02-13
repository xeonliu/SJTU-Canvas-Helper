package com.sjtu.canvas.helper.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjtu.canvas.helper.data.model.CanvasCourseFile
import com.sjtu.canvas.helper.data.model.CanvasFolder
import com.sjtu.canvas.helper.data.repository.CanvasRepository
import com.sjtu.canvas.helper.util.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CourseFilesUiState {
    object Loading : CourseFilesUiState()
    data class Success(
        val allFiles: List<CanvasCourseFile>,
        val allFolders: List<CanvasFolder>,
        val foldersMap: Map<Long, CanvasFolder>,
        val currentFolderId: Long?,
        val currentFolderPath: List<CanvasFolder>
    ) : CourseFilesUiState() {
        // 获取当前文件夹的文件和子文件夹
        val currentFiles: List<CanvasCourseFile>
            get() = allFiles.filter { it.folderId == currentFolderId }
        
        val childFolders: List<CanvasFolder>
            get() = allFolders.filter { it.parentFolderId == currentFolderId }
    }

    data class Error(val message: String) : CourseFilesUiState()
}

data class DownloadProgress(
    val processed: Long,
    val total: Long,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val message: String? = null
) {
    val ratio: Float
        get() = when {
            status == DownloadStatus.COMPLETED -> 1f
            total > 0 -> (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val finished: Boolean
        get() = status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

sealed class CourseFilesEvent {
    data class Message(val text: String) : CourseFilesEvent()
    data class OpenFile(val uri: Uri, val mimeType: String) : CourseFilesEvent()
}

@HiltViewModel
class CourseFilesViewModel @Inject constructor(
    private val repository: CanvasRepository,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : SelectableScreenViewModel() {

    private val courseId: Long = savedStateHandle.get<String>("courseId")?.toLongOrNull() ?: 0L

    private val _uiState = MutableStateFlow<CourseFilesUiState>(CourseFilesUiState.Loading)
    val uiState: StateFlow<CourseFilesUiState> = _uiState.asStateFlow()

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncingCount = MutableStateFlow(0)
    val syncingCount: StateFlow<Int> = _syncingCount.asStateFlow()

    private val _courseIdentifier = MutableStateFlow<String?>(null)

    private val _downloadProgress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<Long, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val _events = MutableSharedFlow<CourseFilesEvent>()
    val events: SharedFlow<CourseFilesEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = CourseFilesUiState.Loading
            val foldersResult = repository.getCourseFolders(courseId)
            val filesResult = repository.getCourseFiles(courseId)
            foldersResult
                .onFailure {
                    _uiState.value = CourseFilesUiState.Error(it.message ?: "获取课程文件夹失败")
                    return@launch
                }
            filesResult
                .onFailure {
                    _uiState.value = CourseFilesUiState.Error(it.message ?: "获取课程文件失败")
                    return@launch
                }

            val folders = foldersResult.getOrDefault(emptyList())
            val files = filesResult.getOrDefault(emptyList())
                .filter { !it.url.isNullOrBlank() }
            repository.getCourses().onSuccess { courses ->
                val course = courses.find { it.id == courseId }
                if (course != null) {
                    val name = course.name?.trim().orEmpty()
                    val term = course.term?.name?.trim().orEmpty()
                    val teacher = course.teachers?.firstOrNull()?.displayName?.trim().orEmpty()
                    val composed = if (name.isNotBlank() && term.isNotBlank() && teacher.isNotBlank()) {
                        "$name($term $teacher)"
                    } else {
                        name
                    }
                    _courseIdentifier.value = composed.ifBlank { null }
                }
            }
            
            // 找到根文件夹 "course files"
            val rootFolder = folders.find { it.name == "course files" }
            val rootFolderId = rootFolder?.id
            _currentFolderId.value = rootFolderId
            
            _uiState.value = CourseFilesUiState.Success(
                allFiles = files,
                allFolders = folders,
                foldersMap = folders.associateBy { it.id },
                currentFolderId = rootFolderId,
                currentFolderPath = buildFolderPath(rootFolderId, folders)
            )
        }
    }

    fun navigateToFolder(folderId: Long?) {
        val state = _uiState.value as? CourseFilesUiState.Success ?: return
        _currentFolderId.value = folderId
        _uiState.value = state.copy(
            currentFolderId = folderId,
            currentFolderPath = buildFolderPath(folderId, state.allFolders)
        )
    }

    fun navigateBack() {
        val state = _uiState.value as? CourseFilesUiState.Success ?: return
        val currentFolder = state.foldersMap[state.currentFolderId]
        if (currentFolder?.parentFolderId != null) {
            navigateToFolder(currentFolder.parentFolderId)
        }
    }

    private fun buildFolderPath(folderId: Long?, allFolders: List<CanvasFolder>): List<CanvasFolder> {
        if (folderId == null) return emptyList()
        
        val path = mutableListOf<CanvasFolder>()
        var currentId: Long? = folderId
        val folderMap = allFolders.associateBy { it.id }
        
        while (currentId != null) {
            val folder = folderMap[currentId] ?: break
            path.add(0, folder)
            currentId = folder.parentFolderId
        }
        return path
    }

    fun downloadSingle(file: CanvasCourseFile) {
        viewModelScope.launch {
            val state = _uiState.value as? CourseFilesUiState.Success ?: return@launch
            val tree = getRootTreeDocument() ?: return@launch
            downloadFileToStorage(file, state, tree)
                .onSuccess {
                    _events.emit(CourseFilesEvent.Message("已下载: ${file.displayName}"))
                }
                .onFailure {
                    _events.emit(CourseFilesEvent.Message(it.message ?: "下载失败: ${file.displayName}"))
                }
        }
    }

    fun downloadMultiple(files: List<CanvasCourseFile>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            val state = _uiState.value as? CourseFilesUiState.Success ?: return@launch
            val tree = getRootTreeDocument() ?: return@launch

            var successCount = 0
            var failureCount = 0
            val failedFiles = mutableListOf<String>()

            files.forEach { file ->
                downloadFileToStorage(file, state, tree)
                    .onSuccess { successCount += 1 }
                    .onFailure {
                        failureCount += 1
                        failedFiles.add(file.displayName)
                    }
            }

            val message = when {
                failureCount == 0 -> "批量下载已启动：$successCount 个文件"
                successCount == 0 -> {
                    val details = failedFiles.take(3).joinToString(", ")
                    if (failedFiles.size > 3) {
                        "批量下载失败：无法启动任何下载。$details 等"
                    } else {
                        "批量下载失败：$details"
                    }
                }
                else -> {
                    val details = failedFiles.take(2).joinToString(", ")
                    "批量下载已启动：$successCount 成功，$failureCount 失败。失败文件：$details"
                }
            }
            _events.emit(CourseFilesEvent.Message(message))
        }
    }

    fun openFile(file: CanvasCourseFile) {
        viewModelScope.launch {
            val state = _uiState.value as? CourseFilesUiState.Success ?: return@launch
            val tree = getRootTreeDocument() ?: return@launch
            val relativeFolder = resolveRelativeFolder(file, state.foldersMap) ?: run {
                _events.emit(CourseFilesEvent.Message("无法解析文件目录: ${file.displayName}"))
                return@launch
            }
            val courseDir = findDirectory(tree, courseIdentifier()) ?: run {
                _events.emit(CourseFilesEvent.Message("文件未下载：${file.displayName}"))
                downloadFileToStorage(file, state, tree)
                    .onSuccess {
                        openFile(file)
                    }
                    .onFailure {
                        _events.emit(CourseFilesEvent.Message(it.message ?: "下载失败: ${file.displayName}"))
                    }
                return@launch
            }

            val targetDir = findDirectory(courseDir, relativeFolder)
            val localFile = targetDir?.findFile(file.displayName)

            if (localFile != null && localFile.isFile) {
                _events.emit(
                    CourseFilesEvent.OpenFile(
                        uri = localFile.uri,
                        mimeType = file.contentType ?: "*/*"
                    )
                )
                return@launch
            }

            _events.emit(CourseFilesEvent.Message("文件未下载，正在下载：${file.displayName}"))
            downloadFileToStorage(file, state, tree)
                .onSuccess {
                    val refreshedCourseDir = findDirectory(tree, courseIdentifier())
                    val refreshedTargetDir = refreshedCourseDir?.let { findDirectory(it, relativeFolder) }
                    val refreshedFile = refreshedTargetDir?.findFile(file.displayName)
                    if (refreshedFile != null && refreshedFile.isFile) {
                        _events.emit(
                            CourseFilesEvent.OpenFile(
                                uri = refreshedFile.uri,
                                mimeType = file.contentType ?: "*/*"
                            )
                        )
                    } else {
                        _events.emit(CourseFilesEvent.Message("下载完成但无法定位文件：${file.displayName}"))
                    }
                }
                .onFailure {
                    _events.emit(CourseFilesEvent.Message(it.message ?: "下载失败: ${file.displayName}"))
                }
        }
    }

    fun syncAll() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            val state = _uiState.value as? CourseFilesUiState.Success ?: return@launch
            val tree = getRootTreeDocument() ?: return@launch
            val filesToSync = state.currentFiles.filter { !it.url.isNullOrBlank() }
            if (filesToSync.isEmpty()) {
                _events.emit(CourseFilesEvent.Message("当前目录没有可同步的文件"))
                return@launch
            }

            _isSyncing.value = true
            _syncingCount.value = filesToSync.size
            try {
                var successCount = 0
                filesToSync.forEach { file ->
                    downloadFileToStorage(file, state, tree)
                        .onSuccess { successCount += 1 }
                }
                _events.emit(CourseFilesEvent.Message("同步完成：$successCount / ${filesToSync.size}"))
            } finally {
                _isSyncing.value = false
                _syncingCount.value = 0
            }
        }
    }

    private suspend fun downloadFileToStorage(
        file: CanvasCourseFile,
        state: CourseFilesUiState.Success,
        rootTree: DocumentFile
    ): Result<Unit> {
        if (file.url.isNullOrBlank()) {
            markProgressFailed(file.id, "无有效URL")
            return Result.failure(IllegalArgumentException("无有效URL"))
        }

        markProgressQueued(file.id, file.size ?: 0L)

        val relativeFolder = resolveRelativeFolder(file, state.foldersMap) ?: run {
            markProgressFailed(file.id, "无法解析文件目录")
            return Result.failure(IllegalStateException("无法解析文件目录: ${file.displayName}"))
        }

        val courseDir = ensureDirectory(rootTree, courseIdentifier()) ?: run {
            markProgressFailed(file.id, "创建课程目录失败")
            return Result.failure(IllegalStateException("创建课程目录失败"))
        }

        val targetDir = ensureDirectory(courseDir, relativeFolder) ?: run {
            markProgressFailed(file.id, "创建目标目录失败")
            return Result.failure(IllegalStateException("创建目标目录失败: $relativeFolder"))
        }

        return repository.downloadCourseFileBytesWithProgress(file) { processed, total ->
            markProgressDownloading(file.id, processed, total)
        }.fold(
            onSuccess = { bytes ->
                if (writeBytesToFile(targetDir, file, bytes)) {
                    val finalTotal = _downloadProgress.value[file.id]?.total
                        ?.takeIf { it > 0 }
                        ?: file.size
                        ?: bytes.size.toLong()
                    markProgressCompleted(file.id, bytes.size.toLong(), finalTotal)
                    Result.success(Unit)
                } else {
                    markProgressFailed(file.id, "写入失败")
                    Result.failure(IllegalStateException("写入失败: ${file.displayName}"))
                }
            },
            onFailure = {
                markProgressFailed(file.id, it.message ?: "下载失败")
                Result.failure(it)
            }
        )
    }

    private fun markProgressQueued(fileId: Long, total: Long) {
        _downloadProgress.value = _downloadProgress.value + (
            fileId to DownloadProgress(
                processed = 0L,
                total = total,
                status = DownloadStatus.QUEUED,
                message = "排队中"
            )
        )
    }

    private fun markProgressDownloading(fileId: Long, processed: Long, total: Long) {
        _downloadProgress.value = _downloadProgress.value + (
            fileId to DownloadProgress(
                processed = processed,
                total = total,
                status = DownloadStatus.DOWNLOADING,
                message = null
            )
        )
    }

    private fun markProgressCompleted(fileId: Long, processed: Long, total: Long) {
        val completedProgress = DownloadProgress(
            processed = processed,
            total = total,
            status = DownloadStatus.COMPLETED,
            message = "下载完成"
        )
        _downloadProgress.value = _downloadProgress.value + (fileId to completedProgress)
        clearTerminalProgressLater(fileId, completedProgress, 2500)
    }

    private fun markProgressFailed(fileId: Long, message: String) {
        val current = _downloadProgress.value[fileId]
        val failedProgress = DownloadProgress(
            processed = current?.processed ?: 0L,
            total = current?.total ?: 0L,
            status = DownloadStatus.FAILED,
            message = message
        )
        _downloadProgress.value = _downloadProgress.value + (fileId to failedProgress)
        clearTerminalProgressLater(fileId, failedProgress, 5000)
    }

    private fun clearTerminalProgressLater(
        fileId: Long,
        target: DownloadProgress,
        delayMs: Long
    ) {
        viewModelScope.launch {
            delay(delayMs)
            if (_downloadProgress.value[fileId] == target) {
                _downloadProgress.value = _downloadProgress.value - fileId
            }
        }
    }

    private suspend fun getRootTreeDocument(): DocumentFile? {
        val uriText = userPreferences.courseFilesTreeUri.first()
        if (uriText.isNullOrBlank()) {
            _events.emit(CourseFilesEvent.Message("请先到设置里配置课程文件存储位置"))
            return null
        }
        val root = DocumentFile.fromTreeUri(context, Uri.parse(uriText))
        if (root == null || !root.canRead() || !root.canWrite()) {
            _events.emit(CourseFilesEvent.Message("目录权限不可用，请到设置重新授权"))
            return null
        }
        return root
    }

    // 对齐 Rust: folder.full_name 以 "course files" 开头；根目录对应空子路径
    private fun resolveRelativeFolder(
        file: CanvasCourseFile,
        foldersMap: Map<Long, CanvasFolder>
    ): String? {
        val folderId = file.folderId ?: return null
        val fullName = foldersMap[folderId]?.fullName ?: return null
        if (fullName.length < 12) return null
        if (fullName == "course files") return ""
        if (!fullName.startsWith("course files/")) return null
        return fullName.substring(13)
    }

    private fun courseIdentifier(): String {
        val fallback = "course_$courseId"
        val identifier = _courseIdentifier.value
        if (!identifier.isNullOrBlank()) {
            return sanitizeFileName(identifier)
        }
        return fallback
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "course_$courseId" }
    }

    private fun ensureDirectory(parent: DocumentFile, relativePath: String): DocumentFile? {
        var current: DocumentFile? = parent
        if (relativePath.isBlank()) return current
        val parts = relativePath
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (part in parts) {
            val existing = current?.findFile(part)
            current = when {
                existing != null && existing.isDirectory -> existing
                existing == null -> current?.createDirectory(part)
                else -> return null
            }
        }
        return current
    }

    private fun findDirectory(parent: DocumentFile, relativePath: String): DocumentFile? {
        var current: DocumentFile? = parent
        if (relativePath.isBlank()) return current
        val parts = relativePath
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (part in parts) {
            val existing = current?.findFile(part)
            if (existing == null || !existing.isDirectory) {
                return null
            }
            current = existing
        }
        return current
    }

    private fun writeBytesToFile(targetDir: DocumentFile, file: CanvasCourseFile, bytes: ByteArray): Boolean {
        val existing = targetDir.findFile(file.displayName)
        val target = when {
            existing != null && existing.isDirectory -> return false
            existing != null && existing.isFile -> existing
            else -> targetDir.createFile(file.contentType ?: "application/octet-stream", file.displayName)
        } ?: return false

        return runCatching {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { os ->
                os.write(bytes)
            } ?: error("无法写入文件")
        }.isSuccess
    }

}
