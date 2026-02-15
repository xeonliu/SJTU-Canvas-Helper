package com.sjtu.canvas.helper.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.sjtu.canvas.helper.R
import com.sjtu.canvas.helper.data.model.Assignment
import com.sjtu.canvas.helper.ui.components.ContextualActionBar
import com.sjtu.canvas.helper.ui.viewmodel.AssignmentsUiState
import com.sjtu.canvas.helper.ui.viewmodel.AssignmentsViewModel
import com.sjtu.canvas.helper.ui.viewmodel.UploadState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssignmentsScreen(
    courseId: Long,
    onNavigateBack: () -> Unit,
    viewModel: AssignmentsViewModel = hiltViewModel()
) {
    var showUploadDialog by remember { mutableStateOf(false) }
    var selectedAssignment by remember { mutableStateOf<Assignment?>(null) }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val selectedFileIds by viewModel.selectedFileIds.collectAsState()

    LaunchedEffect(uploadState) {
        if (uploadState is UploadState.Success) {
            showUploadDialog = false
            selectedAssignment = null
            viewModel.resetUploadState()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assignments_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is AssignmentsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AssignmentsUiState.Error -> {
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

            is AssignmentsUiState.Success -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.assignments) { assignment ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            selectedAssignment = assignment
                                            showUploadDialog = true
                                        },
                                        onLongClick = {
                                            viewModel.toggleFileSelection(assignment.id.toString())
                                        }
                                    )
                            ) {
                                AssignmentCard(
                                    assignment = assignment,
                                    isSelected = assignment.id.toString() in selectedFileIds,
                                    onUploadClick = {
                                        selectedAssignment = assignment
                                        showUploadDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // Contextual Action Bar for multi-select
                    ContextualActionBar(
                        isVisible = selectedFileIds.isNotEmpty(),
                        selectedCount = selectedFileIds.size,
                        onDownloadClick = {
                            viewModel.clearSelection()
                        },
                        onCloseClick = {
                            viewModel.clearSelection()
                        }
                    )
                }
            }
        }
    }
    
    if (showUploadDialog && selectedAssignment != null) {
        UploadDialog(
            assignment = selectedAssignment!!,
            onDismiss = {
                showUploadDialog = false
                selectedAssignment = null
                viewModel.resetUploadState()
            },
            onUpload = { uri ->
                val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (fileBytes == null) {
                    viewModel.resetUploadState()
                    return@UploadDialog
                }
                val fileName = context.resolveFileName(uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                viewModel.uploadAssignment(
                    assignmentId = selectedAssignment!!.id,
                    fileName = fileName,
                    fileBytes = fileBytes,
                    mimeType = mimeType
                )
            }
        )
    }

    if (uploadState is UploadState.Uploading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    if (uploadState is UploadState.Error) {
        val message = (uploadState as UploadState.Error).message
        AlertDialog(
            onDismissRequest = { viewModel.resetUploadState() },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetUploadState() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

private fun android.content.Context.resolveFileName(uri: Uri): String {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment ?: "upload_file"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentCard(
    assignment: Assignment,
    isSelected: Boolean = false,
    onUploadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier
                        .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = assignment.name?.takeIf { it.isNotBlank() } ?: "未命名作业",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                assignment.description?.let { desc ->
                    val descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            TextView(context).apply {
                                textSize = 14f
                                setTextColor(descriptionColor)
                            }
                        },
                        update = { textView ->
                            textView.text = HtmlCompat.fromHtml(
                                desc,
                                HtmlCompat.FROM_HTML_MODE_LEGACY
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        assignment.dueAt?.let { dueDate ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = dueDate.substring(0, 10), // Simple date formatting
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = onUploadClick,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.assignments_upload))
                    }
                }
            }
            
            // Show checkbox when selected
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Checkbox(checked = true, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
fun UploadDialog(
    assignment: Assignment,
    onDismiss: () -> Unit,
    onUpload: (Uri) -> Unit
) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.assignments_upload)) },
        text = {
            Column {
                Text("作业: ${assignment.name?.takeIf { it.isNotBlank() } ?: "未命名作业"}")
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { launcher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedFileUri?.lastPathSegment ?: "选择文件")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedFileUri?.let { onUpload(it) }
                },
                enabled = selectedFileUri != null
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
