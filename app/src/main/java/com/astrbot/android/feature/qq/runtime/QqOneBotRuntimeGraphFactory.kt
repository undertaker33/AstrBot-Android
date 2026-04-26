@file:Suppress("UNUSED_PARAMETER")

package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal interface QqRuntimeGraphFactory {
    fun create(
        dependencies: QqOneBotRuntimeDependencies,
        transport: OneBotReverseWebSocketTransport,
        appChatPluginRuntime: AppChatLlmPipelineRuntime,
        replyOverrideProvider: () -> ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)?,
        filesDirProvider: () -> File?,
        rateLimiter: QqRateLimiter,
        markMessageId: (String) -> Boolean,
        scheduleStashReplay: (com.astrbot.android.model.BotProfile, ConfigProfile, String) -> Unit,
        currentLanguageTag: () -> String,
        transcribeAudio: (ProviderProfile, ConversationAttachment) -> String,
        resolvePluginPrivateRootPath: (String) -> String,
        log: (String) -> Unit,
    ): QqOneBotRuntimeGraph
}

@Singleton
internal class HiltQqRuntimeGraphFactory @Inject constructor(
    private val pluginDispatchServiceFactory: QqPluginDispatchServiceFactory,
    private val messageRuntimeServiceFactory: QqMessageRuntimeServiceFactory,
    private val botCommandRuntimeServiceFactory: QqBotCommandRuntimeServiceFactory,
    private val streamingReplyServiceFactory: QqStreamingReplyServiceFactory,
) : QqRuntimeGraphFactory {
    override fun create(
        dependencies: QqOneBotRuntimeDependencies,
        transport: OneBotReverseWebSocketTransport,
        appChatPluginRuntime: AppChatLlmPipelineRuntime,
        replyOverrideProvider: () -> ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)?,
        filesDirProvider: () -> File?,
        rateLimiter: QqRateLimiter,
        markMessageId: (String) -> Boolean,
        scheduleStashReplay: (com.astrbot.android.model.BotProfile, ConfigProfile, String) -> Unit,
        currentLanguageTag: () -> String,
        transcribeAudio: (ProviderProfile, ConversationAttachment) -> String,
        resolvePluginPrivateRootPath: (String) -> String,
        log: (String) -> Unit,
    ): QqOneBotRuntimeGraph {
        return assembleQqRuntimeGraph(
            dependencies = dependencies,
            transport = transport,
            appChatPluginRuntime = appChatPluginRuntime,
            replyOverrideProvider = replyOverrideProvider,
            filesDirProvider = filesDirProvider,
            rateLimiter = rateLimiter,
            markMessageId = markMessageId,
            scheduleStashReplay = scheduleStashReplay,
            currentLanguageTag = currentLanguageTag,
            transcribeAudio = transcribeAudio,
            resolvePluginPrivateRootPath = resolvePluginPrivateRootPath,
            log = log,
            pluginDispatchServiceBuilder = { replySender, profileResolver, privateRootPathResolver ->
                pluginDispatchServiceFactory.create(
                    replySender = replySender,
                    profileResolver = profileResolver,
                    resolvePluginPrivateRootPath = privateRootPathResolver,
                    log = log,
                )
            },
            botCommandRuntimeServiceBuilder = { replySender, profileResolver ->
                botCommandRuntimeServiceFactory.create(
                    dependencies = dependencies,
                    replySender = replySender,
                    profileResolver = profileResolver,
                    currentLanguageTag = currentLanguageTag,
                    log = log,
                )
            },
            streamingReplyServiceBuilder = { replySender ->
                streamingReplyServiceFactory.create(
                    replySender = replySender,
                    synthesizeSpeech = dependencies.llmProviderProbePort::synthesizeSpeech,
                    log = log,
                )
            },
            messageRuntimeServiceBuilder = { replySender, profileResolver, botCommandRuntimeService, pluginDispatchService, streamingReplyService ->
                messageRuntimeServiceFactory.create(
                    replySender = replySender,
                    llmRuntime = appChatPluginRuntime,
                    rateLimiter = rateLimiter,
                    markMessageId = markMessageId,
                    scheduleStashReplay = scheduleStashReplay,
                    currentLanguageTag = currentLanguageTag,
                    transcribeAudio = transcribeAudio,
                    profileResolver = profileResolver,
                    botCommandRuntimeService = botCommandRuntimeService,
                    pluginDispatchService = pluginDispatchService,
                    streamingReplyService = streamingReplyService,
                    executeLegacyPluginsDuringLlmDispatch = dependencies.executeLegacyPluginsDuringLlmDispatch,
                    log = log,
                )
            },
        )
    }
}

private fun assembleQqRuntimeGraph(
    dependencies: QqOneBotRuntimeDependencies,
    transport: OneBotReverseWebSocketTransport,
    appChatPluginRuntime: AppChatLlmPipelineRuntime,
    replyOverrideProvider: () -> ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)?,
    filesDirProvider: () -> File?,
    rateLimiter: QqRateLimiter,
    markMessageId: (String) -> Boolean,
    scheduleStashReplay: (com.astrbot.android.model.BotProfile, ConfigProfile, String) -> Unit,
    currentLanguageTag: () -> String,
    transcribeAudio: (ProviderProfile, ConversationAttachment) -> String,
    resolvePluginPrivateRootPath: (String) -> String,
    log: (String) -> Unit,
    pluginDispatchServiceBuilder: (
        replySender: QqReplySender,
        profileResolver: QqRuntimeProfileResolver,
        resolvePluginPrivateRootPath: (String) -> String,
    ) -> QqPluginDispatchService,
    botCommandRuntimeServiceBuilder: (
        replySender: QqReplySender,
        profileResolver: QqRuntimeProfileResolver,
    ) -> QqBotCommandRuntimeService,
    streamingReplyServiceBuilder: (
        replySender: QqReplySender,
    ) -> QqStreamingReplyService,
    messageRuntimeServiceBuilder: (
        replySender: QqReplySender,
        profileResolver: QqRuntimeProfileResolver,
        botCommandRuntimeService: QqBotCommandRuntimeService,
        pluginDispatchService: QqPluginDispatchService,
        streamingReplyService: QqStreamingReplyService,
    ) -> QqMessageRuntimeService,
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
    val pluginDispatchService = pluginDispatchServiceBuilder(
        replySender,
        profileResolver,
        resolvePluginPrivateRootPath,
    )
    val botCommandRuntimeService = botCommandRuntimeServiceBuilder(replySender, profileResolver)
    val streamingReplyService = streamingReplyServiceBuilder(replySender)
    val runtimeService = messageRuntimeServiceBuilder(
        replySender,
        profileResolver,
        botCommandRuntimeService,
        pluginDispatchService,
        streamingReplyService,
    )
    return QqOneBotRuntimeGraph(
        outboundGateway = outboundGateway,
        pluginDispatchService = pluginDispatchService,
        runtimeService = runtimeService,
        replySender = replySender,
        log = log,
    )
}
