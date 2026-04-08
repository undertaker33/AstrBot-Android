package com.astrbot.android.data.db

import com.astrbot.android.download.DownloadOwnerType
import com.astrbot.android.download.DownloadTaskRecord
import com.astrbot.android.download.DownloadTaskStatus

fun DownloadTaskEntity.toRecord(): DownloadTaskRecord {
    return DownloadTaskRecord(
        taskKey = taskKey,
        url = url,
        targetFilePath = targetFilePath,
        partialFilePath = partialFilePath,
        displayName = displayName,
        ownerType = DownloadOwnerType.valueOf(ownerType),
        ownerId = ownerId,
        status = DownloadTaskStatus.valueOf(status),
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        bytesPerSecond = bytesPerSecond,
        etag = etag,
        lastModified = lastModified,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
    )
}

fun DownloadTaskRecord.toEntity(): DownloadTaskEntity {
    return DownloadTaskEntity(
        taskKey = taskKey,
        url = url,
        targetFilePath = targetFilePath,
        partialFilePath = partialFilePath,
        displayName = displayName,
        ownerType = ownerType.name,
        ownerId = ownerId,
        status = status.name,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        bytesPerSecond = bytesPerSecond,
        etag = etag,
        lastModified = lastModified,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
    )
}
