package com.elymbot.android.feature.geofence.runtime

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.context.IngressTrigger
import com.elymbot.android.core.runtime.context.ResolvedRuntimeContext
import com.elymbot.android.core.runtime.context.RuntimeBotSnapshot
import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.core.runtime.context.RuntimeConversationAttachment
import com.elymbot.android.core.runtime.context.RuntimeIngressEvent
import com.elymbot.android.core.runtime.context.RuntimeMessageType
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.elymbot.android.core.runtime.context.RuntimeStreamingMode
import com.elymbot.android.core.runtime.context.SenderInfo
import com.elymbot.android.core.runtime.context.StreamingModeResolver
import com.elymbot.android.core.runtime.llm.LlmClientPort
import com.elymbot.android.core.runtime.llm.LlmConversationAttachment
import com.elymbot.android.core.runtime.llm.LlmConversationMessage
import com.elymbot.android.core.runtime.llm.LlmConversationToolCall
import com.elymbot.android.core.runtime.llm.LlmFeatureSupportState
import com.elymbot.android.core.runtime.llm.LlmInvocationRequest
import com.elymbot.android.core.runtime.llm.LlmInvocationResult
import com.elymbot.android.core.runtime.llm.LlmProviderCapability
import com.elymbot.android.core.runtime.llm.LlmProviderProfile
import com.elymbot.android.core.runtime.llm.LlmProviderType
import com.elymbot.android.core.runtime.llm.LlmRuntimeConfig
import com.elymbot.android.core.runtime.llm.LlmStreamEvent
import com.elymbot.android.core.runtime.llm.LlmToolDefinition
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutionContext
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutionResult
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutorPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageAttachment
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryRequest
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryResult
import com.elymbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.feature.plugin.domain.runtime.AppChatPluginRuntime
import com.elymbot.android.feature.plugin.domain.runtime.PlatformLlmCallbacks
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.domain.runtime.PluginLlmResponse
import com.elymbot.android.feature.plugin.domain.runtime.PluginLlmToolCall
import com.elymbot.android.feature.plugin.domain.runtime.PluginLlmToolCallDelta
import com.elymbot.android.feature.plugin.domain.runtime.PluginMessageEventResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginProviderMessageDto
import com.elymbot.android.feature.plugin.domain.runtime.PluginProviderMessagePartDto
import com.elymbot.android.feature.plugin.domain.runtime.PluginProviderMessageRole
import com.elymbot.android.feature.plugin.domain.runtime.PluginProviderRequest
import com.elymbot.android.feature.plugin.domain.runtime.PluginProviderToolDefinition
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2AfterSentView
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2FollowupSender
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostLlmDeliveryResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostPreparedReply
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostSendResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2LlmPipelineResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2ProviderInvocationResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2ProviderStreamChunk
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationToolCall
import com.elymbot.android.model.plugin.PluginV2StreamingMode
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

