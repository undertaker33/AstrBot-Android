package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class QqBotCommandRuntimeServiceFactory @Inject constructor() {
    fun create(
        dependencies: QqOneBotRuntimeDependencies,
        replySender: QqReplySender,
        profileResolver: QqRuntimeProfileResolver,
        currentLanguageTag: () -> String,
        log: (String) -> Unit,
    ): QqBotCommandRuntimeService {
        return QqBotCommandRuntimeService(
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
}

@Singleton
internal class QqStreamingReplyServiceFactory @Inject constructor() {
    fun create(
        replySender: QqReplySender,
        synthesizeSpeech: (ProviderProfile, String, String, Boolean) -> ConversationAttachment,
        log: (String) -> Unit,
    ): QqStreamingReplyService {
        return QqStreamingReplyService(
            replySender = replySender,
            synthesizeSpeech = synthesizeSpeech,
            log = log,
        )
    }
}

@Singleton
internal class QqPluginDispatchServiceFactory @Inject constructor(
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
    private val hostActionExecutor: ExternalPluginHostActionExecutor,
    private val dispatchEngine: PluginV2DispatchEngine,
    private val executionService: QqPluginExecutionService,
) {
    fun create(
        replySender: QqReplySender,
        profileResolver: QqRuntimeProfileResolver,
        resolvePluginPrivateRootPath: (String) -> String,
        log: (String) -> Unit,
    ): QqPluginDispatchService {
        return QqPluginDispatchService(
            replySender = replySender,
            profileResolver = profileResolver,
            resolvePluginPrivateRootPath = resolvePluginPrivateRootPath,
            hostCapabilityGateway = hostCapabilityGateway,
            hostActionExecutor = hostActionExecutor,
            dispatchEngine = dispatchEngine,
            executionService = executionService,
            log = log,
        )
    }
}

@Singleton
internal class QqMessageRuntimeServiceFactory @Inject constructor(
    private val configPort: ConfigRepositoryPort,
    private val conversationPort: QqConversationPort,
    private val platformConfigPort: QqPlatformConfigPort,
    private val orchestrator: RuntimeLlmOrchestratorPort,
    private val runtimeContextResolverPort: com.astrbot.android.core.runtime.context.RuntimeContextResolverPort,
    private val providerInvoker: DefaultQqProviderInvoker,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
) {
    fun create(
        replySender: QqReplySender,
        llmRuntime: AppChatLlmPipelineRuntime,
        rateLimiter: QqRateLimiter,
        markMessageId: (String) -> Boolean,
        scheduleStashReplay: (BotProfile, ConfigProfile, String) -> Unit,
        currentLanguageTag: () -> String,
        transcribeAudio: (ProviderProfile, ConversationAttachment) -> String,
        profileResolver: QqRuntimeProfileResolver,
        botCommandRuntimeService: QqBotCommandRuntimeService,
        pluginDispatchService: QqPluginDispatchService,
        streamingReplyService: QqStreamingReplyService,
        executeLegacyPluginsDuringLlmDispatch: Boolean,
        log: (String) -> Unit,
    ): QqMessageRuntimeService {
        return QqMessageRuntimeService(
            configPort = configPort,
            conversationPort = conversationPort,
            platformConfigPort = platformConfigPort,
            orchestrator = orchestrator,
            runtimeContextResolverPort = runtimeContextResolverPort,
            replySender = replySender,
            llmRuntime = llmRuntime,
            providerInvoker = providerInvoker,
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
            executeLegacyPluginsDuringLlmDispatch = executeLegacyPluginsDuringLlmDispatch,
            log = log,
        )
    }
}
