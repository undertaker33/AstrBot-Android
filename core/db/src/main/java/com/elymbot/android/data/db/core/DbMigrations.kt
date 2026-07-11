package com.elymbot.android.data.db.core

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversations (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                botId TEXT NOT NULL,
                personaId TEXT NOT NULL,
                providerId TEXT NOT NULL,
                maxContextMessages INTEGER NOT NULL,
                sessionSttEnabled INTEGER NOT NULL,
                sessionTtsEnabled INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                titleCustomized INTEGER NOT NULL DEFAULT 0,
                messagesJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

internal val migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE conversations ADD COLUMN titleCustomized INTEGER NOT NULL DEFAULT 0")
    }
}

internal val migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversations_new (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                botId TEXT NOT NULL,
                personaId TEXT NOT NULL,
                providerId TEXT NOT NULL,
                platformId TEXT NOT NULL,
                messageType TEXT NOT NULL,
                originSessionId TEXT NOT NULL,
                maxContextMessages INTEGER NOT NULL,
                sessionSttEnabled INTEGER NOT NULL,
                sessionTtsEnabled INTEGER NOT NULL,
                pinned INTEGER NOT NULL,
                titleCustomized INTEGER NOT NULL,
                messagesJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO conversations_new (
                id, title, botId, personaId, providerId,
                platformId, messageType, originSessionId,
                maxContextMessages, sessionSttEnabled, sessionTtsEnabled,
                pinned, titleCustomized, messagesJson, updatedAt
            )
            SELECT
                id, title, botId, personaId, providerId,
                CASE
                    WHEN id LIKE 'qq-%-private-%' THEN 'qq'
                    WHEN id LIKE 'qq-%-group-%' THEN 'qq'
                    ELSE 'app'
                END AS platformId,
                CASE
                    WHEN id LIKE 'qq-%-private-%' THEN 'friend'
                    WHEN id LIKE 'qq-%-group-%' THEN 'group'
                    ELSE 'other'
                END AS messageType,
                CASE
                    WHEN id LIKE 'qq-%-private-%' THEN 'friend:' || substr(id, instr(id, '-private-') + 9)
                    WHEN id LIKE 'qq-%-group-%' THEN
                        CASE
                            WHEN instr(substr(id, instr(id, '-group-') + 7), '-user-') > 0 THEN
                                'group:' ||
                                substr(
                                    substr(id, instr(id, '-group-') + 7),
                                    1,
                                    instr(substr(id, instr(id, '-group-') + 7), '-user-') - 1
                                ) ||
                                ':user:' ||
                                substr(
                                    substr(id, instr(id, '-group-') + 7),
                                    instr(substr(id, instr(id, '-group-') + 7), '-user-') + 6
                                )
                            ELSE 'group:' || substr(id, instr(id, '-group-') + 7)
                        END
                    ELSE id
                END AS originSessionId,
                maxContextMessages, sessionSttEnabled, sessionTtsEnabled,
                pinned, titleCustomized, messagesJson, updatedAt
            FROM conversations
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE conversations")
        db.execSQL("ALTER TABLE conversations_new RENAME TO conversations")
    }
}

