package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.core.runtime.llm.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.feature.qq.domain.QqReplyPayload
import com.astrbot.android.feature.qq.domain.QqRuntimePort
import com.astrbot.android.feature.qq.domain.QqRuntimeResult
import com.astrbot.android.feature.qq.domain.QqSendResult
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
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
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.runtime.ConversationSessionLockManager
import com.astrbot.android.runtime.botcommand.BotCommandContext
import com.astrbot.android.runtime.botcommand.BotCommandParser
import com.astrbot.android.runtime.botcommand.BotCommandResources
import com.astrbot.android.runtime.botcommand.BotCommandRouter
import com.astrbot.android.runtime.botcommand.BotCommandSource
import com.astrbot.android.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.runtime.context.RuntimeContextResolver
import com.astrbot.android.runtime.context.RuntimeIngressEvent
import com.astrbot.android.runtime.context.RuntimePlatform
import com.astrbot.android.runtime.context.SenderInfo
import com.astrbot.android.runtime.context.StreamingModeResolver
import com.astrbot.android.runtime.plugin.AppChatLlmPipelineRuntime
import com.astrbot.android.runtime.plugin.DefaultAppChatPluginRuntime
import com.astrbot.android.runtime.plugin.DefaultPluginHostCapabilityGateway
import com.astrbot.android.runtime.plugin.ExternalPluginHostActionExecutor
import com.astrbot.android.runtime.plugin.PlatformLlmCallbacks
import com.astrbot.android.runtime.plugin.PluginExecutionEngine
import com.astrbot.android.runtime.plugin.PluginExecutionHostToolHandlers
import com.astrbot.android.runtime.plugin.PluginExecutionOutcome
import com.astrbot.android.runtime.plugin.PluginFailureGuard
import com.astrbot.android.runtime.plugin.PluginHostCapabilityGateway
import com.astrbot.android.runtime.plugin.PluginMessageEvent
import com.astrbot.android.runtime.plugin.PluginMessageEventResult
import com.astrbot.android.runtime.plugin.PluginProviderRequest
import com.astrbot.android.runtime.plugin.PluginRuntimeDispatcher
import com.astrbot.android.runtime.plugin.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import com.astrbot.android.runtime.plugin.PluginV2AfterSentView
import com.astrbot.android.runtime.plugin.PluginV2CommandResponse
import com.astrbot.android.runtime.plugin.PluginV2CommandResponseAttachment
import com.astrbot.android.runtime.plugin.PluginV2DispatchEngineProvider
import com.astrbot.android.runtime.plugin.PluginV2FollowupSender
import com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult
import com.astrbot.android.runtime.plugin.PluginV2HostPreparedReply
import com.astrbot.android.runtime.plugin.PluginV2HostSendResult
import com.astrbot.android.runtime.plugin.PluginV2MessageDispatchResult
import com.astrbot.android.runtime.plugin.PluginV2ProviderInvocationResult
import com.astrbot.android.runtime.plugin.PluginV2LlmPipelineResult
import com.astrbot.android.runtime.qq.QqConversationTitleResolver
import com.astrbot.android.runtime.qq.QqKeywordDetector
import com.astrbot.android.runtime.qq.QqRateLimiter
import com.astrbot.android.runtime.qq.QqReplyDecisionReason
import com.astrbot.android.runtime.qq.QqReplyPolicyEvaluator
import com.astrbot.android.runtime.qq.QqReplyPolicyInput
import com.astrbot.android.runtime.qq.QqSessionKeyFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

