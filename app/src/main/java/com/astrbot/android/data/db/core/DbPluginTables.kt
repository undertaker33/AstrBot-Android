package com.astrbot.android.data.db.core

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun SupportSQLiteDatabase.createPluginTablesV10() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_install_records (
            pluginId TEXT NOT NULL PRIMARY KEY,
            sourceType TEXT NOT NULL,
            sourceLocation TEXT NOT NULL,
            sourceImportedAt INTEGER NOT NULL,
            protocolSupported INTEGER,
            minHostVersionSatisfied INTEGER,
            maxHostVersionSatisfied INTEGER,
            compatibilityNotes TEXT NOT NULL,
            uninstallPolicy TEXT NOT NULL,
            enabled INTEGER NOT NULL,
            installedAt INTEGER NOT NULL,
            lastUpdatedAt INTEGER NOT NULL,
            localPackagePath TEXT NOT NULL,
            extractedDir TEXT NOT NULL
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_manifest_snapshots (
            pluginId TEXT NOT NULL PRIMARY KEY,
            version TEXT NOT NULL,
            protocolVersion INTEGER NOT NULL,
            author TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            minHostVersion TEXT NOT NULL,
            maxHostVersion TEXT NOT NULL,
            sourceType TEXT NOT NULL,
            entrySummary TEXT NOT NULL,
            riskLevel TEXT NOT NULL,
            FOREIGN KEY(pluginId) REFERENCES plugin_install_records(pluginId) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_manifest_permissions (
            pluginId TEXT NOT NULL,
            permissionId TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            riskLevel TEXT NOT NULL,
            required INTEGER NOT NULL,
            sortIndex INTEGER NOT NULL,
            PRIMARY KEY(pluginId, permissionId),
            FOREIGN KEY(pluginId) REFERENCES plugin_manifest_snapshots(pluginId) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_permission_snapshots (
            pluginId TEXT NOT NULL,
            permissionId TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            riskLevel TEXT NOT NULL,
            required INTEGER NOT NULL,
            sortIndex INTEGER NOT NULL,
            PRIMARY KEY(pluginId, permissionId),
            FOREIGN KEY(pluginId) REFERENCES plugin_install_records(pluginId) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE INDEX IF NOT EXISTS index_plugin_manifest_permissions_pluginId_sortIndex
        ON plugin_manifest_permissions(pluginId, sortIndex)
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE INDEX IF NOT EXISTS index_plugin_permission_snapshots_pluginId_sortIndex
        ON plugin_permission_snapshots(pluginId, sortIndex)
        """.trimIndent(),
    )
}
