package com.elymbot.android.data.db

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
class ElymBotDatabaseGeofenceMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        ElymBotDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    @Throws(IOException::class)
    fun migrate23To24_createsGeofenceTablesWithForeignKeysAndValidatesSchema() {
        val databaseName = "core-db-migration-test-23-24"
        helper.createDatabase(databaseName, 23).apply {
            execSQL(
                """
                INSERT INTO config_profiles (
                    id, name, defaultChatProviderId, defaultVisionProviderId,
                    defaultSttProviderId, defaultTtsProviderId,
                    sttEnabled, ttsEnabled, alwaysTtsEnabled, ttsReadBracketedContent,
                    textStreamingEnabled, voiceStreamingEnabled, streamingMessageIntervalMs,
                    realWorldTimeAwarenessEnabled, imageCaptionTextEnabled, webSearchEnabled,
                    proactiveEnabled, includeScheduledTaskConversationContext, ttsVoiceId,
                    pluginCommandsAdminOnlyEnabled, sessionIsolationEnabled,
                    wakeWordsAdminOnlyEnabled, privateChatRequiresWakeWord,
                    replyTextPrefix, quoteSenderMessageEnabled, mentionSenderEnabled,
                    replyOnAtOnlyEnabled, whitelistEnabled, logOnWhitelistMiss,
                    adminGroupBypassWhitelistEnabled, adminPrivateBypassWhitelistEnabled,
                    ignoreSelfMessageEnabled, ignoreAtAllEventEnabled,
                    replyWhenPermissionDenied, rateLimitWindowSeconds,
                    rateLimitMaxCount, rateLimitStrategy, keywordDetectionEnabled,
                    contextLimitStrategy, maxContextTurns, dequeueContextTurns,
                    llmCompressInstruction, llmCompressKeepRecent,
                    llmCompressProviderId, sortIndex, updatedAt
                ) VALUES (
                    'config-geofence', 'Geofence Config', '', '',
                    '', '',
                    0, 0, 0, 1,
                    0, 0, 120,
                    0, 0, 0,
                    0, 0, '',
                    0, 0,
                    0, 0,
                    '', 0, 0,
                    1, 0, 0,
                    1, 1,
                    1, 1,
                    0, 0,
                    0, 'drop', 0,
                    'truncate_by_turns', -1, 1,
                    '', 6,
                    '', 0, 1000
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 24, true, *astrBotDatabaseMigrations)

        val database = openDatabaseForVerification(databaseName)
        listOf("geofence_rules", "geofence_regions", "config_geofence_bindings", "geofence_execution_records")
            .forEach { tableName ->
                database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'", null)
                    .use { cursor ->
                        org.junit.Assert.assertTrue("Expected $tableName to exist", cursor.moveToFirst())
                    }
            }
        assertForeignKey(
            database = database,
            tableName = "geofence_regions",
            referencedTable = "geofence_rules",
            fromColumn = "ruleId",
            toColumn = "ruleId",
            onDelete = "CASCADE",
        )
        assertForeignKey(
            database = database,
            tableName = "config_geofence_bindings",
            referencedTable = "config_profiles",
            fromColumn = "configId",
            toColumn = "id",
            onDelete = "CASCADE",
        )
        assertForeignKey(
            database = database,
            tableName = "config_geofence_bindings",
            referencedTable = "geofence_rules",
            fromColumn = "ruleId",
            toColumn = "ruleId",
            onDelete = "CASCADE",
        )
        database.close()
    }

    private fun openDatabaseForVerification(databaseName: String): SQLiteDatabase =
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).apply {
            setForeignKeyConstraintsEnabled(true)
        }

    private fun assertForeignKey(
        database: SQLiteDatabase,
        tableName: String,
        referencedTable: String,
        fromColumn: String,
        toColumn: String,
        onDelete: String,
    ) {
        database.rawQuery("PRAGMA foreign_key_list($tableName)", null).use { cursor ->
            var found = false
            val tableIndex = cursor.getColumnIndexOrThrow("table")
            val fromIndex = cursor.getColumnIndexOrThrow("from")
            val toIndex = cursor.getColumnIndexOrThrow("to")
            val onDeleteIndex = cursor.getColumnIndexOrThrow("on_delete")
            while (cursor.moveToNext()) {
                found = found ||
                    cursor.getString(tableIndex) == referencedTable &&
                    cursor.getString(fromIndex) == fromColumn &&
                    cursor.getString(toIndex) == toColumn &&
                    cursor.getString(onDeleteIndex) == onDelete
            }
            org.junit.Assert.assertTrue(
                "Expected FK $tableName.$fromColumn -> $referencedTable.$toColumn ON DELETE $onDelete",
                found,
            )
        }
    }
}
