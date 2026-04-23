package com.astrbot.android.testsupport

import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import kotlinx.coroutines.flow.StateFlow

class CompatBotRepositoryPort : BotRepositoryPort {
    override val bots: StateFlow<List<BotProfile>>
        get() = BotRepository.botProfiles

    override val selectedBotId: StateFlow<String>
        get() = BotRepository.selectedBotId

    override fun currentBot(): BotProfile = BotRepository.botProfile.value

    override fun snapshotProfiles(): List<BotProfile> = BotRepository.snapshotProfiles()

    override fun create(name: String): BotProfile = BotRepository.create(name)

    override suspend fun save(profile: BotProfile) {
        BotRepository.save(profile)
    }

    override suspend fun create(profile: BotProfile) {
        BotRepository.save(profile)
    }

    override suspend fun delete(id: String) {
        BotRepository.delete(id)
    }

    override suspend fun select(id: String) {
        BotRepository.select(id)
    }
}

class CompatConfigRepositoryPort : ConfigRepositoryPort {
    override val profiles: StateFlow<List<ConfigProfile>>
        get() = ConfigRepository.profiles

    override val selectedProfileId: StateFlow<String>
        get() = ConfigRepository.selectedProfileId

    override fun snapshotProfiles(): List<ConfigProfile> = ConfigRepository.snapshotProfiles()

    override fun create(name: String): ConfigProfile = ConfigRepository.create(name)

    override fun resolve(id: String): ConfigProfile = ConfigRepository.resolve(id)

    override fun resolveExistingId(id: String?): String = ConfigRepository.resolveExistingId(id)

    override suspend fun save(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override suspend fun delete(id: String) {
        ConfigRepository.delete(id)
    }

    override suspend fun select(id: String) {
        ConfigRepository.select(id)
    }
}

class CompatPersonaRepositoryPort : PersonaRepositoryPort {
    override val personas: StateFlow<List<PersonaProfile>>
        get() = PersonaRepository.personas

    override fun snapshotProfiles(): List<PersonaProfile> = PersonaRepository.snapshotProfiles()

    override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot> =
        PersonaRepository.snapshotProfiles().map { persona ->
            PersonaToolEnablementSnapshot(
                personaId = persona.id,
                enabled = persona.enabled,
                enabledTools = persona.enabledTools.toSet(),
            )
        }

    override fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? =
        PersonaRepository.snapshotToolEnablement(personaId)

    override suspend fun add(profile: PersonaProfile) {
        PersonaRepository.add(
            name = profile.name,
            tag = profile.tag,
            systemPrompt = profile.systemPrompt,
            enabledTools = profile.enabledTools,
            defaultProviderId = profile.defaultProviderId,
            maxContextMessages = profile.maxContextMessages,
        )
    }

    override suspend fun update(profile: PersonaProfile) {
        PersonaRepository.update(profile)
    }

    override suspend fun toggleEnabled(id: String, enabled: Boolean) {
        val current = PersonaRepository.snapshotToolEnablement(id)?.enabled ?: return
        if (current != enabled) {
            PersonaRepository.toggleEnabled(id)
        }
    }

    override suspend fun toggleEnabled(id: String) {
        PersonaRepository.toggleEnabled(id)
    }

    override suspend fun delete(id: String) {
        PersonaRepository.delete(id)
    }
}

class CompatProviderRepositoryPort : ProviderRepositoryPort {
    override val providers: StateFlow<List<ProviderProfile>>
        get() = ProviderRepository.providers

    override fun snapshotProfiles(): List<ProviderProfile> = ProviderRepository.snapshotProfiles()

    override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> =
        ProviderRepository.providers.value.filter { capability in it.capabilities }

    override fun toggleEnabled(id: String) {
        ProviderRepository.toggleEnabled(id)
    }

    override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) {
        ProviderRepository.updateMultimodalProbeSupport(id, support)
    }

