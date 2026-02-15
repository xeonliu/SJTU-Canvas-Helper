package com.sjtu.canvas.helper.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base ViewModel class for screens that support multi-select functionality.
 * Manages selection state via StateFlow for reactive composition updates.
 * 
 * Subclasses should extend this to inherit file selection capabilities.
 */
abstract class SelectableScreenViewModel : ViewModel() {
    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()

    /**
     * Toggle selection state of a file.
     * If the file is selected, deselect it. If deselected, select it.
     */
    fun toggleFileSelection(fileId: String) {
        val currentSet = _selectedFileIds.value.toMutableSet()
        if (currentSet.contains(fileId)) {
            currentSet.remove(fileId)
        } else {
            currentSet.add(fileId)
        }
        _selectedFileIds.value = currentSet
    }

    /**
     * Select a file.
     */
    fun selectFile(fileId: String) {
        val currentSet = _selectedFileIds.value.toMutableSet()
        currentSet.add(fileId)
        _selectedFileIds.value = currentSet
    }

    /**
     * Deselect a file.
     */
    fun deselectFile(fileId: String) {
        val currentSet = _selectedFileIds.value.toMutableSet()
        currentSet.remove(fileId)
        _selectedFileIds.value = currentSet
    }

    /**
     * Clear all selections.
     */
    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    /**
     * Select all files from the given list.
     */
    fun selectAll(fileIds: List<String>) {
        _selectedFileIds.value = fileIds.toSet()
    }

    /**
     * Check if a file is currently selected.
     */
    fun isFileSelected(fileId: String): Boolean {
        return _selectedFileIds.value.contains(fileId)
    }

    /**
     * Get count of selected files.
     */
    fun getSelectedCount(): Int {
        return _selectedFileIds.value.size
    }

    /**
     * Clear selection state when ViewModel is cleared.
     * This ensures no lingering selection state on navigating away.
     */
    override fun onCleared() {
        super.onCleared()
        clearSelection()
    }
}
