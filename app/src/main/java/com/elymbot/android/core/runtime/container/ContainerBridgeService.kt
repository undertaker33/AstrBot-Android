package com.elymbot.android.core.runtime.container

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.elymbot.android.core.common.logging.RuntimeLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class ContainerBridgeService : Service() {
    @Inject
    lateinit var bridgeStatePort: ContainerBridgeStatePort

    @Inject
    lateinit var containerRuntimeInstaller: ContainerRuntimeInstallerPort

    @Inject
    lateinit var containerRuntimeController: ContainerRuntimeController

    @Inject
    lateinit var runtimeLogger: RuntimeLogger

    @Inject
    lateinit var bridgeHealthChecker: BridgeHealthChecker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressMonitorJob: Job? = null
    private var healthMonitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Container idle"))
        runtimeLogger.append("ContainerBridgeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BRIDGE -> serviceScope.launch { handleStartBridge() }
            ACTION_STOP_BRIDGE -> serviceScope.launch { handleStopBridge() }
            ACTION_CHECK_BRIDGE -> serviceScope.launch { handleCheckBridge() }
            else -> runtimeLogger.append("Bridge service received default start")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopHealthMonitor()
        stopProgressMonitor()
        serviceScope.cancel()
        runtimeLogger.append("ContainerBridgeService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun handleStartBridge() {
        stopHealthMonitor()
        bridgeStatePort.markStarting()
        containerRuntimeInstaller.ensureInstalled()
        val config = bridgeStatePort.config.value
        syncProgressFromRuntimeFiles()
        startProgressMonitor()
        runtimeLogger.append("Starting local NapCat bridge")
        updateForeground("Starting NapCat")

        val result = containerRuntimeController.startNapCat()
        if (result.exitCode == 0) {
            runtimeLogger.append("Start command completed: ${result.stdout.ifBlank { "no output" }}")
            syncProgressFromRuntimeFiles()
            runtimeLogger.append("Waiting for NapCat health endpoint")

            val health = bridgeHealthChecker.checkWithRetry(config.healthUrl)
            if (health.ok) {
                stopHealthMonitor()
                stopProgressMonitor()
                bridgeStatePort.markRunning(pidHint = "napcat-local")
                runtimeLogger.append("Health check passed after start: ${health.message}")
                updateForeground("NapCat running")
                return
            }

            val statusResult = containerRuntimeController.statusNapCat()
            if (statusResult.exitCode == 0) {
                val pidHint = statusResult.stdout.substringAfter("RUNNING:", "napcat-local").trim().ifBlank { "napcat-local" }
                bridgeStatePort.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is running, but HTTP endpoint is not ready: ${health.message}",
                )
                syncProgressFromRuntimeFiles()
                runtimeLogger.append("Health endpoint not ready yet, but process is running: ${health.message}")
                appendContainerLogTail("NapCat log")
                updateForeground(buildProgressNotificationText())
                startHealthMonitor(config.healthUrl, pidHint)
            } else {
                stopHealthMonitor()
                stopProgressMonitor()
                bridgeStatePort.markError("Started, but health check failed: ${health.message}")
                runtimeLogger.append("Health check failed after start: ${health.message}")
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat start incomplete")
            }
        } else {
            stopHealthMonitor()
            stopProgressMonitor()
            bridgeStatePort.markError("Start command failed: ${result.stderr.ifBlank { result.stdout }}")
            runtimeLogger.append("Start command failed: ${result.stderr.ifBlank { result.stdout }}")
            appendContainerLogTail("NapCat log")
            updateForeground("NapCat start failed")
        }
    }

    private fun startHealthMonitor(url: String, pidHint: String) {
        if (healthMonitorJob?.isActive == true) return

        healthMonitorJob = serviceScope.launch {
            try {
                waitForHealthy(url, pidHint)
            } finally {
                healthMonitorJob = null
            }
        }
    }

    private fun stopHealthMonitor() {
        healthMonitorJob?.cancel()
        healthMonitorJob = null
    }

    private suspend fun waitForHealthy(url: String, pidHint: String) {
        val startedAt = System.currentTimeMillis()
        var lastActivityAt = runtimeActivityTimestamp().takeIf { it > 0L } ?: startedAt

        while (true) {
            delay(HEALTH_POLL_INTERVAL_MS)
            val snapshot = syncProgressFromRuntimeFiles()
            runtimeActivityTimestamp()
                .takeIf { it > 0L }
                ?.let { lastActivityAt = maxOf(lastActivityAt, it) }

            val health = bridgeHealthChecker.check(url)
            if (health.ok) {
                stopProgressMonitor()
                bridgeStatePort.markRunning(pidHint = pidHint)
                runtimeLogger.append("Health endpoint became ready: ${health.message}")
                updateForeground("NapCat running")
                return
            }

            val statusResult = containerRuntimeController.statusNapCat()
            if (statusResult.exitCode != 0) {
                stopProgressMonitor()
                syncProgressFromRuntimeFiles()
                bridgeStatePort.markStopped(reason = "NapCat process exited during startup")
                runtimeLogger.append(
                    "NapCat process exited before health endpoint became ready: ${statusResult.stderr.ifBlank { statusResult.stdout }}",
                )
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat stopped")
                return
            }

            bridgeStatePort.markProcessRunning(
                pidHint = pidHint,
                details = buildPendingHealthDetails(snapshot, health, startedAt),
            )
            updateForeground(buildProgressNotificationText())

            val now = System.currentTimeMillis()
            val elapsed = now - startedAt
            val silentFor = now - lastActivityAt
            if (elapsed >= MAX_STARTUP_WAIT_MS) {
                stopProgressMonitor()
                syncProgressFromRuntimeFiles()
                bridgeStatePort.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is still running, but WebUI startup exceeded ${MAX_STARTUP_WAIT_MS / 60000} minutes. Check runtime logs.",
                )
                runtimeLogger.append("Health endpoint still not ready after max wait: elapsed=${elapsed / 1000}s")
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat still warming up")
                return
            }

            if (elapsed >= MIN_WAIT_BEFORE_STALE_TIMEOUT_MS && silentFor >= STALE_ACTIVITY_TIMEOUT_MS) {
                stopProgressMonitor()
                syncProgressFromRuntimeFiles()
                bridgeStatePort.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is still running, but no runtime activity was seen for ${silentFor / 60000} minutes. Check runtime logs.",
                )
                runtimeLogger.append(
                    "Health endpoint still not ready and runtime activity is stale: elapsed=${elapsed / 1000}s silent=${silentFor / 1000}s",
                )
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat startup stalled")
                return
            }
        }
    }

    private suspend fun handleStopBridge() {
        containerRuntimeInstaller.ensureInstalled()
        stopHealthMonitor()
        stopProgressMonitor()
        val result = containerRuntimeController.stopNapCat()
        runtimeLogger.append(
            if (result.exitCode == 0) {
                "Stop command completed"
            } else {
                "Stop command failed: ${result.stderr.ifBlank { result.stdout }}"
            },
        )
        bridgeStatePort.markStopped()
        updateForeground("NapCat stopped")
    }

    private suspend fun handleCheckBridge() {
        containerRuntimeInstaller.ensureInstalled()
        val config = bridgeStatePort.config.value
        bridgeStatePort.markChecking()
        syncProgressFromRuntimeFiles()
        runtimeLogger.append("Checking local NapCat bridge")

        val statusResult = containerRuntimeController.statusNapCat()
        runtimeLogger.append(
            if (statusResult.exitCode == 0) {
                "Status command completed: ${statusResult.stdout.ifBlank { "ok" }}"
            } else {
                "Status command failed: ${statusResult.stderr.ifBlank { statusResult.stdout }}"
            },
        )

        val health = bridgeHealthChecker.check(config.healthUrl)
        if (health.ok) {
            stopHealthMonitor()
            stopProgressMonitor()
            bridgeStatePort.markRunning(pidHint = "napcat-local")
            runtimeLogger.append("Bridge health check passed: ${health.message}")
            updateForeground("NapCat healthy")
            return
        }

        if (statusResult.exitCode == 0) {
            val pidHint = statusResult.stdout.substringAfter("RUNNING:", "napcat-local").trim().ifBlank { "napcat-local" }
            bridgeStatePort.markProcessRunning(
                pidHint = pidHint,
                details = "NapCat process is running, but HTTP endpoint is not ready: ${health.message}",
            )
            syncProgressFromRuntimeFiles()
            runtimeLogger.append("Bridge process is running but endpoint is not ready: ${health.message}")
            startProgressMonitor()
            startHealthMonitor(config.healthUrl, pidHint)
            updateForeground(buildProgressNotificationText())
            return
        }

        stopHealthMonitor()
        stopProgressMonitor()
        bridgeStatePort.markStopped(reason = "Health check failed")
        runtimeLogger.append("Bridge health check failed: ${health.message}")
        updateForeground("NapCat stopped")
    }

    private fun startProgressMonitor() {
        if (progressMonitorJob?.isActive == true) return

        progressMonitorJob = serviceScope.launch {
            while (isActive) {
                syncProgressFromRuntimeFiles()
                updateForeground(buildProgressNotificationText())
                delay(1000)
            }
        }
    }

    private fun stopProgressMonitor() {
        progressMonitorJob?.cancel()
        progressMonitorJob = null
    }

    private fun syncProgressFromRuntimeFiles(): RuntimeProgressSnapshot {
        val snapshot = ContainerBridgeRuntimeSupport.loadProgressSnapshot(filesDir)

        bridgeStatePort.markInstallerCached(snapshot.installerCached)
        if (snapshot.label.isNotBlank() || snapshot.percent > 0) {
            bridgeStatePort.updateProgress(
                label = snapshot.label,
                percent = snapshot.percent,
                indeterminate = snapshot.indeterminate,
                installerCached = snapshot.installerCached,
            )
        }

        return snapshot
    }

    private fun buildProgressNotificationText(): String {
        return ContainerBridgeRuntimeSupport.buildProgressNotificationText(bridgeStatePort.runtimeState.value)
    }

    private fun buildPendingHealthDetails(
        snapshot: RuntimeProgressSnapshot,
        health: HealthCheckResult,
        startedAt: Long,
    ): String {
        return ContainerBridgeRuntimeSupport.buildPendingHealthDetails(
            snapshot = snapshot,
            health = health,
            startedAtMs = startedAt,
        )
    }

    private fun runtimeActivityTimestamp(): Long {
        return ContainerBridgeRuntimeSupport.runtimeActivityTimestamp(filesDir)
    }

    private fun appendContainerLogTail(prefix: String, maxLines: Int = 200) {
        val logFile = File(filesDir, "runtime/logs/napcat.log")
        if (!logFile.exists()) {
            runtimeLogger.append("$prefix unavailable: ${logFile.absolutePath}")
            return
        }

        val lines = logFile.readLines()
            .takeLast(maxLines)
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            runtimeLogger.append("$prefix is empty")
            return
        }

        lines.forEach { runtimeLogger.append("$prefix | $it") }
    }

    private fun updateForeground(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ElymBot Runtime",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("ElymBot Runtime")
            .setContentText(content)
            .setOngoing(true)
            .build()
    }

    private fun File.readSafeText(): String {
        return if (exists()) {
            runCatching { readText().trim() }.getOrDefault("")
        } else {
            ""
        }
    }

    private fun File.readIntOrDefault(defaultValue: Int): Int {
        return readSafeText().toIntOrNull() ?: defaultValue
    }
    companion object {
        const val ACTION_START_BRIDGE = "com.elymbot.android.action.START_BRIDGE"
        const val ACTION_STOP_BRIDGE = "com.elymbot.android.action.STOP_BRIDGE"
        const val ACTION_CHECK_BRIDGE = "com.elymbot.android.action.CHECK_BRIDGE"

        private const val HEALTH_POLL_INTERVAL_MS = 5_000L
        private const val MAX_STARTUP_WAIT_MS = 45 * 60 * 1000L
        private const val MIN_WAIT_BEFORE_STALE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val STALE_ACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L
        private const val CHANNEL_ID = "elymbot_runtime"
        private const val NOTIFICATION_ID = 1001
    }
}
