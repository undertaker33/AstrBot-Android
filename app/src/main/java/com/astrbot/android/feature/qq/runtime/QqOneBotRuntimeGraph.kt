package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import java.io.File

internal class QqOneBotRuntimeGraph(
    private val dependencies: QqOneBotRuntimeDependencies,
    private val transport: OneBotReverseWebSocketTransport,
    private val appChatPluginRuntime: AppChatLlmPipelineRuntime,
    private val replyOverrideProvider: () -> ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)?,
    private val filesDirProvider: () -> File?,
    private val rateLimiter: QqRateLimiter,
    private val markMessageId: (String) -> Boolean,
    private val scheduleStashReplay: (com.astrbot.android.model.BotProfile, ConfigProfile, String) -> Unit,
    private val currentLanguageTag: () -> String,
    private val transcribeAudio: (ProviderProfile, ConversationAttachment) -> String,
    private val resolvePluginPrivateRootPath: (String) -> String,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
    private val log: (String) -> Unit,
) {
    private val outboundGateway: QqOneBotOutboundGateway by lazy {
        QqOneBotOutboundGateway(
            transport = transport,
            botPort = dependencies.botPort,
            configPort = dependencies.configPort,
            platformConfigPort = dependencies.platformConfigPort,
            audioMaterializer = QqAudioAttachmentMaterializer(
                filesDirProvider = filesDirProvider,
                log = log,
            ),
            replyOverrideProvider = replyOverrideProvider,
            log = log,
        )
    }

    private val replySender: QqReplySender by lazy {
        outboundGateway.buildReplySender()
    }

    private val profileResolver: QqRuntimeProfileResolver by lazy {
        QqRuntimeProfileResolver(
            botPort = dependencies.botPort,
            configPort = dependencies.configPort,
            personaPort = dependencies.personaPort,
            providerPort = dependencies.providerPort,
        )
    }

    private val botCommandRuntimeService: QqBotCommandRuntimeService by lazy {
        QqBotCommandRuntimeService(
            botPort = dependencies.botPort,
            configPort = dependencies.configPort,
            providerPort = dependencies.providerPort,
            conversationPort = dependencies.conversationPort,
            replySender = replySender,
            profileResolver = profileResolver,
            currentLanguageTag = currentLanguageTag,
            log = log,
        )
    }

    private val pluginDispatchService: QqPluginDispatchService by lazy {
        QqPluginDispatchService(
            replySender = replySender,
            profileResolver = profileResolver,
            resolvePluginPrivateRootPath = resolvePluginPrivateRootPath,
            gatewayFactory = gatewayFactory,
            dispatchEngine = dependencies.pluginV2DispatchEngine,
            failureStateStore = dependencies.failureStateStore,
            scopedFailureStateStore = dependencies.scopedFailureStateStore,
            logBus = dependencies.logBus,
            log = log,
        )
    }

    private val streamingReplyService: QqStreamingReplyService by lazy {
        QqStreamingReplyService(
            replySender = replySender,
            synthesizeSpeech = dependencies.llmProviderProbePort::synthesizeSpeech,
            log = log,
        )
    }

    fun adapter(): OneBotServerAdapter {
        return OneBotServerAdapter(
            parser = OneBotPayloadParser(),
            runtime = runtimeService(),
            log = log,
        )
    }

    fun outboundGateway(): QqOneBotOutboundGateway = outboundGateway

    fun pluginDispatchService(): QqPluginDispatchService = pluginDispatchService

    fun runtimeService(): QqMessageRuntimeService {
        return QqMessageRuntimeService(
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
            gatewayFactory = gatewayFactory,
            log = log,
        )
    }

    internal fun pluginDispatchServiceForTests(): QqPluginDispatchService = pluginDispatchService

    internal fun replySenderForTests(): QqReplySender = replySender
}
