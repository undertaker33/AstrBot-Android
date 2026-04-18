package com.astrbot.android.runtime.cron

import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.runtime.OneBotBridgeServer
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.context.MessageConverters.toConversationMessages
import com.astrbot.android.runtime.context.RuntimeContextResolver
import com.astrbot.android.runtime.context.RuntimeIngressEvent
import com.astrbot.android.runtime.context.RuntimePlatform
import com.astrbot.android.runtime.context.SenderInfo
import com.astrbot.android.runtime.llm.LegacyChatCompletionServiceAdapter
import com.astrbot.android.runtime.plugin.DefaultAppChatPluginRuntime
import com.astrbot.android.runtime.plugin.DefaultPluginHostCapabilityGateway
import com.astrbot.android.runtime.plugin.PluginLlmResponse
import com.astrbot.android.runtime.plugin.PluginLlmToolCall
import com.astrbot.android.runtime.plugin.PluginLlmToolCallDelta
import com.astrbot.android.runtime.plugin.PluginMessageEventResult
import com.astrbot.android.runtime.plugin.PluginProviderRequest
import com.astrbot.android.runtime.plugin.PluginV2HostPreparedReply
import com.astrbot.android.runtime.plugin.PluginV2HostSendResult
import com.astrbot.android.runtime.plugin.PluginV2ProviderInvocationResult
import com.astrbot.android.runtime.plugin.PluginV2ProviderStreamChunk
import com.astrbot.android.runtime.plugin.RuntimeOrchestrator
import org.json.JSONObject

internal object ScheduledTaskRuntimeExecutor {

    private val llmClient: LlmClientPort = LegacyChatCompletionServiceAdapter()

