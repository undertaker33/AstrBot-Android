package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tts_voice_provider_bindings",
    foreignKeys = [
        ForeignKey(
            entity = TtsVoiceAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["assetId", "sortIndex"]),
        Index(value = ["assetId", "providerId", "model", "voiceId"], unique = true),
    ],
)
data class TtsVoiceProviderBindingEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val providerId: String,
    val providerType: String,
    val model: String,
    val voiceId: String,
    val displayName: String,
    val createdAt: Long,
    val lastVerifiedAt: Long,
    val status: String,
    val sortIndex: Int,
)
