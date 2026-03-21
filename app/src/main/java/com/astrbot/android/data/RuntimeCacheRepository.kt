package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.runtime.RuntimeLogRepository
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeCacheCleanupState(
    val isRunning: Boolean = false,
    val lastSummary: String = "",
)

object RuntimeCacheRepository {
    private val cleanupTargets = listOf(
        CleanupTarget("runtime/downloads", recreate = true),
        CleanupTarget("runtime/tts-out", recreate = true),
        CleanupTarget(
            relativePath = "runtime/rootfs/ubuntu/var/cache/apt/archives",
            recreate = true,
            extraDirectories = listOf("partial"),
        ),
        CleanupTarget(
            relativePath = "runtime/rootfs/ubuntu/root/.config/QQ/nt_qq/global/nt_data/Log",
            recreate = true,
        ),
    )

    private val _state = MutableStateFlow(RuntimeCacheCleanupState())
    val state: StateFlow<RuntimeCacheCleanupState> = _state.asStateFlow()

    suspend fun clearSafeRuntimeCaches(context: Context): String {
        val appContext = context.applicationContext
        _state.value = _state.value.copy(isRunning = true)
        return runCatching {
            val clearedBytes = cleanupTargets.sumOf { target ->
                clearDirectoryContents(File(appContext.filesDir, target.relativePath), target)
            }
            val summary = "Cleared ${formatBytes(clearedBytes)} from runtime cache."
            RuntimeLogRepository.append("Runtime cache cleanup finished: ${formatBytes(clearedBytes)} freed")
            _state.value = RuntimeCacheCleanupState(
                isRunning = false,
                lastSummary = summary,
            )
            summary
        }.getOrElse { error ->
            val summary = error.message ?: error.javaClass.simpleName
            RuntimeLogRepository.append("Runtime cache cleanup failed: $summary")
            _state.value = RuntimeCacheCleanupState(
                isRunning = false,
                lastSummary = summary,
            )
            throw error
        }
    }

    private fun clearDirectoryContents(directory: File, target: CleanupTarget): Long {
        if (!directory.exists()) {
            if (target.recreate) recreateDirectory(directory, target.extraDirectories)
            return 0L
        }
        val bytes = directory.listFiles().orEmpty().sumOf { child ->
            val childBytes = child.directorySize()
            if (!child.deleteRecursively()) {
                throw IllegalStateException("Failed to clear ${child.absolutePath}")
            }
            childBytes
        }
        if (target.recreate) recreateDirectory(directory, target.extraDirectories)
        return bytes
    }

    private fun recreateDirectory(directory: File, extraDirectories: List<String>) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Failed to recreate ${directory.absolutePath}")
        }
        extraDirectories.forEach { child ->
            val nested = File(directory, child)
            if (!nested.exists() && !nested.mkdirs()) {
                throw IllegalStateException("Failed to recreate ${nested.absolutePath}")
            }
        }
    }

    private fun File.directorySize(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        val pattern = if (value >= 10 || unitIndex == 0) "%.0f %s" else "%.1f %s"
        return String.format(Locale.US, pattern, value, units[unitIndex])
    }

    private data class CleanupTarget(
        val relativePath: String,
        val recreate: Boolean,
        val extraDirectories: List<String> = emptyList(),
    )
}