    suspend fun execute(context: CronJobExecutionContext): CronJobDeliverySummary {
        val note = context.note.trim().ifBlank { context.description.trim() }
        if (note.isBlank()) {
            throw CronJobExecutionFailure(
                code = "empty_note",
                retryable = false,
                message = "Scheduled task note is empty for job=${context.jobId}",
            )
        }

        require(context.platform.isNotBlank()) { "Scheduled task missing platform for job=${context.jobId}" }
        require(context.conversationId.isNotBlank() || context.sessionId.isNotBlank()) {
            "Scheduled task missing conversation target for job=${context.jobId}"
        }
        require(context.botId.isNotBlank()) { "Scheduled task missing bot target for job=${context.jobId}" }
        require(context.configProfileId.isNotBlank()) { "Scheduled task missing config profile for job=${context.jobId}" }
        require(context.providerId.isNotBlank()) { "Scheduled task missing provider target for job=${context.jobId}" }

        val platform = resolvePlatform(context.platform)
        val conversationId = resolveConversationId(context)
        val bot = resolveBot(context.botId)
        require(bot.configProfileId == context.configProfileId) {
            "Scheduled task config mismatch for job=${context.jobId}: bot=${bot.id} config=${bot.configProfileId} payload=${context.configProfileId}"
        }

        val userMessageId = ConversationRepository.appendMessage(
            sessionId = conversationId,
            role = "user",
            content = note,
        )
        val userMessage = ConversationRepository.session(conversationId).messages
            .firstOrNull { it.id == userMessageId }
            ?: ConversationMessage(
                id = userMessageId,
                role = "user",
                content = note,
                timestamp = System.currentTimeMillis(),
            )

        val ingressEvent = RuntimeIngressEvent(
            platform = platform,
            conversationId = conversationId,
            messageId = "cron:${context.jobId}",
            sender = SenderInfo(userId = "cron:${context.jobId}", nickname = "scheduled-task"),
            messageType = resolveMessageType(platform, conversationId),
            text = note,
            trigger = com.astrbot.android.runtime.context.IngressTrigger.SCHEDULED_TASK,
            rawPlatformPayload = mapOf(
                "jobId" to context.jobId,
                "trigger" to PluginTriggerSource.BeforeSendMessage.wireValue,
                "scheduledTask" to mapOf(
                    "jobId" to context.jobId,
                    "name" to context.name,
                    "description" to context.description,
                    "note" to note,
                    "jobType" to context.jobType,
                    "sessionId" to context.sessionId,
                    "platform" to context.platform,
                    "conversationId" to context.conversationId,
                    "botId" to context.botId,
                    "configProfileId" to context.configProfileId,
                    "personaId" to context.personaId,
                    "providerId" to context.providerId,
                    "origin" to context.origin,
                    "runOnce" to context.runOnce,
                    "runAt" to context.runAt,
                ),
            ),
        )

        val resolvedContext = RuntimeContextResolver.resolve(
            event = ingressEvent,
            bot = bot,
            overrideProviderId = context.providerId.takeIf { it.isNotBlank() },
            overridePersonaId = context.personaId.takeIf { it.isNotBlank() },
        )

        val callbacks = object : com.astrbot.android.runtime.plugin.PlatformLlmCallbacks {
            override val platformInstanceKey: String = "cron:${context.jobId}"
            override val hostCapabilityGateway = DefaultPluginHostCapabilityGateway()
            override val followupSender = null

            override suspend fun prepareReply(
                result: com.astrbot.android.runtime.plugin.PluginV2LlmPipelineResult,
            ): PluginV2HostPreparedReply {
                val sendable = result.sendableResult
                return PluginV2HostPreparedReply(
                    text = sendable.text,
                    attachments = sendable.attachments.toConversationAttachments(),
                    deliveredEntries = listOf(
                        com.astrbot.android.runtime.plugin.PluginV2AfterSentView.DeliveredEntry(
                            entryId = result.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                            entryType = "assistant",
                            textPreview = sendable.text.take(160),
                            attachmentCount = sendable.attachments.size,
                        ),
                    ),
                )
            }

            override suspend fun sendReply(prepared: PluginV2HostPreparedReply): PluginV2HostSendResult {
                return if (platform == RuntimePlatform.QQ_ONEBOT) {
                    OneBotBridgeServer.sendScheduledMessage(
                        conversationId = conversationId,
                        text = prepared.text,
                        attachments = prepared.attachments,
                        botId = bot.id,
                    ).toHostSendResult()
                } else {
                    PluginV2HostSendResult(success = true)
                }
            }

            override suspend fun persistDeliveredReply(
                prepared: PluginV2HostPreparedReply,
                sendResult: PluginV2HostSendResult,
                pipelineResult: com.astrbot.android.runtime.plugin.PluginV2LlmPipelineResult,
            ) {
                if (!sendResult.success) return
                ConversationRepository.appendMessage(
                    sessionId = conversationId,
                    role = "assistant",
                    content = prepared.text,
                    attachments = prepared.attachments,
                )
            }

            override suspend fun invokeProvider(
                request: PluginProviderRequest,
                mode: PluginV2StreamingMode,
                ctx: com.astrbot.android.runtime.context.ResolvedRuntimeContext,
            ): PluginV2ProviderInvocationResult {
                val resolvedProvider = ctx.availableProviders.firstOrNull { profile ->
                    profile.id == request.selectedProviderId &&
                        profile.enabled &&
                        ProviderCapability.CHAT in profile.capabilities
                } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")

                val messages = request.messages.toConversationMessages(request.requestId)
                val llmTools = request.tools.map { def ->
                    LlmToolDefinition(
                        name = def.name,
                        description = def.description,
                        parametersJson = JSONObject(
                            def.inputSchema.filterValues { it != null } as Map<*, *>,
                        ).toString(),
                    )
                }
                val llmRequest = LlmInvocationRequest(
                    provider = resolvedProvider,
                    messages = messages,
                    systemPrompt = request.systemPrompt,
                    config = ctx.config,
                    availableProviders = ctx.availableProviders,
                    tools = llmTools,
                )

                return if (mode != PluginV2StreamingMode.NATIVE_STREAM || !request.streamingEnabled) {
                    val result = llmClient.sendWithTools(llmRequest)
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
                    var completedResult: LlmInvocationResult? = null
                    llmClient.streamWithTools(llmRequest).collect { event ->
                        when (event) {
                            is LlmStreamEvent.TextDelta -> {
                                chunks += PluginV2ProviderStreamChunk(deltaText = event.text)
                            }
                            is LlmStreamEvent.ToolCallDelta -> {
                                // Collect tool call deltas; finalization happens on Completed.
                            }
                            is LlmStreamEvent.Completed -> {
                                completedResult = event.result
                            }
                            is LlmStreamEvent.Failed -> {
                                throw event.throwable
                            }
                        }
                    }
                    val result = completedResult ?: LlmInvocationResult(text = "")
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
                    chunks += PluginV2ProviderStreamChunk(
                        isCompletion = true,
                        finishReason = if (result.toolCalls.isNotEmpty()) "tool_calls" else "stop",
                    )
                    if (result.text.isNotBlank() && chunks.size == 1) {
                        chunks.add(0, PluginV2ProviderStreamChunk(deltaText = result.text))
                    }
                    PluginV2ProviderInvocationResult.Streaming(events = chunks)
                }
            }
        }

        val deliveryResult = RuntimeOrchestrator.dispatchLlm(
            ctx = resolvedContext,
            llmRuntime = DefaultAppChatPluginRuntime,
            callbacks = callbacks,
            userMessage = userMessage,
        )
        if (deliveryResult is com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult.SendFailed) {
            error(deliveryResult.sendResult.errorSummary.ifBlank { "scheduled_task_send_failed" })
        }
        RuntimeLogRepository.append(
            "CronJobBridge: job=${context.jobId} completed with ${deliveryResult::class.simpleName.orEmpty()} conversation=$conversationId",
        )
        return when (deliveryResult) {
            is com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult.Sent -> CronJobDeliverySummary(
                platform = context.platform,
                conversationId = conversationId,
                deliveredMessageCount = deliveryResult.preparedReply.deliveredEntries.size.coerceAtLeast(1),
                receiptIds = deliveryResult.sendResult.receiptIds,
                textPreview = deliveryResult.preparedReply.text.take(160),
            )
            is com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult.Suppressed -> {
                RuntimeLogRepository.append(
                    "CronJobBridge: job=${context.jobId} suppressed without sending conversation=$conversationId",
                )
                throw CronJobExecutionFailure(
                    code = "scheduled_task_suppressed",
                    retryable = false,
                    message = "Scheduled task completed without sending a reminder for job=${context.jobId}",
                )
            }
            is com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult.SendFailed -> error("unreachable")
        }
    }

