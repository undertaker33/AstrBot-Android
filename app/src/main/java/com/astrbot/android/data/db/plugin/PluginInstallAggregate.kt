package com.astrbot.android.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class PluginInstallAggregate(
    @Embedded val record: PluginInstallRecordEntity,
    @Relation(parentColumn = "pluginId", entityColumn = "pluginId")
    val manifestSnapshots: List<PluginManifestSnapshotEntity>,
    @Relation(parentColumn = "pluginId", entityColumn = "pluginId")
    val manifestPermissions: List<PluginManifestPermissionEntity>,
    @Relation(parentColumn = "pluginId", entityColumn = "pluginId")
    val permissionSnapshots: List<PluginPermissionSnapshotEntity>,
)

data class PluginInstallWriteModel(
    val record: PluginInstallRecordEntity,
    val manifestSnapshot: PluginManifestSnapshotEntity,
    val manifestPermissions: List<PluginManifestPermissionEntity>,
    val permissionSnapshots: List<PluginPermissionSnapshotEntity>,
)
