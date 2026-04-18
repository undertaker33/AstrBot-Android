package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.ErrorResult
import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.model.plugin.PluginTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginExecutionEngineTest {
    @Test
    fun engine_returns_protocol_results_in_dispatch_order() {
        val clock = TestClock()
        val failureGuard = PluginFailureGuard(clock = { clock.now })
        val engine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(failureGuard),
            failureGuard = failureGuard,
        )
        val plugins = listOf(
            runtimePlugin("alpha") { TextResult("alpha-result") },
            runtimePlugin("beta") { TextResult("beta-result") },
        )

        val batch = engine.executeBatch(
            trigger = PluginTriggerSource.OnCommand,
            plugins = plugins,
            contextFactory = ::executionContextFor,
        )

        assertEquals(listOf("alpha", "beta"), batch.outcomes.map { outcome -> outcome.pluginId })
        assertEquals(listOf("alpha-result", "beta-result"), batch.outcomes.map { outcome -> (outcome.result as TextResult).text })
        assertTrue(batch.outcomes.all { outcome -> outcome.succeeded })
        assertTrue(batch.skipped.isEmpty())
    }

    @Test
    fun engine_isolates_plugin_failures_writes_error_summary_and_does_not_interrupt_remaining_plugins() {
        val clock = TestClock()
        val sharedStore = InMemoryPluginFailureStateStore()
        val failureGuard = PluginFailureGuard(
            store = sharedStore,
            policy = PluginFailurePolicy(maxConsecutiveFailures = 2, suspensionWindowMillis = 1_000L),
            clock = { clock.now },
        )
        val engine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(failureGuard),
            failureGuard = failureGuard,
        )
        val executed = mutableListOf<String>()
        val plugins = listOf(
            runtimePlugin("alpha") {
                executed += "alpha"
                TextResult("alpha-result")
            },
            runtimePlugin("boom") {
                executed += "boom"
                error("boom")
            },
            runtimePlugin("omega") {
                executed += "omega"
                TextResult("omega-result")
            },
        )

        val batch = engine.executeBatch(
            trigger = PluginTriggerSource.OnCommand,
            plugins = plugins,
            contextFactory = ::executionContextFor,
        )

        assertEquals(listOf("alpha", "boom", "omega"), executed)
        assertEquals(listOf("alpha", "boom", "omega"), batch.outcomes.map { outcome -> outcome.pluginId })
        assertTrue(batch.outcomes[0].succeeded)
        assertFalse(batch.outcomes[1].succeeded)
        assertTrue(batch.outcomes[1].result is ErrorResult)
        assertEquals("boom", (batch.outcomes[1].result as ErrorResult).message)
        assertTrue(batch.outcomes[2].succeeded)
        assertEquals("omega-result", (batch.outcomes[2].result as TextResult).text)
        val observerGuard = PluginFailureGuard(
            store = sharedStore,
            policy = PluginFailurePolicy(maxConsecutiveFailures = 2, suspensionWindowMillis = 1_000L),
            clock = { clock.now },
        )
        val snapshot = observerGuard.snapshot("boom")
        assertEquals(1, snapshot.consecutiveFailureCount)
        assertEquals("boom", snapshot.lastErrorSummary)
    }

    @Test
    fun engine_updates_scheduler_state_and_exposes_merged_batch_snapshot() {
        val clock = TestClock(now = 50_000L)
        val failureGuard = PluginFailureGuard(clock = { clock.now })
        val scheduler = PluginRuntimeScheduler(clock = { clock.now })
        val policy = PluginSchedulePolicy(successCooldownMillis = 400L)
        val engine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(
                failureGuard = failureGuard,
                clock = { clock.now },
                scheduler = scheduler,
                policyResolver = { _, _ -> policy },
            ),
            failureGuard = failureGuard,
            clock = { clock.now },
            scheduler = scheduler,
            policyResolver = { _, _ -> policy },
        )
        val plugins = listOf(
            runtimePlugin("alpha") {
                SettingsUiRequest(PluginSettingsSchema(title = "alpha"))
            },
            runtimePlugin("beta") {
                SettingsUiRequest(PluginSettingsSchema(title = "beta"))
            },
        )

        val firstBatch = engine.executeBatch(
            trigger = PluginTriggerSource.OnCommand,
            plugins = plugins,
            contextFactory = ::executionContextFor,
        )

        assertEquals("beta", firstBatch.merged.primaryInteractivePluginId)
        assertEquals(1, firstBatch.merged.conflicts.size)

        val secondBatch = engine.executeBatch(
            trigger = PluginTriggerSource.OnCommand,
            plugins = plugins,
            contextFactory = ::executionContextFor,
        )

        assertTrue(secondBatch.outcomes.isEmpty())
        assertEquals(2, secondBatch.skipped.size)
        assertTrue(
            secondBatch.skipped.all { skipped ->
                skipped.reason == PluginDispatchSkipReason.SchedulerCoolingDown
            },
        )
    }

    @Test
    fun engine_batch_normalizes_context_trigger_to_batch_trigger_for_failure_isolation() {
        val clock = TestClock(now = 90_000L)
        val scopedStore = InMemoryPluginScopedFailureStateStore()
        val failureGuard = PluginFailureGuard(
            scopedStore = scopedStore,
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 3,
                suspensionWindowMillis = 1_000L,
            ),
            clock = { clock.now },
        )
        val engine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(failureGuard),
            failureGuard = failureGuard,
        )
        val plugin = runtimePlugin(
            pluginId = "shared-plugin",
            supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
        ) {
            error("batch trigger boom")
        }

        repeat(3) {
            engine.executeBatch(
                trigger = PluginTriggerSource.BeforeSendMessage,
                plugins = listOf(plugin),
                contextFactory = {
                    executionContextFor(
                        plugin = plugin,
                        trigger = PluginTriggerSource.OnCommand,
                    )
                },
            )
        }

        val beforeSendSnapshot = failureGuard.snapshot(
            pluginId = "shared-plugin",
            trigger = PluginTriggerSource.BeforeSendMessage,
        )
        val onCommandSnapshot = failureGuard.snapshot(
            pluginId = "shared-plugin",
            trigger = PluginTriggerSource.OnCommand,
        )

        assertTrue(beforeSendSnapshot.isSuspended)
        assertEquals(3, beforeSendSnapshot.consecutiveFailureCount)
        assertEquals(0, onCommandSnapshot.consecutiveFailureCount)
    }

    @Test
    fun engine_execute_legacy_batch_noops_when_phase4_llm_stage_is_routed_back_into_legacy_path() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 12_000L })
        val failureGuard = PluginFailureGuard(clock = { 12_000L })
        val engine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(
                failureGuard = failureGuard,
                clock = { 12_000L },
                logBus = logBus,
            ),
            failureGuard = failureGuard,
            clock = { 12_000L },
            logBus = logBus,
        )

        val attempt = engine.executeLegacyBatch(
            trigger = PluginTriggerSource.BeforeSendMessage,
            plugins = listOf(runtimePlugin("alpha") { TextResult("alpha") }),
            contextFactory = ::executionContextFor,
            requestedStage = PluginExecutionStage.AfterMessageSent,
        )

        assertFalse(attempt.accepted)
        assertEquals("phase4_stage_after_message_sent", attempt.reason)
        assertTrue(attempt.batchResult == null)
        val guardrail = logBus.snapshot(limit = 10)
            .first { it.category == PluginRuntimeLogCategory.Execution }
        assertEquals(PluginRuntimeLogLevel.Warning, guardrail.level)
        assertEquals("legacy_execution_guardrail", guardrail.code)
        assertEquals("after_message_sent", guardrail.metadata["requestedStage"])
        assertEquals("phase4_stage_after_message_sent", guardrail.metadata["reason"])
    }

    @Test
    fun engine_execute_legacy_batch_noops_when_legacy_trigger_source_is_missing() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 13_000L })
        val failureGuard = PluginFailureGuard(clock = { 13_000L })
        val engine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(
                failureGuard = failureGuard,
                clock = { 13_000L },
                logBus = logBus,
            ),
            failureGuard = failureGuard,
            clock = { 13_000L },
            logBus = logBus,
        )

        val attempt = engine.executeLegacyBatch(
            trigger = null,
            plugins = listOf(runtimePlugin("alpha") { TextResult("alpha") }),
            contextFactory = ::executionContextFor,
        )

        assertFalse(attempt.accepted)
        assertEquals("missing_legacy_trigger_source", attempt.reason)
        assertTrue(attempt.batchResult == null)
        val guardrail = logBus.snapshot(limit = 10)
            .first { it.category == PluginRuntimeLogCategory.Execution }
        assertEquals("legacy_execution_guardrail", guardrail.code)
        assertEquals("missing_legacy_trigger_source", guardrail.metadata["reason"])
    }
}
