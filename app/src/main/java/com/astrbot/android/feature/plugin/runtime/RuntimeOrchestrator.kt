package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.plugin.PluginV2StreamingMode

/**
 * Thin compatibility shell retained during phase 3 migration.
 *
 * The real orchestration logic now lives in [DefaultRuntimeLlmOrchestrator].
 * New feature code should depend on [RuntimeLlmOrchestratorPort] instead of
 * calling this static object directly.
 */
internal object RuntimeOrchestrator {
    private val compatDelegate: RuntimeLlmOrchestratorPort = DefaultRuntimeLlmOrchestrator()

    /**
     * Compatibility entry point that forwards to the class-based orchestrator.
     */
    suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent? = null,
    ): PluginV2HostLlmDeliveryResult {
        return compatDelegate.dispatchLlm(
            ctx = ctx,
            llmRuntime = llmRuntime,
            callbacks = callbacks,
            userMessage = userMessage,
            preBuiltPluginEvent = preBuiltPluginEvent,
        )
    }
}

/**
 * Platform-specific callbacks for the [RuntimeOrchestrator]. Each platform (App,
 * QQ) provides an implementation with its own delivery, persistence, and provider
 * invocation logic.
 */
internal interface PlatformLlmCallbacks {
    val platformInstanceKey: String
    val hostCapabilityGateway: PluginHostCapabilityGateway
    val followupSender: PluginV2FollowupSender?

    suspend fun prepareReply(result: PluginV2LlmPipelineResult): PluginV2HostPreparedReply
    suspend fun sendReply(prepared: PluginV2HostPreparedReply): PluginV2HostSendResult
    suspend fun persistDeliveredReply(
        prepared: PluginV2HostPreparedReply,
        sendResult: PluginV2HostSendResult,
        pipelineResult: PluginV2LlmPipelineResult,
    )

    suspend fun invokeProvider(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
    ): PluginV2ProviderInvocationResult
}
