package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tts_voice_clips",
    foreignKeys = [
        ForeignKey(
            entity = TtsVoiceAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["assetId", "sortIndex"])],
)
data class TtsVoiceClipEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val localPath: String,
    val durationMs: Long,
    val sampleRateHz: Int,
    val createdAt: Long,
    val sortIndex: Int,
)
