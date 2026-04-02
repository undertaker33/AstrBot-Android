package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_install_records")
data class PluginInstallRecordEntity(
    @PrimaryKey val pluginId: String,
    val sourceType: String,
    val sourceLocation: String,
    val sourceImportedAt: Long,
    val protocolSupported: Boolean?,
    val minHostVersionSatisfied: Boolean?,
    val maxHostVersionSatisfied: Boolean?,
    val compatibilityNotes: String,
    val uninstallPolicy: String,
    val enabled: Boolean,
    val installedAt: Long,
    val lastUpdatedAt: Long,
    val localPackagePath: String,
    val extractedDir: String,
)
