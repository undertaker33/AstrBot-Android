package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_config_snapshots")
data class PluginConfigSnapshotEntity(
    @PrimaryKey val pluginId: String,
    val coreConfigJson: String,
    val extensionConfigJson: String,
    val updatedAt: Long,
)
