package com.elymbot.android.di.hilt

import com.elymbot.android.core.runtime.llm.LlmClientPort
import com.elymbot.android.core.runtime.llm.LlmConversationMessage
import com.elymbot.android.core.runtime.llm.LlmInvocationRequest
import com.elymbot.android.core.runtime.llm.LlmProviderCapability
import com.elymbot.android.core.runtime.llm.LlmProviderProfile
import com.elymbot.android.core.runtime.llm.LlmProviderType
import com.elymbot.android.core.runtime.llm.LlmRuntimeConfig
import com.elymbot.android.core.runtime.llm.LlmToolDefinition
import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.provider.api.runtime.ProviderRuntimePort
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.feature.provider.domain.model.ProviderCapability
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.feature.plugin.runtime.DefaultPluginExecutionHostResolver
import com.elymbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.elymbot.android.feature.plugin.runtime.PluginFailureGuard
import com.elymbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.elymbot.android.feature.plugin.runtime.DefaultPluginExecutionHostOperations
import com.elymbot.android.feature.plugin.runtime.PluginExecutionHostOperations
import com.elymbot.android.feature.plugin.runtime.PluginExecutionHostResolver
import com.elymbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryApi
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryAttachmentRef
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryMessage
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryPortRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryReadPort
import com.elymbot.android.feature.plugin.runtime.PluginV2ContextCompressApi
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiAsyncBridge
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiAuditLogger
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiFacade
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiPermissionPolicy
import com.elymbot.android.feature.plugin.runtime.PluginV2HostNetworkApi
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmApi
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmPort
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmPortRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmPortResult
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageAttachmentRef
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendApi
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendPort
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendPortRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendPortResult
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamApi
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPlatformMode
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPort
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortChunkRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortCloseRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortFailRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortMutationResult
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortOpenRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortOpenResult
import com.elymbot.android.feature.plugin.runtime.PluginLlmToolCall
import com.elymbot.android.feature.plugin.runtime.PluginV2ProviderReadApi
import com.elymbot.android.feature.plugin.runtime.PluginV2ProviderReadModel
import com.elymbot.android.feature.plugin.runtime.PluginV2ProviderReadPort
import com.elymbot.android.feature.plugin.runtime.PluginV2ProviderReadProvider
import com.elymbot.android.feature.qq.domain.QqScheduledMessageSender
import com.elymbot.android.model.chat.ConversationAttachment
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginHostCapabilityModule {

    @Binds
    @Singleton
    abstract fun bindPluginExecutionHostResolver(
        impl: DefaultPluginExecutionHostResolver,
    ): PluginExecutionHostResolver

    companion object {
        @Provides
        @Singleton
        @JvmStatic
        fun provideDefaultPluginExecutionHostOperations(
            factory: PluginDataWiringFactory,
        ): DefaultPluginExecutionHostOperations = factory.createDefaultPluginExecutionHostOperations()

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginExecutionHostOperations(
            operations: DefaultPluginExecutionHostOperations,
        ): PluginExecutionHostOperations = operations

        @Provides
        @Singleton
        @JvmStatic
        fun provideExternalPluginHostActionExecutor(
            failureGuard: PluginFailureGuard,
            logBus: PluginRuntimeLogBus,
        ): ExternalPluginHostActionExecutor = ExternalPluginHostActionExecutor(
            failureGuard = failureGuard,
            logBus = logBus,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginHostCapabilityGatewayFactory(
            resolver: PluginExecutionHostResolver,
            hostActionExecutor: ExternalPluginHostActionExecutor,
        ): PluginHostCapabilityGatewayFactory = PluginHostCapabilityGatewayFactory(
            resolver = resolver,
            hostActionExecutor = hostActionExecutor,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2HostApiPermissionPolicy(): PluginV2HostApiPermissionPolicy =
            PluginV2HostApiPermissionPolicy()

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2HostApiAsyncBridge(): PluginV2HostApiAsyncBridge =
            PluginV2HostApiAsyncBridge()

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2HostApiAuditLogger(
            logBus: PluginRuntimeLogBus,
        ): PluginV2HostApiAuditLogger = PluginV2HostApiAuditLogger(
            logBus = logBus,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2HostApiFacade(
            permissionPolicy: PluginV2HostApiPermissionPolicy,
            asyncBridge: PluginV2HostApiAsyncBridge,
            auditLogger: PluginV2HostApiAuditLogger,
        ): PluginV2HostApiFacade = PluginV2HostApiFacade(
            permissionPolicy = permissionPolicy,
            asyncBridge = asyncBridge,
            auditLogger = auditLogger,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2HostNetworkApi(
            facade: PluginV2HostApiFacade,
            transport: RuntimeNetworkTransport,
        ): PluginV2HostNetworkApi = PluginV2HostNetworkApi(
            facade = facade,
            transport = transport,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2ProviderReadPort(
            providerRuntimePort: ProviderRuntimePort,
        ): PluginV2ProviderReadPort = object : PluginV2ProviderReadPort {
            override suspend fun providers(): List<PluginV2ProviderReadProvider> {
                return providerRuntimePort.providers.value.map { provider ->
                    val capabilities = provider.capabilities.mapTo(linkedSetOf()) { it.name.lowercase() }
                    val defaultModelId = provider.model.trim()
                    PluginV2ProviderReadProvider(
                        providerId = provider.id,
                        displayName = provider.name,
                        enabled = provider.enabled,
                        capabilities = capabilities,
                        defaultModelId = defaultModelId,
                        models = defaultModelId.takeIf(String::isNotBlank)?.let { modelId ->
                            listOf(
                                PluginV2ProviderReadModel(
                                    modelId = modelId,
                                    displayName = modelId,
                                    capabilities = capabilities,
                                    contextWindow = null,
                                    supportsToolCalling = ProviderCapability.CHAT in provider.capabilities,
                                    supportsStreaming = provider.nativeStreamingRuleSupport == FeatureSupportState.SUPPORTED ||
                                        provider.nativeStreamingProbeSupport == FeatureSupportState.SUPPORTED,
                                ),
                            )
                        } ?: emptyList(),
                    )
                }
            }
        }

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2ProviderReadApi(
            facade: PluginV2HostApiFacade,
            providerReadPort: PluginV2ProviderReadPort,
        ): PluginV2ProviderReadApi = PluginV2ProviderReadApi(
            facade = facade,
            providerReader = providerReadPort,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2MessageSendPort(
            conversationRepository: ConversationRepositoryPort,
            qqScheduledMessageSender: QqScheduledMessageSender,
        ): PluginV2MessageSendPort = PluginV2MessageSendPort { request: PluginV2MessageSendPortRequest ->
            val attachments = request.attachments.toConversationAttachments()
            if (request.platformAdapterType.isQqPlatform()) {
                val sendResult = qqScheduledMessageSender.sendScheduledMessage(
                    conversationId = request.conversationId,
                    text = request.text,
                    attachments = attachments,
                    botId = "",
                )
                if (!sendResult.success) {
                    return@PluginV2MessageSendPort PluginV2MessageSendPortResult(
                        success = false,
                        errorCode = "qq_delivery_failed",
                        errorSummary = sendResult.errorSummary,
                    )
                }
                val localMessageId = conversationRepository.appendMessage(
                    sessionId = conversationRepository.resolveRepositorySessionId(request.conversationId),
                    role = "assistant",
                    content = request.text,
                    attachments = attachments,
                )
                return@PluginV2MessageSendPort PluginV2MessageSendPortResult(
                    success = true,
                    receiptIds = (sendResult.receiptIds + localMessageId).filter(String::isNotBlank),
                )
            }
            val messageId = conversationRepository.appendMessage(
                sessionId = conversationRepository.resolveRepositorySessionId(request.conversationId),
                role = "assistant",
                content = request.text,
                attachments = attachments,
            )
            PluginV2MessageSendPortResult(
                success = true,
                receiptIds = listOf(messageId),
            )
        }

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2MessageSendApi(
            facade: PluginV2HostApiFacade,
            sendPort: PluginV2MessageSendPort,
        ): PluginV2MessageSendApi = PluginV2MessageSendApi(
            facade = facade,
            sendPort = sendPort,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2MessageStreamPort(
            conversationRepository: ConversationRepositoryPort,
            qqScheduledMessageSender: QqScheduledMessageSender,
        ): PluginV2MessageStreamPort = DefaultPluginV2MessageStreamPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqScheduledMessageSender,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2MessageStreamApi(
            facade: PluginV2HostApiFacade,
            streamPort: PluginV2MessageStreamPort,
        ): PluginV2MessageStreamApi = PluginV2MessageStreamApi(
            facade = facade,
            streamPort = streamPort,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2ConversationHistoryReadPort(
            conversationRepository: ConversationRepositoryPort,
        ): PluginV2ConversationHistoryReadPort = PluginV2ConversationHistoryReadPort { request: PluginV2ConversationHistoryPortRequest ->
            runCatching {
                val session = conversationRepository.findExistingSessionByConversationId(request.conversationId)
                    ?: return@runCatching emptyList()
                session.messages.map { message ->
                    PluginV2ConversationHistoryMessage(
                        messageId = message.id,
                        role = message.role,
                        senderId = message.role,
                        messageType = session.messageType.wireValue,
                        text = message.content,
                        timestampEpochMillis = message.timestamp,
                        attachmentRefs = message.attachments.map { attachment ->
                            PluginV2ConversationHistoryAttachmentRef(
                                ref = attachment.id,
                                uri = attachment.toHostSafeUri(),
                                mimeType = attachment.mimeType,
                                type = attachment.type,
                            )
                        },
                    )
                }
            }.getOrDefault(emptyList())
        }

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2ConversationHistoryApi(
            facade: PluginV2HostApiFacade,
            historyReadPort: PluginV2ConversationHistoryReadPort,
        ): PluginV2ConversationHistoryApi = PluginV2ConversationHistoryApi(
            facade = facade,
            historyReader = historyReadPort,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2HostLlmPort(
            providerRuntimePort: ProviderRuntimePort,
            llmClientPort: LlmClientPort,
        ): PluginV2HostLlmPort = PluginV2HostLlmPort { request: PluginV2HostLlmPortRequest ->
            val provider = providerRuntimePort.providers.value.first { it.id == request.providerId }
            val llmProvider = provider.toLlmProviderProfile(modelId = request.modelId)
            val result = llmClientPort.sendWithTools(
                LlmInvocationRequest(
                    provider = llmProvider,
                    messages = request.messages.mapIndexed { index, message ->
                        LlmConversationMessage(
                            id = "${request.requestId}:$index",
                            role = message.role,
                            content = message.text,
                            timestamp = System.currentTimeMillis(),
                        )
                    },
                    systemPrompt = request.systemPrompt,
                    config = LlmRuntimeConfig(id = request.conversationId),
                    availableProviders = providerRuntimePort.providers.value
                        .filter { it.enabled }
                        .map { it.toLlmProviderProfile(modelId = it.model) },
                    tools = request.tools.map { tool ->
                        LlmToolDefinition(
                            name = tool.name,
                            description = tool.description,
                            parametersJson = org.json.JSONObject(tool.inputSchema).toString(),
                        )
                    },
                ),
            )
            PluginV2HostLlmPortResult(
                text = result.text,
                finishReason = result.finishReason,
                providerId = request.providerId,
                modelId = request.modelId,
                toolCalls = result.toolCalls.map { toolCall ->
                    PluginLlmToolCall(
                        toolCallId = toolCall.id,
                        toolName = toolCall.name,
                        arguments = mapOf("argumentsJson" to toolCall.arguments),
                    )
                },
            )
        }

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2HostLlmApi(
            facade: PluginV2HostApiFacade,
            providerReadPort: PluginV2ProviderReadPort,
            llmPort: PluginV2HostLlmPort,
        ): PluginV2HostLlmApi = PluginV2HostLlmApi(
            facade = facade,
            providerReader = providerReadPort,
            llmPort = llmPort,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginV2ContextCompressApi(
            facade: PluginV2HostApiFacade,
            historyReadPort: PluginV2ConversationHistoryReadPort,
            llmPort: PluginV2HostLlmPort,
        ): PluginV2ContextCompressApi = PluginV2ContextCompressApi(
            facade = facade,
            historyReader = historyReadPort,
            llmPort = llmPort,
        )

        private fun List<PluginV2MessageAttachmentRef>.toConversationAttachments(): List<ConversationAttachment> {
            return map { attachment ->
                val uri = attachment.uri.trim()
                ConversationAttachment(
                    id = uri,
                    type = attachment.mimeType.substringBefore('/').ifBlank { "file" },
                    mimeType = attachment.mimeType,
                    fileName = uri.substringAfterLast('/').ifBlank { "plugin-attachment" },
                    remoteUrl = uri,
                )
            }
        }

        private fun ConversationAttachment.toHostSafeUri(): String {
            return remoteUrl.takeIf { it.startsWith("plugin://") || it.startsWith("host://") }
                ?: "host://conversation-attachments/$id"
        }

        private fun String.isQqPlatform(): Boolean {
            return when (trim().lowercase()) {
                "qq",
                "onebot",
                "qq_onebot",
                "qq-onebot",
                -> true
                else -> false
            }
        }

        private fun ProviderProfile.toLlmProviderProfile(modelId: String): LlmProviderProfile {
            return LlmProviderProfile(
                id = id,
                name = name,
                baseUrl = baseUrl,
                model = modelId.ifBlank { model },
                providerType = runCatching { LlmProviderType.valueOf(providerType.name) }
                    .getOrDefault(LlmProviderType.CUSTOM),
                apiKey = apiKey,
                capabilities = capabilities.mapNotNullTo(linkedSetOf()) { capability ->
                    runCatching { LlmProviderCapability.valueOf(capability.name) }.getOrNull()
                },
                enabled = enabled,
                multimodalRuleSupport = runCatching {
                    com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.valueOf(multimodalRuleSupport.name)
                }.getOrDefault(com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.UNKNOWN),
                multimodalProbeSupport = runCatching {
                    com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.valueOf(multimodalProbeSupport.name)
                }.getOrDefault(com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.UNKNOWN),
                nativeStreamingRuleSupport = runCatching {
                    com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.valueOf(nativeStreamingRuleSupport.name)
                }.getOrDefault(com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.UNKNOWN),
                nativeStreamingProbeSupport = runCatching {
                    com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.valueOf(nativeStreamingProbeSupport.name)
                }.getOrDefault(com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.UNKNOWN),
                sttProbeSupport = runCatching {
                    com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.valueOf(sttProbeSupport.name)
                }.getOrDefault(com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.UNKNOWN),
                ttsProbeSupport = runCatching {
                    com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.valueOf(ttsProbeSupport.name)
                }.getOrDefault(com.elymbot.android.core.runtime.llm.LlmFeatureSupportState.UNKNOWN),
                ttsVoiceOptions = ttsVoiceOptions,
            )
        }
    }
}

private class DefaultPluginV2MessageStreamPort(
    private val conversationRepository: ConversationRepositoryPort,
    private val qqScheduledMessageSender: QqScheduledMessageSender,
) : PluginV2MessageStreamPort {
    private val streams = ConcurrentHashMap<String, HostStreamState>()

    override suspend fun open(request: PluginV2MessageStreamPortOpenRequest): PluginV2MessageStreamPortOpenResult {
        val mode = if (request.platformAdapterType.isQqPlatform()) {
            PluginV2MessageStreamPlatformMode.FinalOnClose
        } else {
            PluginV2MessageStreamPlatformMode.Editable
        }
        val streamId = "${request.requestId}:stream"
        streams[streamId] = HostStreamState(
            streamId = streamId,
            conversationId = request.conversationId,
            platformAdapterType = request.platformAdapterType,
            mode = mode,
        )
        return PluginV2MessageStreamPortOpenResult(
            streamId = streamId,
            platformMode = mode,
            receiptId = "",
        )
    }

    override suspend fun append(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult {
        val state = streams[request.streamId] ?: return unknownStream()
        state.buffer.append(request.text)
        return if (state.mode == PluginV2MessageStreamPlatformMode.FinalOnClose) {
            PluginV2MessageStreamPortMutationResult(success = true)
        } else {
            state.syncEditable(conversationRepository)
        }
    }

    override suspend fun replace(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult {
        val state = streams[request.streamId] ?: return unknownStream()
        state.buffer.clear()
        state.buffer.append(request.text)
        return if (state.mode == PluginV2MessageStreamPlatformMode.FinalOnClose) {
            PluginV2MessageStreamPortMutationResult(success = true)
        } else {
            state.syncEditable(conversationRepository)
        }
    }

    override suspend fun close(request: PluginV2MessageStreamPortCloseRequest): PluginV2MessageStreamPortMutationResult {
        val state = streams.remove(request.streamId) ?: return unknownStream()
        if (state.mode != PluginV2MessageStreamPlatformMode.FinalOnClose) {
            return PluginV2MessageStreamPortMutationResult(success = true)
        }
        val text = state.buffer.toString()
        if (text.isBlank()) {
            return PluginV2MessageStreamPortMutationResult(success = true)
        }
        val sendResult = qqScheduledMessageSender.sendScheduledMessage(
            conversationId = request.conversationId,
            text = text,
            attachments = emptyList(),
            botId = "",
        )
        if (!sendResult.success) {
            return PluginV2MessageStreamPortMutationResult(
                success = false,
                errorCode = "qq_stream_delivery_failed",
                errorSummary = sendResult.errorSummary,
            )
        }
        conversationRepository.appendMessage(
            sessionId = conversationRepository.resolveRepositorySessionId(request.conversationId),
            role = "assistant",
            content = text,
            attachments = emptyList(),
        )
        return PluginV2MessageStreamPortMutationResult(success = true)
    }

    override suspend fun fail(request: PluginV2MessageStreamPortFailRequest): PluginV2MessageStreamPortMutationResult {
        streams.remove(request.streamId)
        return PluginV2MessageStreamPortMutationResult(success = true)
    }

    private fun unknownStream(): PluginV2MessageStreamPortMutationResult {
        return PluginV2MessageStreamPortMutationResult(
            success = false,
            errorCode = "unknown_stream",
            errorSummary = "Unknown message stream.",
        )
    }
}

private data class HostStreamState(
    val streamId: String,
    val conversationId: String,
    val platformAdapterType: String,
    val mode: PluginV2MessageStreamPlatformMode,
    val buffer: StringBuilder = StringBuilder(),
    var messageId: String = "",
) {
    fun syncEditable(conversationRepository: ConversationRepositoryPort): PluginV2MessageStreamPortMutationResult {
        val text = buffer.toString()
        if (messageId.isBlank()) {
            messageId = conversationRepository.appendMessage(
                sessionId = conversationRepository.resolveRepositorySessionId(conversationId),
                role = "assistant",
                content = text,
                attachments = emptyList(),
            )
        } else {
            conversationRepository.updateMessage(
                sessionId = conversationRepository.resolveRepositorySessionId(conversationId),
                messageId = messageId,
                content = text,
                attachments = emptyList(),
            )
        }
        return PluginV2MessageStreamPortMutationResult(success = true)
    }
}

private fun String.isQqPlatform(): Boolean {
    return when (trim().lowercase()) {
        "qq",
        "onebot",
        "qq_onebot",
        "qq-onebot",
        -> true
        else -> false
    }
}

private fun ConversationRepositoryPort.resolveRepositorySessionId(conversationId: String): String {
    val target = conversationId.trim()
    if (target.isBlank()) return target
    return sessions.value.firstOrNull { session -> session.id == target }?.id
        ?: sessions.value.firstOrNull { session -> session.originSessionId == target }?.id
        ?: target
}

private fun ConversationRepositoryPort.findExistingSessionByConversationId(conversationId: String) =
    findPublicGroupHistorySession(conversationId.trim())
        ?: sessions.value.firstOrNull { session -> session.id == conversationId.trim() }
        ?: sessions.value.firstOrNull { session -> session.originSessionId == conversationId.trim() }

private fun ConversationRepositoryPort.findPublicGroupHistorySession(conversationId: String) =
    conversationId.toPublicGroupOriginSessionId()
        ?.let { publicOriginSessionId ->
            sessions.value.firstOrNull { session -> session.originSessionId == publicOriginSessionId }
        }

private fun String.toPublicGroupOriginSessionId(): String? {
    val target = trim()
    if (!target.startsWith("group:")) return null
    val groupId = target
        .removePrefix("group:")
        .substringBefore(":user:")
        .substringBefore(":")
        .trim()
    return groupId.takeIf(String::isNotBlank)?.let { "group:$it" }
}