internal val migration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_preferences (
                `key` TEXT NOT NULL PRIMARY KEY,
                value TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS provider_profiles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                baseUrl TEXT NOT NULL,
                model TEXT NOT NULL,
                providerType TEXT NOT NULL,
                apiKey TEXT NOT NULL,
                capabilitiesJson TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                multimodalRuleSupport TEXT NOT NULL,
                multimodalProbeSupport TEXT NOT NULL,
                nativeStreamingRuleSupport TEXT NOT NULL,
                nativeStreamingProbeSupport TEXT NOT NULL,
                sttProbeSupport TEXT NOT NULL,
                ttsProbeSupport TEXT NOT NULL,
                ttsVoiceOptionsJson TEXT NOT NULL,
                sortIndex INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS persona_profiles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                tag TEXT NOT NULL,
                systemPrompt TEXT NOT NULL,
                enabledToolsJson TEXT NOT NULL,
                defaultProviderId TEXT NOT NULL,
                maxContextMessages INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                sortIndex INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS config_profiles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                defaultChatProviderId TEXT NOT NULL,
                defaultVisionProviderId TEXT NOT NULL,
                defaultSttProviderId TEXT NOT NULL,
                defaultTtsProviderId TEXT NOT NULL,
                sttEnabled INTEGER NOT NULL,
                ttsEnabled INTEGER NOT NULL,
                alwaysTtsEnabled INTEGER NOT NULL,
                ttsReadBracketedContent INTEGER NOT NULL,
                textStreamingEnabled INTEGER NOT NULL,
                voiceStreamingEnabled INTEGER NOT NULL,
                streamingMessageIntervalMs INTEGER NOT NULL,
                realWorldTimeAwarenessEnabled INTEGER NOT NULL,
                imageCaptionTextEnabled INTEGER NOT NULL,
                webSearchEnabled INTEGER NOT NULL,
                proactiveEnabled INTEGER NOT NULL,
                includeScheduledTaskConversationContext INTEGER NOT NULL,
                ttsVoiceId TEXT NOT NULL,
                imageCaptionPrompt TEXT NOT NULL,
                adminUidsJson TEXT NOT NULL,
                sessionIsolationEnabled INTEGER NOT NULL,
                wakeWordsJson TEXT NOT NULL,
                wakeWordsAdminOnlyEnabled INTEGER NOT NULL,
                privateChatRequiresWakeWord INTEGER NOT NULL,
                replyTextPrefix TEXT NOT NULL,
                quoteSenderMessageEnabled INTEGER NOT NULL,
                mentionSenderEnabled INTEGER NOT NULL,
                replyOnAtOnlyEnabled INTEGER NOT NULL,
                whitelistEnabled INTEGER NOT NULL,
                whitelistEntriesJson TEXT NOT NULL,
                logOnWhitelistMiss INTEGER NOT NULL,
                adminGroupBypassWhitelistEnabled INTEGER NOT NULL,
                adminPrivateBypassWhitelistEnabled INTEGER NOT NULL,
                ignoreSelfMessageEnabled INTEGER NOT NULL,
                ignoreAtAllEventEnabled INTEGER NOT NULL,
                replyWhenPermissionDenied INTEGER NOT NULL,
                rateLimitWindowSeconds INTEGER NOT NULL,
                rateLimitMaxCount INTEGER NOT NULL,
                rateLimitStrategy TEXT NOT NULL,
                keywordDetectionEnabled INTEGER NOT NULL,
                keywordPatternsJson TEXT NOT NULL,
                sortIndex INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

internal val migration6To7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bots ADD COLUMN boundQqUinsJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE bots ADD COLUMN persistConversationLocally INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bots ADD COLUMN configProfileId TEXT NOT NULL DEFAULT 'default'")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_qq_accounts (
                uin TEXT NOT NULL PRIMARY KEY,
                nickName TEXT NOT NULL,
                avatarUrl TEXT NOT NULL,
                sortIndex INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

internal val migration7To8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tts_voice_assets (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                source TEXT NOT NULL,
                localPath TEXT NOT NULL,
                remoteUrl TEXT NOT NULL,
                durationMs INTEGER NOT NULL,
                sampleRateHz INTEGER NOT NULL,
                clipsJson TEXT NOT NULL,
                providerBindingsJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

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

internal val migration22To23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE config_profiles
            ADD COLUMN pluginCommandsAdminOnlyEnabled INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
    }
}

