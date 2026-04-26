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

internal val migration18To19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE config_skills ADD COLUMN content TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE config_skills ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
    }
}

internal val migration19To20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS resource_center_items (
                resourceId TEXT NOT NULL PRIMARY KEY,
                kind TEXT NOT NULL,
                skillKind TEXT,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                content TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                source TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_resource_center_items_kind_name
            ON resource_center_items(kind, name)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS config_resource_projections (
                configId TEXT NOT NULL,
                resourceId TEXT NOT NULL,
                kind TEXT NOT NULL,
                active INTEGER NOT NULL,
                priority INTEGER NOT NULL,
                sortIndex INTEGER NOT NULL,
                configJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(configId, kind, resourceId),
                FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE,
                FOREIGN KEY(resourceId) REFERENCES resource_center_items(resourceId) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_config_resource_projections_configId_kind_sortIndex
            ON config_resource_projections(configId, kind, sortIndex)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_config_resource_projections_resourceId
            ON config_resource_projections(resourceId)
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT OR IGNORE INTO resource_center_items (
                resourceId, kind, skillKind, name, description, content,
                payloadJson, source, enabled, createdAt, updatedAt
            )
            SELECT
                serverId,
                'MCP_SERVER',
                NULL,
                name,
                CASE
                    WHEN url != '' THEN url
                    WHEN command != '' THEN command
                    ELSE transport
                END,
                '',
                '{"url":"' || replace(url, '"', '\"') ||
                    '","transport":"' || replace(transport, '"', '\"') ||
                    '","command":"' || replace(command, '"', '\"') ||
                    '","args":' || argsJson ||
                    ',"headers":' || headersJson ||
                    ',"timeoutSeconds":' || timeoutSeconds || '}',
                'legacy_config',
                active,
                0,
                0
            FROM config_mcp_servers
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO resource_center_items (
                resourceId, kind, skillKind, name, description, content,
                payloadJson, source, enabled, createdAt, updatedAt
            )
            SELECT
                skillId,
                'SKILL',
                'PROMPT',
                name,
                description,
                content,
                '{}',
                'legacy_config',
                active,
                0,
                0
            FROM config_skills
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO config_resource_projections (
                configId, resourceId, kind, active, priority, sortIndex,
                configJson, createdAt, updatedAt
            )
            SELECT
                configId,
                serverId,
                'MCP_SERVER',
                active,
                0,
                sortIndex,
                '{"url":"' || replace(url, '"', '\"') ||
                    '","transport":"' || replace(transport, '"', '\"') ||
                    '","command":"' || replace(command, '"', '\"') ||
                    '","args":' || argsJson ||
                    ',"headers":' || headersJson ||
                    ',"timeoutSeconds":' || timeoutSeconds || '}',
                0,
                0
            FROM config_mcp_servers
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO config_resource_projections (
                configId, resourceId, kind, active, priority, sortIndex,
                configJson, createdAt, updatedAt
            )
            SELECT
                configId,
                skillId,
                'SKILL',
                active,
                priority,
                sortIndex,
                '{}',
                0,
                0
            FROM config_skills
            """.trimIndent(),
        )
        db.execSQL("ALTER TABLE cron_jobs ADD COLUMN platform TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cron_jobs ADD COLUMN conversationId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cron_jobs ADD COLUMN botId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cron_jobs ADD COLUMN configProfileId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cron_jobs ADD COLUMN personaId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cron_jobs ADD COLUMN providerId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cron_jobs ADD COLUMN origin TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cron_job_execution_records (
                executionId TEXT NOT NULL PRIMARY KEY,
                jobId TEXT NOT NULL,
                status TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                completedAt INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                attempt INTEGER NOT NULL,
                trigger TEXT NOT NULL,
                errorCode TEXT NOT NULL,
                errorMessage TEXT NOT NULL,
                deliverySummary TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_cron_job_execution_records_jobId_startedAt
            ON cron_job_execution_records(jobId, startedAt)
            """.trimIndent(),
        )
    }
}

internal val migration20To21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS plugin_config_snapshots_new (
                pluginId TEXT NOT NULL PRIMARY KEY,
                coreConfigJson TEXT NOT NULL,
                extensionConfigJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO plugin_config_snapshots_new (
                pluginId, coreConfigJson, extensionConfigJson, updatedAt
            )
            SELECT pluginId, coreConfigJson, extensionConfigJson, updatedAt
            FROM plugin_config_snapshots
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE plugin_config_snapshots")
        db.execSQL("ALTER TABLE plugin_config_snapshots_new RENAME TO plugin_config_snapshots")
        db.createPluginStateTablesV21()
    }
}

internal val migration21To22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE config_profiles
            ADD COLUMN includeScheduledTaskConversationContext INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
    }
}
