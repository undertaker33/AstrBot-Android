package com.astrbot.android.runtime.llm

import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.PlatformLlmCallbacks
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryResult
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort

@Suppress("DEPRECATION")
@Deprecated(
    "Phase-2 residue — test seam only. Production uses Hilt-provided RuntimeLlmOrchestratorPort.",
    level = DeprecationLevel.WARNING,
)
internal class LegacyRuntimeOrchestratorAdapter(
    private val delegate: RuntimeLlmOrchestratorPort,
) : RuntimeLlmOrchestratorPort {
    override suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent?,
    ): PluginV2HostLlmDeliveryResult {
        return delegate.dispatchLlm(
            ctx = ctx,
            llmRuntime = llmRuntime,
            callbacks = callbacks,
            userMessage = userMessage,
            preBuiltPluginEvent = preBuiltPluginEvent,
        )
    }
}
