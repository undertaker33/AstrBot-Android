package com.astrbot.android.di

import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.data.NapCatLoginRepository
import com.astrbot.android.data.NapCatLoginService
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.data.SherpaOnnxBridge
import com.astrbot.android.data.TtsVoiceAssetRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.RuntimeAssetState
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.runtime.ConversationSessionLockManager
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.StateFlow

interface BridgeViewModelDependencies {
    val config: StateFlow<NapCatBridgeConfig>
    val runtimeState: StateFlow<NapCatRuntimeState>

    fun saveConfig(config: NapCatBridgeConfig)
}

object DefaultBridgeViewModelDependencies : BridgeViewModelDependencies {
    override val config: StateFlow<NapCatBridgeConfig> = NapCatBridgeRepository.config
    override val runtimeState: StateFlow<NapCatRuntimeState> = NapCatBridgeRepository.runtimeState

    override fun saveConfig(config: NapCatBridgeConfig) {
        NapCatBridgeRepository.updateConfig(config)
    }
}

interface BotViewModelDependencies {
    val botProfile: StateFlow<BotProfile>
    val botProfiles: StateFlow<List<BotProfile>>
    val selectedBotId: StateFlow<String>
    val providers: StateFlow<List<ProviderProfile>>
    val personas: StateFlow<List<PersonaProfile>>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val loginState: StateFlow<NapCatLoginState>

    fun select(botId: String)

    fun save(profile: BotProfile)

    fun saveConfig(profile: ConfigProfile)

    fun create()

    fun delete(botId: String)

    fun resolveConfig(profileId: String): ConfigProfile
}

object DefaultBotViewModelDependencies : BotViewModelDependencies {
    override val botProfile: StateFlow<BotProfile> = BotRepository.botProfile
    override val botProfiles: StateFlow<List<BotProfile>> = BotRepository.botProfiles
    override val selectedBotId: StateFlow<String> = BotRepository.selectedBotId
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val loginState: StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    override fun select(botId: String) {
        BotRepository.select(botId)
    }

    override fun save(profile: BotProfile) {
        BotRepository.save(profile)
    }

    override fun saveConfig(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override fun create() {
        BotRepository.create()
    }

    override fun delete(botId: String) {
        BotRepository.delete(botId)
    }

    override fun resolveConfig(profileId: String): ConfigProfile {
        return ConfigRepository.resolve(profileId)
    }
}

interface ProviderViewModelDependencies {
    val providers: StateFlow<List<ProviderProfile>>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val selectedConfigProfileId: StateFlow<String>

    fun save(profile: ProviderProfile)

    fun saveConfig(profile: ConfigProfile)

    fun toggleEnabled(id: String)

    fun delete(id: String)

    fun updateMultimodalProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState)

