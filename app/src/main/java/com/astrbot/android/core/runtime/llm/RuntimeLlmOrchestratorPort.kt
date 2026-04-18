package com.astrbot.android.core.runtime.llm

import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.runtime.plugin.AppChatLlmPipelineRuntime
import com.astrbot.android.runtime.plugin.PlatformLlmCallbacks
import com.astrbot.android.runtime.plugin.PluginMessageEvent
import com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult

internal interface RuntimeLlmOrchestratorPort {
    suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent? = null,
    ): PluginV2HostLlmDeliveryResult
}
