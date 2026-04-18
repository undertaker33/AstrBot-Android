package com.astrbot.android.core.runtime.container

import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.RuntimeStatus
import java.io.File

internal object ContainerBridgeRuntimeSupport {
    fun loadProgressSnapshot(filesDir: File): RuntimeProgressSnapshot {
        val progressDir = File(filesDir, "runtime/run")
        val percent = progressDir.resolve("napcat_progress").readIntOrDefault(0)
        val rawLabel = progressDir.resolve("napcat_progress_label").readSafeText()
        val indeterminate = progressDir.resolve("napcat_progress_mode").readSafeText() == "1"
        val installerCached = progressDir.resolve("napcat_installer_cached").readSafeText() == "1"
        return RuntimeProgressSnapshot(
            label = localizeProgressLabel(rawLabel),
            percent = percent,
            indeterminate = indeterminate,
            installerCached = installerCached,
        )
    }

    fun buildProgressNotificationText(runtimeState: NapCatRuntimeState): String {
        return when (runtimeState.statusType) {
            RuntimeStatus.RUNNING -> "NapCat running"
            RuntimeStatus.ERROR -> "NapCat start failed"
            RuntimeStatus.STOPPED -> "NapCat stopped"
            else -> {
                if (runtimeState.progressLabel.isNotBlank()) {
                    "NapCat starting: ${runtimeState.progressLabel}"
                } else {
                    "NapCat warming up"
                }
            }
        }
    }

    fun buildPendingHealthDetails(
        snapshot: RuntimeProgressSnapshot,
        health: HealthCheckResult,
        startedAtMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val stage = snapshot.label.ifBlank { "Starting NapCat" }
        val percentSuffix = if (snapshot.percent in 1..99) " (${snapshot.percent}%)" else ""
        return "NapCat process is running. Current stage: $stage$percentSuffix. Waiting for HTTP endpoint: ${health.message}. Elapsed ${formatElapsed(nowMs - startedAtMs)}."
    }

    fun runtimeActivityTimestamp(filesDir: File): Long {
        val progressDir = File(filesDir, "runtime/run")
        val activityFiles = mutableListOf(File(filesDir, "runtime/logs/napcat.log"))
        progressDir.listFiles()?.let { activityFiles.addAll(it) }
        return activityFiles
            .filter { it.exists() }
            .maxOfOrNull { it.lastModified() }
            ?: 0L
    }

    private fun localizeProgressLabel(rawLabel: String): String {
        return when (rawLabel) {
            "preparing-start" -> "Preparing start"
            "prepare-container" -> "Preparing container"
            "install-base" -> "Installing base packages from network"
            "base-ready" -> "Base packages ready"
            "backup-config" -> "Backing up existing NapCat config"
            "download-installer" -> "Downloading upstream installer"
            "installer-downloaded" -> "Installer script downloaded"
            "installer-cached" -> "Existing install detected"
            "run-installer" -> "Installing NapCat from network"
            "restore-config" -> "Restoring NapCat config"
            "write-config" -> "Writing NapCat config"
            "start-napcat" -> "Starting NapCat"
            else -> rawLabel
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "$minutes min $seconds s" else "$seconds s"
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
}

internal data class RuntimeProgressSnapshot(
    val label: String = "",
    val percent: Int = 0,
    val indeterminate: Boolean = false,
    val installerCached: Boolean = false,
)
