package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.core.runtime.context.ResolvedRuntimeContext
import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.core.runtime.context.RuntimeIngressEvent
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.core.runtime.context.SenderInfo
import com.elymbot.android.core.runtime.context.StreamingModeResolver
import com.elymbot.android.core.runtime.session.SessionLockCoordinator
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.cron.runtime.ScheduledTaskIntentFallbackResponder
import com.elymbot.android.feature.cron.runtime.ScheduledTaskIntentGuardContext
import com.elymbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGatewayFactory
import com.elymbot.android.feature.plugin.domain.runtime.PlatformLlmCallbacks
import com.elymbot.android.feature.plugin.domain.runtime.PluginProviderRequest
import com.elymbot.android.feature.plugin.domain.runtime.PluginToolResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2FollowupSender
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostLlmDeliveryResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostLlmDeliverySendFailed
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostLlmDeliverySent
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostLlmDeliverySuppressed
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostPreparedReply
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostSendResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2LlmPipelineResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2ProviderInvocationResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2ToolResultDeliveryRequest
import com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort
import com.elymbot.android.feature.qq.domain.IncomingQqMessage
import com.elymbot.android.feature.qq.domain.QqConversationPort
import com.elymbot.android.feature.qq.domain.QqPlatformConfigPort
import com.elymbot.android.feature.qq.domain.QqReplyPayload
import com.elymbot.android.feature.qq.domain.QqRuntimePort
import com.elymbot.android.feature.qq.domain.QqRuntimeResult
import com.elymbot.android.feature.qq.domain.QqSendResult
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.model.chat.MessageSessionRef
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginTriggerSource
import kotlinx.coroutines.CancellationException

