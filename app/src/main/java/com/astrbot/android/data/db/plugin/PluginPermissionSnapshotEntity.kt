package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "plugin_permission_snapshots",
    primaryKeys = ["pluginId", "permissionId"],
    indices = [
        Index(value = ["pluginId", "sortIndex"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PluginInstallRecordEntity::class,
            parentColumns = ["pluginId"],
            childColumns = ["pluginId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PluginPermissionSnapshotEntity(
    val pluginId: String,
    val permissionId: String,
    val title: String,
    val description: String,
    val riskLevel: String,
    val required: Boolean,
    val sortIndex: Int,
)
