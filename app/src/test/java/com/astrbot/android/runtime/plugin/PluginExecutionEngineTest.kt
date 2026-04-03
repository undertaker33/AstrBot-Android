package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.ErrorResult
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
}