class DefaultGeofenceActionExecutor @Inject constructor(
    private val deliveryPort: GeofenceMessageDeliveryPort,
    private val botRepositoryPort: BotRepositoryPort,
    private val configRepositoryPort: ConfigRepositoryPort,
    private val runtimeContextResolverPort: RuntimeContextResolverPort,
    private val llmClientPort: LlmClientPort,
    private val orchestrator: com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort,
    private val appChatPluginRuntime: AppChatPluginRuntime,
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
    private val runtimeLogger: RuntimeLogger,
) : GeofenceActionExecutorPort {
    override suspend fun execute(context: GeofenceActionExecutionContext): GeofenceActionExecutionResult {
        return runCatching {
            when (context.rule.actionType) {
                GeofenceActionType.SEND_MESSAGE -> sendMessage(context)
                GeofenceActionType.AGENT_PROMPT -> runAgentPrompt(context, context.rule.actionPrompt)
                GeofenceActionType.WEATHER_FORECAST -> {
                    requireWebSearchEnabled(context)
                    runAgentPrompt(context, weatherPrompt(context))
                }
                GeofenceActionType.NEWS_DIGEST -> {
                    requireWebSearchEnabled(context)
                    runAgentPrompt(context, newsDigestPrompt(context))
                }
                GeofenceActionType.HOST_CAPABILITY -> GeofenceActionExecutionResult.failure(
                    errorCode = "unsupported_action",
                    errorMessage = "host_capability geofence action is not supported in this version",
                    deliverySummary = "unsupported_action",
                )
            }
        }.getOrElse { error ->
            if (error is GeofenceActionFailure) {
                GeofenceActionExecutionResult.failure(
                    errorCode = error.code,
                    errorMessage = error.message.orEmpty(),
                    deliverySummary = error.summary.ifBlank { error.code },
                )
            } else {
                runtimeLogger.append(
                    "Geofence action failed ruleId=${context.rule.ruleId} regionId=${context.region.regionId} " +
                        "action=${context.rule.actionType.persistedValue} error=${error.javaClass.simpleName}",
                )
                GeofenceActionExecutionResult.failure(
                    errorCode = "delivery_failed",
                    errorMessage = error.message ?: error.javaClass.simpleName,
                    deliverySummary = "delivery_failed",
                )
            }
        }
    }

    private suspend fun sendMessage(context: GeofenceActionExecutionContext): GeofenceActionExecutionResult {
        val target = context.resolveTargetContext()
        val text = context.rule.actionPrompt.trim()
        if (text.isBlank()) throw GeofenceActionFailure("missing_target_context", "Geofence send_message action prompt is empty")
        val result = deliveryPort.deliver(
            GeofenceMessageDeliveryRequest(
                platform = target.platform.wireValue,
                conversationId = target.conversationId,
                text = text,
                botId = target.bot.id,
            ),
        )
        if (!result.success) {
            throw GeofenceActionFailure(
                code = result.errorCode.ifBlank { "delivery_failed" },
                message = result.errorSummary.ifBlank { "Geofence message delivery failed" },
                summary = result.errorSummary.ifBlank { result.errorCode.ifBlank { "delivery_failed" } },
            )
        }
        return GeofenceActionExecutionResult.success(result.toDeliverySummary())
    }

    private suspend fun runAgentPrompt(
        context: GeofenceActionExecutionContext,
        prompt: String,
    ): GeofenceActionExecutionResult {
        val target = context.resolveTargetContext()
        val effectivePrompt = prompt.trim()
        if (effectivePrompt.isBlank()) {
            throw GeofenceActionFailure("missing_target_context", "Geofence agent prompt is empty")
        }
        val llmRuntime = appChatPluginRuntime as? AppChatLlmPipelineRuntime
            ?: throw GeofenceActionFailure("delivery_failed", "AppChatPluginRuntime does not support LLM pipeline")
        val messageId = "geofence:${context.rule.ruleId}:${context.occurredAtMillis}"
        val userMessage = ConversationMessage(
            id = messageId,
            role = "geofence",
            content = effectivePrompt,
            timestamp = context.occurredAtMillis,
        )
        val ingress = RuntimeIngressEvent(
            platform = target.platform,
            conversationId = target.conversationId,
            messageId = messageId,
            sender = SenderInfo(userId = "geofence:${context.rule.ruleId}", nickname = "geofence"),
            messageType = target.messageType,
            text = effectivePrompt,
            rawPlatformPayload = context.toHookVisibleRawPayload(),
            trigger = IngressTrigger.GEOFENCE_EVENT,
        )
        val resolvedContext = runtimeContextResolverPort.resolve(
            event = ingress,
            bot = target.bot.toRuntimeBotSnapshot(),
            overrideProviderId = target.providerId.takeIf(String::isNotBlank),
            overridePersonaId = target.personaId.takeIf(String::isNotBlank),
        )
        resolvedContext.requireTargetMatches(target)
        val callbacks = GeofenceLlmCallbacksFactory(
            deliveryPort = deliveryPort,
            providerInvocationService = GeofenceProviderInvocationService(llmClientPort),
            hostCapabilityGateway = hostCapabilityGateway,
        ).create(
            context = context,
            target = target,
        )
        val result = orchestrator.dispatchLlm(
            ctx = resolvedContext,
            llmRuntime = llmRuntime,
            callbacks = callbacks,
            userMessage = userMessage,
        )
        return when (result) {
            is PluginV2HostLlmDeliveryResult.Sent -> GeofenceActionExecutionResult.success(
                result.sendResult.toDeliverySummary(),
            )
            is PluginV2HostLlmDeliveryResult.SendFailed -> GeofenceActionExecutionResult.failure(
                errorCode = "delivery_failed",
                errorMessage = result.sendResult.errorSummary.ifBlank { "Geofence Agent delivery failed" },
                deliverySummary = result.sendResult.errorSummary.ifBlank { "delivery_failed" },
            )
            is PluginV2HostLlmDeliveryResult.Suppressed -> GeofenceActionExecutionResult.failure(
                errorCode = "delivery_failed",
                errorMessage = "Geofence Agent result was suppressed",
                deliverySummary = "agent_result_suppressed",
            )
        }
    }

    private fun requireWebSearchEnabled(context: GeofenceActionExecutionContext) {
        val config = configRepositoryPort.requireExactConfig(context.targetConfigId())
        if (!config.webSearchEnabled) {
            throw GeofenceActionFailure(
                code = "web_search_disabled",
                message = "Target ConfigProfile web search is disabled",
                summary = "web_search_disabled",
            )
        }
    }

    private fun GeofenceActionExecutionContext.resolveTargetContext(): GeofenceResolvedActionTarget {
        val configId = targetConfigId()
        val config = configRepositoryPort.requireExactConfig(configId)
        val bot = resolveBot(rule, config)
        val conversationId = rule.targetConversationId.trim()
        if (conversationId.isBlank()) {
            throw GeofenceActionFailure(
                code = "missing_target_context",
                message = "Geofence action is missing target conversation context",
            )
        }
        val platform = normalizePlatform(rule.targetPlatform)
        return GeofenceResolvedActionTarget(
            platform = platform,
            conversationId = conversationId,
            messageType = resolveMessageType(platform, conversationId),
            bot = bot,
            config = config,
            providerId = rule.targetProviderId
                .ifBlank { config.defaultChatProviderId }
                .ifBlank { bot.defaultProviderId },
            personaId = rule.targetPersonaId.ifBlank { bot.defaultPersonaId },
            explicitPersonaId = rule.targetPersonaId.trim(),
        )
    }

    private fun GeofenceActionExecutionContext.targetConfigId(): String =
        rule.targetConfigProfileId.ifBlank { binding.configId }.trim()
            .ifBlank {
                throw GeofenceActionFailure(
                    code = "missing_target_context",
                    message = "Geofence action is missing target config context",
                )
            }

    private fun ConfigRepositoryPort.requireExactConfig(configId: String): ConfigProfile =
        snapshotProfiles().firstOrNull { it.id == configId }
            ?: throw GeofenceActionFailure(
                code = "missing_target_context",
                message = "Geofence action target config is missing: $configId",
            )

    private fun resolveBot(rule: GeofenceRule, config: ConfigProfile): BotProfile {
        val bots = botRepositoryPort.snapshotProfiles()
        rule.targetBotId.trim().takeIf(String::isNotBlank)?.let { botId ->
            return bots.firstOrNull { it.id == botId }
                ?.takeIf { it.configProfileId == config.id }
                ?: throw GeofenceActionFailure(
                    "missing_target_context",
                    "Geofence target bot is missing or does not belong to ConfigProfile ${config.id}: $botId",
                )
        }
        return bots.firstOrNull { it.configProfileId == config.id && it.autoReplyEnabled }
            ?: runCatching { botRepositoryPort.currentBot() }.getOrNull()?.takeIf { it.configProfileId == config.id }
            ?: throw GeofenceActionFailure(
                code = "missing_target_context",
                message = "Geofence action cannot resolve a bot for config ${config.id}",
            )
    }

    private fun normalizePlatform(value: String): RuntimePlatform {
        return when (value.trim().lowercase()) {
            "",
            RuntimePlatform.APP_CHAT.wireValue,
            -> RuntimePlatform.APP_CHAT
            "qq",
            "onebot",
            RuntimePlatform.QQ_ONEBOT.wireValue,
            -> RuntimePlatform.QQ_ONEBOT
            else -> throw GeofenceActionFailure(
                code = "invalid_target_platform",
                message = "Unsupported geofence target platform: ${value.trim()}",
            )
        }
    }

    private fun ResolvedRuntimeContext.requireTargetMatches(target: GeofenceResolvedActionTarget) {
        if (config.id != target.config.id || bot.configProfileId != target.config.id || bot.id != target.bot.id) {
            throw GeofenceActionFailure(
                code = "missing_target_context",
                message = "Resolved geofence runtime context does not match the target ConfigProfile and bot.",
            )
        }
        if (target.providerId.isNotBlank() && provider.id != target.providerId) {
            throw GeofenceActionFailure(
                code = "missing_target_context",
                message = "Resolved geofence runtime context does not match the target provider.",
            )
        }
        if (target.explicitPersonaId.isNotBlank() && persona?.id != target.explicitPersonaId) {
            throw GeofenceActionFailure(
                code = "missing_target_context",
                message = "Resolved geofence runtime context does not match the target persona.",
            )
        }
    }

    private fun resolveMessageType(platform: RuntimePlatform, conversationId: String): RuntimeMessageType {
        if (platform != RuntimePlatform.QQ_ONEBOT) return RuntimeMessageType.OtherMessage
        return if (conversationId.startsWith("group:")) RuntimeMessageType.GroupMessage else RuntimeMessageType.FriendMessage
    }

    private fun weatherPrompt(context: GeofenceActionExecutionContext): String =
        buildString {
            appendLine(context.rule.actionPrompt.ifBlank { "Provide a concise weather forecast for this geofence event." })
            appendLine()
            appendLine("Geofence event:")
            appendLine("- rule: ${context.rule.name}")
            appendLine("- region: ${context.region.label}")
            appendLine("- transition: ${context.transition.persistedValue}")
            appendLine("- occurredAt: ${context.occurredAtMillis}")
            appendLine("- location: redacted")
            appendLine("- radiusMeters: ${context.region.radiusMeters}")
            appendLine("Use web search only if needed and summarize the forecast for the target location.")
        }.trim()

    private fun newsDigestPrompt(context: GeofenceActionExecutionContext): String =
        buildString {
            appendLine(context.rule.actionPrompt.ifBlank { "Create a concise local news digest for this geofence event." })
            appendLine()
            appendLine("Geofence event:")
            appendLine("- rule: ${context.rule.name}")
            appendLine("- region: ${context.region.label}")
            appendLine("- transition: ${context.transition.persistedValue}")
            appendLine("- occurredAt: ${context.occurredAtMillis}")
            appendLine("- location: redacted")
            appendLine("- radiusMeters: ${context.region.radiusMeters}")
            appendLine("Use web search and include only concise, relevant local updates.")
        }.trim()
}

