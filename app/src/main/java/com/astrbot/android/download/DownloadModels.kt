package com.astrbot.android.download

import java.io.File

enum class DownloadOwnerType {
    PLUGIN_PACKAGE,
    RUNTIME_ASSET,
}

enum class DownloadTaskStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    FAILED,
    COMPLETED,
    CANCELED,
    ;

    val isTerminal: Boolean
        get() = this == FAILED || this == COMPLETED || this == CANCELED
}

data class DownloadRequest(
    val taskKey: String,
    val url: String,
    val targetFilePath: String,
    val displayName: String,
    val ownerType: DownloadOwnerType,
    val ownerId: String,
) {
    init {
        require(taskKey.isNotBlank()) { "Download task key must not be blank." }
        require(url.isNotBlank()) { "Download url must not be blank." }
        require(targetFilePath.isNotBlank()) { "Download target path must not be blank." }
        require(displayName.isNotBlank()) { "Download display name must not be blank." }
        require(ownerId.isNotBlank()) { "Download owner id must not be blank." }
    }

    val partialFilePath: String
        get() = "$targetFilePath.part"
}

data class DownloadTaskRecord(
    val taskKey: String,
    val url: String,
    val targetFilePath: String,
    val partialFilePath: String,
    val displayName: String,
    val ownerType: DownloadOwnerType,
    val ownerId: String,
    val status: DownloadTaskStatus,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
    val etag: String?,
    val lastModified: String?,
    val errorMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
) {
    val progressFraction: Float?
        get() = totalBytes?.takeIf { it > 0L }?.let { total ->
            (downloadedBytes.coerceIn(0L, total).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val isIndeterminate: Boolean
        get() = progressFraction == null

    fun toRequest(): DownloadRequest {
        return DownloadRequest(
            taskKey = taskKey,
            url = url,
            targetFilePath = targetFilePath,
            displayName = displayName,
            ownerType = ownerType,
            ownerId = ownerId,
        )
    }

    fun targetFile(): File = File(targetFilePath)

    fun partialFile(): File = File(partialFilePath)
}

data class DownloadTransferSnapshot(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
    val etag: String?,
    val lastModified: String?,
)

data class DownloadCompletion(
    val totalBytes: Long,
    val etag: String?,
    val lastModified: String?,
)
