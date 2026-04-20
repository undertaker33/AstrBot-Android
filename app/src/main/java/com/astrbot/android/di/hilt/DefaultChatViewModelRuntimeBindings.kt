@file:Suppress("DEPRECATION")

package com.astrbot.android.di.hilt

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmMediaService
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.core.runtime.session.ConversationSessionLockManager
import com.astrbot.android.feature.bot.data.FeatureBotRepository as BotRepository
import com.astrbot.android.feature.chat.data.FeatureConversationRepository as ConversationRepository
import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.chat.domain.SendAppMessageUseCase
import com.astrbot.android.feature.chat.presentation.AppChatSendHandler
import com.astrbot.android.feature.chat.runtime.AppChatPluginCommandService
import com.astrbot.android.feature.chat.runtime.AppChatPreparedReplyService
import com.astrbot.android.feature.chat.runtime.AppChatProviderInvocationService
import com.astrbot.android.feature.chat.runtime.AppChatRuntimeService
import com.astrbot.android.feature.config.data.FeatureConfigRepository as ConfigRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository as PersonaRepository
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.provider.data.FeatureProviderRepository as ProviderRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import com.astrbot.android.ui.viewmodel.ChatSessionController
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.CoroutineContext

internal class DefaultChatViewModelRuntimeBindings @Inject constructor(
    private val runtimeLlmOrchestrator: RuntimeLlmOrchestratorPort,
    private val llmClientPort: LlmClientPort,
    override val runtimeContextResolverPort: RuntimeContextResolverPort,
    private val defaultAppChatPluginRuntime: AppChatPluginRuntime,
    override val conversationRepositoryPort: ConversationRepositoryPort,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
) : ChatViewModelRuntimeBindings {

    override val defaultSessionId: String = ConversationRepository.DEFAULT_SESSION_ID
    override val defaultSessionTitle: String = ConversationRepository.DEFAULT_SESSION_TITLE
    override val bots: StateFlow<List<BotProfile>> = BotRepository.botProfiles
    override val selectedBotId: StateFlow<String> = BotRepository.selectedBotId
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val sessions: StateFlow<List<ConversationSession>> = ConversationRepository.sessions
    override val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas

    override fun session(sessionId: String): ConversationSession = ConversationRepository.session(sessionId)

    override fun createSession(botId: String): ConversationSession = ConversationRepository.createSession(botId = botId)

    override fun deleteSession(sessionId: String) {
        ConversationRepository.deleteSession(sessionId)
    }

    override fun renameSession(sessionId: String, title: String) {
        ConversationRepository.renameSession(sessionId, title)
    }

    override fun toggleSessionPinned(sessionId: String) {
        ConversationRepository.toggleSessionPinned(sessionId)
    }

    override fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean?, sessionTtsEnabled: Boolean?) {
        ConversationRepository.updateSessionServiceFlags(sessionId, sessionSttEnabled, sessionTtsEnabled)
    }

    override fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) {
        ConversationRepository.updateSessionBindings(sessionId, providerId, personaId, botId)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String = ConversationRepository.appendMessage(sessionId, role, content, attachments)

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        ConversationRepository.replaceMessages(sessionId, messages)
    }

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) {
        ConversationRepository.updateMessage(sessionId, messageId, content, attachments)
    }

    override fun syncSystemSessionTitle(sessionId: String, title: String) {
        ConversationRepository.syncSystemSessionTitle(sessionId, title)
    }

    override fun resolveConfig(profileId: String): ConfigProfile = ConfigRepository.resolve(profileId)

    override fun saveConfig(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override fun saveBot(profile: BotProfile) {
        BotRepository.save(profile)
    }

    override fun saveProvider(profile: ProviderProfile) {
        ProviderRepository.save(
            id = profile.id,
            name = profile.name,
            baseUrl = profile.baseUrl,
            model = profile.model,
            providerType = profile.providerType,
            apiKey = profile.apiKey,
            capabilities = profile.capabilities,
            enabled = profile.enabled,
            multimodalRuleSupport = profile.multimodalRuleSupport,
            multimodalProbeSupport = profile.multimodalProbeSupport,
            nativeStreamingRuleSupport = profile.nativeStreamingRuleSupport,
            nativeStreamingProbeSupport = profile.nativeStreamingProbeSupport,
            sttProbeSupport = profile.sttProbeSupport,
            ttsProbeSupport = profile.ttsProbeSupport,
            ttsVoiceOptions = profile.ttsVoiceOptions,
        )
    }

    override suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String {
        return LlmMediaService.transcribeAudio(provider, attachment)
    }

    override suspend fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): String {
        return sendConfiguredChatWithTools(
            provider = provider,
            messages = messages,
            systemPrompt = systemPrompt,
            config = config,
            availableProviders = availableProviders,
            tools = emptyList(),
        ).text
    }

    override suspend fun sendConfiguredChatStream(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
        onDelta: suspend (String) -> Unit,
    ): String {
        return sendConfiguredChatStreamWithTools(
            provider = provider,
            messages = messages,
            systemPrompt = systemPrompt,
            config = config,
            availableProviders = availableProviders,
            tools = emptyList(),
            onDelta = onDelta,
            onToolCallDelta = { _, _, _ -> },
        ).text
    }

    override suspend fun sendConfiguredChatWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
    ): LlmInvocationResult {
        return llmClientPort.sendWithTools(
            LlmInvocationRequest(
                provider = provider,
                messages = messages,
                systemPrompt = systemPrompt,
                config = config,
                availableProviders = availableProviders,
                tools = tools,
            ),
        )
    }

    override suspend fun sendConfiguredChatStreamWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
        onDelta: suspend (String) -> Unit,
        onToolCallDelta: suspend (index: Int, name: String, argumentsFragment: String) -> Unit,
    ): LlmInvocationResult {
        var completed: LlmInvocationResult? = null
        var failure: Throwable? = null
        val accumulatedText = StringBuilder()
        llmClientPort.streamWithTools(
            LlmInvocationRequest(
                provider = provider,
                messages = messages,
                systemPrompt = systemPrompt,
                config = config,
                availableProviders = availableProviders,
                tools = tools,
            ),
        ).collect { event ->
            when (event) {
                is LlmStreamEvent.TextDelta -> {
                    if (event.text.isNotBlank()) {
                        accumulatedText.append(event.text)
                        onDelta(event.text)
                    }
                }
                is LlmStreamEvent.ToolCallDelta -> {
                    onToolCallDelta(event.index, event.name.orEmpty(), event.argumentsFragment)
                }
                is LlmStreamEvent.Completed -> {
                    completed = event.result
                }
                is LlmStreamEvent.Failed -> {
                    failure = event.throwable
                }
            }
        }
        failure?.let { throw it }
        return completed ?: LlmInvocationResult(text = accumulatedText.toString())
    }

    override suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return LlmMediaService.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
    }

    override suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T {
        return ConversationSessionLockManager.withLock(sessionId, block)
    }

    override fun log(message: String) {
        RuntimeLogRepository.append(message)
    }

    private fun createProviderInvocationService(
        ioDispatcher: CoroutineContext = Dispatchers.IO,
    ): AppChatProviderInvocationService {
        return AppChatProviderInvocationService(
            chatDependencies = this,
            ioDispatcher = ioDispatcher,
        )
    }

    private fun createPreparedReplyService(
        ioDispatcher: CoroutineContext = Dispatchers.IO,
    ): AppChatPreparedReplyService {
        return AppChatPreparedReplyService(
            chatDependencies = this,
            ioDispatcher = ioDispatcher,
        )
    }

    private fun createAppChatRuntimePort(
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext = Dispatchers.IO,
    ): AppChatRuntimePort {
        return AppChatRuntimeService(
            chatDependencies = this,
            appChatPluginRuntime = appChatPluginRuntime,
            llmOrchestrator = runtimeLlmOrchestrator,
            providerInvocationService = createProviderInvocationService(ioDispatcher),
            preparedReplyService = createPreparedReplyService(ioDispatcher),
            gatewayFactory = gatewayFactory,
        )
    }

    override val appChatRuntimePort: AppChatRuntimePort by lazy {
        createAppChatRuntimePort(defaultAppChatPluginRuntime)
    }

    override val sendAppMessageUseCase: SendAppMessageUseCase by lazy {
        SendAppMessageUseCase(
            conversations = conversationRepositoryPort,
            runtime = appChatRuntimePort,
        )
    }

    override val chatSessionController: ChatSessionController by lazy {
        ChatSessionController(this)
    }

    override fun createChatSendHandler(
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext,
    ): AppChatSendHandler {
        return AppChatSendHandler(
            SendAppMessageUseCase(
                conversations = conversationRepositoryPort,
                runtime = createAppChatRuntimePort(
                    appChatPluginRuntime = appChatPluginRuntime,
                    ioDispatcher = ioDispatcher,
                ),
            ),
        )
    }

    override fun createAppChatPluginCommandService(
        appChatPluginRuntime: AppChatPluginRuntime,
    ): AppChatPluginCommandService {
        return AppChatPluginCommandService(
            dependencies = this,
            appChatPluginRuntime = appChatPluginRuntime,
            gatewayFactory = gatewayFactory,
        )
    }
}
