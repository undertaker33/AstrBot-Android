package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginRuntimeDispatcherTest {
    @Test
    fun dispatcher_filters_non_runnable_plugins_and_preserves_dispatch_order() {
        val clock = TestClock()
        val sharedStore = InMemoryPluginFailureStateStore()
        val writerGuard = PluginFailureGuard(
            store = sharedStore,
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 2,
                suspensionWindowMillis = 500L,
            ),
            clock = { clock.now },
        )
        writerGuard.recordFailure("failed-suspended", errorSummary = "shared failure")
        writerGuard.recordFailure("failed-suspended", errorSummary = "shared failure")

        val dispatcher = PluginRuntimeDispatcher(
            PluginFailureGuard(
                store = sharedStore,
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
}
