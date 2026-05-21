package com.elymbot.android.update

import java.io.File
import java.util.Locale

internal enum class AppUpdateBuildTrack {
    DEBUG,
    RELEASE,
}

internal data class AppUpdateAsset(
    val name: String,
    val browserDownloadUrl: String,
    val sizeBytes: Long?,
    val contentType: String,
    val state: String,
    val digest: String?,
)

internal data class AppUpdateReleaseInfo(
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val publishedAt: String,
    val assets: List<AppUpdateAsset>,
)

internal data class AppUpdateSuppression(
    val ignoredTagName: String?,
    val snoozedTagName: String?,
    val remindAfterEpochMillis: Long,
)

internal data class AppUpdateCandidate(
    val release: AppUpdateReleaseInfo,
    val asset: AppUpdateAsset,
    val track: AppUpdateBuildTrack,
    val currentVersionName: String,
    val currentVersionCode: Int,
) {
    val displayVersion: String
        get() = release.name.ifBlank { release.tagName }
}

internal data class AppUpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
) {
    val progressFraction: Float?
        get() = totalBytes?.takeIf { it > 0L }?.let { total ->
            (downloadedBytes.coerceIn(0L, total).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val isIndeterminate: Boolean
        get() = progressFraction == null

    val speedLabel: String
        get() = "${formatBytes(bytesPerSecond)}/s"

    val downloadedLabel: String
        get() = formatBytes(downloadedBytes)

    val totalLabel: String
        get() = totalBytes?.let(::formatBytes) ?: "--"
}

internal data class AppUpdateDownloadedPackage(
    val candidate: AppUpdateCandidate,
    val file: File,
)

internal data class AppUpdateUiState(
    val dialog: AppUpdateDialogState? = null,
)

internal sealed interface AppUpdateDialogState {
    data class Available(val candidate: AppUpdateCandidate) : AppUpdateDialogState

    data class Downloading(
        val candidate: AppUpdateCandidate,
        val progress: AppUpdateDownloadProgress,
    ) : AppUpdateDialogState

    data class ReadyToInstall(
        val packageFile: AppUpdateDownloadedPackage,
    ) : AppUpdateDialogState

    data class Failed(
        val candidate: AppUpdateCandidate,
        val message: String,
    ) : AppUpdateDialogState
}

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val mib = safeBytes.toDouble() / (1024.0 * 1024.0)
    return if (mib >= 10.0) {
        String.format(Locale.US, "%.0f MB", mib)
    } else {
        String.format(Locale.US, "%.1f MB", mib)
    }
}
