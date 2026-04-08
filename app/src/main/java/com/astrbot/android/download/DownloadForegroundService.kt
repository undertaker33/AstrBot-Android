package com.astrbot.android.download

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifications: DownloadNotificationController
    private var processingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications = DownloadNotificationController(this)
        notifications.ensureChannel()
        updateForeground(task = null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (processingJob?.isActive == true) {
            return START_STICKY
        }
        processingJob = serviceScope.launch {
            AppDownloadManager.processQueue { task ->
                updateForeground(task)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        processingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateForeground(task: DownloadTaskRecord?) {
        val notification = notifications.build(task)
        startForeground(
            DownloadNotificationController.NOTIFICATION_ID,
            notification,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            DownloadNotificationController.NOTIFICATION_ID,
            notification,
        )
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction("com.astrbot.android.action.PROCESS_DOWNLOAD_QUEUE")
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
