package com.elymbot.android.di.hilt

import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.provider.api.runtime.ProviderRuntimePort
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.feature.provider.domain.model.ProviderCapability
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
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiAsyncBridge
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiAuditLogger
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiFacade
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiPermissionPolicy
import com.elymbot.android.feature.plugin.runtime.PluginV2HostNetworkApi
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageAttachmentRef
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendApi
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendPort
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendPortRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendPortResult
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
                    sessionId = request.conversationId,
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
                sessionId = request.conversationId,
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
        fun providePluginV2ConversationHistoryReadPort(
            conversationRepository: ConversationRepositoryPort,
        ): PluginV2ConversationHistoryReadPort = PluginV2ConversationHistoryReadPort { request: PluginV2ConversationHistoryPortRequest ->
            runCatching {
                conversationRepository.session(request.conversationId).let { session ->
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
    }
}