    fun updateNativeStreamingProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState)

    fun updateSttProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState)

    fun updateTtsProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState)

    fun fetchModels(provider: ProviderProfile): List<String>

    fun detectMultimodalRule(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun probeMultimodalSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun detectNativeStreamingRule(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun probeNativeStreamingSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun probeSttSupport(provider: ProviderProfile): ChatCompletionService.SttProbeResult

    fun probeTtsSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>>

    fun ttsAssetState(context: android.content.Context): com.astrbot.android.data.SherpaOnnxAssetManager.TtsAssetState

    fun isSherpaFrameworkReady(): Boolean

    fun isSherpaSttReady(): Boolean

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment
}

object DefaultProviderViewModelDependencies : ProviderViewModelDependencies {
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val selectedConfigProfileId: StateFlow<String> = ConfigRepository.selectedProfileId

    override fun save(profile: ProviderProfile) {
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

    override fun saveConfig(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override fun toggleEnabled(id: String) {
        ProviderRepository.toggleEnabled(id)
    }

    override fun delete(id: String) {
        ProviderRepository.delete(id)
    }

    override fun updateMultimodalProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) {
        ProviderRepository.updateMultimodalProbeSupport(id, support)
    }

    override fun updateNativeStreamingProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) {
        ProviderRepository.updateNativeStreamingProbeSupport(id, support)
    }

    override fun updateSttProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) {
        ProviderRepository.updateSttProbeSupport(id, support)
    }

    override fun updateTtsProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) {
        ProviderRepository.updateTtsProbeSupport(id, support)
    }

    override fun fetchModels(provider: ProviderProfile): List<String> {
        return ChatCompletionService.fetchModels(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            providerType = provider.providerType,
        )
    }

    override fun detectMultimodalRule(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.detectMultimodalRule(provider)
    }

    override fun probeMultimodalSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.probeMultimodalSupport(provider)
    }

    override fun detectNativeStreamingRule(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.detectNativeStreamingRule(provider)
    }

    override fun probeNativeStreamingSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.probeNativeStreamingSupport(provider)
    }

    override fun probeSttSupport(provider: ProviderProfile): ChatCompletionService.SttProbeResult {
        return ChatCompletionService.probeSttSupport(provider)
    }

    override fun probeTtsSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.probeTtsSupport(provider)
    }

    override fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        return TtsVoiceAssetRepository.listVoiceChoicesFor(provider)
    }

    override fun ttsAssetState(context: android.content.Context): com.astrbot.android.data.SherpaOnnxAssetManager.TtsAssetState {
        return RuntimeAssetRepository.ttsAssetState(context)
    }

    override fun isSherpaFrameworkReady(): Boolean {
        return SherpaOnnxBridge.isFrameworkReady()
    }

    override fun isSherpaSttReady(): Boolean {
        return SherpaOnnxBridge.isSttReady()
    }

    override fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return ChatCompletionService.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
    }
}

interface ConfigViewModelDependencies {
    val configProfiles: StateFlow<List<ConfigProfile>>
    val selectedConfigProfileId: StateFlow<String>
    val providers: StateFlow<List<ProviderProfile>>
    val bots: StateFlow<List<BotProfile>>
    val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>>

    fun select(profileId: String)

    fun save(profile: ConfigProfile)

    fun create(): ConfigProfile

    fun delete(profileId: String): String

    fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String)

    fun resolve(profileId: String): ConfigProfile
}

object DefaultConfigViewModelDependencies : ConfigViewModelDependencies {
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val selectedConfigProfileId: StateFlow<String> = ConfigRepository.selectedProfileId
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val bots: StateFlow<List<BotProfile>> = BotRepository.botProfiles
    override val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = TtsVoiceAssetRepository.assets

    override fun select(profileId: String) {
        ConfigRepository.select(profileId)
    }

    override fun save(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override fun create(): ConfigProfile {
        return ConfigRepository.create()
    }

    override fun delete(profileId: String): String {
        return ConfigRepository.delete(profileId)
    }

    override fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) {
        BotRepository.replaceConfigBinding(deletedConfigId, fallbackConfigId)
    }

    override fun resolve(profileId: String): ConfigProfile {
        return ConfigRepository.resolve(profileId)
    }
}

interface ConversationViewModelDependencies {
    val defaultSessionId: String
    val sessions: StateFlow<List<ConversationSession>>

    fun contextPreview(sessionId: String): String

    fun session(sessionId: String): ConversationSession

    fun appendMessage(sessionId: String, role: String, content: String)

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>)
}

object DefaultConversationViewModelDependencies : ConversationViewModelDependencies {
    override val defaultSessionId: String = ConversationRepository.DEFAULT_SESSION_ID
    override val sessions: StateFlow<List<ConversationSession>> = ConversationRepository.sessions

    override fun contextPreview(sessionId: String): String {
        return ConversationRepository.buildContextPreview(sessionId)
    }

    override fun session(sessionId: String): ConversationSession {
        return ConversationRepository.session(sessionId)
    }

