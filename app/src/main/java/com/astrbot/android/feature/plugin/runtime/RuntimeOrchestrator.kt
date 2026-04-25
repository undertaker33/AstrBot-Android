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
@Deprecated(
    "Phase-2 residue — not a production path. Use Hilt-provided RuntimeLlmOrchestratorPort.",
    level = DeprecationLevel.WARNING,
)
internal object RuntimeOrchestrator {
    @Volatile
    private var compatDelegate: RuntimeLlmOrchestratorPort? = null

    internal fun installCompatDelegateForTests(delegate: RuntimeLlmOrchestratorPort?) {
        compatDelegate = delegate
    }

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
        val delegate = compatDelegate
            ?: error("RuntimeOrchestrator compatibility shell requires an installed RuntimeLlmOrchestratorPort.")
        return delegate.dispatchLlm(
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

    suspend fun handleToolResult(
        request: PluginV2ToolResultDeliveryRequest,
    ): PluginToolResult = request.result

    suspend fun invokeProvider(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
    ): PluginV2ProviderInvocationResult
}
