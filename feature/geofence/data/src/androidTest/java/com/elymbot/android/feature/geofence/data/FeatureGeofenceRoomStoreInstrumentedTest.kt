package com.elymbot.android.feature.geofence.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.data.db.ElymBotDatabase
import com.elymbot.android.data.db.geofence.GeofenceRegionEntity
import com.elymbot.android.data.db.geofence.GeofenceRuleEntity
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureGeofenceRoomStoreInstrumentedTest {
    @Test
    fun store_usesRealRoomTransactionsReferencesAndStableRegionOrdering() = runBlocking {
        val database = newDatabase()
        seedConfig(database, "config-1")
        val store = FeatureGeofenceRuleRepositoryStore(database.geofenceRuleDao())

        store.createRule(
            validRule(),
            regions = listOf(
                validRegion(regionId = "region-2", sortIndex = 20),
                validRegion(regionId = "region-1", sortIndex = 10),
            ),
        )
        store.upsertConfigBinding(ConfigGeofenceBinding(configId = "config-1", ruleId = "rule-1", enabled = true))
        store.recordExecution(validExecution())

        assertEquals(listOf("region-1", "region-2"), store.getRule("rule-1")?.regions.orEmpty().map { it.regionId })
        assertEquals(listOf("rule-1"), store.listConfigBindings("config-1").map { it.ruleId })
        assertEquals("execution-1", store.latestExecutionRecord("rule-1")?.executionId)

        store.deleteRule("rule-1")

        assertNull(store.getRule("rule-1"))
        assertEquals(0, count(database, "geofence_regions"))
        assertEquals(0, count(database, "config_geofence_bindings"))
        assertEquals(
            "Execution records retain trigger-time snapshot ids after rule deletion.",
            1,
            count(database, "geofence_execution_records"),
        )
        database.close()
    }

    @Test
    fun dao_upsertRuleWithRegionsRollsBackRuleWhenRegionInsertViolatesForeignKey() = runBlocking {
        val database = newDatabase()
        val dao = database.geofenceRuleDao()

        val result = runCatching {
            dao.upsertRuleWithRegions(
                rule = validRuleEntity(ruleId = "tx-rule"),
                regions = listOf(validRegionEntity(regionId = "bad-region", ruleId = "missing-rule")),
            )
        }

        assertTrue(result.exceptionOrNull() is SQLiteConstraintException)
        assertNull(dao.getRuleWithRegions("tx-rule"))
        database.close()
    }

    @Test
    fun store_rejectsMissingExecutionReferencesBeforeWritingSnapshotRecord() = runBlocking {
        val database = newDatabase()
        val store = FeatureGeofenceRuleRepositoryStore(database.geofenceRuleDao())

        val result = runCatching {
            store.recordExecution(validExecution())
        }

        assertTrue(result.exceptionOrNull() is GeofenceReferenceException)
        assertEquals(0, count(database, "geofence_execution_records"))
        database.close()
    }

    private fun newDatabase(): ElymBotDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ElymBotDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

    private fun seedConfig(database: ElymBotDatabase, configId: String) {
        database.openHelper.writableDatabase.execSQL(
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
                ?, 'Geofence Config', '', '',
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
            arrayOf(configId),
        )
    }

    private fun count(database: ElymBotDatabase, tableName: String): Int {
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun validRule(): GeofenceRule =
        GeofenceRule(
            ruleId = "rule-1",
            name = "Office reminder",
            description = "Trigger at office",
            triggerEnter = true,
            actionType = GeofenceActionType.AGENT_PROMPT,
            actionPrompt = "Remind me",
        )

    private fun validRegion(regionId: String, sortIndex: Int): GeofenceRegion =
        GeofenceRegion(
            regionId = regionId,
            ruleId = "rule-1",
            label = regionId,
            latitude = 31.2304,
            longitude = 121.4737,
            radiusMeters = 100f,
            sortIndex = sortIndex,
        )

    private fun validExecution(): GeofenceExecutionRecord =
        GeofenceExecutionRecord(
            executionId = "execution-1",
            ruleId = "rule-1",
            regionId = "region-1",
            configId = "config-1",
            transition = GeofenceTransition.ENTER,
            startedAt = 10L,
        )

    private fun validRuleEntity(ruleId: String): GeofenceRuleEntity =
        GeofenceRuleEntity(
            ruleId = ruleId,
            name = "Transaction test",
            description = "",
            enabled = true,
            triggerEnter = true,
            triggerExit = false,
            triggerDwell = false,
            dwellDelayMillis = 0L,
            actionType = GeofenceActionType.AGENT_PROMPT.persistedValue,
            actionPrompt = "Run",
            targetPlatform = "",
            targetConversationId = "",
            targetBotId = "",
            targetConfigProfileId = "",
            targetPersonaId = "",
            targetProviderId = "",
            minimumTriggerIntervalMillis = 0L,
            status = "active",
            lastRegisteredAt = 0L,
            lastTriggeredAt = 0L,
            lastError = "",
            createdAt = 1L,
            updatedAt = 1L,
        )

    private fun validRegionEntity(regionId: String, ruleId: String): GeofenceRegionEntity =
        GeofenceRegionEntity(
            regionId = regionId,
            ruleId = ruleId,
            label = regionId,
            latitude = 31.2304,
            longitude = 121.4737,
            radiusMeters = 100f,
            addressLabel = "",
            sortIndex = 0,
            createdAt = 1L,
            updatedAt = 1L,
        )
}
