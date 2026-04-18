package com.astrbot.android.runtime.llm

import com.astrbot.android.core.runtime.llm.RuntimeLlmOrchestratorPort
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.runtime.plugin.AppChatLlmPipelineRuntime
import com.astrbot.android.runtime.plugin.PlatformLlmCallbacks
import com.astrbot.android.runtime.plugin.PluginMessageEvent
import com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult
import com.astrbot.android.runtime.plugin.RuntimeOrchestrator

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
