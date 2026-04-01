package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "config_text_rules",
    primaryKeys = ["configId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfigProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ConfigTextRuleEntity(
    val configId: String,
    val imageCaptionPrompt: String,
)
