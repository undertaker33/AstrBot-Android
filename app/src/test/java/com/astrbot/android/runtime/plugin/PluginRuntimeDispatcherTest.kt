package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PluginRuntimeDispatcherTest {
    @Test
    fun dispatcher_filters_non_runnable_plugins_and_preserves_dispatch_order() {
        val clock = TestClock()
        val sharedStore = InMemoryPluginFailureStateStore()
        val scopedStore = InMemoryPluginScopedFailureStateStore()
        val writerGuard = PluginFailureGuard(
            store = sharedStore,
            scopedStore = scopedStore,
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 2,
                suspensionWindowMillis = 500L,
            ),
            clock = { clock.now },
        )
        writerGuard.recordFailure(
            pluginId = "failed-suspended",
            trigger = PluginTriggerSource.OnCommand,
            errorSummary = "shared failure",
        )
        writerGuard.recordFailure(
            pluginId = "failed-suspended",
            trigger = PluginTriggerSource.OnCommand,
            errorSummary = "shared failure",
        )

        val dispatcher = PluginRuntimeDispatcher(
            PluginFailureGuard(
                store = sharedStore,
                scopedStore = scopedStore,
                policy = PluginFailurePolicy(
                    maxConsecutiveFailures = 2,
                    suspensionWindowMillis = 500L,
                ),
                clock = { clock.now },
            ),
        )
        val plan = dispatcher.dispatch(
            trigger = PluginTriggerSource.OnCommand,
            plugins = listOf(
                runtimePlugin(
                    pluginId = "not-installed",
                    installStatus = PluginInstallStatus.NOT_INSTALLED,
                ),
                runtimePlugin(
                    pluginId = "disabled",
                    enabled = false,
                ),
                runtimePlugin(
                    pluginId = "incompatible",
                    compatibilityStatus = PluginCompatibilityStatus.INCOMPATIBLE,
                ),
                runtimePlugin(pluginId = "failed-suspended"),
                runtimePlugin(pluginId = "alpha"),
                runtimePlugin(pluginId = "omega"),
            ),
        )

        assertEquals(
            listOf("alpha", "omega"),
            plan.executable.map { plugin -> plugin.pluginId },
        )
        assertEquals(
            mapOf(
                "not-installed" to PluginDispatchSkipReason.NotInstalled,
                "disabled" to PluginDispatchSkipReason.Disabled,
                "incompatible" to PluginDispatchSkipReason.Incompatible,
                "failed-suspended" to PluginDispatchSkipReason.FailureSuspended,
            ),
            plan.skipped.associate { skipped -> skipped.plugin.pluginId to skipped.reason },
        )
    }

    @Test
    fun dispatcher_respects_scheduler_windows_and_exposes_trigger_scope() {
        val clock = TestClock()
        val policy = PluginSchedulePolicy(successCooldownMillis = 500L)
        val scheduler = PluginRuntimeScheduler(clock = { clock.now })
        scheduler.recordDispatched(
            pluginId = "alpha",
            trigger = PluginTriggerSource.OnCommand,
        )
        scheduler.recordSuccess(
            pluginId = "alpha",
            trigger = PluginTriggerSource.OnCommand,
            policy = policy,
        )

        val dispatcher = PluginRuntimeDispatcher(
            failureGuard = PluginFailureGuard(clock = { clock.now }),
            clock = { clock.now },
            scheduler = scheduler,
            policyResolver = { _, _ -> policy },
        )

        val plan = dispatcher.dispatch(
            trigger = PluginTriggerSource.OnCommand,
            plugins = listOf(
                runtimePlugin(pluginId = "alpha"),
                runtimePlugin(pluginId = "beta"),
            ),
        )

        assertEquals(PluginDispatchScope.Message, plan.scope)
        assertEquals(listOf("beta"), plan.executable.map { plugin -> plugin.pluginId })
        assertEquals(
            mapOf("alpha" to PluginDispatchSkipReason.SchedulerCoolingDown),
            plan.skipped.associate { skipped -> skipped.plugin.pluginId to skipped.reason },
        )
    }

    @Test
    fun dispatch_legacy_noops_and_logs_guardrail_when_phase4_llm_stage_is_reintroduced() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1_000L })
        val dispatcher = PluginRuntimeDispatcher(
            failureGuard = PluginFailureGuard(clock = { 1_000L }),
            clock = { 1_000L },
            logBus = logBus,
        )

        val attempt = dispatcher.dispatchLegacy(
            trigger = PluginTriggerSource.BeforeSendMessage,
            plugins = listOf(runtimePlugin(pluginId = "alpha")),
            requestedStage = PluginExecutionStage.ResultDecorating,
        )

        assertFalse(attempt.accepted)
        assertEquals("phase4_stage_result_decorating", attempt.reason)
        val guardrail = logBus.snapshot(limit = 10).single()
        assertEquals(PluginRuntimeLogCategory.Dispatcher, guardrail.category)
        assertEquals(PluginRuntimeLogLevel.Warning, guardrail.level)
        assertEquals("legacy_dispatch_guardrail", guardrail.code)
        assertEquals("result_decorating", guardrail.metadata["requestedStage"])
        assertEquals("phase4_stage_result_decorating", guardrail.metadata["reason"])
    }

    @Test
    fun dispatch_legacy_noops_and_logs_guardrail_when_trigger_source_is_missing() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 2_000L })
        val dispatcher = PluginRuntimeDispatcher(
            failureGuard = PluginFailureGuard(clock = { 2_000L }),
            clock = { 2_000L },
            logBus = logBus,
        )

        val attempt = dispatcher.dispatchLegacy(
            trigger = null,
            plugins = listOf(runtimePlugin(pluginId = "alpha")),
        )

        assertFalse(attempt.accepted)
        assertEquals("missing_legacy_trigger_source", attempt.reason)
        val guardrail = logBus.snapshot(limit = 10).single()
        assertEquals("legacy_dispatch_guardrail", guardrail.code)
        assertEquals("missing_legacy_trigger_source", guardrail.metadata["reason"])
    }
}
