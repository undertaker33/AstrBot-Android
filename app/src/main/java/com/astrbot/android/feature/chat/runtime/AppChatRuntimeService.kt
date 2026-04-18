package com.astrbot.android.feature.chat.runtime

import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.StreamingResponseSegmenter
import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.feature.chat.domain.AppChatRequest
import com.astrbot.android.feature.chat.domain.AppChatRuntimeEvent
import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.hasNativeStreamingSupport
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.runtime.context.MessageConverters.toConversationMessages
import com.astrbot.android.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.runtime.context.RuntimeContextResolver
import com.astrbot.android.runtime.context.RuntimeIngressEvent
import com.astrbot.android.runtime.context.RuntimePlatform
import com.astrbot.android.runtime.context.SenderInfo
import com.astrbot.android.runtime.context.StreamingModeResolver
import com.astrbot.android.runtime.plugin.AppChatLlmPipelineRuntime
import com.astrbot.android.runtime.plugin.AppChatPluginRuntime
import com.astrbot.android.runtime.plugin.DefaultPluginHostCapabilityGateway
import com.astrbot.android.runtime.plugin.PlatformLlmCallbacks
import com.astrbot.android.runtime.plugin.PluginLlmResponse
import com.astrbot.android.runtime.plugin.PluginLlmToolCall
import com.astrbot.android.runtime.plugin.PluginLlmToolCallDelta
import com.astrbot.android.runtime.plugin.PluginProviderRequest
import com.astrbot.android.runtime.plugin.PluginV2AfterSentView
import com.astrbot.android.runtime.plugin.PluginV2FollowupSender
import com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult
import com.astrbot.android.runtime.plugin.PluginV2HostPreparedReply
import com.astrbot.android.runtime.plugin.PluginV2HostSendResult
import com.astrbot.android.runtime.plugin.PluginV2LlmPipelineResult
import com.astrbot.android.runtime.plugin.PluginV2ProviderInvocationResult
import com.astrbot.android.runtime.plugin.PluginMessageEventResult
import com.astrbot.android.runtime.plugin.PluginV2ProviderStreamChunk
import com.astrbot.android.runtime.plugin.RuntimeOrchestrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Wraps [RuntimeContextResolver] and [RuntimeOrchestrator] behind [AppChatRuntimePort].
 *
 * This service owns the LLM pipeline invocation for App Chat. It resolves runtime
 * context, calls the shared orchestrator, and emits [AppChatRuntimeEvent]s for the
 * domain layer to consume.
 */
