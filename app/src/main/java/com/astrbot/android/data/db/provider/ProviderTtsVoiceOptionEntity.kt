package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "provider_tts_voice_options",
    primaryKeys = ["providerId", "voiceOption"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["providerId", "sortIndex"])],
)
data class ProviderTtsVoiceOptionEntity(
    val providerId: String,
    val voiceOption: String,
    val sortIndex: Int,
)