private data class GeofenceResolvedActionTarget(
    val platform: RuntimePlatform,
    val conversationId: String,
    val messageType: RuntimeMessageType,
    val bot: BotProfile,
    val config: ConfigProfile,
    val providerId: String,
    val personaId: String,
    val explicitPersonaId: String,
)

private class GeofenceActionFailure(
    val code: String,
    override val message: String,
    val summary: String = code,
) : RuntimeException(message)

private class GeofenceLlmCallbacksFactory(
    private val deliveryPort: GeofenceMessageDeliveryPort,
    private val providerInvocationService: GeofenceProviderInvocationService,
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
) {
    fun create(
        context: GeofenceActionExecutionContext,
        target: GeofenceResolvedActionTarget,
    ): PlatformLlmCallbacks {
        return object : PlatformLlmCallbacks {
            override val platformInstanceKey: String = "geofence:${context.rule.ruleId}"
            override val hostCapabilityGateway: PluginHostCapabilityGateway =
                this@GeofenceLlmCallbacksFactory.hostCapabilityGateway
            override val followupSender: PluginV2FollowupSender? = null

            override suspend fun prepareReply(result: PluginV2LlmPipelineResult): PluginV2HostPreparedReply {
                val sendable = result.sendableResult
                val attachments = sendable.attachments.toGeofenceAttachments()
                return PluginV2HostPreparedReply(
                    text = sendable.text,
                    attachments = attachments.toConversationAttachments(),
                    deliveredEntries = listOf(
                        PluginV2AfterSentView.DeliveredEntry(
                            entryId = result.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                            entryType = "assistant",
                            textPreview = sendable.text.take(160),
                            attachmentCount = attachments.size,
                        ),
                    ),
                )
            }

            override suspend fun sendReply(prepared: PluginV2HostPreparedReply): PluginV2HostSendResult {
                return deliveryPort.deliver(
                    GeofenceMessageDeliveryRequest(
                        platform = target.platform.wireValue,
                        conversationId = target.conversationId,
                        text = prepared.text,
                        attachments = prepared.attachments.map { attachment ->
                            GeofenceMessageAttachment(
                                id = attachment.id,
                                type = attachment.type,
                                mimeType = attachment.mimeType,
                                fileName = attachment.fileName,
                                base64Data = attachment.base64Data,
                                remoteUrl = attachment.remoteUrl,
                            )
                        },
                        botId = target.bot.id,
                    ),
                ).toHostSendResult()
            }

            override suspend fun persistDeliveredReply(
                prepared: PluginV2HostPreparedReply,
                sendResult: PluginV2HostSendResult,
                pipelineResult: PluginV2LlmPipelineResult,
            ) = Unit

            override suspend fun invokeProvider(
                request: PluginProviderRequest,
                mode: PluginV2StreamingMode,
                ctx: ResolvedRuntimeContext,
            ): PluginV2ProviderInvocationResult {
                return providerInvocationService.invokeProvider(request, mode, ctx)
            }
        }
    }
}

