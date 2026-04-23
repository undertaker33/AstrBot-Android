package com.astrbot.android.feature.plugin.runtime

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
        val writerGuard = testPluginFailureGuard(
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

        val dispatcher = testPluginRuntimeDispatcher(
            failureGuard = testPluginFailureGuard(
                store = sharedStore,
                scopedStore = scopedStore,
                policy = PluginFailurePolicy(
                    maxConsecutiveFailures = 2,
                    suspensionWindowMillis = 500L,
                ),
                clock = { clock.now },
            ),
            clock = { clock.now },
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
        val scheduler = testPluginRuntimeScheduler(clock = { clock.now })
        scheduler.recordDispatched(
            pluginId = "alpha",
            trigger = PluginTriggerSource.OnCommand,
        )
        scheduler.recordSuccess(
            pluginId = "alpha",
            trigger = PluginTriggerSource.OnCommand,
            policy = policy,
        )

        val dispatcher = testPluginRuntimeDispatcher(
            failureGuard = testPluginFailureGuard(clock = { clock.now }),
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

}
