package com.astrbot.android.data.db.core

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration8To9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.resetSchemaToV9()
    }
}

internal val migration9To10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.createPluginTablesV10()
    }
}

internal val migration10To11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN consecutiveFailureCount INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN lastFailureAtEpochMillis INTEGER
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN lastErrorSummary TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN suspendedUntilEpochMillis INTEGER
            """.trimIndent(),
        )
    }
}

internal val migration11To12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN catalogSourceId TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN installedPackageUrl TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN lastCatalogCheckAtEpochMillis INTEGER
            """.trimIndent(),
        )
        db.createPluginCatalogTablesV12()
    }
}

internal val migration12To13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE plugin_catalog_sources
            ADD COLUMN lastSyncAtEpochMillis INTEGER
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_catalog_sources
            ADD COLUMN lastSyncStatus TEXT NOT NULL DEFAULT 'NEVER_SYNCED'
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_catalog_sources
            ADD COLUMN lastSyncErrorSummary TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
    }
}

internal val migration13To14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.createPluginConfigTablesV14()
    }
}

internal val migration14To15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS download_tasks (
                taskKey TEXT NOT NULL PRIMARY KEY,
                url TEXT NOT NULL,
                targetFilePath TEXT NOT NULL,
                partialFilePath TEXT NOT NULL,
                displayName TEXT NOT NULL,
                ownerType TEXT NOT NULL,
                ownerId TEXT NOT NULL,
                status TEXT NOT NULL,
                downloadedBytes INTEGER NOT NULL,
                totalBytes INTEGER,
                bytesPerSecond INTEGER NOT NULL,
                etag TEXT,
                lastModified TEXT,
                errorMessage TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                completedAt INTEGER
            )
            """.trimIndent(),
        )
    }
}

internal val migration15To16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.createPluginPackageContractTablesV16()
    }
}

internal val migration16To17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Context strategy columns on config_profiles
        db.execSQL("ALTER TABLE config_profiles ADD COLUMN contextLimitStrategy TEXT NOT NULL DEFAULT 'truncate_by_turns'")
        db.execSQL("ALTER TABLE config_profiles ADD COLUMN maxContextTurns INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE config_profiles ADD COLUMN dequeueContextTurns INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE config_profiles ADD COLUMN llmCompressInstruction TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE config_profiles ADD COLUMN llmCompressKeepRecent INTEGER NOT NULL DEFAULT 6")
        db.execSQL("ALTER TABLE config_profiles ADD COLUMN llmCompressProviderId TEXT NOT NULL DEFAULT ''")

        // MCP server entries (per-config)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS config_mcp_servers (
                configId TEXT NOT NULL,
                serverId TEXT NOT NULL,
                name TEXT NOT NULL,
                url TEXT NOT NULL,
                transport TEXT NOT NULL,
                command TEXT NOT NULL,
                argsJson TEXT NOT NULL,
                headersJson TEXT NOT NULL,
                timeoutSeconds INTEGER NOT NULL,
                active INTEGER NOT NULL,
                sortIndex INTEGER NOT NULL,
                PRIMARY KEY(configId, serverId),
                FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_config_mcp_servers_configId_sortIndex ON config_mcp_servers(configId, sortIndex)")

        // Skill entries (per-config)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS config_skills (
                configId TEXT NOT NULL,
                skillId TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                active INTEGER NOT NULL,
                sortIndex INTEGER NOT NULL,
                PRIMARY KEY(configId, skillId),
                FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_config_skills_configId_sortIndex ON config_skills(configId, sortIndex)")
    }
}

internal val migration17To18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cron_jobs (
                jobId TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                jobType TEXT NOT NULL,
                cronExpression TEXT NOT NULL,
                timezone TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                runOnce INTEGER NOT NULL,
                status TEXT NOT NULL,
                lastRunAt INTEGER NOT NULL,
                nextRunTime INTEGER NOT NULL,
                lastError TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
