package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "config_whitelist_entries",
    primaryKeys = ["configId", "entry"],
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
data class ConfigWhitelistEntryEntity(
    val configId: String,
    val entry: String,
    val sortIndex: Int,
)
