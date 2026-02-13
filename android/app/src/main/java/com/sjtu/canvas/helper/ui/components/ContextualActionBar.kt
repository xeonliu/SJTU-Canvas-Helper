package com.sjtu.canvas.helper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material 3-styled Contextual Action Bar (CAB) for multi-select operations.
 * Appears at the bottom when files are selected, disappears when selection is empty.
 *
 * @param isVisible Whether the CAB should be visible
 * @param selectedCount Number of selected files
 * @param onDownloadClick Callback when download button is clicked
 * @param onCloseClick Callback when close/back button is clicked
 * @param modifier Modifier for customization
 */
@Composable
fun ContextualActionBar(
    isVisible: Boolean,
    selectedCount: Int,
    onDownloadClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button and selection count on the left
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close multi-select",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Text(
                        text = when (selectedCount) {
                            1 -> "1 selected"
                            else -> "$selectedCount selected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // Download button on the right
                IconButton(
                    onClick = onDownloadClick,
                    enabled = selectedCount > 0,
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download selected files",
                        tint = if (selectedCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
        }
    }
}