private class GeofenceProviderInvocationService(
    private val llmClient: LlmClientPort,
) {
    suspend fun invokeProvider(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
    ): PluginV2ProviderInvocationResult {
        val availableProviders = ctx.availableProviders.map { it.toLlmProviderProfile() }
        val resolvedProvider = availableProviders.firstOrNull { profile ->
            profile.id == request.selectedProviderId &&
                profile.enabled &&
                LlmProviderCapability.CHAT in profile.capabilities
        } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")
        val messages = request.messages.toConversationMessages(request.requestId)
        val llmRequest = LlmInvocationRequest(
            provider = resolvedProvider,
            messages = messages.toLlmConversationMessages(),
            systemPrompt = request.systemPrompt,
            config = ctx.toLlmRuntimeConfig(),
            availableProviders = availableProviders,
            tools = request.tools.toLlmTools(),
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
                    is LlmStreamEvent.TextDelta -> chunks += PluginV2ProviderStreamChunk(deltaText = event.text)
                    is LlmStreamEvent.ToolCallDelta -> Unit
                    is LlmStreamEvent.Completed -> completedResult = event.result
                    is LlmStreamEvent.Failed -> throw event.throwable
                }
            }
            val result = completedResult ?: LlmInvocationResult(text = "")
            val toolDeltas = result.toolCalls.mapIndexedNotNull { index, toolCall ->
                val name = toolCall.name.trim()
                if (name.isBlank()) null else PluginLlmToolCallDelta(
                    index = index,
                    toolCallId = toolCall.id,
                    toolName = name,
                    arguments = parseToolCallArguments(toolCall.arguments),
                )
            }
            if (toolDeltas.isNotEmpty()) chunks += PluginV2ProviderStreamChunk(toolCallDeltas = toolDeltas)
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

    private fun List<PluginProviderToolDefinition>.toLlmTools(): List<LlmToolDefinition> =
        map { def ->
            LlmToolDefinition(
                name = def.name,
                description = def.description,
                parametersJson = JSONObject(def.inputSchema.filterValues { it != null } as Map<*, *>).toString(),
            )
        }
}