    private fun resolvePlatform(platform: String): RuntimePlatform {
        return when (platform.trim().lowercase()) {
            "qq",
            "onebot",
            RuntimePlatform.QQ_ONEBOT.wireValue -> RuntimePlatform.QQ_ONEBOT
            else -> RuntimePlatform.APP_CHAT
        }
    }

    private fun resolveConversationId(context: CronJobExecutionContext): String {
        return context.conversationId.takeIf { it.isNotBlank() }
            ?: context.sessionId.takeIf { it.isNotBlank() }
            ?: error("Scheduled task missing conversation target for job=${context.jobId}")
    }

    private fun resolveMessageType(platform: RuntimePlatform, conversationId: String): MessageType {
        if (platform != RuntimePlatform.QQ_ONEBOT) return MessageType.OtherMessage
        return if (conversationId.startsWith("group:")) {
            MessageType.GroupMessage
        } else {
            MessageType.FriendMessage
        }
    }

    private fun resolveBot(botId: String): com.astrbot.android.model.BotProfile {
        val snapshot = BotRepository.snapshotProfiles()
        return snapshot.firstOrNull { it.id == botId && it.autoReplyEnabled }
            ?: error("Scheduled task bot not found or auto reply disabled: $botId")
    }

    private fun parseToolCallArguments(json: String): Map<String, Any?> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun List<PluginMessageEventResult.Attachment>.toConversationAttachments(): List<ConversationAttachment> {
        return mapIndexed { index, attachment ->
            ConversationAttachment(
                id = "cron-llm-result-$index-${attachment.uri.hashCode()}",
                type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                remoteUrl = attachment.uri,
            )
        }
    }

    private fun com.astrbot.android.runtime.OneBotSendResult.toHostSendResult(): PluginV2HostSendResult {
        return PluginV2HostSendResult(
            success = success,
            receiptIds = receiptIds,
            errorSummary = errorSummary,
        )
    }
}
