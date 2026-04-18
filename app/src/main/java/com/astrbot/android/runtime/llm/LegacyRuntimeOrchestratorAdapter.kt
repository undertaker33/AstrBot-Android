package com.astrbot.android.runtime.llm

import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.PlatformLlmCallbacks
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryResult
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.plugin.runtime.RuntimeOrchestrator

internal class LegacyRuntimeOrchestratorAdapter : RuntimeLlmOrchestratorPort {
    override suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent?,
    ): PluginV2HostLlmDeliveryResult {
        return RuntimeOrchestrator.dispatchLlm(
            ctx = ctx,
            llmRuntime = llmRuntime,
            callbacks = callbacks,
            userMessage = userMessage,
            preBuiltPluginEvent = preBuiltPluginEvent,
        )
    }
}