private fun GeofenceActionExecutionContext.toHookVisibleRawPayload(): Map<String, Any?> =
    mapOf(
        "ruleId" to rule.ruleId,
        "ruleName" to rule.name,
        "regionId" to region.regionId,
        "regionLabel" to region.label,
        "transition" to transition.persistedValue,
        "latitude" to "redacted",
        "longitude" to "redacted",
        "radiusMeters" to region.radiusMeters,
        "occurredAt" to occurredAtMillis,
        "configId" to binding.configId,
    )

private fun BotProfile.toRuntimeBotSnapshot(): RuntimeBotSnapshot =
    RuntimeBotSnapshot(
        id = id,
        displayName = displayName,
        defaultProviderId = defaultProviderId,
        defaultPersonaId = defaultPersonaId,
        configProfileId = configProfileId,
    )

private fun RuntimeStreamingMode.toPluginStreamingMode(): PluginV2StreamingMode =
    when (this) {
        RuntimeStreamingMode.NON_STREAM -> PluginV2StreamingMode.NON_STREAM
        RuntimeStreamingMode.NATIVE_STREAM -> PluginV2StreamingMode.NATIVE_STREAM
        RuntimeStreamingMode.PSEUDO_STREAM -> PluginV2StreamingMode.PSEUDO_STREAM
    }

