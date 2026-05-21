package com.elymbot.android.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.network.RuntimeNetworkCapability
import com.elymbot.android.core.runtime.network.RuntimeNetworkRequest
import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import com.elymbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.elymbot.android.download.DownloadManagerPort
import com.elymbot.android.download.DownloadOwnerType
import com.elymbot.android.download.DownloadRequest
import com.elymbot.android.download.DownloadTaskRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/undertaker33/ElymBot/releases/latest"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val UPDATE_DIR_NAME = "app-updates"

internal class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val networkTransport: RuntimeNetworkTransport,
    private val downloadManager: DownloadManagerPort,
    private val runtimeLogger: RuntimeLogger,
) {
    private val localStore = AppUpdateLocalStore(appContext)

    suspend fun findUpdateCandidate(
        currentVersionName: String,
        currentVersionCode: Int,
        track: AppUpdateBuildTrack,
    ): AppUpdateCandidate? = withContext(Dispatchers.IO) {
        localStore.cleanupInstalledUpdate(currentVersionName)
        val now = System.currentTimeMillis()
        val release = fetchLatestRelease(currentVersionName)
        val suppression = localStore.suppression()
        if (!shouldOfferAppUpdate(release, currentVersionName, suppression, now)) {
            return@withContext null
        }
        val asset = selectAppUpdateAsset(release, track) ?: run {
            runtimeLogger.append("App update check skipped: no ${track.name.lowercase()} APK asset in ${release.tagName}")
            return@withContext null
        }
        AppUpdateCandidate(
            release = release,
            asset = asset,
            track = track,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
        )
    }

    suspend fun snooze(candidate: AppUpdateCandidate) = withContext(Dispatchers.IO) {
        localStore.snooze(candidate = candidate, nowEpochMillis = System.currentTimeMillis())
    }

    suspend fun ignore(candidate: AppUpdateCandidate) = withContext(Dispatchers.IO) {
        localStore.ignore(candidate)
    }

    suspend fun downloadUpdate(
        candidate: AppUpdateCandidate,
        onProgress: (AppUpdateDownloadProgress) -> Unit,
    ): AppUpdateDownloadedPackage = withContext(Dispatchers.IO) {
        val targetFile = targetFileFor(candidate)
        removeMismatchedExistingFiles(targetFile, candidate.asset.sizeBytes)
        val taskKey = taskKeyFor(candidate)
        downloadManager.enqueue(
            DownloadRequest(
                taskKey = taskKey,
                url = candidate.asset.browserDownloadUrl,
                targetFilePath = targetFile.absolutePath,
                displayName = candidate.asset.name,
                ownerType = DownloadOwnerType.APP_UPDATE,
                ownerId = candidate.release.tagName,
            ),
        )
        val record = downloadManager.awaitCompletion(taskKey) { task ->
            onProgress(task.toAppUpdateDownloadProgress(candidate.asset.sizeBytes))
        }
        val packageFile = record.targetFile()
        localStore.markPendingCleanup(candidate, packageFile)
        AppUpdateDownloadedPackage(candidate = candidate, file = packageFile)
    }

    fun buildInstallIntent(packageFile: File): Intent {
        require(packageFile.exists()) { "Downloaded update package does not exist: ${packageFile.absolutePath}" }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            packageFile,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun cleanupInstalledUpdate(currentVersionName: String) {
        localStore.cleanupInstalledUpdate(currentVersionName)
    }

    private suspend fun fetchLatestRelease(currentVersionName: String): AppUpdateReleaseInfo {
        val response = networkTransport.execute(
            RuntimeNetworkRequest(
                capability = RuntimeNetworkCapability.APP_UPDATE,
                method = "GET",
                url = GITHUB_LATEST_RELEASE_URL,
                headers = mapOf(
                    "Accept" to "application/vnd.github+json",
                    "X-GitHub-Api-Version" to "2022-11-28",
                    "User-Agent" to "ElymBot-Android/${currentVersionName.ifBlank { "unknown" }}",
                ),
                timeoutProfile = RuntimeTimeoutProfile.APP_UPDATE,
            ),
        )
        return parseAppUpdateRelease(response.bodyString)
    }

    private fun targetFileFor(candidate: AppUpdateCandidate): File {
        val releaseDir = File(appContext.filesDir, "$UPDATE_DIR_NAME/${sanitizeUpdatePathSegment(candidate.release.tagName)}")
        return File(releaseDir, sanitizeUpdatePathSegment(candidate.asset.name))
    }

    private fun taskKeyFor(candidate: AppUpdateCandidate): String {
        return "app-update:${sanitizeUpdatePathSegment(candidate.release.tagName)}:${candidate.track.name.lowercase()}"
    }

    private fun removeMismatchedExistingFiles(targetFile: File, expectedSizeBytes: Long?) {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists() && expectedSizeBytes != null && targetFile.length() != expectedSizeBytes) {
            targetFile.delete()
        }
        val partialFile = File("${targetFile.absolutePath}.part")
        if (targetFile.exists()) return
        if (partialFile.exists() && expectedSizeBytes != null && partialFile.length() > expectedSizeBytes) {
            partialFile.delete()
        }
    }
}

private fun DownloadTaskRecord.toAppUpdateDownloadProgress(expectedTotalBytes: Long?): AppUpdateDownloadProgress {
    return AppUpdateDownloadProgress(
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes?.takeIf { it > 0L } ?: expectedTotalBytes?.takeIf { it > 0L },
        bytesPerSecond = bytesPerSecond,
    )
}
