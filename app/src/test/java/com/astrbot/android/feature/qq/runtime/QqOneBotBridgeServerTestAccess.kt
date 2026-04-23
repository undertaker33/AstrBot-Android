package com.astrbot.android.feature.qq.runtime

internal object QqOneBotBridgeServerTestAccess {
    private val runtimeDependenciesField = QqOneBotBridgeServer::class.java
        .getDeclaredField("runtimeDependencies")
        .apply { isAccessible = true }
    private val runtimeGraphFactoryField = QqOneBotBridgeServer::class.java
        .getDeclaredField("runtimeGraphFactoryOverrideForTests")
        .apply { isAccessible = true }

    fun primeRuntimeDependencies(dependencies: QqOneBotRuntimeDependencies) {
        runtimeDependenciesField.set(QqOneBotBridgeServer, dependencies)
        runtimeGraphFactoryField.set(QqOneBotBridgeServer, TestQqRuntimeGraphFactory)
    }

    fun updateRuntimeDependencies(
        transform: (QqOneBotRuntimeDependencies) -> QqOneBotRuntimeDependencies,
    ) {
        val current = runtimeDependenciesField.get(QqOneBotBridgeServer) as? QqOneBotRuntimeDependencies
            ?: error("QqOneBotBridgeServer requires runtime dependencies before test updates.")
        runtimeDependenciesField.set(QqOneBotBridgeServer, transform(current))
    }

    fun clearRuntimeDependencies() {
        runtimeDependenciesField.set(QqOneBotBridgeServer, null)
        runtimeGraphFactoryField.set(QqOneBotBridgeServer, null)
    }
}

private object TestQqRuntimeGraphFactory : QqRuntimeGraphFactory {
    override fun create(
        dependencies: QqOneBotRuntimeDependencies,
        transport: OneBotReverseWebSocketTransport,
        appChatPluginRuntime: com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime,
        replyOverrideProvider: () -> ((IncomingMessageEvent, String, List<com.astrbot.android.model.chat.ConversationAttachment>) -> OneBotSendResult)?,
        filesDirProvider: () -> java.io.File?,
        rateLimiter: QqRateLimiter,
        markMessageId: (String) -> Boolean,
        scheduleStashReplay: (com.astrbot.android.model.BotProfile, com.astrbot.android.model.ConfigProfile, String) -> Unit,
        currentLanguageTag: () -> String,
        transcribeAudio: (com.astrbot.android.model.ProviderProfile, com.astrbot.android.model.chat.ConversationAttachment) -> String,
        resolvePluginPrivateRootPath: (String) -> String,
        log: (String) -> Unit,
    ): QqOneBotRuntimeGraph {
        val outboundGateway = QqOneBotOutboundGateway(
            transport = transport,
            botPort = dependencies.botPort,
            configPort = dependencies.configPort,
            platformConfigPort = dependencies.platformConfigPort,
            audioMaterializer = QqAudioAttachmentMaterializer(
                filesDirProvider = filesDirProvider,
                encodeSilkAudio = dependencies.silkAudioEncoder,
                log = log,
            ),
            replyOverrideProvider = replyOverrideProvider,
            log = log,
        )
        val replySender = outboundGateway.buildReplySender()
        val profileResolver = QqRuntimeProfileResolver(
            botPort = dependencies.botPort,
            configPort = dependencies.configPort,
            personaPort = dependencies.personaPort,
            providerPort = dependencies.providerPort,
        )
        val botCommandRuntimeService = QqBotCommandRuntimeService(
            botPort = dependencies.botPort,
            configPort = dependencies.configPort,
            providerPort = dependencies.providerPort,
            conversationPort = dependencies.conversationPort,
            replySender = replySender,
            profileResolver = profileResolver,
            currentLanguageTag = currentLanguageTag,
            log = log,
        )
        val pluginDispatchService = QqPluginDispatchService(
            replySender = replySender,
            profileResolver = profileResolver,
            resolvePluginPrivateRootPath = resolvePluginPrivateRootPath,
            hostCapabilityGateway = dependencies.hostCapabilityGateway,
            hostActionExecutor = dependencies.hostActionExecutor,
            dispatchEngine = dependencies.pluginV2DispatchEngine,
            executionService = dependencies.pluginExecutionService,
            log = log,
        )
        val streamingReplyService = QqStreamingReplyService(
            replySender = replySender,
            synthesizeSpeech = dependencies.llmProviderProbePort::synthesizeSpeech,
            log = log,
        )
        val runtimeService = QqMessageRuntimeService(
            configPort = dependencies.configPort,
            conversationPort = dependencies.conversationPort,
            platformConfigPort = dependencies.platformConfigPort,
            orchestrator = dependencies.orchestrator,
            runtimeContextResolverPort = dependencies.runtimeContextResolverPort,
            replySender = replySender,
            llmRuntime = appChatPluginRuntime,
            providerInvoker = dependencies.providerInvoker,
            rateLimiter = rateLimiter,
            markMessageId = markMessageId,
            scheduleStashReplay = scheduleStashReplay,
            currentLanguageTag = currentLanguageTag,
            transcribeAudio = transcribeAudio,
            profileResolver = profileResolver,
            botCommandRuntimeService = botCommandRuntimeService,
            pluginDispatchService = pluginDispatchService,
            streamingReplyService = streamingReplyService,
            gatewayFactory = dependencies.gatewayFactory,
            executeLegacyPluginsDuringLlmDispatch = dependencies.executeLegacyPluginsDuringLlmDispatch,
            log = log,
        )
        return QqOneBotRuntimeGraph(
            outboundGateway = outboundGateway,
            pluginDispatchService = pluginDispatchService,
            runtimeService = runtimeService,
            replySender = replySender,
            log = log,
        )
    }
}
