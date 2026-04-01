package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tts_voice_assets")
data class TtsVoiceAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: String,
    val localPath: String,
    val remoteUrl: String,
    val durationMs: Long,
    val sampleRateHz: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
