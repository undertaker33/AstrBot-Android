package com.astrbot.android.core.runtime.container

import com.astrbot.android.core.common.logging.RuntimeLogRepository

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.astrbot.android.model.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class ContainerBridgeService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressMonitorJob: Job? = null
    private var healthMonitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Container idle"))
        RuntimeLogRepository.append("ContainerBridgeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BRIDGE -> serviceScope.launch { handleStartBridge() }
            ACTION_STOP_BRIDGE -> serviceScope.launch { handleStopBridge() }
            ACTION_CHECK_BRIDGE -> serviceScope.launch { handleCheckBridge() }
            else -> RuntimeLogRepository.append("Bridge service received default start")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopHealthMonitor()
        stopProgressMonitor()
        serviceScope.cancel()
        RuntimeLogRepository.append("ContainerBridgeService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun handleStartBridge() {
        val bridgeState = ContainerBridgeStateRegistry.port
        stopHealthMonitor()
        bridgeState.markStarting()
        ContainerRuntimeInstaller.ensureInstalled(applicationContext)
        val config = bridgeState.config.value
        syncProgressFromRuntimeFiles()
        startProgressMonitor()
        RuntimeLogRepository.append("Starting local NapCat bridge")
        updateForeground("Starting NapCat")

        val result = BridgeCommandRunner.execute(config.startCommand)
        if (result.exitCode == 0 || config.startCommand.isBlank()) {
            RuntimeLogRepository.append("Start command completed: ${result.stdout.ifBlank { "no output" }}")
            syncProgressFromRuntimeFiles()
            RuntimeLogRepository.append("Waiting for NapCat health endpoint")

            val health = BridgeHealthChecker.checkWithRetry(config.healthUrl)
            if (health.ok) {
                stopHealthMonitor()
                stopProgressMonitor()
                bridgeState.markRunning(pidHint = "napcat-local")
                RuntimeLogRepository.append("Health check passed after start: ${health.message}")
                updateForeground("NapCat running")
                return
            }

            val statusResult = BridgeCommandRunner.execute(config.statusCommand)
            if (statusResult.exitCode == 0) {
                val pidHint = statusResult.stdout.substringAfter("RUNNING:", "napcat-local").trim().ifBlank { "napcat-local" }
                bridgeState.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is running, but HTTP endpoint is not ready: ${health.message}",
                )
                syncProgressFromRuntimeFiles()
                RuntimeLogRepository.append("Health endpoint not ready yet, but process is running: ${health.message}")
                appendContainerLogTail("NapCat log")
                updateForeground(buildProgressNotificationText())
                startHealthMonitor(config.healthUrl, pidHint)
            } else {
                stopHealthMonitor()
                stopProgressMonitor()
                bridgeState.markError("Started, but health check failed: ${health.message}")
                RuntimeLogRepository.append("Health check failed after start: ${health.message}")
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat start incomplete")
            }
        } else {
            stopHealthMonitor()
            stopProgressMonitor()
            bridgeState.markError("Start command failed: ${result.stderr.ifBlank { result.stdout }}")
            RuntimeLogRepository.append("Start command failed: ${result.stderr.ifBlank { result.stdout }}")
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
        val bridgeState = ContainerBridgeStateRegistry.port
        val config = bridgeState.config.value
        val startedAt = System.currentTimeMillis()
        var lastActivityAt = runtimeActivityTimestamp().takeIf { it > 0L } ?: startedAt

        while (true) {
            delay(HEALTH_POLL_INTERVAL_MS)
            val snapshot = syncProgressFromRuntimeFiles()
            runtimeActivityTimestamp()
                .takeIf { it > 0L }
                ?.let { lastActivityAt = maxOf(lastActivityAt, it) }

            val health = BridgeHealthChecker.check(url)
            if (health.ok) {
                stopProgressMonitor()
                bridgeState.markRunning(pidHint = pidHint)
                RuntimeLogRepository.append("Health endpoint became ready: ${health.message}")
                updateForeground("NapCat running")
                return
            }

            val statusResult = BridgeCommandRunner.execute(config.statusCommand)
            if (config.statusCommand.isNotBlank() && statusResult.exitCode != 0) {
                stopProgressMonitor()
                syncProgressFromRuntimeFiles()
                bridgeState.markStopped(reason = "NapCat process exited during startup")
                RuntimeLogRepository.append(
                    "NapCat process exited before health endpoint became ready: ${statusResult.stderr.ifBlank { statusResult.stdout }}",
                )
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat stopped")
                return
            }

            bridgeState.markProcessRunning(
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
                bridgeState.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is still running, but WebUI startup exceeded ${MAX_STARTUP_WAIT_MS / 60000} minutes. Check runtime logs.",
                )
                RuntimeLogRepository.append("Health endpoint still not ready after max wait: elapsed=${elapsed / 1000}s")
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat still warming up")
                return
            }

            if (elapsed >= MIN_WAIT_BEFORE_STALE_TIMEOUT_MS && silentFor >= STALE_ACTIVITY_TIMEOUT_MS) {
                stopProgressMonitor()
                syncProgressFromRuntimeFiles()
                bridgeState.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is still running, but no runtime activity was seen for ${silentFor / 60000} minutes. Check runtime logs.",
                )
                RuntimeLogRepository.append(
                    "Health endpoint still not ready and runtime activity is stale: elapsed=${elapsed / 1000}s silent=${silentFor / 1000}s",
                )
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat startup stalled")
                return
            }
        }
    }

    private suspend fun handleStopBridge() {
        val bridgeState = ContainerBridgeStateRegistry.port
        ContainerRuntimeInstaller.ensureInstalled(applicationContext)
        val config = bridgeState.config.value
        stopHealthMonitor()
        stopProgressMonitor()
        val result = BridgeCommandRunner.execute(config.stopCommand)
        RuntimeLogRepository.append(
            if (result.exitCode == 0 || config.stopCommand.isBlank()) {
                "Stop command completed"
            } else {
                "Stop command failed: ${result.stderr.ifBlank { result.stdout }}"
            },
        )
        bridgeState.markStopped()
        updateForeground("NapCat stopped")
    }

    private suspend fun handleCheckBridge() {
        val bridgeState = ContainerBridgeStateRegistry.port
        ContainerRuntimeInstaller.ensureInstalled(applicationContext)
        val config = bridgeState.config.value
        bridgeState.markChecking()
        syncProgressFromRuntimeFiles()
        RuntimeLogRepository.append("Checking local NapCat bridge")

        val statusResult = BridgeCommandRunner.execute(config.statusCommand)
        if (config.statusCommand.isNotBlank()) {
            RuntimeLogRepository.append(
                if (statusResult.exitCode == 0) {
                    "Status command completed: ${statusResult.stdout.ifBlank { "ok" }}"
                } else {
                    "Status command failed: ${statusResult.stderr.ifBlank { statusResult.stdout }}"
                },
            )
        }

        val health = BridgeHealthChecker.check(config.healthUrl)
        if (health.ok) {
            stopHealthMonitor()
            stopProgressMonitor()
            bridgeState.markRunning(pidHint = "napcat-local")
            RuntimeLogRepository.append("Bridge health check passed: ${health.message}")
            updateForeground("NapCat healthy")
            return
        }

        if (statusResult.exitCode == 0) {
            val pidHint = statusResult.stdout.substringAfter("RUNNING:", "napcat-local").trim().ifBlank { "napcat-local" }
            bridgeState.markProcessRunning(
                pidHint = pidHint,
                details = "NapCat process is running, but HTTP endpoint is not ready: ${health.message}",
            )
            syncProgressFromRuntimeFiles()
            RuntimeLogRepository.append("Bridge process is running but endpoint is not ready: ${health.message}")
            startProgressMonitor()
            startHealthMonitor(config.healthUrl, pidHint)
            updateForeground(buildProgressNotificationText())
            return
        }

        stopHealthMonitor()
        stopProgressMonitor()
        bridgeState.markStopped(reason = "Health check failed")
        RuntimeLogRepository.append("Bridge health check failed: ${health.message}")
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
        val bridgeState = ContainerBridgeStateRegistry.port
        val snapshot = ContainerBridgeRuntimeSupport.loadProgressSnapshot(filesDir)

        bridgeState.markInstallerCached(snapshot.installerCached)
        if (snapshot.label.isNotBlank() || snapshot.percent > 0) {
            bridgeState.updateProgress(
                label = snapshot.label,
                percent = snapshot.percent,
                indeterminate = snapshot.indeterminate,
                installerCached = snapshot.installerCached,
            )
        }

        return snapshot
    }

    private fun buildProgressNotificationText(): String {
        return ContainerBridgeRuntimeSupport.buildProgressNotificationText(ContainerBridgeStateRegistry.port.runtimeState.value)
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

    private fun appendContainerLogTail(prefix: String, maxLines: Int = 60) {
        val logFile = File(filesDir, "runtime/logs/napcat.log")
        if (!logFile.exists()) {
            RuntimeLogRepository.append("$prefix unavailable: ${logFile.absolutePath}")
            return
        }

        val lines = logFile.readLines()
            .takeLast(maxLines)
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            RuntimeLogRepository.append("$prefix is empty")
            return
        }

        lines.forEach { RuntimeLogRepository.append("$prefix | $it") }
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
        const val ACTION_START_BRIDGE = "com.astrbot.android.action.START_BRIDGE"
        const val ACTION_STOP_BRIDGE = "com.astrbot.android.action.STOP_BRIDGE"
        const val ACTION_CHECK_BRIDGE = "com.astrbot.android.action.CHECK_BRIDGE"

        private const val HEALTH_POLL_INTERVAL_MS = 5_000L
        private const val MAX_STARTUP_WAIT_MS = 45 * 60 * 1000L
        private const val MIN_WAIT_BEFORE_STALE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val STALE_ACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L
        private const val CHANNEL_ID = "astrbot_runtime"
        private const val NOTIFICATION_ID = 1001
    }
}