internal val migration23To24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS geofence_rules (
                ruleId TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                triggerEnter INTEGER NOT NULL,
                triggerExit INTEGER NOT NULL,
                triggerDwell INTEGER NOT NULL,
                dwellDelayMillis INTEGER NOT NULL,
                actionType TEXT NOT NULL,
                actionPrompt TEXT NOT NULL,
                targetPlatform TEXT NOT NULL,
                targetConversationId TEXT NOT NULL,
                targetBotId TEXT NOT NULL,
                targetConfigProfileId TEXT NOT NULL,
                targetPersonaId TEXT NOT NULL,
                targetProviderId TEXT NOT NULL,
                minimumTriggerIntervalMillis INTEGER NOT NULL,
                status TEXT NOT NULL,
                lastRegisteredAt INTEGER NOT NULL,
                lastTriggeredAt INTEGER NOT NULL,
                lastError TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS geofence_regions (
                regionId TEXT NOT NULL PRIMARY KEY,
                ruleId TEXT NOT NULL,
                label TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                radiusMeters REAL NOT NULL,
                addressLabel TEXT NOT NULL,
                sortIndex INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(ruleId) REFERENCES geofence_rules(ruleId) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_geofence_regions_ruleId_sortIndex
            ON geofence_regions(ruleId, sortIndex)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS config_geofence_bindings (
                configId TEXT NOT NULL,
                ruleId TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                sortIndex INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(configId, ruleId),
                FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE,
                FOREIGN KEY(ruleId) REFERENCES geofence_rules(ruleId) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_config_geofence_bindings_configId_sortIndex
            ON config_geofence_bindings(configId, sortIndex)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_config_geofence_bindings_ruleId
            ON config_geofence_bindings(ruleId)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS geofence_execution_records (
                executionId TEXT NOT NULL PRIMARY KEY,
                ruleId TEXT NOT NULL,
                regionId TEXT NOT NULL,
                configId TEXT NOT NULL,
                transition TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                completedAt INTEGER NOT NULL,
                status TEXT NOT NULL,
                errorCode TEXT NOT NULL,
                errorMessage TEXT NOT NULL,
                deliverySummary TEXT NOT NULL,
                locationSnapshotJson TEXT NOT NULL,
                triggerPayloadJson TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_geofence_execution_records_ruleId_startedAt
            ON geofence_execution_records(ruleId, startedAt)
            """.trimIndent(),
        )
    }
}

internal val migration24To25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE persona_tags_temp (
                personaId TEXT NOT NULL, tag TEXT NOT NULL, sortIndex INTEGER NOT NULL,
                PRIMARY KEY(personaId, tag)
            )
        """.trimIndent())
        db.execSQL("""
            WITH RECURSIVE split(personaId, rest, tag, sourceIndex) AS (
                SELECT id, replace(trim(tag), '，', ',') || ',', '', 0 FROM persona_profiles
                UNION ALL
                SELECT personaId, substr(rest, instr(rest, ',') + 1), trim(substr(rest, 1, instr(rest, ',') - 1)), sourceIndex + 1
                FROM split WHERE rest <> ''
            ), unique_tags AS (
                SELECT personaId, tag, min(sourceIndex) AS firstIndex FROM split WHERE tag <> '' GROUP BY personaId, tag
            ), ranked AS (
                SELECT personaId, tag, row_number() OVER (PARTITION BY personaId ORDER BY firstIndex) - 1 AS sortIndex FROM unique_tags
            )
            INSERT OR IGNORE INTO persona_tags_temp(personaId, tag, sortIndex)
            SELECT personaId, tag, sortIndex FROM ranked WHERE sortIndex < 3
        """.trimIndent())
        db.execSQL("CREATE TEMP TABLE persona_prompts_temp AS SELECT * FROM persona_prompts")
        db.execSQL("CREATE TEMP TABLE persona_enabled_tools_temp AS SELECT * FROM persona_enabled_tools")
        db.execSQL("DROP TABLE persona_prompts")
        db.execSQL("DROP TABLE persona_enabled_tools")
        db.execSQL("""
            CREATE TABLE persona_profiles_new (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, defaultProviderId TEXT NOT NULL,
                maxContextMessages INTEGER NOT NULL, enabled INTEGER NOT NULL, sortIndex INTEGER NOT NULL, updatedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO persona_profiles_new(id, name, defaultProviderId, maxContextMessages, enabled, sortIndex, updatedAt)
            SELECT id, name, defaultProviderId, maxContextMessages, enabled, sortIndex, updatedAt FROM persona_profiles
        """.trimIndent())
        db.execSQL("DROP TABLE persona_profiles")
        db.execSQL("ALTER TABLE persona_profiles_new RENAME TO persona_profiles")
        db.execSQL("""
            CREATE TABLE persona_prompts (
                personaId TEXT NOT NULL PRIMARY KEY, systemPrompt TEXT NOT NULL,
                FOREIGN KEY(personaId) REFERENCES persona_profiles(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("INSERT INTO persona_prompts SELECT * FROM persona_prompts_temp")
        db.execSQL("DROP TABLE persona_prompts_temp")
        db.execSQL("""
            CREATE TABLE persona_enabled_tools (
                personaId TEXT NOT NULL, toolName TEXT NOT NULL, sortIndex INTEGER NOT NULL,
                PRIMARY KEY(personaId, toolName), FOREIGN KEY(personaId) REFERENCES persona_profiles(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("INSERT INTO persona_enabled_tools SELECT * FROM persona_enabled_tools_temp")
        db.execSQL("DROP TABLE persona_enabled_tools_temp")
        db.execSQL("CREATE INDEX index_persona_enabled_tools_personaId_sortIndex ON persona_enabled_tools(personaId, sortIndex)")
        db.execSQL("""
            CREATE TABLE persona_tags (
                personaId TEXT NOT NULL, tag TEXT NOT NULL, sortIndex INTEGER NOT NULL,
                PRIMARY KEY(personaId, tag), FOREIGN KEY(personaId) REFERENCES persona_profiles(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("INSERT INTO persona_tags SELECT * FROM persona_tags_temp")
        db.execSQL("DROP TABLE persona_tags_temp")
        db.execSQL("CREATE INDEX index_persona_tags_personaId_sortIndex ON persona_tags(personaId, sortIndex)")
        db.execSQL("""
            CREATE TABLE persona_cover_assets (
                personaId TEXT NOT NULL PRIMARY KEY, assetRef TEXT NOT NULL, contentSha256 TEXT NOT NULL,
                pixelWidth INTEGER NOT NULL, pixelHeight INTEGER NOT NULL,
                portraitCenterX REAL NOT NULL, portraitCenterY REAL NOT NULL, portraitZoom REAL NOT NULL,
                squareCenterX REAL NOT NULL, squareCenterY REAL NOT NULL, squareZoom REAL NOT NULL,
                updatedAt INTEGER NOT NULL, FOREIGN KEY(personaId) REFERENCES persona_profiles(id) ON DELETE CASCADE
            )
        """.trimIndent())
    }
}
