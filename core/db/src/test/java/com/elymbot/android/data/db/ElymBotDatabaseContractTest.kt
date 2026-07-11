package com.elymbot.android.data.db

import com.elymbot.android.data.db.resource.ResourceCenterItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ElymBotDatabaseContractTest {

    @Test
    fun database_schema_contract_tracks_current_terminal_tables() {
        val executionRecord = CronJobExecutionRecordEntity(
            executionId = "exec-1",
            jobId = "job-1",
            status = "succeeded",
            startedAt = 1L,
            completedAt = 2L,
            durationMs = 1L,
            attempt = 1,
            trigger = "run_now",
            errorCode = "",
            errorMessage = "",
            deliverySummary = "{}",
        )
        val pluginState = PluginStateEntryEntity(
            pluginId = "plugin",
            scopeKind = "plugin",
            scopeId = "plugin",
            key = "key",
            valueJson = "{}",
            updatedAt = 1L,
        )
        val resource = ResourceCenterItemEntity(
            resourceId = "resource",
            kind = "skill",
            skillKind = "prompt",
            name = "Resource",
            description = "",
            content = "",
            payloadJson = "{}",
            source = "test",
            enabled = true,
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertEquals(25, astrBotDatabaseMigrations.last().endVersion)
        assertEquals("exec-1", executionRecord.executionId)
        assertEquals("plugin", pluginState.pluginId)
        assertEquals("resource", resource.resourceId)
    }
}
