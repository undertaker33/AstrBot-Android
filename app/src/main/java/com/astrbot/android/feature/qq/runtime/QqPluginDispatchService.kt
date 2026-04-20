package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqReplyPayload
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.plugin.ErrorResult
import com.astrbot.android.model.plugin.ExternalPluginHostActionPolicy
import com.astrbot.android.model.plugin.ExternalPluginMediaSourceResolver
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.MediaResult
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginBotSummary
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.feature.plugin.runtime.PluginExecutionEngine
import com.astrbot.android.feature.plugin.runtime.PluginExecutionOutcome
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeDispatcher
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimePlugin
import com.astrbot.android.feature.plugin.runtime.PluginV2CommandResponse
import com.astrbot.android.feature.plugin.runtime.PluginV2CommandResponseAttachment
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngineProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2MessageDispatchResult
import com.astrbot.android.feature.plugin.runtime.createCompatPluginHostCapabilityGatewayFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File

internal class QqPluginDispatchService(
    private val replySender: QqReplySender,
    private val profileResolver: QqRuntimeProfileResolver,
    private val resolvePluginPrivateRootPath: (String) -> String,
    private val pluginCatalog: () -> List<PluginRuntimePlugin> = { PluginRuntimeCatalog.plugins() },
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
    private val log: (String) -> Unit = {},
) {
    constructor(
        replySender: QqReplySender,
        profileResolver: QqRuntimeProfileResolver,
        resolvePluginPrivateRootPath: (String) -> String,
        pluginCatalog: () -> List<PluginRuntimePlugin> = { PluginRuntimeCatalog.plugins() },
        log: (String) -> Unit = {},
    ) : this(
        replySender = replySender,
        profileResolver = profileResolver,
        resolvePluginPrivateRootPath = resolvePluginPrivateRootPath,
        pluginCatalog = pluginCatalog,
        gatewayFactory = createCompatPluginHostCapabilityGatewayFactory(),
        log = log,
    )

    fun executePlugins(
        trigger: PluginTriggerSource,
        message: IncomingQqMessage,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginV2MessageDispatchResult? {
        if (trigger.isV2MessageIngressTrigger()) {
            val dispatchResult = dispatchMessageIngress(trigger, message)
            if (dispatchResult.terminatedByCustomFilterFailure || dispatchResult.propagationStopped) {
                dispatchResult.userVisibleFailureMessage
                    ?.takeIf(String::isNotBlank)
                    ?.let { failureMessage ->
                        replySender.send(
                            QqReplyPayload(
                                conversationId = message.conversationId,
                                messageType = message.messageType,
                                text = failureMessage,
                            ),
                            message,
                        )
                    }
                return dispatchResult
            }
        }
        executeLegacyPlugins(
            trigger = trigger,
            message = message,
            contextFactory = contextFactory,
        )
        return null
    }

    fun handlePluginCommand(
        message: IncomingQqMessage,
        bot: BotProfile,
        config: ConfigProfile,
        session: ConversationSession,
        currentPersona: PersonaProfile?,
    ): Boolean {
        val trimmedText = message.text.trim()
        if (!trimmedText.startsWith("/")) {
            return false
        }
        val dispatchResult = dispatchMessageIngress(
            trigger = PluginTriggerSource.OnCommand,
            message = message,
            conversationId = session.pluginConversationId(),
            botId = bot.id,
            configProfileId = config.id,
            personaId = currentPersona?.id.orEmpty(),
            providerId = profileResolver.resolveProvider(bot, session.providerId)?.id.orEmpty(),
        )
        dispatchResult.commandResponse?.let { commandResponse ->
            consumeCommandResponse(message, commandResponse)
            return true
        }
        if (dispatchResult.terminatedByCustomFilterFailure || dispatchResult.propagationStopped) {
            dispatchResult.userVisibleFailureMessage
                ?.takeIf(String::isNotBlank)
                ?.let { failureMessage ->
                    replySender.send(
                        QqReplyPayload(
                            conversationId = message.conversationId,
                            messageType = message.messageType,
                            text = failureMessage,
                        ),
                        message,
                    )
                }
            return true
        }

        val syntheticMessage = ConversationMessage(
            id = "qq-plugin-command:${session.id}:${trimmedText.hashCode()}",
            role = "user",
            content = trimmedText,
            timestamp = System.currentTimeMillis(),
        )
        val batch = runCatching {
            val pluginFailureGuard = PluginFailureGuard(
                store = PluginRuntimeFailureStateStoreProvider.store(),
            )
            PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(pluginFailureGuard),
                failureGuard = pluginFailureGuard,
            ).executeBatch(
                trigger = PluginTriggerSource.OnCommand,
                plugins = pluginCatalog(),
                contextFactory = { plugin ->
                    buildPluginContext(
                        plugin = plugin,
                        trigger = PluginTriggerSource.OnCommand,
                        session = session,
                        conversationMessage = syntheticMessage,
                        provider = profileResolver.resolveProvider(bot, session.providerId),
                        bot = bot,
                        persona = currentPersona,
                        config = config,
                        message = message,
                    )
                },
            )
        }.onFailure { error ->
            error.rethrowIfCancellation()
            log(
                "QQ plugin command runtime failed: command=${trimmedText.substringBefore(' ')} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull() ?: return false

        if (batch.outcomes.isEmpty()) {
            return false
        }
        val consumableOutcomes = batch.outcomes.filter(::isConsumableOutcome)
        if (consumableOutcomes.isEmpty()) {
            log(
                "QQ plugin command produced no consumable results: command=${trimmedText.substringBefore(' ')} outcomes=${batch.outcomes.joinToString { it.result::class.simpleName.orEmpty() }}",
            )
            return false
        }
        consumableOutcomes.forEach { pluginOutcome ->
            consumePluginOutcome(message, pluginOutcome)
            log("QQ plugin command handled: plugin=${pluginOutcome.pluginId} result=${pluginOutcome.result::class.simpleName.orEmpty()}")
        }
        return true
    }

    fun executeLegacyPlugins(
        trigger: PluginTriggerSource,
        message: IncomingQqMessage,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ) {
        val plugins = pluginCatalog()
        if (plugins.isEmpty()) return
        val pluginFailureGuard = PluginFailureGuard(
            store = PluginRuntimeFailureStateStoreProvider.store(),
        )
        val pluginEngine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(pluginFailureGuard),
            failureGuard = pluginFailureGuard,
        )
        val batch = runCatching {
            pluginEngine.executeBatch(
                trigger = trigger,
                plugins = plugins,
                contextFactory = contextFactory,
            )
        }.onFailure { error ->
            error.rethrowIfCancellation()
            log("QQ runtime plugin dispatch failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}")
        }.getOrNull() ?: return

        batch.skipped.forEach { skip ->
            log("QQ runtime plugin skipped: trigger=${trigger.wireValue} plugin=${skip.plugin.pluginId} reason=${skip.reason.name}")
        }
        batch.merged.conflicts.forEach { conflict ->
            log(
                "QQ runtime plugin merge conflict: trigger=${trigger.wireValue} plugin=${conflict.pluginId} overriddenBy=${conflict.overriddenByPluginId} type=${conflict.resultType}",
            )
        }
        batch.outcomes.forEach { pluginOutcome ->
            val resultName = pluginOutcome.result::class.simpleName ?: "UnknownResult"
            if (pluginOutcome.succeeded) {
                log("QQ runtime plugin executed: trigger=${trigger.wireValue} plugin=${pluginOutcome.pluginId} result=$resultName")
            } else {
                val errorResult = pluginOutcome.result as? ErrorResult
                log(
                    "QQ runtime plugin failed: trigger=${trigger.wireValue} plugin=${pluginOutcome.pluginId} code=${errorResult?.code.orEmpty()} message=${errorResult?.message.orEmpty()}",
                )
            }
            consumePluginOutcome(message, pluginOutcome)
        }
    }

    fun dispatchMessageIngress(
        trigger: PluginTriggerSource,
        message: IncomingQqMessage,
        materializedEvent: PluginMessageEvent? = null,
        conversationId: String? = null,
        botId: String = "",
        configProfileId: String = "",
        personaId: String = "",
        providerId: String = "",
    ): PluginV2MessageDispatchResult {
        return runCatching {
            runBlocking {
                PluginV2DispatchEngineProvider.engine().dispatchMessage(
                    event = materializedEvent ?: message.toPluginMessageEvent(
                        trigger = trigger,
                        conversationId = conversationId,
                        botId = botId,
                        configProfileId = configProfileId,
                        personaId = personaId,
                        providerId = providerId,
                    ),
                )
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            log("QQ v2 message ingress failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}")
        }.getOrDefault(PluginV2MessageDispatchResult())
    }

    fun buildPluginContext(
        plugin: PluginRuntimePlugin,
        trigger: PluginTriggerSource,
        session: ConversationSession,
        conversationMessage: ConversationMessage,
        provider: com.astrbot.android.model.ProviderProfile?,
        bot: BotProfile,
        persona: PersonaProfile?,
        config: ConfigProfile,
        message: IncomingQqMessage,
    ): PluginExecutionContext {
        val base = PluginExecutionContext(
            trigger = trigger,
            pluginId = plugin.pluginId,
            pluginVersion = plugin.pluginVersion,
            sessionRef = MessageSessionRef(
                platformId = session.platformId,
                messageType = session.messageType,
                originSessionId = session.originSessionId,
            ),
            message = PluginMessageSummary(
                messageId = conversationMessage.id,
                contentPreview = conversationMessage.content.take(500),
                senderId = if (conversationMessage.role == "assistant") bot.id else message.senderId,
                messageType = session.messageType.wireValue,
                attachmentCount = conversationMessage.attachments.size,
                timestamp = conversationMessage.timestamp,
            ),
            bot = PluginBotSummary(
                botId = bot.id,
                displayName = bot.displayName,
                platformId = session.platformId,
            ),
            config = PluginConfigSummary(
                providerId = provider?.id.orEmpty(),
                modelId = provider?.model.orEmpty(),
                personaId = persona?.id.orEmpty(),
                extras = buildMap {
                    put("sessionId", session.id)
                    put("source", "qq_runtime")
                    put("selfId", message.selfId)
                    put("userId", message.senderId)
                    put("groupId", message.groupIdOrBlank)
                    put("streamingEnabled", config.textStreamingEnabled.toString())
                    put("ttsEnabled", config.ttsEnabled.toString())
                },
            ),
            permissionSnapshot = plugin.installState.permissionSnapshot.map { permission ->
                PluginPermissionGrant(
                    permissionId = permission.permissionId,
                    title = permission.title,
                    granted = true,
                    required = permission.required,
                    riskLevel = permission.riskLevel,
                )
            },
            hostActionWhitelist = ExternalPluginHostActionPolicy.openActions(),
            triggerMetadata = PluginTriggerMetadata(
                eventId = "${trigger.wireValue}:${session.id}:${conversationMessage.id}",
                extras = mapOf(
                    "source" to "qq_runtime",
                    "selfId" to message.selfId,
                    "userId" to message.senderId,
                    "groupId" to message.groupIdOrBlank,
                    "messageId" to message.messageId,
                ),
            ),
        )
        return gatewayFactory.create().injectContext(base)
    }

    internal fun executePlugins(
        trigger: PluginTriggerSource,
        event: IncomingMessageEvent,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginV2MessageDispatchResult? {
        return executePlugins(trigger, event.toIncomingQqMessage(), contextFactory)
    }

    internal fun handlePluginCommand(
        event: IncomingMessageEvent,
        bot: BotProfile,
        config: ConfigProfile,
        session: ConversationSession,
        currentPersona: PersonaProfile? = null,
    ): Boolean {
        return handlePluginCommand(
            message = event.toIncomingQqMessage(),
            bot = bot,
            config = config,
            session = session,
            currentPersona = currentPersona,
        )
    }

    private fun consumeCommandResponse(
        message: IncomingQqMessage,
        response: PluginV2CommandResponse,
    ) {
        val attachments = response.attachments.mapIndexedNotNull { index, attachment ->
            resolveCommandAttachment(response, attachment)?.let { resolvedSource ->
                ConversationAttachment(
                    id = "qq-v2-command-$index-${resolvedSource.hashCode()}",
                    type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                    mimeType = attachment.mimeType.ifBlank { "image/jpeg" },
                    fileName = attachment.label.ifBlank {
                        resolvedSource.substringAfterLast('/', missingDelimiterValue = "attachment-$index")
                    },
                    remoteUrl = resolvedSource,
                )
            }
        }
        replySender.send(
            QqReplyPayload(
                conversationId = message.conversationId,
                messageType = message.messageType,
                text = response.text,
                attachments = attachments,
            ),
            message,
        )
        log("QQ v2 command handled: plugin=${response.pluginId} textLength=${response.text.length} attachments=${attachments.size}")
    }

    private fun resolveCommandAttachment(
        response: PluginV2CommandResponse,
        attachment: PluginV2CommandResponseAttachment,
    ): String? {
        val source = attachment.source.trim()
        if (source.isBlank()) return null
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return source
        }
        if (source.startsWith("plugin://package/")) {
            val relativePath = source.removePrefix("plugin://package/").trim()
            if (relativePath.isBlank()) return null
            return File(response.extractedDir, relativePath).absolutePath
        }
        val sourceFile = File(source)
        return when {
            sourceFile.isAbsolute -> sourceFile.absolutePath
            response.extractedDir.isNotBlank() -> File(response.extractedDir, source).absolutePath
            else -> source
        }
    }

    private fun consumePluginOutcome(
        message: IncomingQqMessage,
        outcome: PluginExecutionOutcome,
    ) {
        when (val result = outcome.result) {
            is TextResult -> {
                replySender.send(
                    QqReplyPayload(message.conversationId, message.messageType, result.text),
                    message,
                )
            }

            is ErrorResult -> {
                replySender.send(
                    QqReplyPayload(message.conversationId, message.messageType, result.message),
                    message,
                )
            }

            is NoOp -> Unit

            is MediaResult -> {
                val attachments = result.items.mapIndexed { index, item ->
                    val resolved = ExternalPluginMediaSourceResolver.resolve(
                        item = item,
                        extractedDir = outcome.installState.extractedDir,
                        privateRootPath = resolvePluginPrivateRootPath(outcome.pluginId),
                    )
                    ConversationAttachment(
                        id = "qq-plugin-media-$index-${resolved.resolvedSource.hashCode()}",
                        type = if (resolved.mimeType.startsWith("audio/")) "audio" else "image",
                        mimeType = resolved.mimeType.ifBlank { "image/jpeg" },
                        fileName = resolved.altText.ifBlank { resolved.resolvedSource.substringAfterLast('/') },
                        remoteUrl = resolved.resolvedSource,
                    )
                }
                replySender.send(
                    QqReplyPayload(
                        conversationId = message.conversationId,
                        messageType = message.messageType,
                        text = "",
                        attachments = attachments,
                    ),
                    message,
                )
            }

            is HostActionRequest -> {
                val emittedMessages = mutableListOf<String>()
                val execution = gatewayFactory.create(
                    sendMessageHandler = { text ->
                        emittedMessages += text
                        replySender.send(
                            QqReplyPayload(message.conversationId, message.messageType, text),
                            message,
                        )
                    },
                    sendNotificationHandler = { title, msg ->
                        log("QQ plugin notification requested: title=$title message=$msg")
                    },
                    openHostPageHandler = { route ->
                        log("QQ plugin requested host page: route=$route")
                    },
                ).executeHostAction(
                    pluginId = outcome.pluginId,
                    request = result,
                    context = outcome.context,
                )
                if (!execution.succeeded) {
                    replySender.send(
                        QqReplyPayload(message.conversationId, message.messageType, execution.message),
                        message,
                    )
                } else if (emittedMessages.isEmpty() && execution.message.isNotBlank()) {
                    replySender.send(
                        QqReplyPayload(message.conversationId, message.messageType, execution.message),
                        message,
                    )
                }
            }

            else -> {
                log("QQ runtime plugin result is not consumable yet: plugin=${outcome.pluginId} result=${result::class.simpleName.orEmpty()}")
            }
        }
    }

    private fun isConsumableOutcome(outcome: PluginExecutionOutcome): Boolean {
        return when (outcome.result) {
            is TextResult, is ErrorResult, is MediaResult, is HostActionRequest -> true
            else -> false
        }
    }

    private fun PluginTriggerSource.isV2MessageIngressTrigger(): Boolean {
        return this == PluginTriggerSource.BeforeSendMessage || this == PluginTriggerSource.OnCommand
    }

    private fun ConversationSession.pluginConversationId(): String {
        return originSessionId.ifBlank { id }
    }

    private fun IncomingMessageEvent.toIncomingQqMessage(): IncomingQqMessage {
        return IncomingQqMessage(
            selfId = selfId,
            senderId = userId,
            senderName = senderName,
            messageType = messageType,
            conversationId = if (messageType == com.astrbot.android.model.chat.MessageType.GroupMessage) groupId else userId,
            messageId = messageId,
            text = text,
            mentionsSelf = mentionsSelf,
            mentionsAll = mentionsAll,
            attachments = attachments,
            rawPayload = "",
        )
    }
}

internal fun IncomingQqMessage.toPluginMessageEvent(
    trigger: PluginTriggerSource,
    conversationId: String? = null,
    botId: String = "",
    configProfileId: String = "",
    personaId: String = "",
    providerId: String = "",
): PluginMessageEvent {
    val resolvedEventId = messageId.takeIf(String::isNotBlank)
        ?: "${trigger.wireValue}:${System.currentTimeMillis()}"
    return PluginMessageEvent(
        eventId = resolvedEventId,
        platformAdapterType = "onebot",
        messageType = messageType,
        conversationId = conversationId ?: fallbackConversationId(),
        senderId = senderId,
        timestampEpochMillis = System.currentTimeMillis(),
        rawText = text,
        rawMentions = buildList {
            if (mentionsSelf && selfId.isNotBlank()) add(selfId)
            if (mentionsAll) add("all")
        },
        initialWorkingText = text,
        extras = buildMap {
            put("source", "qq_runtime")
            put("trigger", trigger.wireValue)
            put("selfId", selfId)
            put("groupId", groupIdOrBlank)
            put("messageId", messageId)
            put("botId", botId)
            put("configProfileId", configProfileId)
            put("personaId", personaId)
            put("providerId", providerId)
        },
    )
}

private fun IncomingQqMessage.fallbackConversationId(): String {
    return when (messageType) {
        com.astrbot.android.model.chat.MessageType.GroupMessage -> "group:$groupIdOrBlank"
        com.astrbot.android.model.chat.MessageType.FriendMessage,
        com.astrbot.android.model.chat.MessageType.OtherMessage,
        -> "friend:$senderId"
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
