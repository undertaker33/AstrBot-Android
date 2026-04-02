package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "plugin_manifest_permissions",
    primaryKeys = ["pluginId", "permissionId"],
    indices = [
        Index(value = ["pluginId", "sortIndex"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PluginManifestSnapshotEntity::class,
            parentColumns = ["pluginId"],
            childColumns = ["pluginId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PluginManifestPermissionEntity(
    val pluginId: String,
    val permissionId: String,
    val title: String,
    val description: String,
    val riskLevel: String,
    val required: Boolean,
    val sortIndex: Int,
)