    override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) {
        ProviderRepository.updateNativeStreamingProbeSupport(id, support)
    }

    override fun updateSttProbeSupport(id: String, support: FeatureSupportState) {
        ProviderRepository.updateSttProbeSupport(id, support)
    }

    override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) {
        ProviderRepository.updateTtsProbeSupport(id, support)
    }

    override suspend fun save(profile: ProviderProfile) {
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

    override suspend fun delete(id: String) {
        ProviderRepository.delete(id)
    }
}

class CompatQqConversationPort : QqConversationPort {
    override fun sessions(): List<ConversationSession> = ConversationRepository.sessions.value

    override fun resolveOrCreateSession(
        sessionId: String,
        title: String,
        messageType: MessageType,
    ): ConversationSession {
        val existing = ConversationRepository.session(sessionId)
        if (existing.title != title || !existing.titleCustomized) {
            ConversationRepository.syncSystemSessionTitle(sessionId, title)
        }
        return ConversationRepository.session(sessionId)
    }

    override fun session(sessionId: String): ConversationSession = ConversationRepository.session(sessionId)

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String = ConversationRepository.appendMessage(
        sessionId = sessionId,
        role = role,
        content = content,
        attachments = attachments,
    )

    override fun updateSessionBindings(
        sessionId: String,
        botId: String,
        providerId: String,
        personaId: String,
    ) {
        ConversationRepository.updateSessionBindings(
            sessionId = sessionId,
            providerId = providerId,
            personaId = personaId,
            botId = botId,
        )
    }

    override fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean?,
        sessionTtsEnabled: Boolean?,
    ) {
        ConversationRepository.updateSessionServiceFlags(
            sessionId = sessionId,
            sessionSttEnabled = sessionSttEnabled,
            sessionTtsEnabled = sessionTtsEnabled,
        )
    }

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        ConversationRepository.replaceMessages(sessionId, messages)
    }

    override fun renameSession(sessionId: String, title: String) {
        ConversationRepository.renameSession(sessionId, title)
    }

    override fun deleteSession(sessionId: String) {
        ConversationRepository.deleteSession(sessionId)
    }
}

class CompatQqPlatformConfigPort : QqPlatformConfigPort {
    override fun resolveQqBot(selfId: String): BotProfile? = BotRepository.resolveBoundBot(selfId)

    override fun qqReplyQuoteEnabled(botId: String): Boolean =
        resolveConfigForBot(botId)?.quoteSenderMessageEnabled == true

    override fun qqReplyMentionEnabled(botId: String): Boolean =
        resolveConfigForBot(botId)?.mentionSenderEnabled == true

    override fun qqAutoReplyEnabled(botId: String): Boolean {
        val bot = BotRepository.botProfiles.value.firstOrNull { it.id == botId }
        return bot?.autoReplyEnabled == true
    }

    override fun qqWakeWords(botId: String): List<String> {
        val bot = BotRepository.botProfiles.value.firstOrNull { it.id == botId } ?: return emptyList()
        val config = resolveConfigForBot(botId)
        return ((bot.triggerWords) + (config?.wakeWords ?: emptyList())).distinct()
    }

    override fun qqWhitelist(botId: String, messageType: MessageType): List<String> =
        resolveConfigForBot(botId)?.whitelistEntries ?: emptyList()

    override fun qqWhitelistEnabled(botId: String): Boolean =
        resolveConfigForBot(botId)?.whitelistEnabled == true

    override fun qqRateLimitWindow(botId: String): Int =
        resolveConfigForBot(botId)?.rateLimitWindowSeconds ?: 0

    override fun qqRateLimitMaxCount(botId: String): Int =
        resolveConfigForBot(botId)?.rateLimitMaxCount ?: 0

    override fun qqRateLimitStrategy(botId: String): String =
        resolveConfigForBot(botId)?.rateLimitStrategy.orEmpty()

    override fun qqIsolateGroupUser(botId: String): Boolean =
        resolveConfigForBot(botId)?.sessionIsolationEnabled == true

    private fun resolveConfigForBot(botId: String): ConfigProfile? {
        val bot = BotRepository.botProfiles.value.firstOrNull { it.id == botId } ?: return null
        return ConfigRepository.resolve(bot.configProfileId)
    }
}