internal class QqMessageRuntimeService(
    private val configPort: ConfigRepositoryPort,
    private val conversationPort: QqConversationPort,
    private val platformConfigPort: QqPlatformConfigPort,
    private val orchestrator: RuntimeLlmOrchestratorPort,
    private val runtimeContextResolverPort: RuntimeContextResolverPort,
    private val replySender: QqReplySender,
    private val llmRuntime: AppChatLlmPipelineRuntime,
    private val providerInvoker: QqProviderInvoker,
    private val rateLimiter: QqRateLimiter,
    private val markMessageId: (String) -> Boolean,
    private val scheduleStashReplay: (BotProfile, ConfigProfile, String) -> Unit,
    private val currentLanguageTag: () -> String,
    private val strings: com.elymbot.android.feature.chat.runtime.botcommand.BotCommandStringResolver =
        com.elymbot.android.feature.chat.runtime.botcommand.BotCommandStringResolver.fallback,
    private val transcribeAudio: (ProviderProfile, ConversationAttachment) -> String,
    private val profileResolver: QqRuntimeProfileResolver,
    private val botCommandRuntimeService: QqBotCommandRuntimeService,
    private val pluginDispatchService: QqPluginDispatchService,
    private val streamingReplyService: QqStreamingReplyService,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
    private val scheduledTaskFallbackResponder: ScheduledTaskIntentFallbackResponder? = null,
    private val sessionLockCoordinator: SessionLockCoordinator,
    private val executeLegacyPluginsDuringLlmDispatch: Boolean = true,
    private val log: (String) -> Unit = {},
) : QqRuntimePort {

    override suspend fun handleIncomingMessage(message: IncomingQqMessage): QqRuntimeResult {
        val bot = platformConfigPort.resolveQqBot(message.selfId)
            ?: return QqRuntimeResult.Ignored("No bot bound to selfId=${message.selfId}")
        if (!bot.autoReplyEnabled) {
            return QqRuntimeResult.Ignored("Bot auto reply disabled")
        }
        val config = configPort.resolve(bot.configProfileId)
        logConfigBindingMismatchIfNeeded(bot, config)
        val replyDecision = evaluateReplyPolicy(message, bot, config)
        if (isDuplicateIncomingMessage(message)) {
            return QqRuntimeResult.Ignored("duplicate_message")
        }
        val publicGroupDeposit = depositPublicGroupMessageIfNeeded(message, bot)
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

        return processMessage(message, bot, config, publicGroupDeposit)
    }

    private suspend fun depositPublicGroupMessageIfNeeded(
        message: IncomingQqMessage,
        bot: BotProfile,
    ): PublicGroupMessageDeposit? {
        if (message.messageType != MessageType.GroupMessage) return null
        return depositPublicGroupMessage(message, bot)
    }

    private suspend fun depositPublicGroupMessage(
        message: IncomingQqMessage,
        bot: BotProfile,
    ): PublicGroupMessageDeposit {
        val sessionId = buildPublicGroupSessionId(bot, message)
        val sessionTitle = QqConversationTitleResolver.build(
            messageType = message.messageType,
            groupId = message.groupIdOrBlank,
            userId = message.senderId,
            senderName = message.senderName,
        )
        val finalPromptContent = buildPromptContent(
            message = message,
            cleanedText = message.text.trim(),
            transcribedAudioText = null,
        )
        val publicGroupDeposit = sessionLockCoordinator.withLock(sessionId) {
            val session = conversationPort.resolveOrCreateSession(sessionId, sessionTitle, message.messageType)
            conversationPort.updateSessionBindings(
                sessionId = sessionId,
                botId = bot.id,
                providerId = session.providerId,
                personaId = session.personaId,
            )
            val messageId = conversationPort.appendMessage(
                sessionId = sessionId,
                role = "user",
                content = finalPromptContent,
                attachments = message.attachments,
            )
            trimPublicGroupHistoryIfNeeded(sessionId)
            PublicGroupMessageDeposit(sessionId = sessionId, messageId = messageId)
        }
        log(
            "QQ public group message deposited: session=$sessionId chars=${message.text.length} attachments=${message.attachments.size}",
        )
        return publicGroupDeposit
    }

    private fun trimPublicGroupHistoryIfNeeded(sessionId: String) {
        val messages = conversationPort.session(sessionId).messages
        if (messages.size <= PUBLIC_GROUP_HISTORY_LIMIT) return
        conversationPort.replaceMessages(sessionId, messages.takeLast(PUBLIC_GROUP_HISTORY_LIMIT))
        log(
            "QQ public group message history trimmed: session=$sessionId kept=$PUBLIC_GROUP_HISTORY_LIMIT dropped=${messages.size - PUBLIC_GROUP_HISTORY_LIMIT}",
        )
    }

    // skipcq: KT-R1006
    private suspend fun processMessage(
        message: IncomingQqMessage,
        bot: BotProfile,
        config: ConfigProfile,
        publicGroupDeposit: PublicGroupMessageDeposit?,
    ): QqRuntimeResult {
        val sessionId = buildSessionId(bot, message, config)
        val sessionTitle = QqConversationTitleResolver.build(
            messageType = message.messageType,
            groupId = message.groupIdOrBlank,
            userId = message.senderId,
            senderName = message.senderName,
        )
        var outcome: QqRuntimeResult = QqRuntimeResult.Ignored("not_processed")
        sessionLockCoordinator.withLock(sessionId) lock@{
            val session = conversationPort.resolveOrCreateSession(sessionId, sessionTitle, message.messageType)
            val persona = profileResolver.resolvePersona(bot, session.personaId)
            val parsedBotCommand = com.elymbot.android.feature.chat.runtime.botcommand.BotCommandParser.parse(message.text)
            when {
                parsedBotCommand != null &&
                    com.elymbot.android.feature.chat.runtime.botcommand.BotCommandRouter.supports(parsedBotCommand.name) -> {
                    if (
                        botCommandRuntimeService.handle(
                            message = message,
                            bot = bot,
                            config = config,
                            sessionId = sessionId,
                            session = session,
                            currentPersona = persona,
                        )
                    ) {
                        outcome = QqRuntimeResult.Replied(QqSendResult(success = true))
                        return@lock
                    }
                }

                parsedBotCommand != null -> {
                    if (
                        pluginDispatchService.handlePluginCommand(
                            message = message,
                            bot = bot,
                            config = config,
                            session = session,
                            currentPersona = persona,
                        )
                    ) {
                        outcome = QqRuntimeResult.Replied(QqSendResult(success = true))
                        return@lock
                    }
                    replySender.send(
                        QqReplyPayload(
                            conversationId = message.conversationId,
                            messageType = message.messageType,
                            text = com.elymbot.android.feature.chat.runtime.botcommand.BotCommandResources.unsupportedCommand(
                  parsedBotCommand.name,
                  currentLanguageTag(),
                  strings,
              ),
                        ),
                        message,
                    )
                    log("Bot command unsupported after plugin fallback: ${parsedBotCommand.name} session=$sessionId")
                    outcome = QqRuntimeResult.Replied(QqSendResult(success = true))
                    return@lock
                }

                pluginDispatchService.handlePluginCommand(
                    message = message,
                    bot = bot,
                    config = config,
                    session = session,
                    currentPersona = persona,
                ) -> {
                    outcome = QqRuntimeResult.Replied(QqSendResult(success = true))
                    return@lock
                }
            }

            val provider = profileResolver.resolveProvider(bot, session.providerId)
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
            val wantsTts = config.ttsEnabled &&
                session.sessionTtsEnabled &&
                (config.alwaysTtsEnabled || ttsSuffixMatched)
            val cleanedText = message.text.trim().removeSuffix("~").trim()
            val sttProvider = config.defaultSttProviderId
                .takeIf { config.sttEnabled && session.sessionSttEnabled }
                ?.let(profileResolver::resolveSttProvider)
            val ttsProvider = config.defaultTtsProviderId
                .takeIf { config.ttsEnabled && session.sessionTtsEnabled }
                ?.let(profileResolver::resolveTtsProvider)
            val transcribedAudioText = if (message.attachments.any { it.type == "audio" } && sttProvider != null) {
                runCatching {
                    message.attachments
                        .filter { it.type == "audio" }
                        .joinToString("\n") { attachment ->
                            transcribeAudio(sttProvider, attachment)
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

            val userMessageId = if (publicGroupDeposit?.sessionId == sessionId) {
                publicGroupDeposit.messageId
            } else {
                conversationPort.appendMessage(
                    sessionId = sessionId,
                    role = "user",
                    content = finalPromptContent,
                    attachments = message.attachments,
                )
            }
            val preModelSession = conversationPort.session(sessionId)
            val userMessage = preModelSession.messages.firstOrNull { historyMessage -> historyMessage.id == userMessageId }
                ?: run {
                    outcome = QqRuntimeResult.Failed("Could not find user message after append")
                    return@lock
                }
            log(
                "QQ message received: type=${message.messageType} session=$sessionId chars=${message.text.length} attachments=${message.attachments.size}",
            )
            val llmEvent = message.toPluginMessageEvent(
                trigger = PluginTriggerSource.BeforeSendMessage,
                conversationId = preModelSession.originSessionId.ifBlank { preModelSession.id },
                sessionUnifiedOrigin = MessageSessionRef(
                    platformId = preModelSession.platformId,
                    messageType = preModelSession.messageType,
                    originSessionId = preModelSession.originSessionId,
                ).unifiedOrigin,
                botId = bot.id,
                configProfileId = config.id,
                personaId = persona?.id.orEmpty(),
                providerId = provider.id,
            )
            val ingressDispatchResult = pluginDispatchService.dispatchMessageIngress(
                trigger = PluginTriggerSource.BeforeSendMessage,
                message = message,
                materializedEvent = llmEvent,
            )
            if (ingressDispatchResult.terminatedByCustomFilterFailure || ingressDispatchResult.propagationStopped) {
                ingressDispatchResult.userVisibleFailureMessage
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
                pluginDispatchService.executeLegacyPlugins(
                    trigger = PluginTriggerSource.BeforeSendMessage,
                    message = message,
                    contextFactory = { plugin ->
                        pluginDispatchService.buildPluginContext(
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

            val runtimeContext = runtimeContextResolverPort.resolve(
                event = RuntimeIngressEvent(
                    platform = RuntimePlatform.QQ_ONEBOT,
                    conversationId = preModelSession.originSessionId.ifBlank { preModelSession.id },
                    repositorySessionId = sessionId,
                    messageId = userMessage.id,
                    sender = SenderInfo(
                        userId = message.senderId,
                        nickname = message.senderName,
                        groupId = message.groupIdOrBlank,
                    ),
                    messageType = message.messageType.toRuntimeMessageType(),
                    text = message.text,
                ),
                bot = bot.toRuntimeBotSnapshot(),
                overrideProviderId = provider.id,
                overridePersonaId = persona?.id,
            )
            val streamingMode = StreamingModeResolver.resolve(runtimeContext).toPluginStreamingMode()
            val callbacks = buildCallbacks(
                message = message,
                sessionId = sessionId,
                config = config,
                wantsTts = wantsTts,
                ttsProvider = ttsProvider,
                streamingMode = streamingMode,
                userText = llmEvent.workingText.ifBlank { finalPromptContent },
                runtimeContext = runtimeContext,
                bot = bot,
                persona = persona,
                provider = provider,
            )
            val deliveryAttempt = runCatching {
                orchestrator.dispatchLlm(
                    ctx = runtimeContext,
                    llmRuntime = llmRuntime,
                    callbacks = callbacks,
                    userMessage = userMessage,
                    preBuiltPluginEvent = llmEvent,
                )
            }
            if (deliveryAttempt.isFailure) {
                val error = requireNotNull(deliveryAttempt.exceptionOrNull())
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
            val deliveryResult = deliveryAttempt.getOrThrow()

            if (deliveryResult is PluginV2HostLlmDeliverySuppressed) {
                log(
                    "QQ llm result suppressed: requestId=${deliveryResult.pipelineResult.admission.requestId} session=$sessionId",
                )
            } else if (
                deliveryResult is PluginV2HostLlmDeliverySent &&
                shouldExecuteLegacyQqPluginsDuringLlmDispatch()
            ) {
                conversationPort.session(sessionId)
                    .messages
                    .lastOrNull { historyMessage -> historyMessage.role == "assistant" }
                    ?.let { assistantMessage ->
                        pluginDispatchService.executeLegacyPlugins(
                            trigger = PluginTriggerSource.AfterModelResponse,
                            message = message,
                            contextFactory = { plugin ->
                                pluginDispatchService.buildPluginContext(
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
                is PluginV2HostLlmDeliverySent -> deliveryResult.sendResult.toQqRuntimeResult()
                is PluginV2HostLlmDeliverySendFailed ->
                    QqRuntimeResult.Failed("Send failed: ${deliveryResult.sendResult.errorSummary}")
                is PluginV2HostLlmDeliverySuppressed ->
                    QqRuntimeResult.Ignored("LLM result suppressed")
            }
        }
        return outcome
    }

    private fun buildCallbacks(
        message: IncomingQqMessage,
        sessionId: String,
        config: ConfigProfile,
        wantsTts: Boolean,
        ttsProvider: ProviderProfile?,
        streamingMode: com.elymbot.android.model.plugin.PluginV2StreamingMode,
        userText: String,
        runtimeContext: ResolvedRuntimeContext,
        bot: BotProfile,
        persona: PersonaProfile?,
        provider: ProviderProfile,
    ): PlatformLlmCallbacks {
        return object : PlatformLlmCallbacks {
            override val platformInstanceKey: String = message.selfId.ifBlank { "onebot" }
            override val hostCapabilityGateway: PluginHostCapabilityGateway =
                gatewayFactory.create(
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
                val effectiveResult = scheduledTaskFallbackResponder?.applyFallbackIfNeeded(
                    userText = userText,
                    context = ScheduledTaskIntentGuardContext(
                        proactiveEnabled = config.proactiveEnabled,
                        platform = RuntimePlatform.QQ_ONEBOT.wireValue,
                        conversationId = runtimeContext.conversationId,
                        botId = bot.id,
                        configProfileId = config.id,
                        personaId = persona?.id.orEmpty(),
                        providerId = provider.id,
                    ),
                    pipelineResult = result,
                    invokeProvider = { followupRequest ->
                        providerInvoker.invoke(
                            request = followupRequest,
                            mode = com.elymbot.android.model.plugin.PluginV2StreamingMode.NON_STREAM,
                            ctx = runtimeContext,
                            config = config,
                        )
                    },
                ) ?: result
                return streamingReplyService.prepareReply(
                    result = effectiveResult,
                    sessionId = sessionId,
                    config = config,
                    wantsTts = wantsTts,
                    ttsProvider = ttsProvider,
                )
            }

            override suspend fun sendReply(
                prepared: PluginV2HostPreparedReply,
            ): PluginV2HostSendResult {
                return streamingReplyService.sendPreparedReply(
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

            override suspend fun handleToolResult(
                request: PluginV2ToolResultDeliveryRequest,
            ): PluginToolResult {
                return streamingReplyService.deliverNewsSearchResultIfNeeded(
                    message = message,
                    toolName = request.descriptor.name,
                    result = request.result,
                )
            }

            override suspend fun invokeProvider(
                request: PluginProviderRequest,
                mode: com.elymbot.android.model.plugin.PluginV2StreamingMode,
                ctx: ResolvedRuntimeContext,
            ): PluginV2ProviderInvocationResult {
                return providerInvoker.invoke(request, mode, ctx, config)
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
            groupId = message.groupIdOrBlank.takeIf(String::isNotBlank),
            isCommand = com.elymbot.android.feature.chat.runtime.botcommand.BotCommandParser.parse(message.text) != null,
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

    private fun isDuplicateIncomingMessage(message: IncomingQqMessage): Boolean {
        if (message.messageId.isBlank()) return false
        val firstSeen = markMessageId(message.messageId)
        if (!firstSeen) {
            log("OneBot duplicate message ignored: ${message.messageId}")
        }
        return !firstSeen
    }

    private fun buildRateLimitSourceKey(
        bot: BotProfile,
        message: IncomingQqMessage,
    ): String {
        return listOf(bot.id, message.messageType.wireValue, message.groupIdOrBlank, message.senderId)
            .filter(String::isNotBlank)
            .joinToString(":")
    }

    private fun logConfigBindingMismatchIfNeeded(bot: BotProfile, config: ConfigProfile) {
        val selectedConfigId = configPort.selectedProfileId.value
        val selectedConfigMismatch = selectedConfigId.isNotBlank() && selectedConfigId != config.id
        val providerMismatch = bot.defaultProviderId.isNotBlank() &&
            config.defaultChatProviderId.isNotBlank() &&
            bot.defaultProviderId != config.defaultChatProviderId
        if (selectedConfigMismatch || providerMismatch) {
            log(
                "QQ config binding mismatch: bot=${bot.id} botConfig=${bot.configProfileId} " +
                    "selectedConfig=${selectedConfigId.ifBlank { "-" }} " +
                    "configDefaultProvider=${config.defaultChatProviderId.ifBlank { "none" }} " +
                    "botDefaultProvider=${bot.defaultProviderId.ifBlank { "none" }}",
            )
        }
    }

    private fun buildPublicGroupSessionId(
        bot: BotProfile,
        message: IncomingQqMessage,
    ): String {
        return QqSessionKeyFactory.build(
            botId = bot.id,
            messageType = message.messageType,
            groupId = message.groupIdOrBlank,
            userId = message.senderId,
            isolated = false,
        )
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
            transcribedAudioText?.takeIf(String::isNotBlank)?.let { sttText ->
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

    private fun shouldExecuteLegacyQqPluginsDuringLlmDispatch(): Boolean {
        return executeLegacyPluginsDuringLlmDispatch
    }

    private fun sendFailureNoticeIfNeeded(
        message: IncomingQqMessage,
        text: String,
    ) {
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
        private const val PUBLIC_GROUP_HISTORY_LIMIT = 100
        private const val AUTO_REPLY_FAILURE_NOTICE = "工具调用失败：本轮自动回复未完成，请稍后再试。"
        private const val KEYWORD_BLOCK_NOTICE = "你的消息或者大模型的响应中包含不适当的内容，已被屏蔽。"
    }

    private data class PublicGroupMessageDeposit(
        val sessionId: String,
        val messageId: String,
    )
}

fun interface QqProviderInvoker {
    suspend fun invoke(
        request: PluginProviderRequest,
        mode: com.elymbot.android.model.plugin.PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
        config: ConfigProfile,
    ): PluginV2ProviderInvocationResult
}