    override fun appendMessage(sessionId: String, role: String, content: String) {
        ConversationRepository.appendMessage(sessionId, role, content)
    }

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        ConversationRepository.replaceMessages(sessionId, messages)
    }
}

interface PersonaViewModelDependencies {
    val personas: StateFlow<List<PersonaProfile>>

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    )

    fun update(profile: PersonaProfile)

    fun toggleEnabled(id: String)

    fun delete(id: String)
}

object DefaultPersonaViewModelDependencies : PersonaViewModelDependencies {
    override val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas

    override fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        PersonaRepository.add(name, tag, systemPrompt, enabledTools, defaultProviderId, maxContextMessages)
    }

    override fun update(profile: PersonaProfile) {
        PersonaRepository.update(profile)
    }

    override fun toggleEnabled(id: String) {
        PersonaRepository.toggleEnabled(id)
    }

    override fun delete(id: String) {
        PersonaRepository.delete(id)
    }
}

interface QQLoginViewModelDependencies {
    val loginState: StateFlow<NapCatLoginState>

    suspend fun refresh(manual: Boolean = false)

    suspend fun refreshQrCode()

    suspend fun quickLoginSavedAccount(uin: String? = null)

    suspend fun saveQuickLoginAccount(uin: String)

    suspend fun logoutCurrentAccount()

    suspend fun passwordLogin(uin: String, password: String)

    suspend fun captchaLogin(uin: String, password: String, ticket: String, randstr: String, sid: String)

    suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?)

    suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult

    suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult

    fun log(message: String)
}

object DefaultQQLoginViewModelDependencies : QQLoginViewModelDependencies {
    override val loginState: StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    override suspend fun refresh(manual: Boolean) {
        NapCatLoginRepository.refresh(manual)
    }

    override suspend fun refreshQrCode() {
        NapCatLoginRepository.refreshQrCode()
    }

    override suspend fun quickLoginSavedAccount(uin: String?) {
        NapCatLoginRepository.quickLoginSavedAccount(uin)
    }

    override suspend fun saveQuickLoginAccount(uin: String) {
        NapCatLoginRepository.saveQuickLoginAccount(uin)
    }

    override suspend fun logoutCurrentAccount() {
        NapCatLoginRepository.logoutCurrentAccount()
    }

    override suspend fun passwordLogin(uin: String, password: String) {
        NapCatLoginRepository.passwordLogin(uin, password)
    }

    override suspend fun captchaLogin(uin: String, password: String, ticket: String, randstr: String, sid: String) {
        NapCatLoginRepository.captchaLogin(uin, password, ticket, randstr, sid)
    }

    override suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?) {
        NapCatLoginRepository.newDeviceLogin(uin, password, verifiedToken)
    }

    override suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult {
        return NapCatLoginRepository.getNewDeviceQRCode()
    }

    override suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult {
        return NapCatLoginRepository.pollNewDeviceQRCode(bytesToken)
    }

    override fun log(message: String) {
        RuntimeLogRepository.append(message)
    }
}

interface RuntimeAssetViewModelDependencies {
    val state: StateFlow<RuntimeAssetState>

    fun refresh()

    suspend fun downloadAsset(assetId: String)

    suspend fun clearAsset(assetId: String)

    suspend fun downloadOnDeviceTtsModel(modelId: String)

    suspend fun clearOnDeviceTtsModel(modelId: String)
}

class DefaultRuntimeAssetViewModelDependencies(
    private val appContext: android.content.Context,
) : RuntimeAssetViewModelDependencies {
    override val state: StateFlow<RuntimeAssetState> = RuntimeAssetRepository.state

    override fun refresh() {
        RuntimeAssetRepository.refresh(appContext)
    }

    override suspend fun downloadAsset(assetId: String) {
        RuntimeAssetRepository.downloadAsset(appContext, assetId)
    }

    override suspend fun clearAsset(assetId: String) {
        RuntimeAssetRepository.clearAsset(appContext, assetId)
    }

    override suspend fun downloadOnDeviceTtsModel(modelId: String) {
        RuntimeAssetRepository.downloadOnDeviceTtsModel(appContext, modelId)
    }

    override suspend fun clearOnDeviceTtsModel(modelId: String) {
        RuntimeAssetRepository.clearOnDeviceTtsModel(appContext, modelId)
    }
}

