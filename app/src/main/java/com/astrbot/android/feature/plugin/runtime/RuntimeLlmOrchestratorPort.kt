package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.model.chat.ConversationMessage

internal interface RuntimeLlmOrchestratorPort {
    suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent? = null,
    ): PluginV2HostLlmDeliveryResult
}
