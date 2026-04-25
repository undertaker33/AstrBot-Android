package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.core.runtime.context.StreamingModeResolver
import com.astrbot.android.core.runtime.context.SystemPromptBuilder
import com.astrbot.android.feature.plugin.runtime.MessageConverters.toPluginProviderMessages
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginV2StreamingMode

/**
 * Default shared runtime orchestrator implementation for LLM delivery.
 *
 * Feature code should prefer this class via [RuntimeLlmOrchestratorPort] instead
 * of depending on the static compatibility shell.
 */
internal class DefaultRuntimeLlmOrchestrator : RuntimeLlmOrchestratorPort {

    override suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent?,
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
            toolResultDeliveryHandler = PluginV2ToolResultDeliveryHandler { request ->
                callbacks.handleToolResult(request)
            },
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

