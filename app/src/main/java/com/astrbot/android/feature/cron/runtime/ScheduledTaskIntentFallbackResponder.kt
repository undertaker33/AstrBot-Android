package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.plugin.runtime.PluginLlmResponse
import com.astrbot.android.feature.plugin.runtime.PluginMessageEventResult
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderInvocationResult
import javax.inject.Inject

internal class ScheduledTaskIntentFallbackResponder @Inject constructor(
    private val intentGuard: ScheduledTaskIntentGuard,
    private val promptStrings: ActiveCapabilityPromptStrings,
) {
    suspend fun applyFallbackIfNeeded(
        userText: String,
        context: ScheduledTaskIntentGuardContext,
        pipelineResult: PluginV2LlmPipelineResult,
        invokeProvider: suspend (PluginProviderRequest) -> PluginV2ProviderInvocationResult,
    ): PluginV2LlmPipelineResult {
        if (pipelineResult.executedToolNames.any { it == CREATE_FUTURE_TASK }) return pipelineResult
        val creation = intentGuard.tryCreateFallback(userText, context) ?: return pipelineResult
        val followupRequest = pipelineResult.finalRequest.forScheduledTaskFallback(creation)
        val followupResponse = invokeProvider(followupRequest).asSingleResponse(followupRequest)
        val followupText = followupResponse.text.ifBlank {
            when (creation) {
                is ScheduledTaskIntentGuardResult.Created -> creation.replyText
                is ScheduledTaskIntentGuardResult.Failed -> creation.replyText
            }
        }
        return pipelineResult.withFallbackReply(
            response = followupResponse.withText(followupText),
            text = followupText,
        )
    }

    private fun PluginProviderRequest.forScheduledTaskFallback(
        creation: ScheduledTaskIntentGuardResult,
    ): PluginProviderRequest {
        return PluginProviderRequest(
            requestId = "$requestId-scheduled-fallback",
            availableProviderIds = availableProviderIds,
            availableModelIdsByProvider = availableModelIdsByProvider,
            conversationId = conversationId,
            messageIds = messageIds,
            llmInputSnapshot = llmInputSnapshot,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            systemPrompt = systemPrompt.withScheduledTaskFallbackInstruction(creation.toFollowupInstruction()),
            messages = messages,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            streamingEnabled = false,
            metadata = metadata,
            tools = emptyList(),
        )
    }

    private fun ScheduledTaskIntentGuardResult.toFollowupInstruction(): String {
        return when (this) {
            is ScheduledTaskIntentGuardResult.Created -> promptStrings.fallbackCreatedInstruction(jobId)
            is ScheduledTaskIntentGuardResult.Failed -> promptStrings.fallbackFailedInstruction(code, replyText)
        }
    }

    private fun String?.withScheduledTaskFallbackInstruction(instruction: String): String {
        val base = this?.trim().orEmpty()
        return buildString {
            if (base.isNotBlank()) {
                append(base)
                append("\n\n")
            }
            append(instruction)
        }
    }

    private fun PluginV2ProviderInvocationResult.asSingleResponse(
        request: PluginProviderRequest,
    ): PluginLlmResponse {
        return when (this) {
            is PluginV2ProviderInvocationResult.NonStreaming -> response
            is PluginV2ProviderInvocationResult.Streaming -> {
                val text = buildString {
                    events.forEach { event ->
                        if (!event.isCompletion) append(event.deltaText)
                    }
                }
                val completion = events.lastOrNull { it.isCompletion }
                PluginLlmResponse(
                    requestId = request.requestId,
                    providerId = request.selectedProviderId,
                    modelId = request.selectedModelId,
                    usage = completion?.usage,
                    finishReason = completion?.finishReason,
                    text = text,
                    metadata = completion?.metadata,
                )
            }
        }
    }

    private fun PluginV2LlmPipelineResult.withFallbackReply(
        response: PluginLlmResponse,
        text: String,
    ): PluginV2LlmPipelineResult {
        return copy(
            admission = admission.copy(requestId = response.requestId),
            finalResponse = response,
            sendableResult = PluginMessageEventResult(
                requestId = response.requestId,
                conversationId = admission.conversationId,
                text = text,
                markdown = response.markdown,
                shouldSend = true,
            ),
        )
    }

    private fun PluginLlmResponse.withText(text: String): PluginLlmResponse {
        return PluginLlmResponse(
            requestId = requestId,
            providerId = providerId,
            modelId = modelId,
            usage = usage,
            finishReason = finishReason,
            text = text,
            markdown = markdown,
            toolCalls = emptyList(),
            metadata = metadata,
        )
    }

    private companion object {
        const val CREATE_FUTURE_TASK = "create_future_task"
    }
}
