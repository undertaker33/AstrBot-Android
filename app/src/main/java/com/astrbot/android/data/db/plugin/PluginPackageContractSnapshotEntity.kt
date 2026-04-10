package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "plugin_package_contract_snapshots",
    primaryKeys = ["pluginId"],
    foreignKeys = [
        ForeignKey(
            entity = PluginInstallRecordEntity::class,
            parentColumns = ["pluginId"],
            childColumns = ["pluginId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PluginPackageContractSnapshotEntity(
    val pluginId: String,
    val protocolVersion: Int,
    val runtimeKind: String,
    val runtimeBootstrap: String,
    val runtimeApiVersion: Int,
    val configStaticSchema: String,
    val configSettingsSchema: String,
)
