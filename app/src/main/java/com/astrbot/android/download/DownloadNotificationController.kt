package com.astrbot.android.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import java.util.Locale

class DownloadNotificationController(
    private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AstrBot Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun build(task: DownloadTaskRecord?): Notification {
        val title = task?.displayName ?: "AstrBot Downloads"
        val text = when {
            task == null -> "Waiting for download tasks"
            task.status == DownloadTaskStatus.COMPLETED -> "Download complete"
            task.status == DownloadTaskStatus.FAILED -> task.errorMessage.ifBlank { "Download failed" }
            task.status == DownloadTaskStatus.PAUSED -> "Download paused"
            task.totalBytes != null && task.totalBytes > 0L -> {
                "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
            }
            else -> "Downloading"
        }
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(task?.status == DownloadTaskStatus.RUNNING || task?.status == DownloadTaskStatus.QUEUED)
        val progress = task?.progressFraction
        if (task != null && task.status != DownloadTaskStatus.COMPLETED && task.status != DownloadTaskStatus.FAILED) {
            builder.setProgress(
                100,
                ((progress ?: 0f) * 100f).toInt().coerceIn(0, 100),
                progress == null,
            )
        } else {
            builder.setProgress(0, 0, false)
        }
        return builder.build()
    }

    private fun formatBytes(bytes: Long): String {
        val megabytes = bytes.toDouble() / 1_048_576.0
        return String.format(Locale.US, "%.1f MB", megabytes)
    }

    companion object {
        const val CHANNEL_ID: String = "astrbot_downloads"
        const val NOTIFICATION_ID: Int = 2048
    }
}