interface ChatViewModelDependencies {
    val defaultSessionId: String
    val defaultSessionTitle: String
    val bots: StateFlow<List<BotProfile>>
    val selectedBotId: StateFlow<String>
    val providers: StateFlow<List<ProviderProfile>>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val sessions: StateFlow<List<ConversationSession>>
    val personas: StateFlow<List<PersonaProfile>>

    fun session(sessionId: String): ConversationSession

    fun createSession(botId: String): ConversationSession

    fun deleteSession(sessionId: String)

    fun renameSession(sessionId: String, title: String)

    fun toggleSessionPinned(sessionId: String)

    fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean? = null, sessionTtsEnabled: Boolean? = null)

    fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String)

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>)

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    )

    fun syncSystemSessionTitle(sessionId: String, title: String)

    fun resolveConfig(profileId: String): ConfigProfile

    fun saveConfig(profile: ConfigProfile)

    fun saveBot(profile: BotProfile)

    fun saveProvider(profile: ProviderProfile)

    suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String

    suspend fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): String

    suspend fun sendConfiguredChatStream(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
        onDelta: suspend (String) -> Unit,
    ): String

    suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment

    suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T

    fun log(message: String)
}

object DefaultChatViewModelDependencies : ChatViewModelDependencies {
    override val defaultSessionId: String = ConversationRepository.DEFAULT_SESSION_ID
    override val defaultSessionTitle: String = ConversationRepository.DEFAULT_SESSION_TITLE
    override val bots: StateFlow<List<BotProfile>> = BotRepository.botProfiles
    override val selectedBotId: StateFlow<String> = BotRepository.selectedBotId
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val sessions: StateFlow<List<ConversationSession>> = ConversationRepository.sessions
    override val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas

    override fun session(sessionId: String): ConversationSession {
        return ConversationRepository.session(sessionId)
    }

    override fun createSession(botId: String): ConversationSession {
        return ConversationRepository.createSession(botId = botId)
    }

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
    ): String {
        return ConversationRepository.appendMessage(sessionId, role, content, attachments)
    }

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

    override fun resolveConfig(profileId: String): ConfigProfile {
        return ConfigRepository.resolve(profileId)
    }

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
        return ChatCompletionService.transcribeAudio(provider, attachment)
    }

    override suspend fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): String {
        return ChatCompletionService.sendConfiguredChat(provider, messages, systemPrompt, config, availableProviders)
    }

    override suspend fun sendConfiguredChatStream(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
        onDelta: suspend (String) -> Unit,
    ): String {
        return ChatCompletionService.sendConfiguredChatStream(
            provider = provider,
            messages = messages,
            systemPrompt = systemPrompt,
            config = config,
            availableProviders = availableProviders,
            onDelta = onDelta,
        )
    }

    override suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return ChatCompletionService.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
    }

    override suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T {
        return ConversationSessionLockManager.withLock(sessionId, block)
    }

    override fun log(message: String) {
        RuntimeLogRepository.append(message)
    }
}

interface MainActivityDependencies {
    val autoStartEnabled: Boolean
    val runtimeState: NapCatRuntimeState

    fun log(message: String)

    fun startBridge(context: android.content.Context)
}

object DefaultMainActivityDependencies : MainActivityDependencies {
    override val autoStartEnabled: Boolean
        get() = NapCatBridgeRepository.config.value.autoStart
    override val runtimeState: NapCatRuntimeState
        get() = NapCatBridgeRepository.runtimeState.value

    override fun log(message: String) {
        RuntimeLogRepository.append(message)
    }

    override fun startBridge(context: android.content.Context) {
        ContainerBridgeController.start(context)
    }
}
