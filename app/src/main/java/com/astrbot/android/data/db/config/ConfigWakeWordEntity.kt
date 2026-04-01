package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "config_wake_words",
    primaryKeys = ["configId", "word"],
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
data class ConfigWakeWordEntity(
    val configId: String,
    val word: String,
    val sortIndex: Int,
)
