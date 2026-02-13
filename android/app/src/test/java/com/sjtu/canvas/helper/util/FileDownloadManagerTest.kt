package com.sjtu.canvas.helper.util

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import com.sjtu.canvas.helper.data.model.CanvasCourseFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileDownloadManagerTest {
    private lateinit var fileDownloadManager: FileDownloadManager
    private lateinit var mockDownloadManager: DownloadManager
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockDownloadManager = mockk(relaxed = true)
        
        // Mock the system service
        every { mockContext.getSystemService(Context.DOWNLOAD_SERVICE) } returns mockDownloadManager
        every { mockDownloadManager.enqueue(any()) } returns 1L
        
        fileDownloadManager = FileDownloadManager(mockContext)
    }

    @Test
    fun testDownloadSingleFile() {
        val file = createMockFile(id = 1, filename = "test.pdf", url = "https://example.com/test.pdf")
        
        val downloadIds = fileDownloadManager.downloadFiles(listOf(file))
        
        assertEquals(1, downloadIds.size)
        assertEquals(1L, downloadIds[0])
    }

    @Test
    fun testDownloadMultipleFiles() {
        val files = listOf(
            createMockFile(id = 1, filename = "file1.pdf", url = "https://example.com/file1.pdf"),
            createMockFile(id = 2, filename = "file2.pdf", url = "https://example.com/file2.pdf"),
            createMockFile(id = 3, filename = "file3.pdf", url = "https://example.com/file3.pdf")
        )
        
        val downloadIds = fileDownloadManager.downloadFiles(files)
        
        assertEquals(3, downloadIds.size)
        assertTrue(downloadIds.all { it == 1L })
    }

    @Test
    fun testDownloadWithSubdirectory() {
        val file = createMockFile(id = 1, filename = "test.pdf", url = "https://example.com/test.pdf")
        
        val downloadIds = fileDownloadManager.downloadFiles(listOf(file), subdirectory = "course_name/section")
        
        assertEquals(1, downloadIds.size)
    }

    @Test
    fun testDownloadFileWithNullUrlThrowsException() {
        val file = createMockFile(id = 1, filename = "test.pdf", url = null)
        
        val downloadIds = fileDownloadManager.downloadFiles(listOf(file))
        
        // Should handle gracefully and return empty list
        assertEquals(0, downloadIds.size)
    }

    @Test
    fun testHandlesMultipleFilesWithMixedValidity() {
        val files = listOf(
            createMockFile(id = 1, filename = "file1.pdf", url = "https://example.com/file1.pdf"),
            createMockFile(id = 2, filename = "file2.pdf", url = null),
            createMockFile(id = 3, filename = "file3.pdf", url = "https://example.com/file3.pdf")
        )
        
        val downloadIds = fileDownloadManager.downloadFiles(files)
        
        // Should handle errors gracefully and continue
        assertEquals(2, downloadIds.size) // Only 2 valid downloads
    }

    @Test
    fun testCancelDownload() {
        fileDownloadManager.cancelDownload(1L)
        verify { mockDownloadManager.remove(1L) }
    }

    @Test
    fun testCancelMultipleDownloads() {
        val downloadIds = listOf(1L, 2L, 3L)
        fileDownloadManager.cancelDownloads(downloadIds)
        
        downloadIds.forEach { id ->
            verify { mockDownloadManager.remove(id) }
        }
    }

    /**
     * Helper function to create a mock CanvasCourseFile.
     */
    private fun createMockFile(
        id: Long,
        filename: String,
        url: String?
    ): CanvasCourseFile {
        return mockk<CanvasCourseFile>(relaxed = true).apply {
            every { this@apply.id } returns id
            every { this@apply.filename } returns filename
            every { this@apply.url } returns url
            every { this@apply.displayName } returns filename
        }
    }
}
