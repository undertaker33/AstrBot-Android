package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginTriggerSource
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppChatPluginRuntimeTest {
    @Test
    fun default_app_chat_runtime_writes_failures_into_shared_store() {
        val attempts = AtomicInteger(0)
        val sharedStore = InMemoryPluginFailureStateStore()
        val scopedStore = InMemoryPluginScopedFailureStateStore()
        PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(sharedStore)
        PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(scopedStore)
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "shared-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    attempts.incrementAndGet()
                    error("app chat boom")
                },
            )
        }

        try {
            repeat(3) {
                DefaultAppChatPluginRuntime.execute(
                    trigger = PluginTriggerSource.BeforeSendMessage,
                    contextFactory = ::executionContextFor,
                )
            }

            val snapshot = sharedStore.get("shared-plugin")
            assertEquals(3, attempts.get())
            assertEquals(3, snapshot?.consecutiveFailureCount)
            assertEquals("app chat boom", snapshot?.lastErrorSummary)
            assertTrue(snapshot?.isSuspended == true)
        } finally {
            PluginRuntimeRegistry.reset()
            PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(null)
            PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(null)
        }
    }

    @Test
    fun default_app_chat_runtime_does_not_call_context_factory_when_no_legacy_plugins_are_registered() {
        val contextFactoryCalls = AtomicInteger(0)

        val batch = DefaultAppChatPluginRuntime.execute(
            trigger = PluginTriggerSource.BeforeSendMessage,
            contextFactory = { plugin ->
                contextFactoryCalls.incrementAndGet()
                executionContextFor(plugin, PluginTriggerSource.BeforeSendMessage)
            },
        )

        assertEquals(0, contextFactoryCalls.get())
        assertTrue(batch.outcomes.isEmpty())
        assertTrue(batch.skipped.isEmpty())
    }
}