class AppChatRuntimeService(
    private val chatDependencies: ChatViewModelDependencies,
    private val appChatPluginRuntime: AppChatPluginRuntime,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : AppChatRuntimePort {

    override fun send(request: AppChatRequest): Flow<AppChatRuntimeEvent> = flow {
        try {
            val session = chatDependencies.session(request.sessionId)
            val bot = chatDependencies.bots.value
                .firstOrNull { it.id == session.botId }
                ?: error("Bot not found: ${session.botId}")
            val provider = chatDependencies.providers.value
                .firstOrNull {
                    it.id == session.providerId &&
                        it.enabled &&
                        ProviderCapability.CHAT in it.capabilities
                }
                ?: chatDependencies.providers.value
                    .firstOrNull { it.enabled && ProviderCapability.CHAT in it.capabilities }
                ?: error("No enabled chat provider available")
            val config = runCatching { chatDependencies.resolveConfig(bot.configProfileId) }.getOrNull()

            val userMessage = session.messages.firstOrNull { it.id == request.userMessageId }
                ?: error("User message not found: ${request.userMessageId}")

            val llmRuntime = appChatPluginRuntime as? AppChatLlmPipelineRuntime
                ?: error("AppChatPluginRuntime must implement AppChatLlmPipelineRuntime")

            val runtimeContext = RuntimeContextResolver.resolve(
                event = RuntimeIngressEvent(
                    platform = RuntimePlatform.APP_CHAT,
                    conversationId = session.originSessionId.ifBlank { session.id },
                    messageId = userMessage.id,
                    sender = SenderInfo(userId = "app-user"),
                    messageType = session.messageType,
                    text = userMessage.content,
                ),
                bot = bot,
                overrideProviderId = provider.id,
                overridePersonaId = session.personaId,
            )

            val streamingMode = StreamingModeResolver.resolve(runtimeContext)

            val wantsTts = config?.ttsEnabled == true &&
                session.sessionTtsEnabled &&
                (config.alwaysTtsEnabled || request.text.endsWith("~"))
            val ttsProvider = config
                ?.defaultTtsProviderId
                ?.takeIf { config.ttsEnabled && session.sessionTtsEnabled }
                ?.let { ttsProviderId ->
                    chatDependencies.providers.value.firstOrNull { it.id == ttsProviderId && it.enabled }
                }

            val sink = StreamingMessageSink()

            val callbacks = object : PlatformLlmCallbacks {
                override val platformInstanceKey = bot.id
                override val hostCapabilityGateway = DefaultPluginHostCapabilityGateway()
                override val followupSender: PluginV2FollowupSender? = null

                override suspend fun prepareReply(
                    result: PluginV2LlmPipelineResult,
                ): PluginV2HostPreparedReply {
                    val sendableResult = result.sendableResult
                    val ttsConfig = config
                    val attachments = if (wantsTts && ttsProvider != null && ttsConfig != null) {
                        buildVoiceReplyAttachments(
                            response = sendableResult.text,
                            provider = ttsProvider,
                            voiceId = ttsConfig.ttsVoiceId,
                            voiceStreamingEnabled = ttsConfig.voiceStreamingEnabled,
                            readBracketedContent = ttsConfig.ttsReadBracketedContent,
                        )
                    } else {
                        sendableResult.attachments.toConversationAttachments()
                    }
                    return PluginV2HostPreparedReply(
                        text = sendableResult.text,
                        attachments = attachments,
                        deliveredEntries = listOf(
                            PluginV2AfterSentView.DeliveredEntry(
                                entryId = result.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                                entryType = "assistant",
                                textPreview = sendableResult.text.take(160),
                                attachmentCount = attachments.size,
                            ),
                        ),
                    )
                }

                override suspend fun sendReply(
                    prepared: PluginV2HostPreparedReply,
                ): PluginV2HostSendResult = PluginV2HostSendResult(success = true)

                override suspend fun persistDeliveredReply(
                    prepared: PluginV2HostPreparedReply,
                    sendResult: PluginV2HostSendResult,
                    pipelineResult: PluginV2LlmPipelineResult,
                ) {
                    sink.updateText(prepared.text)
                    if (prepared.attachments.isNotEmpty()) {
                        sink.updateAttachments(prepared.attachments)
                    }
                    sink.seal()
                }

                override suspend fun invokeProvider(
                    request: PluginProviderRequest,
                    mode: PluginV2StreamingMode,
                    ctx: ResolvedRuntimeContext,
                ): PluginV2ProviderInvocationResult {
                    return invokeProviderForPipeline(
                        request = request,
                        mode = mode,
                        config = config,
                        availableProviders = ctx.availableProviders,
                    )
                }
            }

            val deliveryResult = RuntimeOrchestrator.dispatchLlm(
                ctx = runtimeContext,
                llmRuntime = llmRuntime,
                callbacks = callbacks,
                userMessage = userMessage,
            )

            when (deliveryResult) {
                is PluginV2HostLlmDeliveryResult.Sent -> {
                    val text = sink.text
                    val attachments = sink.attachments

                    if (streamingMode != PluginV2StreamingMode.NON_STREAM &&
                        attachments.isEmpty() &&
                        text.isNotBlank()
                    ) {
                        val segments = StreamingResponseSegmenter.split(
                            text = text,
                            stripTrailingBoundaryPunctuation = true,
                        )
                        if (segments.isNotEmpty()) {
                            val buffer = StringBuilder()
                            for (segment in segments) {
                                buffer.append(segment)
                                emit(AppChatRuntimeEvent.AssistantDelta(buffer.toString()))
                            }
                        }
                    }

                    emit(AppChatRuntimeEvent.AssistantFinal(text))

                    if (attachments.isNotEmpty()) {
                        emit(AppChatRuntimeEvent.AttachmentUpdate(attachments))
                    }
                }

                is PluginV2HostLlmDeliveryResult.Suppressed -> {
                    emit(
                        AppChatRuntimeEvent.Failure(
                            "App chat LLM result suppressed: ${deliveryResult.pipelineResult.admission.requestId}",
                        ),
                    )
                }

                is PluginV2HostLlmDeliveryResult.SendFailed -> {
                    emit(
                        AppChatRuntimeEvent.Failure(
                            deliveryResult.sendResult.errorSummary.ifBlank { "send_failed" },
                        ),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AppChatRuntimeEvent.Failure(e.message ?: e.javaClass.simpleName, e))
        }
    }

    private suspend fun invokeProviderForPipeline(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        config: com.astrbot.android.model.ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): PluginV2ProviderInvocationResult {
        val resolvedProvider = availableProviders.firstOrNull { profile ->
            profile.id == request.selectedProviderId &&
                profile.enabled &&
                ProviderCapability.CHAT in profile.capabilities
        } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")

        val messages = request.messages.toConversationMessages(request.requestId)

        val chatTools = request.tools.map { def ->
            ChatCompletionService.ChatToolDefinition(
                name = def.name,
                description = def.description,
                parameters = org.json.JSONObject(def.inputSchema.filterValues { it != null } as Map<*, *>),
            )
        }

        return if (mode != PluginV2StreamingMode.NATIVE_STREAM || !request.streamingEnabled || config == null) {
            val result = withContext(ioDispatcher) {
                chatDependencies.sendConfiguredChatWithTools(
                    provider = resolvedProvider,
                    messages = messages,
                    systemPrompt = request.systemPrompt,
                    config = config,
                    availableProviders = availableProviders,
                    tools = chatTools,
                )
            }
            PluginV2ProviderInvocationResult.NonStreaming(
                response = PluginLlmResponse(
                    requestId = request.requestId,
                    providerId = resolvedProvider.id,
                    modelId = request.selectedModelId.ifBlank { resolvedProvider.model },
                    text = result.text,
                    toolCalls = result.toolCalls.map { tc ->
                        PluginLlmToolCall(
                            toolCallId = tc.id,
                            toolName = tc.name,
                            arguments = parseToolCallArguments(tc.arguments),
                        )
                    },
                ),
            )
        } else {
            val chunks = mutableListOf<PluginV2ProviderStreamChunk>()
            val result = withContext(ioDispatcher) {
                chatDependencies.sendConfiguredChatStreamWithTools(
                    provider = resolvedProvider,
                    messages = messages,
                    systemPrompt = request.systemPrompt,
                    config = config,
                    availableProviders = availableProviders,
                    tools = chatTools,
                    onDelta = { delta ->
                        if (delta.isNotBlank()) {
                            chunks += PluginV2ProviderStreamChunk(deltaText = delta)
                        }
                    },
                    onToolCallDelta = { _, _, _ -> },
                )
            }
            val finalizedToolDeltas = result.toolCalls.mapIndexedNotNull { index, toolCall ->
                val normalizedName = toolCall.name.trim()
                if (normalizedName.isBlank()) {
                    null
                } else {
                    PluginLlmToolCallDelta(
                        index = index,
                        toolCallId = toolCall.id,
                        toolName = normalizedName,
                        arguments = parseToolCallArguments(toolCall.arguments),
                    )
                }
            }
            if (finalizedToolDeltas.isNotEmpty()) {
                chunks += PluginV2ProviderStreamChunk(toolCallDeltas = finalizedToolDeltas)
            }
            val finishReason = if (result.toolCalls.isNotEmpty()) "tool_calls" else "stop"
            chunks += PluginV2ProviderStreamChunk(
                deltaText = "",
                isCompletion = true,
                finishReason = finishReason,
            )
            if (result.text.isNotBlank() && chunks.size == 1) {
                chunks.add(0, PluginV2ProviderStreamChunk(deltaText = result.text))
            }
            PluginV2ProviderInvocationResult.Streaming(events = chunks.toList())
        }
    }

    private fun List<PluginMessageEventResult.Attachment>.toConversationAttachments(): List<ConversationAttachment> {
        return mapIndexed { index, attachment ->
            ConversationAttachment(
                id = "llm-result-$index-${attachment.uri.hashCode()}",
                type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                remoteUrl = attachment.uri,
            )
        }
    }

    private fun parseToolCallArguments(json: String): Map<String, Any?> {
        return try {
            val obj = org.json.JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun buildVoiceReplyAttachments(
        response: String,
        provider: ProviderProfile,
        voiceId: String,
        voiceStreamingEnabled: Boolean,
        readBracketedContent: Boolean,
    ): List<ConversationAttachment> {
        if (!voiceStreamingEnabled) {
            return synthesizeSingleVoiceReply(provider, response, voiceId, readBracketedContent)
                ?.let(::listOf) ?: emptyList()
        }
        val segments = StreamingResponseSegmenter.splitForVoiceStreaming(response)
        if (segments.size <= 1) {
            return synthesizeSingleVoiceReply(provider, response, voiceId, readBracketedContent)
                ?.let(::listOf) ?: emptyList()
        }
        val streamedAttachments = mutableListOf<ConversationAttachment>()
        for (segment in segments) {
            val attachment = synthesizeSingleVoiceReply(provider, segment, voiceId, readBracketedContent)
                ?: return synthesizeSingleVoiceReply(provider, response, voiceId, readBracketedContent)
                    ?.let(::listOf) ?: emptyList()
            streamedAttachments += attachment
        }
        return streamedAttachments.toList()
    }

    private suspend fun synthesizeSingleVoiceReply(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment? {
        return withContext(ioDispatcher) {
            runCatching {
                chatDependencies.synthesizeSpeech(
                    provider = provider,
                    text = text,
                    voiceId = voiceId,
                    readBracketedContent = readBracketedContent,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                chatDependencies.log("Chat TTS failed: ${error.message ?: error.javaClass.simpleName}")
            }.getOrNull()
        }
    }
}