private fun List<PluginProviderMessageDto>.toConversationMessages(requestId: String): List<ConversationMessage> =
    mapIndexed { index, message ->
        ConversationMessage(
            id = "$requestId:$index",
            role = message.role.toConversationRole(),
            content = message.parts.textContent(),
            timestamp = System.currentTimeMillis(),
            attachments = emptyList(),
            toolCallId = message.metadata?.hostToolCallId().orEmpty(),
            assistantToolCalls = message.toolCalls.map { call ->
                ConversationToolCall(
                    id = call.normalizedId,
                    name = call.normalizedToolName,
                    arguments = JSONObject(call.normalizedArguments).toString(),
                )
            },
        )
    }

private fun PluginProviderMessageRole.toConversationRole(): String =
    when (this) {
        PluginProviderMessageRole.SYSTEM -> "system"
        PluginProviderMessageRole.USER -> "user"
        PluginProviderMessageRole.ASSISTANT -> "assistant"
        PluginProviderMessageRole.TOOL -> "tool"
    }

private fun List<PluginProviderMessagePartDto>.textContent(): String =
    joinToString("\n") { part ->
        when (part) {
            is PluginProviderMessagePartDto.TextPart -> part.text
            is PluginProviderMessagePartDto.MediaRefPart -> "[media:${part.mimeType}] ${part.uri}"
        }
    }

private fun Map<String, Any?>.hostToolCallId(): String {
    @Suppress("UNCHECKED_CAST")
    val host = this["__host"] as? Map<String, Any?> ?: return ""
    return (host["toolCallId"] as? String).orEmpty()
}

private fun ResolvedRuntimeContext.toLlmRuntimeConfig(): LlmRuntimeConfig =
    LlmRuntimeConfig(
        id = config.id,
        imageCaptionTextEnabled = config.imageCaptionTextEnabled,
        defaultVisionProviderId = config.defaultVisionProviderId,
    )

