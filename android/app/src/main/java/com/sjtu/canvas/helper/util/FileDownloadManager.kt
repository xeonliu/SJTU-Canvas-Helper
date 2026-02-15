package com.sjtu.canvas.helper.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import com.sjtu.canvas.helper.data.model.CanvasCourseFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manages file downloads using Android's DownloadManager API.
 * Files are downloaded to the device's Downloads folder.
 */
class FileDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Download multiple files.
     * Each file will be downloaded as a separate DownloadManager request.
     *
     * @param files List of files to download
     * @param subdirectory Optional subdirectory within Downloads folder
     * @return List of download IDs for tracking progress
     */
    fun downloadFiles(files: List<CanvasCourseFile>, subdirectory: String? = null): List<Long> {
        val downloadIds = mutableListOf<Long>()

        for (file in files) {
            try {
                val downloadId = downloadFile(file, subdirectory)
                downloadIds.add(downloadId)
            } catch (e: Exception) {
                // Log error but continue with other files
                android.util.Log.e("FileDownloadManager", "Failed to queue download for ${file.filename}", e)
            }
        }

        return downloadIds
    }

    /**
     * Download a single file.
     *
     * @param file The file to download
     * @param subdirectory Optional subdirectory within Downloads folder
     * @return The download ID
     */
    fun downloadFile(file: CanvasCourseFile, subdirectory: String? = null): Long {
        val downloadUri = file.url?.toUri() ?: throw IllegalArgumentException("File URL is null")
        val filename = file.filename ?: "download"

        val destinationPath = if (subdirectory?.isNotBlank() == true) {
            "$subdirectory/$filename"
        } else {
            filename
        }

        val request = DownloadManager.Request(downloadUri).apply {
            setTitle(filename)
            setDescription("Downloading file...")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationPath)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        return downloadManager.enqueue(request)
    }

    /**
     * Cancel a download by ID.
     */
    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
    }

    /**
     * Cancel multiple downloads.
     */
    fun cancelDownloads(downloadIds: List<Long>) {
        downloadIds.forEach { cancelDownload(it) }
    }

    /**
     * Get the DownloadManager for query operations.
     */
    fun getDownloadManager(): DownloadManager {
        return downloadManager
    }

    /**
     * Check if downloads are allowed over cellular connections.
     */
    fun areDownloadsAllowedOnMetered(): Boolean {
        return true
    }
}
