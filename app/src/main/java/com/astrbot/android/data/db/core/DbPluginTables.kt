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

internal fun SupportSQLiteDatabase.createPluginCatalogTablesV12() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_catalog_sources (
            sourceId TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            catalogUrl TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_catalog_entries (
            sourceId TEXT NOT NULL,
            pluginId TEXT NOT NULL,
            title TEXT NOT NULL,
            author TEXT NOT NULL,
            description TEXT NOT NULL,
            entrySummary TEXT NOT NULL,
            scenariosJson TEXT NOT NULL,
            sortIndex INTEGER NOT NULL,
            PRIMARY KEY(sourceId, pluginId),
            FOREIGN KEY(sourceId) REFERENCES plugin_catalog_sources(sourceId) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_catalog_versions (
            sourceId TEXT NOT NULL,
            pluginId TEXT NOT NULL,
            version TEXT NOT NULL,
            packageUrl TEXT NOT NULL,
            publishedAt INTEGER NOT NULL,
            protocolVersion INTEGER NOT NULL,
            minHostVersion TEXT NOT NULL,
            maxHostVersion TEXT NOT NULL,
            permissionsJson TEXT NOT NULL,
            changelog TEXT NOT NULL,
            sortIndex INTEGER NOT NULL,
            PRIMARY KEY(sourceId, pluginId, version),
            FOREIGN KEY(sourceId, pluginId) REFERENCES plugin_catalog_entries(sourceId, pluginId) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE INDEX IF NOT EXISTS index_plugin_catalog_entries_sourceId_sortIndex
        ON plugin_catalog_entries(sourceId, sortIndex)
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE INDEX IF NOT EXISTS index_plugin_catalog_versions_sourceId_pluginId_sortIndex
        ON plugin_catalog_versions(sourceId, pluginId, sortIndex)
        """.trimIndent(),
    )
}

internal fun SupportSQLiteDatabase.createPluginConfigTablesV14() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_config_snapshots (
            pluginId TEXT NOT NULL PRIMARY KEY,
            coreConfigJson TEXT NOT NULL,
            extensionConfigJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL,
            FOREIGN KEY(pluginId) REFERENCES plugin_install_records(pluginId) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
}

internal fun SupportSQLiteDatabase.createPluginPackageContractTablesV16() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS plugin_package_contract_snapshots (
            pluginId TEXT NOT NULL PRIMARY KEY,
            protocolVersion INTEGER NOT NULL,
            runtimeKind TEXT NOT NULL,
            runtimeBootstrap TEXT NOT NULL,
            runtimeApiVersion INTEGER NOT NULL,
            configStaticSchema TEXT NOT NULL,
            configSettingsSchema TEXT NOT NULL,
            FOREIGN KEY(pluginId) REFERENCES plugin_install_records(pluginId) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
}
