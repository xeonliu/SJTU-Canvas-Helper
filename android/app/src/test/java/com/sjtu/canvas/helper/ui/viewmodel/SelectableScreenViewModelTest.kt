package com.sjtu.canvas.helper.ui.viewmodel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SelectableScreenViewModelTest {
    private lateinit var viewModel: TestableSelectableScreenViewModel

    @Before
    fun setUp() {
        viewModel = TestableSelectableScreenViewModel()
    }

    @Test
    fun testToggleFileSelection() = runTest {
        val fileId = "file1"
        
        // Initially, file should not be selected
        assertFalse(viewModel.isFileSelected(fileId))
        
        // Toggle selection
        viewModel.toggleFileSelection(fileId)
        assertTrue(viewModel.isFileSelected(fileId))
        
        // Toggle again to deselect
        viewModel.toggleFileSelection(fileId)
        assertFalse(viewModel.isFileSelected(fileId))
    }

    @Test
    fun testSelectFile() = runTest {
        val fileId = "file1"
        viewModel.selectFile(fileId)
        assertTrue(viewModel.isFileSelected(fileId))
        assertEquals(1, viewModel.getSelectedCount())
    }

    @Test
    fun testDeselectFile() = runTest {
        val fileId = "file1"
        viewModel.selectFile(fileId)
        assertTrue(viewModel.isFileSelected(fileId))
        
        viewModel.deselectFile(fileId)
        assertFalse(viewModel.isFileSelected(fileId))
    }

    @Test
    fun testClearSelection() = runTest {
        viewModel.selectFile("file1")
        viewModel.selectFile("file2")
        viewModel.selectFile("file3")
        assertEquals(3, viewModel.getSelectedCount())
        
        viewModel.clearSelection()
        assertEquals(0, viewModel.getSelectedCount())
    }

    @Test
    fun testSelectAll() = runTest {
        val fileIds = listOf("file1", "file2", "file3")
        viewModel.selectAll(fileIds)
        
        assertEquals(3, viewModel.getSelectedCount())
        fileIds.forEach { assertTrue(viewModel.isFileSelected(it)) }
    }

    @Test
    fun testMultipleSelectionsAndDeselections() = runTest {
        val ids = listOf("file1", "file2", "file3", "file4", "file5")
        
        // Select all
        viewModel.selectAll(ids)
        assertEquals(5, viewModel.getSelectedCount())
        
        // Deselect some
        viewModel.deselectFile("file1")
        viewModel.deselectFile("file3")
        assertEquals(3, viewModel.getSelectedCount())
        
        // Toggle
        viewModel.toggleFileSelection("file1") // select back
        assertEquals(4, viewModel.getSelectedCount())
        
        // Clear all
        viewModel.clearSelection()
        assertEquals(0, viewModel.getSelectedCount())
    }

    /**
     * Concrete implementation for testing the abstract class.
     */
    private class TestableSelectableScreenViewModel : SelectableScreenViewModel()
}
