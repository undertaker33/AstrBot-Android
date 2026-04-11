package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginExecutionResultMergerTest {
    @Test
    fun merger_preserves_chain_order_and_reports_interactive_conflicts() {
        val merger = PluginExecutionResultMerger()
        val merged = merger.merge(
            trigger = PluginTriggerSource.OnCommand,
            outcomes = listOf(
                outcome(pluginId = "alpha", result = TextResult("alpha")),
                outcome(
                    pluginId = "beta",
                    result = SettingsUiRequest(PluginSettingsSchema(title = "beta")),
                ),
                outcome(
                    pluginId = "gamma",
                    result = SettingsUiRequest(PluginSettingsSchema(title = "gamma")),
                ),
            ),
        )

        assertEquals(listOf("alpha", "beta", "gamma"), merged.orderedPluginIds)
        assertEquals("gamma", merged.primaryInteractivePluginId)
        assertEquals("settings_ui", merged.primaryInteractiveResultType)
        assertEquals(listOf("beta"), merged.conflicts.map { conflict -> conflict.pluginId })
        assertEquals("interactive_result_overridden", merged.conflicts.single().reason)
    }

    @Test
    fun merger_noops_and_logs_guardrail_when_phase4_llm_stage_is_routed_into_legacy_merge() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 25L })
        val merger = PluginExecutionResultMerger(logBus = logBus, clock = { 25L })

        val merged = merger.merge(
            trigger = PluginTriggerSource.OnCommand,
            outcomes = listOf(
                outcome(pluginId = "alpha", result = TextResult("alpha")),
            ),
            requestedStage = PluginExecutionStage.LlmRequest,
        )

        assertTrue(merged.orderedPluginIds.isEmpty())
        assertTrue(merged.resultTypeCounts.isEmpty())
        assertTrue(merged.conflicts.isEmpty())
        val guardrail = logBus.snapshot(limit = 10).single()
        assertEquals(PluginRuntimeLogCategory.ResultMerger, guardrail.category)
        assertEquals(PluginRuntimeLogLevel.Warning, guardrail.level)
        assertEquals("legacy_result_merger_guardrail", guardrail.code)
        assertEquals("llm_request", guardrail.metadata["requestedStage"])
        assertEquals("phase4_stage_llm_request", guardrail.metadata["reason"])
    }

    private fun outcome(
        pluginId: String,
        result: com.astrbot.android.model.plugin.PluginExecutionResult,
    ): PluginExecutionOutcome {
        val plugin = runtimePlugin(pluginId = pluginId) { result }
        return PluginExecutionOutcome(
            pluginId = plugin.pluginId,
            pluginVersion = plugin.pluginVersion,
            installState = plugin.installState,
            context = executionContextFor(plugin),
            result = result,
            succeeded = true,
            failureSnapshot = PluginFailureSnapshot(pluginId = pluginId),
        )
    }
}
