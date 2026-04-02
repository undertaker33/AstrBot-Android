package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "plugin_manifest_snapshots",
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
data class PluginManifestSnapshotEntity(
    val pluginId: String,
    val version: String,
    val protocolVersion: Int,
    val author: String,
    val title: String,
    val description: String,
    val minHostVersion: String,
    val maxHostVersion: String,
    val sourceType: String,
    val entrySummary: String,
    val riskLevel: String,
)
