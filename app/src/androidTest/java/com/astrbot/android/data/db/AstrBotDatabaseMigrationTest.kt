package com.astrbot.android.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AstrBotDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        AstrBotDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun Context.openDatabaseForVerification(databaseName: String): SQLiteDatabase {
        return openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).apply {
            // Foreign key enforcement is configured per SQLite connection. These migration
            // assertions reopen the database, so the verification connection must enable it.
            setForeignKeyConstraintsEnabled(true)
            rawQuery("PRAGMA foreign_keys", null).use { cursor ->
                cursor.moveToFirst()
                org.junit.Assert.assertEquals(
                    "Expected foreign key enforcement to be enabled for verification",
                    1,
                    cursor.getInt(0),
                )
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_preservesBotsAndCreatesTtsTable() {
        val databaseName = "migration-test-7-8"
        helper.createDatabase(databaseName, 7).apply {
            execSQL(
                """
                INSERT INTO bots (
                    id, platformName, displayName, tag, accountHint,
                    boundQqUinsJson, triggerWordsCsv, autoReplyEnabled,
                    persistConversationLocally, bridgeMode, bridgeEndpoint,
                    defaultProviderId, defaultPersonaId, configProfileId,
                    status, updatedAt
                ) VALUES (
                    'qq-main', 'QQ', 'Primary Bot', 'Default', '10001',
                    '[""10001""]', 'astrbot,assistant', 1,
                    1, 'NapCat local bridge', 'ws://127.0.0.1:6199/ws',
                    'provider-1', 'default', 'config-1',
                    'Idle', 123
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 8, true, *AstrBotDatabase.allMigrations)

        val database = context.openDatabaseForVerification(databaseName)
        database.rawQuery("SELECT COUNT(*) FROM bots", null).use { cursor ->
            cursor.moveToFirst()
            org.junit.Assert.assertEquals(1, cursor.getInt(0))
        }
        database.rawQuery("SELECT COUNT(*) FROM tts_voice_assets", null).use { cursor ->
            cursor.moveToFirst()
            org.junit.Assert.assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To8_preservesSelectedConfigPreferenceAndCreatesNewTables() {
        val databaseName = "migration-test-6-8"
        helper.createDatabase(databaseName, 6).apply {
            execSQL(
                """
                INSERT INTO app_preferences (`key`, value, updatedAt)
                VALUES ('selected_config_profile_id', 'config-1', 123)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO config_profiles (
                    id, name, defaultChatProviderId, defaultVisionProviderId,
                    defaultSttProviderId, defaultTtsProviderId,
                    sttEnabled, ttsEnabled, alwaysTtsEnabled, ttsReadBracketedContent,
                    textStreamingEnabled, voiceStreamingEnabled, streamingMessageIntervalMs,
                    realWorldTimeAwarenessEnabled, imageCaptionTextEnabled, webSearchEnabled,
                    proactiveEnabled, ttsVoiceId, imageCaptionPrompt, adminUidsJson,
                    sessionIsolationEnabled, wakeWordsJson, wakeWordsAdminOnlyEnabled,
                    privateChatRequiresWakeWord, replyTextPrefix, quoteSenderMessageEnabled,
                    mentionSenderEnabled, replyOnAtOnlyEnabled, whitelistEnabled,
                    whitelistEntriesJson, logOnWhitelistMiss, adminGroupBypassWhitelistEnabled,
                    adminPrivateBypassWhitelistEnabled, ignoreSelfMessageEnabled,
                    ignoreAtAllEventEnabled, replyWhenPermissionDenied,
                    rateLimitWindowSeconds, rateLimitMaxCount, rateLimitStrategy,
                    keywordDetectionEnabled, keywordPatternsJson, sortIndex, updatedAt
                ) VALUES (
                    'config-1', 'Main', 'provider-1', '', '', '',
                    0, 0, 0, 1,
                    0, 0, 120,
                    0, 0, 0,
                    0, '', 'Describe image', '[]',
                    0, '[]', 0,
                    0, '', 0,
                    0, 1, 0,
                    '[]', 0, 1,
                    1, 1,
                    1, 0,
                    0, 0, 'drop',
                    0, '[]', 0, 123
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 8, true, *AstrBotDatabase.allMigrations)

        val database = context.openDatabaseForVerification(databaseName)
        database.rawQuery(
            "SELECT value FROM app_preferences WHERE `key` = 'selected_config_profile_id'",
            null,
        ).use { cursor ->
            cursor.moveToFirst()
            org.junit.Assert.assertEquals("config-1", cursor.getString(0))
        }
        database.rawQuery("SELECT COUNT(*) FROM saved_qq_accounts", null).use { cursor ->
            cursor.moveToFirst()
            org.junit.Assert.assertEquals(0, cursor.getInt(0))
        }
        database.rawQuery("SELECT COUNT(*) FROM tts_voice_assets", null).use { cursor ->
            cursor.moveToFirst()
            org.junit.Assert.assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9_recreatesAggregateTables() {
        val databaseName = "migration-test-8-9"
        helper.createDatabase(databaseName, 8).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, botId, personaId, providerId, platformId,
                    messageType, originSessionId, maxContextMessages,
                    sessionSttEnabled, sessionTtsEnabled, pinned,
                    titleCustomized, messagesJson, updatedAt
                ) VALUES (
                    'chat-main', 'Main', 'qq-main', '', '', 'app',
                    'other', 'chat-main', 12,
                    1, 1, 0,
                    0, '[]', 123
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 9, true, *AstrBotDatabase.allMigrations)

        val database = context.openDatabaseForVerification(databaseName)
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='conversation_messages'",
            null,
        ).use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToFirst())
        }
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='conversation_attachments'",
            null,
        ).use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToFirst())
        }
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='tts_voice_clips'",
            null,
        ).use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToFirst())
        }
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10_createsPluginTablesAndCascadesChildren() {
        val databaseName = "migration-test-9-10"
        helper.createDatabase(databaseName, 9).apply {
            execSQL(
                """
                INSERT INTO app_preferences (`key`, value, updatedAt)
                VALUES ('selected_config_profile_id', 'config-1', 123)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 10, true, *AstrBotDatabase.allMigrations)

        val database = context.openDatabaseForVerification(databaseName)
        listOf(
            "plugin_install_records",
            "plugin_manifest_snapshots",
            "plugin_manifest_permissions",
            "plugin_permission_snapshots",
        ).forEach { tableName ->
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'",
                null,
            ).use { cursor ->
                org.junit.Assert.assertTrue("Expected $tableName to exist", cursor.moveToFirst())
            }
        }

        database.execSQL(
            """
            INSERT INTO plugin_install_records (
                pluginId, sourceType, sourceLocation, sourceImportedAt,
                protocolSupported, minHostVersionSatisfied, maxHostVersionSatisfied, compatibilityNotes,
                uninstallPolicy, enabled, installedAt, lastUpdatedAt, localPackagePath, extractedDir
            ) VALUES (
                'plugin.demo', 'LOCAL_FILE', '/plugins/demo.zip', 100,
                NULL, NULL, NULL, '',
                'KEEP_DATA', 1, 100, 100, '/plugins/packages/demo.zip', '/plugins/extracted/plugin.demo'
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO plugin_manifest_snapshots (
                pluginId, version, protocolVersion, author, title, description,
                minHostVersion, maxHostVersion, sourceType, entrySummary, riskLevel
            ) VALUES (
                'plugin.demo', '1.0.0', 1, 'AstrBot', 'Demo', 'Demo plugin',
                '0.3.0', '', 'LOCAL_FILE', 'Entry summary', 'LOW'
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO plugin_manifest_permissions (
                pluginId, permissionId, title, description, riskLevel, required, sortIndex
            ) VALUES (
                'plugin.demo', 'net.access', 'Network access', 'Allows outgoing requests', 'MEDIUM', 1, 0
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO plugin_permission_snapshots (
                pluginId, permissionId, title, description, riskLevel, required, sortIndex
            ) VALUES (
                'plugin.demo', 'net.access', 'Network access', 'Allows outgoing requests', 'MEDIUM', 1, 0
            )
            """.trimIndent(),
        )

        database.execSQL("DELETE FROM plugin_install_records WHERE pluginId = 'plugin.demo'")

        listOf(
            "plugin_manifest_snapshots",
            "plugin_manifest_permissions",
            "plugin_permission_snapshots",
        ).forEach { tableName ->
            database.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
                cursor.moveToFirst()
                org.junit.Assert.assertEquals("Expected $tableName rows to cascade", 0, cursor.getInt(0))
            }
        }
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_addsPluginFailureStateColumnsWithCompatibleDefaults() {
        val databaseName = "migration-test-10-11"
        helper.createDatabase(databaseName, 10).apply {
            execSQL(
                """
                INSERT INTO plugin_install_records (
                    pluginId, sourceType, sourceLocation, sourceImportedAt,
                    protocolSupported, minHostVersionSatisfied, maxHostVersionSatisfied, compatibilityNotes,
                    uninstallPolicy, enabled, installedAt, lastUpdatedAt, localPackagePath, extractedDir
                ) VALUES (
                    'plugin.demo', 'LOCAL_FILE', '/plugins/demo.zip', 100,
                    1, 1, 1, '',
                    'KEEP_DATA', 1, 100, 100, '/plugins/packages/demo.zip', '/plugins/extracted/plugin.demo'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO plugin_manifest_snapshots (
                    pluginId, version, protocolVersion, author, title, description,
                    minHostVersion, maxHostVersion, sourceType, entrySummary, riskLevel
                ) VALUES (
                    'plugin.demo', '1.0.0', 1, 'AstrBot', 'Demo', 'Demo plugin',
                    '0.3.0', '', 'LOCAL_FILE', 'Entry summary', 'LOW'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 11, true, *AstrBotDatabase.allMigrations)

        val database = context.openDatabaseForVerification(databaseName)
        database.rawQuery(
            """
            SELECT consecutiveFailureCount, lastFailureAtEpochMillis, lastErrorSummary, suspendedUntilEpochMillis
            FROM plugin_install_records
            WHERE pluginId = 'plugin.demo'
            """.trimIndent(),
            null,
        ).use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToFirst())
            org.junit.Assert.assertEquals(0, cursor.getInt(0))
            org.junit.Assert.assertTrue(cursor.isNull(1))
            org.junit.Assert.assertEquals("", cursor.getString(2))
            org.junit.Assert.assertTrue(cursor.isNull(3))
        }

        database.execSQL(
            """
            UPDATE plugin_install_records
            SET consecutiveFailureCount = 3,
                lastFailureAtEpochMillis = 1234,
                lastErrorSummary = 'socket timeout',
                suspendedUntilEpochMillis = 5678
            WHERE pluginId = 'plugin.demo'
            """.trimIndent(),
        )

        database.rawQuery(
            """
            SELECT consecutiveFailureCount, lastFailureAtEpochMillis, lastErrorSummary, suspendedUntilEpochMillis
            FROM plugin_install_records
            WHERE pluginId = 'plugin.demo'
            """.trimIndent(),
            null,
        ).use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToFirst())
            org.junit.Assert.assertEquals(3, cursor.getInt(0))
            org.junit.Assert.assertEquals(1234L, cursor.getLong(1))
            org.junit.Assert.assertEquals("socket timeout", cursor.getString(2))
            org.junit.Assert.assertEquals(5678L, cursor.getLong(3))
        }
        database.close()
    }
}
