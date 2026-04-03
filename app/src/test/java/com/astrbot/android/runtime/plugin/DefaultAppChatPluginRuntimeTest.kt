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
        PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(sharedStore)
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
        }
    }
}
