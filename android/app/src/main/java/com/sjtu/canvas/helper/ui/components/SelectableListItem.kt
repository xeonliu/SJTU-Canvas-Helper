package com.sjtu.canvas.helper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * Wrapper Composable for file list items that adds multi-select functionality.
 * Displays a checkbox indicator and handles selection state changes.
 *
 * @param fileId Unique identifier for the file
 * @param isSelected Whether the file is currently selected
 * @param onSelectionChange Callback when selection state changes
 * @param isSelectionMode Whether multi-select mode is active
 * @param onLongPress Callback when user long-presses the item (to enter select mode)
 * @param content The content to display (usually the file item UI)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableListItem(
    fileId: String,
    isSelected: Boolean,
    onSelectionChange: (String, Boolean) -> Unit,
    isSelectionMode: Boolean,
    onLongPress: () -> Unit,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    // Animate background color based on selection
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected && isSelectionMode) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            Color.Transparent
        },
        label = "selectionBackground"
    )

    Box(
        modifier = Modifier
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectionChange(fileId, !isSelected)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onLongClick = {
                    // Enter selection mode and select this item
                    onLongPress()
                    onSelectionChange(fileId, true)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 8.dp)
        ) {
            // Show checkbox only in selection mode
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { newChecked ->
                        onSelectionChange(fileId, newChecked)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                )
            }

            // File item content
            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                content()
            }
        }
    }
}
