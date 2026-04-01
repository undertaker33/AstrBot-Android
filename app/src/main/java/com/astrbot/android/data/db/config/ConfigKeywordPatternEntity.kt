package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "config_keyword_patterns",
    primaryKeys = ["configId", "pattern"],
    foreignKeys = [
        ForeignKey(
            entity = ConfigProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["configId", "sortIndex"])],
)
data class ConfigKeywordPatternEntity(
    val configId: String,
    val pattern: String,
    val sortIndex: Int,
)