private fun RuntimeProviderSnapshot.toLlmProviderProfile(): LlmProviderProfile =
    LlmProviderProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        providerType = providerType.toLlmProviderType(),
        apiKey = apiKey,
        capabilities = capabilities.mapNotNull { it.toLlmProviderCapabilityOrNull() }.toSet()
            .ifEmpty { setOf(LlmProviderCapability.CHAT) },
        enabled = enabled,
        multimodalRuleSupport = multimodalRuleSupport.toLlmFeatureSupportState(),
        multimodalProbeSupport = multimodalProbeSupport.toLlmFeatureSupportState(),
        nativeStreamingRuleSupport = nativeStreamingRuleSupport.toLlmFeatureSupportState(),
        nativeStreamingProbeSupport = nativeStreamingProbeSupport.toLlmFeatureSupportState(),
        sttProbeSupport = sttProbeSupport.toLlmFeatureSupportState(),
        ttsProbeSupport = ttsProbeSupport.toLlmFeatureSupportState(),
        ttsVoiceOptions = ttsVoiceOptions,
    )

private fun String.toLlmProviderType(): LlmProviderType =
    runCatching { LlmProviderType.valueOf(this) }.getOrDefault(LlmProviderType.CUSTOM)

private fun String.toLlmProviderCapabilityOrNull(): LlmProviderCapability? =
    runCatching { LlmProviderCapability.valueOf(this) }.getOrNull()

private fun String.toLlmFeatureSupportState(): LlmFeatureSupportState =
    runCatching { LlmFeatureSupportState.valueOf(this) }.getOrDefault(LlmFeatureSupportState.UNKNOWN)

private fun List<ConversationMessage>.toLlmConversationMessages(): List<LlmConversationMessage> =
    map { message ->
        LlmConversationMessage(
            id = message.id,
            role = message.role,
            content = message.content,
            timestamp = message.timestamp,
            attachments = message.attachments.map { attachment ->
                LlmConversationAttachment(
                    id = attachment.id,
                    type = attachment.type,
                    mimeType = attachment.mimeType,
                    fileName = attachment.fileName,
                    base64Data = attachment.base64Data,
                    remoteUrl = attachment.remoteUrl,
                )
            },
            toolCallId = message.toolCallId,
            assistantToolCalls = message.assistantToolCalls.map { toolCall ->
                LlmConversationToolCall(
                    id = toolCall.id,
                    name = toolCall.name,
                    arguments = toolCall.arguments,
                )
            },
        )
    }

private fun List<PluginMessageEventResult.Attachment>.toGeofenceAttachments(): List<GeofenceMessageAttachment> =
    mapIndexed { index, attachment ->
        GeofenceMessageAttachment(
            id = "geofence-llm-result-$index-${attachment.uri.hashCode()}",
            type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
            mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
            remoteUrl = attachment.uri,
        )
    }

private fun List<GeofenceMessageAttachment>.toConversationAttachments(): List<com.elymbot.android.model.chat.ConversationAttachment> =
    map { attachment ->
        com.elymbot.android.model.chat.ConversationAttachment(
            id = attachment.id,
            type = attachment.type,
            mimeType = attachment.mimeType,
            fileName = attachment.fileName,
            base64Data = attachment.base64Data,
            remoteUrl = attachment.remoteUrl,
        )
    }

private fun GeofenceMessageDeliveryResult.toHostSendResult(): PluginV2HostSendResult =
    PluginV2HostSendResult(
        success = success,
        receiptIds = receiptIds,
        errorSummary = errorSummary.ifBlank { errorCode },
    )

private fun GeofenceMessageDeliveryResult.toDeliverySummary(): String =
    JSONObject().apply {
        put("delivered_message_count", deliveredMessageCount)
        put("receipt_ids", receiptIds)
        if (errorCode.isNotBlank()) put("error_code", errorCode)
        if (errorSummary.isNotBlank()) put("error_summary", errorSummary)
    }.toString()

private fun PluginV2HostSendResult.toDeliverySummary(): String =
    JSONObject().apply {
        put("delivered_message_count", receiptIds.size.coerceAtLeast(if (success) 1 else 0))
        put("receipt_ids", receiptIds)
        if (errorSummary.isNotBlank()) put("error_summary", errorSummary)
    }.toString()

private fun parseToolCallArguments(json: String): Map<String, Any?> {
    return try {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { key -> obj.opt(key) }
    } catch (_: Exception) {
        emptyMap()
    }
}
