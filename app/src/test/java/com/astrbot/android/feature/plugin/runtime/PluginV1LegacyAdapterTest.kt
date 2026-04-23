package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginV2LlmStage
import com.astrbot.android.feature.plugin.runtime.PluginLegacyBatchAttempt
import com.astrbot.android.feature.plugin.runtime.PluginLegacyDispatchAttempt
import com.astrbot.android.feature.plugin.runtime.PluginRuntimePlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PluginV1DispatchAdapterTest {

    @Test
    fun dispatchLegacy_delegates_to_explicit_legacy_boundary() {
        var capturedTrigger: PluginTriggerSource? = null
        var capturedRequestedStage: PluginExecutionStage? = null
        var capturedPlugins: List<PluginRuntimePlugin>? = null
        val expected = PluginLegacyDispatchAttempt(accepted = true, reason = "legacy")
        val adapter = PluginV1DispatchAdapter(
            dispatchV1Call = { trigger: PluginTriggerSource?, plugins: List<PluginRuntimePlugin>, requestedStage: PluginExecutionStage? ->
                capturedTrigger = trigger
                capturedPlugins = plugins
                capturedRequestedStage = requestedStage
                expected
            },
            executeV1BatchCall = { _, _, _, _ ->
                PluginLegacyBatchAttempt(accepted = true)
            },
        )

        val result = adapter.dispatchLegacy(
            trigger = PluginTriggerSource.OnCommand,
            plugins = emptyList(),
            requestedStage = PluginV2LlmStage.LlmRequest,
        )

        assertSame(expected, result)
        assertEquals(PluginTriggerSource.OnCommand, capturedTrigger)
        assertEquals(PluginV2LlmStage.LlmRequest, capturedRequestedStage)
        assertEquals(emptyList<PluginRuntimePlugin>(), capturedPlugins)
    }

    @Test
    fun executeLegacyBatch_delegates_to_explicit_compat_boundary() {
        var capturedTrigger: PluginTriggerSource? = null
        var capturedRequestedStage: PluginExecutionStage? = null
        var capturedPlugins: List<PluginRuntimePlugin>? = null
        val expected = PluginLegacyBatchAttempt(
            accepted = true,
            reason = "legacy-batch",
        )
        val adapter = PluginV1DispatchAdapter(
            dispatchV1Call = { _, _, _ ->
                PluginLegacyDispatchAttempt(accepted = true)
            },
            executeV1BatchCall = { trigger, plugins, _, requestedStage ->
                capturedTrigger = trigger
                capturedPlugins = plugins
                capturedRequestedStage = requestedStage
                expected
            },
        )

        val result = adapter.executeLegacyBatch(
            trigger = PluginTriggerSource.BeforeSendMessage,
            plugins = emptyList(),
            contextFactory = { executionContextFor(runtimePlugin("alpha") { TextResult("ok") }) },
            requestedStage = PluginV2LlmStage.LlmRequest,
        )

        assertSame(expected, result)
        assertEquals(PluginTriggerSource.BeforeSendMessage, capturedTrigger)
        assertEquals(PluginV2LlmStage.LlmRequest, capturedRequestedStage)
        assertEquals(emptyList<PluginRuntimePlugin>(), capturedPlugins)
    }
}