internal class QqMessageRuntimeService(
    private val botPort: BotRepositoryPort,
    private val configPort: ConfigRepositoryPort,
    private val personaPort: PersonaRepositoryPort,
    private val providerPort: ProviderRepositoryPort,
    private val conversationPort: QqConversationPort,
    private val platformConfigPort: QqPlatformConfigPort,
    private val orchestrator: RuntimeLlmOrchestratorPort,
    private val replySender: QqReplySender,
    private val llmRuntime: AppChatLlmPipelineRuntime,
    private val providerInvoker: QqProviderInvoker,
    private val rateLimiter: QqRateLimiter,
    private val markMessageId: (String) -> Boolean,
    private val scheduleStashReplay: (BotProfile, ConfigProfile, String) -> Unit,
    private val compatBridge: QqRuntimeCompatBridge,
    private val log: (String) -> Unit = {},
) : QqRuntimePort {

    override suspend fun handleIncomingMessage(message: IncomingQqMessage): QqRuntimeResult {
        val bot = platformConfigPort.resolveQqBot(message.selfId)
            ?: return QqRuntimeResult.Ignored("No bot bound to selfId=${message.selfId}")
        if (!bot.autoReplyEnabled) {
            return QqRuntimeResult.Ignored("Bot auto reply disabled")
        }
        val config = configPort.resolve(bot.configProfileId)
        val replyDecision = evaluateReplyPolicy(message, bot, config)
        if (!replyDecision.shouldReply) {
            if (replyDecision.shouldLogInfo) {
                log(
                    "QQ reply blocked: reason=${replyDecision.reason} user=${message.senderId} group=${message.groupIdOrBlank.ifBlank { "-" }}",
                )
            }
            replyDecision.permissionDeniedNotice?.let { notice ->
                replySender.send(
                    QqReplyPayload(
                        conversationId = message.conversationId,
                        messageType = message.messageType,
                        text = notice,
                    ),
                    message,
                )
            }
            return QqRuntimeResult.Ignored(replyDecision.reason.name)
        }
        if (config.keywordDetectionEnabled && QqKeywordDetector(config.keywordPatterns).matches(message.text)) {
            log("QQ inbound keyword blocked: user=${message.senderId} group=${message.groupIdOrBlank.ifBlank { "-" }}")
            replySender.send(
                QqReplyPayload(
                    conversationId = message.conversationId,
                    messageType = message.messageType,
                    text = KEYWORD_BLOCK_NOTICE,
                ),
                message,
            )
            return QqRuntimeResult.Ignored("keyword_blocked")
        }
        val sourceKey = buildRateLimitSourceKey(bot, message)
        val rateLimitResult = rateLimiter.tryAcquire(
            sourceKey = sourceKey,
            windowSeconds = config.rateLimitWindowSeconds,
            maxCount = config.rateLimitMaxCount,
            strategy = config.rateLimitStrategy,
            payload = message,
        )
        if (!rateLimitResult.allowed) {
            log(
                "QQ rate limit blocked: bot=${bot.id} user=${message.senderId} group=${message.groupIdOrBlank.ifBlank { "-" }} strategy=${config.rateLimitStrategy}",
            )
            if (rateLimitResult.stashed) {
                scheduleStashReplay(bot, config, sourceKey)
            } else if (config.replyWhenPermissionDenied) {
                replySender.send(
                    QqReplyPayload(
                        conversationId = message.conversationId,
                        messageType = message.messageType,
                        text = "Rate limit exceeded.",
                    ),
                    message,
                )
            }
            return QqRuntimeResult.Ignored("rate_limited")
        }
        if (message.messageId.isNotBlank() && !markMessageId(message.messageId)) {
            log("OneBot duplicate message ignored: ${message.messageId}")
            return QqRuntimeResult.Ignored("duplicate_message")
        }

        return processMessage(message, bot, config)
    }

    private suspend fun processMessage(
        message: IncomingQqMessage,
        bot: BotProfile,
        config: ConfigProfile,
    ): QqRuntimeResult {
        val sessionId = buildSessionId(bot, message, config)
        val sessionTitle = QqConversationTitleResolver.build(
            messageType = message.messageType,
            groupId = message.groupIdOrBlank,
            userId = message.senderId,
            senderName = message.senderName,
        )
        var outcome: QqRuntimeResult = QqRuntimeResult.Ignored("not_processed")
        ConversationSessionLockManager.withLock(sessionId) lock@{
            val session = conversationPort.resolveOrCreateSession(sessionId, sessionTitle, message.messageType)
            val persona = resolvePersona(bot, session.personaId)
            val parsedBotCommand = BotCommandParser.parse(message.text)
            when {
                parsedBotCommand != null && BotCommandRouter.supports(parsedBotCommand.name) -> {
                    if (handleBotCommand(message, bot, config, sessionId, session, persona)) {
                        outcome = QqRuntimeResult.Replied(QqSendResult(success = true))
                        return@lock
                    }
                }

                parsedBotCommand != null -> {
                    if (handlePluginCommand(message, bot, config, sessionId, session, persona)) {
                        outcome = QqRuntimeResult.Replied(QqSendResult(success = true))
                        return@lock
                    }
                    replySender.send(
                        QqReplyPayload(
                            conversationId = message.conversationId,
                            messageType = message.messageType,
                            text = BotCommandResources.unsupportedCommand(
                                parsedBotCommand.name,
                                compatBridge.currentLanguageTag(),
                            ),
                        ),
                        message,
                    )
                    log("Bot command unsupported after plugin fallback: ${parsedBotCommand.name} session=$sessionId")
                    outcome = QqRuntimeResult.Replied(QqSendResult(success = true))
                    return@lock
                }

                handlePluginCommand(message, bot, config, sessionId, session, persona) -> {
                    outcome = QqRuntimeResult.Replied(QqSendResult(success = true))
                    return@lock
                }
            }

            val provider = resolveProvider(bot, session.providerId)
            if (provider == null) {
                log("Auto reply skipped: no enabled chat provider configured")
                sendFailureNoticeIfNeeded(message, "No chat model is configured for this bot.")
                outcome = QqRuntimeResult.Failed("No enabled chat provider")
                return@lock
            }
            conversationPort.updateSessionBindings(
                sessionId = sessionId,
                providerId = provider.id,
                personaId = persona?.id.orEmpty(),
                botId = bot.id,
            )
            val ttsSuffixMatched = message.text.trim().endsWith("~")
            val alwaysTtsEnabled = config.alwaysTtsEnabled
            val wantsTts = config.ttsEnabled &&
                session.sessionTtsEnabled &&
                (alwaysTtsEnabled || ttsSuffixMatched)
            val cleanedText = message.text.trim().removeSuffix("~").trim()
            val sttProvider = config.defaultSttProviderId
                .takeIf { config.sttEnabled && session.sessionSttEnabled }
                ?.let(::resolveSttProvider)
            val ttsProvider = config.defaultTtsProviderId
                .takeIf { config.ttsEnabled && session.sessionTtsEnabled }
                ?.let(::resolveTtsProvider)
            val transcribedAudioText = if (message.attachments.any { it.type == "audio" } && sttProvider != null) {
                runCatching {
                    message.attachments
                        .filter { it.type == "audio" }
                        .joinToString("\n") { attachment ->
                            compatBridge.transcribeAudio(sttProvider, attachment)
                        }
                }.onFailure { error ->
                    log("QQ STT failed: ${error.message ?: error.javaClass.simpleName}")
                }.getOrNull()
            } else {
                null
            }
            val finalPromptContent = buildPromptContent(
                message = message,
                cleanedText = cleanedText,
                transcribedAudioText = transcribedAudioText,
            )

            conversationPort.appendMessage(
                sessionId = sessionId,
                role = "user",
                content = finalPromptContent,
                attachments = message.attachments,
            )
            val preModelSession = conversationPort.session(sessionId)
            val userMessage = preModelSession.messages.lastOrNull { it.role == "user" }
                ?: run {
                    outcome = QqRuntimeResult.Failed("Could not find user message after append")
                    return@lock
                }
            log(
                "QQ message received: type=${message.messageType} session=$sessionId chars=${message.text.length} attachments=${message.attachments.size}",
            )
            val llmEvent = message.toPluginMessageEvent(
                trigger = PluginTriggerSource.BeforeSendMessage,
                conversationId = preModelSession.pluginConversationId(),
                botId = bot.id,
                configProfileId = config.id,
                personaId = persona?.id.orEmpty(),
                providerId = provider.id,
            )
            val ingressDispatchResult = dispatchQqV2MessageIngress(
                trigger = PluginTriggerSource.BeforeSendMessage,
                message = message,
                materializedEvent = llmEvent,
            )
            if (ingressDispatchResult.terminatedByCustomFilterFailure || ingressDispatchResult.propagationStopped) {
                ingressDispatchResult.userVisibleFailureMessage
                    ?.takeIf { it.isNotBlank() }
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
                outcome = QqRuntimeResult.Ignored("ingress_blocked")
                return@lock
            }
            if (message.text.isBlank() && llmEvent.workingText.isBlank()) {
                llmEvent.workingText = buildLlmInputSnapshotFallback(
                    finalPromptContent = finalPromptContent,
                    attachments = message.attachments,
                )
            }
            if (shouldExecuteLegacyQqPluginsDuringLlmDispatch()) {
                executeLegacyQqPlugins(
                    trigger = PluginTriggerSource.BeforeSendMessage,
                    message = message,
                    contextFactory = { plugin ->
                        buildQqPluginContext(
                            plugin = plugin,
                            trigger = PluginTriggerSource.BeforeSendMessage,
                            session = conversationPort.session(sessionId),
                            conversationMessage = userMessage,
                            provider = provider,
                            bot = bot,
                            persona = persona,
                            config = config,
                            message = message,
                        )
                    },
                )
            }

            val runtimeContext = RuntimeContextResolver.resolve(
                event = RuntimeIngressEvent(
                    platform = RuntimePlatform.QQ_ONEBOT,
                    conversationId = preModelSession.pluginConversationId(),
                    repositorySessionId = sessionId,
                    messageId = userMessage.id,
                    sender = SenderInfo(
                        userId = message.senderId,
                        nickname = message.senderName,
                        groupId = message.groupIdOrBlank,
                    ),
                    messageType = message.messageType,
                    text = message.text,
                ),
                bot = bot,
                overrideProviderId = provider.id,
                overridePersonaId = persona?.id,
            )
            val streamingMode = StreamingModeResolver.resolve(runtimeContext)

            val callbacks = buildCallbacks(
                message = message,
                sessionId = sessionId,
                config = config,
                provider = provider,
                persona = persona,
                runtimeContext = runtimeContext,
                wantsTts = wantsTts,
                ttsProvider = ttsProvider,
                streamingMode = streamingMode,
            )
            val deliveryResult = runCatching {
                orchestrator.dispatchLlm(
                    ctx = runtimeContext,
                    llmRuntime = llmRuntime,
                    callbacks = callbacks,
                    userMessage = userMessage,
                    preBuiltPluginEvent = llmEvent,
                )
            }.getOrElse { error ->
                error.rethrowIfCancellation()
                val details = error.message ?: error.javaClass.simpleName
                log("Auto reply failed: $details")
                conversationPort.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = AUTO_REPLY_FAILURE_NOTICE,
                )
                sendFailureNoticeIfNeeded(message, AUTO_REPLY_FAILURE_NOTICE)
                outcome = QqRuntimeResult.Failed(details, error)
                return@lock
            }

            if (deliveryResult is PluginV2HostLlmDeliveryResult.Suppressed) {
                log(
                    "QQ llm result suppressed: requestId=${deliveryResult.pipelineResult.admission.requestId} session=$sessionId",
                )
            } else if (
                deliveryResult is PluginV2HostLlmDeliveryResult.Sent &&
                shouldExecuteLegacyQqPluginsDuringLlmDispatch()
            ) {
                conversationPort.session(sessionId)
                    .messages
                    .lastOrNull { it.role == "assistant" }
                    ?.let { assistantMessage ->
                        executeLegacyQqPlugins(
                            trigger = PluginTriggerSource.AfterModelResponse,
                            message = message,
                            contextFactory = { plugin ->
                                buildQqPluginContext(
                                    plugin = plugin,
                                    trigger = PluginTriggerSource.AfterModelResponse,
                                    session = conversationPort.session(sessionId),
                                    conversationMessage = assistantMessage,
                                    provider = provider,
                                    bot = bot,
                                    persona = persona,
                                    config = config,
                                    message = message,
                                )
                            },
                        )
                    }
            }
            outcome = when (deliveryResult) {
                is PluginV2HostLlmDeliveryResult.Sent -> deliveryResult.sendResult.toQqRuntimeResult()
                is PluginV2HostLlmDeliveryResult.SendFailed ->
                    QqRuntimeResult.Failed("Send failed: ${deliveryResult.sendResult.errorSummary}")
                is PluginV2HostLlmDeliveryResult.Suppressed ->
                    QqRuntimeResult.Ignored("LLM result suppressed")
            }
        }
        return outcome
    }

    private fun buildCallbacks(
        message: IncomingQqMessage,
        sessionId: String,
        config: ConfigProfile,
        provider: ProviderProfile,
        persona: PersonaProfile?,
        runtimeContext: ResolvedRuntimeContext,
        wantsTts: Boolean,
        ttsProvider: ProviderProfile?,
        streamingMode: PluginV2StreamingMode,
    ): PlatformLlmCallbacks {
        return object : PlatformLlmCallbacks {
            override val platformInstanceKey: String = message.selfId.ifBlank { "onebot" }
            override val hostCapabilityGateway: PluginHostCapabilityGateway =
                DefaultPluginHostCapabilityGateway(
                    hostToolHandlers = PluginExecutionHostToolHandlers(
                        sendMessageHandler = { text ->
                            replySender.send(
                                QqReplyPayload(
                                    conversationId = message.conversationId,
                                    messageType = message.messageType,
                                    text = text,
                                ),
                                message,
                            )
                        },
                        sendNotificationHandler = { title, msg ->
                            log("QQ v2 host notification requested: title=$title message=$msg")
                        },
                        openHostPageHandler = { route ->
                            log("QQ v2 host page requested: route=$route")
                        },
                    ),
                )
            override val followupSender: PluginV2FollowupSender = PluginV2FollowupSender { text, attachments ->
                replySender.sendWithOutcome(
                    QqReplyPayload(
                        conversationId = message.conversationId,
                        messageType = message.messageType,
                        text = text,
                        attachments = attachments,
                    ),
                    message,
                )
            }

            override suspend fun prepareReply(
                result: PluginV2LlmPipelineResult,
            ): PluginV2HostPreparedReply {
                val sendableResult = result.sendableResult
                val decoratedAttachments = sendableResult.attachments.toConversationAttachments()
                val assistantAttachments = if (wantsTts && ttsProvider != null) {
                    compatBridge.buildVoiceReplyAttachments(
                        provider = ttsProvider,
                        response = sendableResult.text,
                        config = config,
                    )
                } else {
                    decoratedAttachments
                }
                val outboundBlocked = config.keywordDetectionEnabled &&
                    QqKeywordDetector(config.keywordPatterns).matches(sendableResult.text)
                val outboundText = if (outboundBlocked) {
                    log("QQ outbound keyword blocked: session=$sessionId")
                    KEYWORD_BLOCK_NOTICE
                } else {
                    sendableResult.text
                }
                val outboundAttachments = if (outboundBlocked) emptyList() else assistantAttachments
                log(
                    buildQqPreparedReplyLog(
                        requestId = result.admission.requestId,
                        text = outboundText,
                        attachmentCount = outboundAttachments.size,
                        hookTrace = result.hookInvocationTrace,
                        decoratingHandlers = result.decoratingRunResult.appliedHandlerIds,
                    ),
                )
                return PluginV2HostPreparedReply(
                    text = outboundText,
                    attachments = outboundAttachments,
                    deliveredEntries = listOf(
                        PluginV2AfterSentView.DeliveredEntry(
                            entryId = result.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                            entryType = "assistant",
                            textPreview = outboundText.take(160),
                            attachmentCount = outboundAttachments.size,
                        ),
                    ),
                )
            }

            override suspend fun sendReply(
                prepared: PluginV2HostPreparedReply,
            ): PluginV2HostSendResult {
                return compatBridge.sendPreparedReply(
                    message = message,
                    prepared = prepared,
                    config = config,
                    streamingMode = streamingMode,
                )
            }

            override suspend fun persistDeliveredReply(
                prepared: PluginV2HostPreparedReply,
                sendResult: PluginV2HostSendResult,
                pipelineResult: PluginV2LlmPipelineResult,
            ) {
                conversationPort.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = prepared.text,
                    attachments = prepared.attachments,
                )
            }

            override suspend fun invokeProvider(
                request: PluginProviderRequest,
                mode: PluginV2StreamingMode,
                ctx: ResolvedRuntimeContext,
            ): PluginV2ProviderInvocationResult {
                return providerInvoker.invoke(request, mode, ctx, config)
            }
        }
    }

    private fun handleBotCommand(
        message: IncomingQqMessage,
        bot: BotProfile,
        config: ConfigProfile,
        sessionId: String,
        session: ConversationSession,
        currentPersona: PersonaProfile?,
    ): Boolean {
        val trimmedText = message.text.trim()
        val result = BotCommandRouter.handle(
            input = trimmedText,
            context = BotCommandContext(
                source = BotCommandSource.QQ,
                languageTag = compatBridge.currentLanguageTag(),
                sessionId = sessionId,
                session = session,
                sessions = conversationPort.sessions(),
                bot = bot,
                availableBots = botPort.snapshotProfiles(),
                config = config,
                activeProviderId = resolveProvider(bot, session.providerId)?.id ?: session.providerId,
                availableProviders = providerPort.snapshotProfiles().filter {
                    it.enabled && ProviderCapability.CHAT in it.capabilities
                },
                currentPersona = currentPersona,
                availablePersonas = personaPort.snapshotProfiles().filter { it.enabled },
                messageType = message.messageType,
                sourceUid = message.senderId,
                sourceGroupId = message.groupIdOrBlank,
                selfId = message.selfId,
                deleteSession = { targetSessionId ->
                    conversationPort.deleteSession(targetSessionId)
                },
                renameSession = { targetSessionId, title ->
                    conversationPort.renameSession(targetSessionId, title)
                },
                updateConfig = { updatedConfig ->
                    runBlocking { configPort.save(updatedConfig) }
                },
                updateBot = { updatedBot ->
                    runBlocking { botPort.save(updatedBot) }
                },
                updateProvider = { updatedProvider ->
                    runBlocking { providerPort.save(updatedProvider) }
                },
                updateSessionServiceFlags = { sttEnabled, ttsEnabled ->
                    conversationPort.updateSessionServiceFlags(
                        sessionId = sessionId,
                        sessionSttEnabled = sttEnabled,
                        sessionTtsEnabled = ttsEnabled,
                    )
                },
                replaceMessages = { messages ->
                    conversationPort.replaceMessages(sessionId, messages)
                },
                updateSessionBindings = { providerId, personaId, botId ->
                    conversationPort.updateSessionBindings(
                        sessionId = sessionId,
                        providerId = providerId,
                        personaId = personaId,
                        botId = botId,
                    )
                },
            ),
        )
        if (!result.handled) {
            return false
        }
        result.replyText?.let { reply ->
            replySender.send(
                QqReplyPayload(
                    conversationId = message.conversationId,
                    messageType = message.messageType,
                    text = reply,
                ),
                message,
            )
        }
        log("Bot command handled via router: ${trimmedText.substringBefore(' ')} session=$sessionId")
        return result.stopModelDispatch
    }

    private fun handlePluginCommand(
        message: IncomingQqMessage,
        bot: BotProfile,
        config: ConfigProfile,
        sessionId: String,
        session: ConversationSession,
        currentPersona: PersonaProfile?,
    ): Boolean {
        val trimmedText = message.text.trim()
        if (!trimmedText.startsWith("/")) {
            return false
        }
        val dispatchResult = dispatchQqV2MessageIngress(
            trigger = PluginTriggerSource.OnCommand,
            message = message,
            conversationId = session.pluginConversationId(),
            botId = bot.id,
            configProfileId = config.id,
            personaId = currentPersona?.id.orEmpty(),
            providerId = resolveProvider(bot, session.providerId)?.id.orEmpty(),
        )
        dispatchResult.commandResponse?.let { commandResponse ->
            consumeQqV2CommandResponse(message, commandResponse)
            return true
        }
        if (dispatchResult.terminatedByCustomFilterFailure || dispatchResult.propagationStopped) {
            dispatchResult.userVisibleFailureMessage
                ?.takeIf { it.isNotBlank() }
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
            id = "qq-plugin-command:${sessionId}:${trimmedText.hashCode()}",
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
                plugins = PluginRuntimeRegistry.plugins(),
                contextFactory = { plugin ->
                    buildQqPluginContext(
                        plugin = plugin,
                        trigger = PluginTriggerSource.OnCommand,
                        session = session,
                        conversationMessage = syntheticMessage,
                        provider = resolveProvider(bot, session.providerId),
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
        val consumableOutcomes = batch.outcomes.filter(::isConsumableQqPluginOutcome)
        if (consumableOutcomes.isEmpty()) {
            log(
                "QQ plugin command produced no consumable results: command=${trimmedText.substringBefore(' ')} outcomes=${batch.outcomes.joinToString { it.result::class.simpleName.orEmpty() }}",
            )
            return false
        }
        consumableOutcomes.forEach { pluginOutcome ->
            consumeQqPluginOutcome(message, pluginOutcome)
            log("QQ plugin command handled: plugin=${pluginOutcome.pluginId} result=${pluginOutcome.result::class.simpleName.orEmpty()}")
        }
        return true
    }

    private fun executeLegacyQqPlugins(
        trigger: PluginTriggerSource,
        message: IncomingQqMessage,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ) {
        val plugins = PluginRuntimeRegistry.plugins()
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
            consumeQqPluginOutcome(message, pluginOutcome)
        }
    }

    private fun dispatchQqV2MessageIngress(
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

    private fun buildQqPluginContext(
        plugin: PluginRuntimePlugin,
        trigger: PluginTriggerSource,
        session: ConversationSession,
        conversationMessage: ConversationMessage,
        provider: ProviderProfile?,
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
        return DefaultPluginHostCapabilityGateway().injectContext(base)
    }

    private fun consumeQqV2CommandResponse(
        message: IncomingQqMessage,
        response: PluginV2CommandResponse,
    ) {
        val attachments = response.attachments.mapIndexedNotNull { index, attachment ->
            resolveQqV2CommandAttachment(response, attachment)?.let { resolvedSource ->
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

    private fun resolveQqV2CommandAttachment(
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
            return java.io.File(response.extractedDir, relativePath).absolutePath
        }
        val sourceFile = java.io.File(source)
        return when {
            sourceFile.isAbsolute -> sourceFile.absolutePath
            response.extractedDir.isNotBlank() -> java.io.File(response.extractedDir, source).absolutePath
            else -> source
        }
    }

    private fun consumeQqPluginOutcome(
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
                        privateRootPath = compatBridge.resolvePluginPrivateRootPath(outcome.pluginId),
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
                val execution = DefaultPluginHostCapabilityGateway(
                    hostActionExecutor = ExternalPluginHostActionExecutor(
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
                    ),
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

    private fun evaluateReplyPolicy(
        message: IncomingQqMessage,
        bot: BotProfile,
        config: ConfigProfile,
    ) = QqReplyPolicyEvaluator.evaluate(
        QqReplyPolicyInput(
            messageType = message.messageType,
            text = message.text,
            userId = message.senderId,
            groupId = message.groupIdOrBlank.takeIf { it.isNotBlank() },
            isCommand = BotCommandParser.parse(message.text) != null,
            mentionsSelf = message.mentionsSelf,
            mentionsAll = message.mentionsAll,
            isSelfMessage = message.selfId.isNotBlank() && message.selfId == message.senderId,
            ignoreSelfMessageEnabled = config.ignoreSelfMessageEnabled,
            ignoreAtAllEventEnabled = config.ignoreAtAllEventEnabled,
            isAdmin = message.senderId in config.adminUids,
            whitelistEnabled = config.whitelistEnabled,
            whitelistEntries = config.whitelistEntries,
            logOnWhitelistMiss = config.logOnWhitelistMiss,
            adminGroupBypassWhitelistEnabled = config.adminGroupBypassWhitelistEnabled,
            adminPrivateBypassWhitelistEnabled = config.adminPrivateBypassWhitelistEnabled,
            replyWhenPermissionDenied = config.replyWhenPermissionDenied,
            replyOnAtOnlyEnabled = config.replyOnAtOnlyEnabled,
            wakeWords = (bot.triggerWords + config.wakeWords).distinct(),
            wakeWordsAdminOnlyEnabled = config.wakeWordsAdminOnlyEnabled,
            privateChatRequiresWakeWord = config.privateChatRequiresWakeWord,
            hasExplicitAtTrigger = message.mentionsSelf || message.mentionsAll,
        ),
    )

    private fun buildRateLimitSourceKey(bot: BotProfile, message: IncomingQqMessage): String {
        return listOf(bot.id, message.messageType.wireValue, message.groupIdOrBlank, message.senderId)
            .filter { it.isNotBlank() }
            .joinToString(":")
    }

    private fun buildSessionId(
        bot: BotProfile,
        message: IncomingQqMessage,
        config: ConfigProfile,
    ): String {
        return QqSessionKeyFactory.build(
            botId = bot.id,
            messageType = message.messageType,
            groupId = message.groupIdOrBlank,
            userId = message.senderId,
            isolated = config.sessionIsolationEnabled,
        )
    }

    private fun buildPromptContent(
        message: IncomingQqMessage,
        cleanedText: String,
        transcribedAudioText: String?,
    ): String {
        val textContent = buildString {
            if (cleanedText.isNotBlank()) {
                append(cleanedText)
            }
            transcribedAudioText?.takeIf { it.isNotBlank() }?.let { sttText ->
                if (isNotBlank()) append("\n\n")
                append(sttText)
            }
        }.trim()
        return when (message.messageType) {
            MessageType.GroupMessage -> "${message.senderName.ifBlank { message.senderId }}: $textContent".trim()
            else -> textContent
        }
    }

    private fun buildLlmInputSnapshotFallback(
        finalPromptContent: String,
        attachments: List<ConversationAttachment>,
    ): String {
        if (finalPromptContent.isNotBlank()) {
            return finalPromptContent
        }
        if (attachments.isEmpty()) {
            return "[empty]"
        }
        val attachmentSummary = attachments
            .groupingBy { attachment -> attachment.type.ifBlank { "attachment" } }
            .eachCount()
            .entries
            .joinToString(separator = ", ") { (type, count) ->
                if (count == 1) type else "$count x $type"
            }
        return "attachment: $attachmentSummary"
    }

    private fun buildQqPreparedReplyLog(
        requestId: String,
        text: String,
        attachmentCount: Int,
        hookTrace: List<String>,
        decoratingHandlers: List<String>,
    ): String {
        val compactText = text.replace("\r", " ")
            .replace("\n", "\\n")
            .take(240)
        val leakedTag = Regex("\\[[a-zA-Z0-9_-]+]$").containsMatchIn(text.trim())
        return buildString {
            append("QQ prepared reply: request=").append(requestId)
            append(" attachments=").append(attachmentCount)
            append(" leakedTag=").append(leakedTag)
            append(" hooks=").append(hookTrace.joinToString(separator = ",").ifBlank { "none" })
            append(" decorators=").append(decoratingHandlers.joinToString(separator = ",").ifBlank { "none" })
            append(" text=\"").append(compactText).append('"')
        }
    }

    private fun shouldExecuteLegacyQqPluginsDuringLlmDispatch(): Boolean {
        return llmRuntime === DefaultAppChatPluginRuntime
    }

    private fun resolveProvider(bot: BotProfile, preferredProviderId: String = ""): ProviderProfile? {
        val providers = providerPort.snapshotProfiles().filter {
            it.enabled && ProviderCapability.CHAT in it.capabilities
        }
        val config = configPort.resolve(bot.configProfileId)
        val preferredIds = listOf(
            preferredProviderId,
            bot.defaultProviderId,
            config.defaultChatProviderId,
        ).filter { it.isNotBlank() }
        return preferredIds.firstNotNullOfOrNull { preferredId ->
            providers.firstOrNull { it.id == preferredId }
        } ?: providers.firstOrNull()
    }

    private fun resolveSttProvider(providerId: String): ProviderProfile? {
        return providerPort.snapshotProfiles().firstOrNull {
            it.id == providerId &&
                it.enabled &&
                ProviderCapability.STT in it.capabilities
        }
    }

    private fun resolveTtsProvider(providerId: String): ProviderProfile? {
        return providerPort.snapshotProfiles().firstOrNull {
            it.id == providerId &&
                it.enabled &&
                ProviderCapability.TTS in it.capabilities
        }
    }

    private fun resolvePersona(bot: BotProfile, sessionPersonaId: String?): PersonaProfile? {
        val personas = personaPort.snapshotProfiles().filter { it.enabled }
        return personas.firstOrNull { it.id == sessionPersonaId && !sessionPersonaId.isNullOrBlank() }
            ?: personas.firstOrNull { it.id == bot.defaultPersonaId }
            ?: personas.firstOrNull()
    }

    private fun sendFailureNoticeIfNeeded(message: IncomingQqMessage, text: String) {
        if (message.messageType == MessageType.FriendMessage) {
            replySender.send(
                QqReplyPayload(
                    conversationId = message.conversationId,
                    messageType = message.messageType,
                    text = text,
                ),
                message,
            )
        }
    }

    private fun isConsumableQqPluginOutcome(outcome: PluginExecutionOutcome): Boolean {
        return when (outcome.result) {
            is TextResult, is ErrorResult, is MediaResult, is HostActionRequest -> true
            else -> false
        }
    }

    private fun IncomingQqMessage.toPluginMessageEvent(
        trigger: PluginTriggerSource,
        conversationId: String? = null,
        botId: String = "",
        configProfileId: String = "",
        personaId: String = "",
        providerId: String = "",
    ): PluginMessageEvent {
        val resolvedEventId = messageId.takeIf { it.isNotBlank() }
            ?: "${trigger.wireValue}:${System.currentTimeMillis()}"
        return PluginMessageEvent(
            eventId = resolvedEventId,
            platformAdapterType = ONE_BOT_PLATFORM_ADAPTER_TYPE,
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
            MessageType.GroupMessage -> "group:${groupIdOrBlank}"
            MessageType.FriendMessage,
            MessageType.OtherMessage,
            -> "friend:$senderId"
        }
    }

    private val IncomingQqMessage.groupIdOrBlank: String
        get() = if (messageType == MessageType.GroupMessage) conversationId else ""

    private fun ConversationSession.pluginConversationId(): String {
        return originSessionId.ifBlank { id }
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

    private fun PluginV2HostSendResult.toQqRuntimeResult(): QqRuntimeResult {
        return if (success) {
            QqRuntimeResult.Replied(
                QqSendResult(
                    success = true,
                    receiptIds = receiptIds,
                ),
            )
        } else {
            QqRuntimeResult.Failed("Send failed: $errorSummary")
        }
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private companion object {
        private const val ONE_BOT_PLATFORM_ADAPTER_TYPE = "onebot"
        private const val AUTO_REPLY_FAILURE_NOTICE = "工具调用失败：本轮自动回复未完成，请稍后再试。"
        private const val KEYWORD_BLOCK_NOTICE = "你的消息或者大模型的响应中包含不适当的内容，已被屏蔽。"
    }
}

internal fun interface QqProviderInvoker {
    suspend fun invoke(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
        config: ConfigProfile,
    ): PluginV2ProviderInvocationResult
}
