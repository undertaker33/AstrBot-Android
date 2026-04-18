package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.core.runtime.context.StreamingModeResolver
import com.astrbot.android.core.runtime.context.SystemPromptBuilder
import com.astrbot.android.feature.plugin.runtime.MessageConverters.toPluginProviderMessages

/**
 * Unified request lifecycle orchestrator. Builds [PluginV2LlmPipelineInput] and
 * [PluginV2HostLlmDeliveryRequest] from a [ResolvedRuntimeContext] and platform
 * callbacks, then delegates to the existing [AppChatLlmPipelineRuntime].
 *
 * This is the single entry point for all LLM pipeline requests — App, QQ,
 * scheduled tasks. Platform-specific concerns are injected via
 * [PlatformLlmCallbacks].
 */
internal object RuntimeOrchestrator {

    /**
     * Execute a full LLM pipeline and deliver the result.
     *
     * @param preBuiltPluginEvent If the platform has already constructed and
     *   potentially modified (e.g. via ingress plugin dispatch) a
     *   [PluginMessageEvent], pass it here so it is forwarded as-is instead of
     *   building a fresh one internally.
     */
    suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent? = null,
    ): PluginV2HostLlmDeliveryResult {
        val systemPrompt = SystemPromptBuilder.build(ctx)
        val streamingMode = StreamingModeResolver.resolve(ctx)
        val messages = ctx.messageWindow.toPluginProviderMessages()

        val llmEvent = preBuiltPluginEvent ?: buildPluginMessageEvent(ctx, userMessage)

        val pipelineInput = PluginV2LlmPipelineInput(
            event = llmEvent,
            messageIds = listOf(userMessage.id),
            streamingMode = streamingMode,
            availableProviderIds = ctx.availableProviders.map { it.id },
            availableModelIdsByProvider = ctx.availableProviders.associate { provider ->
                provider.id to listOf(provider.model).filter { it.isNotBlank() }
            },
            selectedProviderId = ctx.provider.id,
            selectedModelId = ctx.provider.model,
            systemPrompt = systemPrompt,
            messages = messages,
            personaToolEnablementSnapshot = ctx.personaToolSnapshot,
            configProfileId = ctx.config.id,
            toolSourceContext = ctx.toolSourceContext,
            supportsToolCalling = ctx.providerCapabilities.supportsToolCalling,
            invokeProvider = { request, mode ->
                callbacks.invokeProvider(request, mode, ctx)
            },
        )

        val deliveryRequest = PluginV2HostLlmDeliveryRequest(
            pipelineInput = pipelineInput,
            conversationId = ctx.conversationId,
            platformAdapterType = ctx.ingressEvent.platform.wireValue,
            platformInstanceKey = callbacks.platformInstanceKey,
            hostCapabilityGateway = callbacks.hostCapabilityGateway,
            followupSender = callbacks.followupSender,
            prepareReply = { pipelineResult -> callbacks.prepareReply(pipelineResult) },
            sendReply = { prepared -> callbacks.sendReply(prepared) },
            persistDeliveredReply = { prepared, sendResult, pipelineResult ->
                callbacks.persistDeliveredReply(prepared, sendResult, pipelineResult)
            },
        )

        return llmRuntime.deliverLlmPipeline(deliveryRequest)
    }

    private fun buildPluginMessageEvent(
        ctx: ResolvedRuntimeContext,
        message: ConversationMessage,
    ): PluginMessageEvent {
        val trigger = PluginTriggerSource.BeforeSendMessage
        val rawText = message.content.take(500)
        return PluginMessageEvent(
            eventId = "${trigger.wireValue}:${ctx.conversationId}:${message.id}",
            platformAdapterType = ctx.ingressEvent.platform.wireValue,
            messageType = ctx.ingressEvent.messageType,
            conversationId = ctx.conversationId,
            senderId = ctx.ingressEvent.sender.userId,
            timestampEpochMillis = message.timestamp,
            rawText = rawText,
            initialWorkingText = rawText,
            rawMentions = emptyList(),
            normalizedMentions = emptyList(),
            extras = buildMap {
                put("source", ctx.ingressEvent.platform.wireValue)
                put("trigger", trigger.wireValue)
                put("sessionId", ctx.conversationId)
                put("messageId", message.id)
                put("providerId", ctx.provider.id)
                put("botId", ctx.bot.id)
                put("personaId", ctx.persona?.id.orEmpty())
                put("configProfileId", ctx.config.id)
                put("streamingEnabled", ctx.deliveryPolicy.streamingEnabled)
                put("ttsEnabled", ctx.deliveryPolicy.ttsEnabled)
            },
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
