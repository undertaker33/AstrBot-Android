package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val taskKey: String,
    val url: String,
    val targetFilePath: String,
    val partialFilePath: String,
    val displayName: String,
    val ownerType: String,
    val ownerId: String,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
    val etag: String?,
    val lastModified: String?,
    val errorMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)
