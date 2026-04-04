package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "plugin_config_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = PluginInstallRecordEntity::class,
            parentColumns = ["pluginId"],
            childColumns = ["pluginId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PluginConfigSnapshotEntity(
    @PrimaryKey val pluginId: String,
    val coreConfigJson: String,
    val extensionConfigJson: String,
    val updatedAt: Long,
)
