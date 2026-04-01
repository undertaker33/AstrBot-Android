package com.astrbot.android.data.db

import android.content.Context
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

        val database = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
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

        val database = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
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

        val database = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
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
}
