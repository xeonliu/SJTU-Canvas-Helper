package com.sjtu.canvas.helper.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * BroadcastReceiver for listening to download completion events.
 * Registers and unregisters the receiver to listen for DownloadManager completion actions.
 */
class DownloadCompletionReceiver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var receiver: BroadcastReceiver? = null
    private var isRegistered = false

    /**
     * Register the broadcast receiver to listen for download completions.
     * 
     * @param onDownloadComplete Callback when a download completes
     */
    fun registerReceiver(onDownloadComplete: (downloadId: Long, success: Boolean) -> Unit) {
        if (isRegistered) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    // Extract download ID from intent extras
                    val downloadId = intent?.getLongExtra("extra_download_id", -1L) ?: -1L
                    if (downloadId != -1L) {
                        val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        downloadManager?.let {
                            try {
                                val cursor = it.query(DownloadManager.Query().setFilterById(downloadId))
                                if (cursor.moveToFirst()) {
                                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                    if (statusIndex >= 0) {
                                        val status = cursor.getInt(statusIndex)
                                        val success = status == DownloadManager.STATUS_SUCCESSFUL
                                        onDownloadComplete(downloadId, success)
                                    }
                                }
                                cursor.close()
                            } catch (e: Exception) {
                                android.util.Log.e("DownloadCompletionReceiver", "Error processing download completion", e)
                            }
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        
        // Use ContextCompat.registerReceiver for API 30+ compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.registerReceiver(
                context,
                receiver!!,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver!!, intentFilter)
        }
        
        isRegistered = true
    }

    /**
     * Unregister the broadcast receiver.
     * Call this when you no longer need to listen for download completions.
     */
    fun unregisterReceiver() {
        if (!isRegistered || receiver == null) return
        
        try {
            context.unregisterReceiver(receiver!!)
            isRegistered = false
        } catch (e: Exception) {
            android.util.Log.e("DownloadCompletionReceiver", "Error unregistering receiver", e)
        }
    }

    /**
     * Check if the receiver is currently registered.
     */
    fun isReceiverRegistered(): Boolean = isRegistered
}
