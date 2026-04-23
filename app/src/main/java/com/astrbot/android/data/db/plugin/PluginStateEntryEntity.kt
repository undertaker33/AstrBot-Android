package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "plugin_state_entries",
    primaryKeys = ["pluginId", "scopeKind", "scopeId", "key"],
    indices = [
        Index(value = ["pluginId", "scopeKind", "scopeId"]),
        Index(value = ["pluginId", "scopeKind", "key"]),
    ],
)
data class PluginStateEntryEntity(
    val pluginId: String,
    val scopeKind: String,
    val scopeId: String,
    val key: String,
    val valueJson: String,
    val updatedAt: Long,
)
