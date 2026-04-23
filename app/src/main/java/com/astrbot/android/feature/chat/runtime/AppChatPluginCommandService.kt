package com.astrbot.android.feature.chat.runtime

import com.astrbot.android.AppStrings
import com.astrbot.android.R
import com.astrbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionHandlers
import com.astrbot.android.feature.plugin.runtime.HOST_SKIP_COMMAND_STAGE_EXTRA_KEY
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginDispatchSkipReason
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.runtime.PluginRuntimePlugin
import com.astrbot.android.feature.plugin.runtime.PluginV2CommandResponse
import com.astrbot.android.feature.plugin.runtime.PluginV2CommandResponseAttachment
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.PluginV2MessageDispatchResult
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
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
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class AppChatPluginCommandService(
    private val dependencies: ChatViewModelRuntimeBindings,
    private val appChatPluginRuntime: AppChatPluginRuntime,
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
    private val hostActionExecutor: ExternalPluginHostActionExecutor,
    private val dispatchEngine: PluginV2DispatchEngine,
) {
    fun isUnsupportedPluginCommand(content: String): Boolean {
        val parsedCommand = com.astrbot.android.feature.chat.runtime.botcommand.BotCommandParser.parse(content) ?: return false
        return !com.astrbot.android.feature.chat.runtime.botcommand.BotCommandRouter.supports(parsedCommand.name)
    }

    fun handlePluginCommand(
        session: ConversationSession,
        bot: BotProfile,
        content: String,
        provider: ProviderProfile?,
        personaId: String,
        languageTag: String,
    ): Boolean {
        if (!content.startsWith("/")) {
            return false
        }
        val config = dependencies.resolveConfig(bot.configProfileId)
        val syntheticMessage = ConversationMessage(
            id = "plugin-command:${session.id}:${content.hashCode()}",
            role = "user",
            content = content,
            timestamp = System.currentTimeMillis(),
        )
        val v2DispatchResult = dispatchAppChatV2MessageIngress(
            trigger = PluginTriggerSource.OnCommand,
            session = session,
            message = syntheticMessage,
            provider = provider,
            bot = bot,
            personaId = personaId,
            config = config,
        )
        dependencies.log(
            "App chat v2 command dispatch finished: command=${content.substringBefore(' ')} session=${session.id} " +
                "hasResponse=${v2DispatchResult.commandResponse != null} " +
                "terminal=${v2DispatchResult.isTerminal()} " +
                "stopped=${v2DispatchResult.propagationStopped}",
        )
        v2DispatchResult.commandResponse?.let { commandResponse ->
            consumeAppChatV2CommandResponse(
                sessionId = session.id,
                response = commandResponse,
            )
            dependencies.log(
                "App chat v2 command response consumed: command=${content.substringBefore(' ')} " +
                    "plugin=${commandResponse.pluginId} attachments=${commandResponse.attachments.size}",
            )
            return true
        }
        if (v2DispatchResult.isTerminal()) {
            appendV2UserVisibleFailure(session.id, v2DispatchResult)
            return true
        }
        val batch = runCatching {
            appChatPluginRuntime.execute(PluginTriggerSource.OnCommand) { plugin ->
                buildAppChatPluginContext(
                    plugin = plugin,
                    trigger = PluginTriggerSource.OnCommand,
                    session = session,
                    message = syntheticMessage,
                    provider = provider,
                    bot = bot,
                    personaId = personaId,
                    config = config,
                )
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            dependencies.log(
                "App chat plugin command runtime failed: command=${content.substringBefore(' ')} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrElse { error ->
            dependencies.appendMessage(
                sessionId = session.id,
                role = "assistant",
                content = pluginCommandRuntimeFailureMessage(
                    reason = error.message ?: error.javaClass.simpleName,
                    languageTag = languageTag,
                ),
            )
            return true
        }

        batch.skipped.forEach { skip ->
            dependencies.log(
                "App chat plugin command skipped: plugin=${skip.plugin.pluginId} reason=${skip.reason.name}",
            )
        }
        if (batch.outcomes.isEmpty()) {
            val suspendedPlugin = batch.skipped.firstOrNull { skip ->
                skip.reason == PluginDispatchSkipReason.FailureSuspended
            }
            if (suspendedPlugin != null) {
                dependencies.appendMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = pluginCommandSuspendedMessage(
                        pluginId = suspendedPlugin.plugin.pluginId,
                        languageTag = languageTag,
                    ),
                )
                return true
            }
            return false
        }
        var handled = false
        batch.outcomes.forEach { outcome ->
            val consumption = consumePluginCommandResult(
                pluginId = outcome.pluginId,
                result = outcome.result,
                context = outcome.context,
                extractedDir = outcome.installState.extractedDir,
            )
            if (consumption.handled) {
                handled = true
                dependencies.appendMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = consumption.replyText,
                    attachments = consumption.attachments,
                )
            }
            dependencies.log(
                "App chat plugin command handled: plugin=${outcome.pluginId} result=${outcome.result::class.simpleName.orEmpty()} handled=${consumption.handled}",
            )
        }
        return handled
    }

    fun dispatchPlugins(
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
        suppressV2CommandStage: Boolean = false,
    ): Boolean {
        if (trigger.isV2MessageIngressTrigger()) {
            val v2DispatchResult = dispatchAppChatV2MessageIngress(
                trigger = trigger,
                session = session,
                message = message,
                provider = provider,
                bot = bot,
                personaId = personaId,
                config = config,
                suppressCommandStage = suppressV2CommandStage,
            )
            if (v2DispatchResult.isTerminal()) {
                appendV2UserVisibleFailure(session.id, v2DispatchResult)
                return true
            }
        }
        val batch = runCatching {
            appChatPluginRuntime.execute(trigger) { plugin ->
                buildAppChatPluginContext(
                    plugin = plugin,
                    trigger = trigger,
                    session = session,
                    message = message,
                    provider = provider,
                    bot = bot,
                    personaId = personaId,
                    config = config,
                )
            }
        }.onFailure { error ->
            dependencies.log(
                "App chat plugin runtime failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull() ?: return false

        batch.skipped.forEach { skip ->
            dependencies.log(
                "App chat plugin skipped: trigger=${trigger.wireValue} plugin=${skip.plugin.pluginId} reason=${skip.reason.name}",
            )
        }
        batch.merged.conflicts.forEach { conflict ->
            dependencies.log(
                "App chat plugin merge conflict: trigger=${trigger.wireValue} plugin=${conflict.pluginId} overriddenBy=${conflict.overriddenByPluginId} type=${conflict.resultType}",
            )
        }
        batch.outcomes.forEach { outcome ->
            val resultName = outcome.result::class.simpleName ?: "UnknownResult"
            if (outcome.succeeded) {
                dependencies.log(
                    "App chat plugin executed: trigger=${trigger.wireValue} plugin=${outcome.pluginId} result=$resultName",
                )
            } else {
                val errorResult = outcome.result as? ErrorResult
                dependencies.log(
                    "App chat plugin failed: trigger=${trigger.wireValue} plugin=${outcome.pluginId} code=${errorResult?.code.orEmpty()} message=${errorResult?.message.orEmpty()}",
                )
            }
            val consumption = consumePluginCommandResult(
                pluginId = outcome.pluginId,
                result = outcome.result,
                context = outcome.context,
                extractedDir = outcome.installState.extractedDir,
            )
            if (consumption.handled) {
                dependencies.appendMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = consumption.replyText,
                    attachments = consumption.attachments,
                )
            }
        }
        return false
    }

    private fun dispatchAppChatV2MessageIngress(
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile?,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
        suppressCommandStage: Boolean = false,
    ): PluginV2MessageDispatchResult {
        return runCatching {
            runBlocking {
                dispatchEngine.dispatchMessage(
                    event = buildAppChatPluginMessageEvent(
                        trigger = trigger,
                        session = session,
                        message = message,
                        provider = provider,
                        bot = bot,
                        personaId = personaId,
                        config = config,
                        suppressCommandStage = suppressCommandStage,
                    ),
                )
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            dependencies.log(
                "App chat v2 message ingress failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrDefault(PluginV2MessageDispatchResult())
    }

    private fun buildAppChatPluginMessageEvent(
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile?,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
        suppressCommandStage: Boolean = false,
    ): PluginMessageEvent {
        val rawText = message.content.take(500)
        return PluginMessageEvent(
            eventId = "${trigger.wireValue}:${session.id}:${message.id}",
            platformAdapterType = APP_CHAT_PLATFORM_ADAPTER_TYPE,
            messageType = session.messageType,
            conversationId = session.originSessionId.ifBlank { session.id },
            senderId = when (message.role) {
                "assistant" -> bot?.id.orEmpty()
                else -> "app-user"
            },
            timestampEpochMillis = message.timestamp,
            rawText = rawText,
            initialWorkingText = rawText,
            rawMentions = emptyList(),
            normalizedMentions = emptyList(),
            extras = buildMap {
                put("source", "app_chat")
                put("trigger", trigger.wireValue)
                put("sessionId", session.id)
                put(
                    "sessionUnifiedOrigin",
                    MessageSessionRef(
                        platformId = session.platformId,
                        messageType = session.messageType,
                        originSessionId = session.originSessionId,
                    ).unifiedOrigin,
                )
                put("messageId", message.id)
                put("providerId", provider?.id.orEmpty())
                put("botId", bot?.id ?: session.botId)
                put("personaId", personaId)
                put("streamingEnabled", config?.textStreamingEnabled == true)
                put("ttsEnabled", config?.ttsEnabled == true)
                if (suppressCommandStage) {
                    put(HOST_SKIP_COMMAND_STAGE_EXTRA_KEY, true)
                }
            },
        )
    }

    private fun appendV2UserVisibleFailure(
        sessionId: String,
        result: PluginV2MessageDispatchResult,
    ) {
        result.userVisibleFailureMessage
            ?.takeIf { message -> message.isNotBlank() }
            ?.let { message ->
                dependencies.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = message,
                )
            }
    }

    private fun consumeAppChatV2CommandResponse(
        sessionId: String,
        response: PluginV2CommandResponse,
    ) {
        val attachments = response.attachments.mapIndexedNotNull { index, attachment ->
            resolveAppChatV2CommandAttachment(
                response = response,
                attachment = attachment,
            )?.let { resolvedSource ->
                ConversationAttachment(
                    id = "app-chat-v2-command-$index-${resolvedSource.hashCode()}",
                    type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                    mimeType = attachment.mimeType.ifBlank { "image/jpeg" },
                    fileName = attachment.label.ifBlank {
                        resolvedSource.substringAfterLast('/', missingDelimiterValue = "attachment-$index")
                    },
                    remoteUrl = resolvedSource,
                )
            }
        }
        dependencies.appendMessage(
            sessionId = sessionId,
            role = "assistant",
            content = response.text,
            attachments = attachments,
        )
        dependencies.log(
            "App chat v2 command handled: plugin=${response.pluginId} textLength=${response.text.length} attachments=${attachments.size}",
        )
    }

    private fun resolveAppChatV2CommandAttachment(
        response: PluginV2CommandResponse,
        attachment: PluginV2CommandResponseAttachment,
    ): String? {
        val source = attachment.source.trim()
        if (source.isBlank()) {
            return null
        }
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return source
        }
        if (source.startsWith("plugin://package/")) {
            val relativePath = source.removePrefix("plugin://package/").trim()
            if (relativePath.isBlank()) {
                return null
            }
            return File(response.extractedDir, relativePath).absolutePath
        }
        val sourceFile = File(source)
        return when {
            sourceFile.isAbsolute -> sourceFile.absolutePath
            response.extractedDir.isNotBlank() -> File(response.extractedDir, source).absolutePath
            else -> source
        }
    }

    private fun resolvePluginPrivateRootPath(pluginId: String): String {
        return runCatching {
            PluginStoragePaths.fromFilesDir(
                com.astrbot.android.feature.plugin.data.FeaturePluginRepository.requireAppContext().filesDir,
            ).privateDir(pluginId).absolutePath
        }.getOrDefault("")
    }

    private fun buildAppChatPluginContext(
        plugin: PluginRuntimePlugin,
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile?,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
    ): PluginExecutionContext {
        val messagePreview = message.content.take(500)
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
                messageId = message.id,
                contentPreview = messagePreview,
                senderId = when (message.role) {
                    "assistant" -> bot?.id.orEmpty()
                    else -> "app-user"
                },
                messageType = session.messageType.wireValue,
                attachmentCount = message.attachments.size,
                timestamp = message.timestamp,
            ),
            bot = PluginBotSummary(
                botId = bot?.id ?: session.botId,
                displayName = bot?.displayName.orEmpty(),
                platformId = session.platformId,
            ),
            config = PluginConfigSummary(
                providerId = provider?.id.orEmpty(),
                modelId = provider?.model.orEmpty(),
                personaId = personaId,
                extras = buildMap {
                    put("sessionId", session.id)
                    put("streamingEnabled", (config?.textStreamingEnabled == true).toString())
                    put("ttsEnabled", (config?.ttsEnabled == true).toString())
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
                eventId = "${trigger.wireValue}:${session.id}:${message.id}",
                command = message.content.takeIf { trigger == PluginTriggerSource.OnCommand }.orEmpty(),
                extras = mapOf("source" to "app_chat"),
            ),
        )
        return hostCapabilityGateway.injectContext(base)
    }

    private fun consumePluginCommandResult(
        pluginId: String,
        result: PluginExecutionResult,
        context: PluginExecutionContext,
        extractedDir: String,
    ): PluginCommandConsumption {
        return when (result) {
            is TextResult -> PluginCommandConsumption(
                replyText = result.text,
                handled = true,
            )

            is MediaResult -> PluginCommandConsumption(
                attachments = result.items.mapIndexed { index, item ->
                    val resolved = ExternalPluginMediaSourceResolver.resolve(
                        item = item,
                        extractedDir = extractedDir,
                        privateRootPath = resolvePluginPrivateRootPath(pluginId),
                    )
                    ConversationAttachment(
                        id = "plugin-media-$index-${resolved.resolvedSource.hashCode()}",
                        type = if (resolved.mimeType.startsWith("audio/")) "audio" else "image",
                        mimeType = resolved.mimeType.ifBlank { "image/jpeg" },
                        fileName = resolved.altText.ifBlank { resolved.resolvedSource.substringAfterLast('/') },
                        remoteUrl = resolved.resolvedSource,
                    )
                },
                handled = true,
            )

            is NoOp -> PluginCommandConsumption(handled = false)

            is ErrorResult -> PluginCommandConsumption(
                replyText = result.message,
                handled = true,
            )

            is HostActionRequest -> {
                val emittedMessages = mutableListOf<String>()
                val execution = hostActionExecutor.execute(
                    pluginId = pluginId,
                    request = result,
                    context = context,
                    handlers = ExternalPluginHostActionHandlers(
                        sendMessage = { text -> emittedMessages += text },
                        sendNotification = { title, message ->
                        dependencies.log("Plugin notification requested: title=$title message=$message")
                        },
                        openHostPage = { route ->
                        dependencies.log("Plugin requested host page: route=$route")
                        },
                    ),
                )
                if (execution.succeeded) {
                    PluginCommandConsumption(
                        replyText = emittedMessages.firstOrNull()
                            ?: when (result.action) {
                                PluginHostAction.SendNotification -> "Notification sent: ${execution.message}"
                                PluginHostAction.OpenHostPage -> "Opened host page: ${execution.message}"
                                else -> execution.message
                            },
                        handled = true,
                    )
                } else {
                    PluginCommandConsumption(
                        replyText = execution.message,
                        handled = true,
                    )
                }
            }

            else -> PluginCommandConsumption(
                replyText = "Command trigger does not support ${result::class.simpleName.orEmpty()} yet.",
                handled = true,
            )
        }
    }

    private fun pluginCommandRuntimeFailureMessage(reason: String, languageTag: String): String {
        return AppStrings.getForLanguageTag(
            languageTag,
            R.string.chat_plugin_command_runtime_failed,
            reason,
        ).ifBlank {
            if (languageTag.startsWith("zh")) {
                "插件命令执行失败：$reason"
            } else {
                "Plugin command failed: $reason"
            }
        }
    }

    private fun pluginCommandSuspendedMessage(pluginId: String, languageTag: String): String {
        return AppStrings.getForLanguageTag(
            languageTag,
            R.string.chat_plugin_command_suspended,
            pluginId,
        ).ifBlank {
            if (languageTag.startsWith("zh")) {
                "插件 $pluginId 因连续失败已被暂时熔断，请稍后再试。"
            } else {
                "Plugin $pluginId is temporarily suspended after repeated failures. Try again later."
            }
        }
    }

    private fun PluginV2MessageDispatchResult.isTerminal(): Boolean {
        return propagationStopped || terminatedByCustomFilterFailure
    }

    private fun PluginTriggerSource.isV2MessageIngressTrigger(): Boolean {
        return this == PluginTriggerSource.BeforeSendMessage ||
            this == PluginTriggerSource.OnCommand
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private data class PluginCommandConsumption(
        val replyText: String = "",
        val attachments: List<ConversationAttachment> = emptyList(),
        val handled: Boolean = false,
    )

    private companion object {
        private const val APP_CHAT_PLATFORM_ADAPTER_TYPE = "app_chat"
    }
}
