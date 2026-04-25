package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class QqRuntimeProfileResolverTest {
    @Test
    fun config_default_provider_wins_over_stale_session_and_bot_default_binding() {
        val resolver = resolver(
            bot = bot(defaultProviderId = "deepseek-chat"),
            config = ConfigProfile(id = "config-1", defaultChatProviderId = "qwen-chat"),
            providers = listOf(
                provider("deepseek-chat"),
                provider("qwen-chat"),
            ),
        )

        val resolved = resolver.resolveProvider(
            bot = bot(defaultProviderId = "deepseek-chat"),
            preferredProviderId = "deepseek-chat",
        )

        assertEquals("qwen-chat", resolved?.id)
    }

    @Test
    fun config_default_provider_wins_over_stale_session_even_when_bot_already_updated() {
        val bot = bot(defaultProviderId = "qwen-chat")
        val resolver = resolver(
            bot = bot,
            config = ConfigProfile(id = "config-1", defaultChatProviderId = "qwen-chat"),
            providers = listOf(
                provider("deepseek-chat"),
                provider("qwen-chat"),
            ),
        )

        val resolved = resolver.resolveProvider(
            bot = bot,
            preferredProviderId = "deepseek-chat",
        )

        assertEquals("qwen-chat", resolved?.id)
    }

    private fun resolver(
        bot: BotProfile,
        config: ConfigProfile,
        providers: List<ProviderProfile>,
    ) = QqRuntimeProfileResolver(
        botPort = FakeBotPort(bot),
        configPort = FakeConfigPort(config),
        personaPort = FakePersonaPort(),
        providerPort = FakeProviderPort(providers),
    )

    private fun bot(defaultProviderId: String) = BotProfile(
        id = "bot-1",
        displayName = "Bot",
        configProfileId = "config-1",
        defaultProviderId = defaultProviderId,
    )

    private fun provider(id: String) = ProviderProfile(
        id = id,
        name = id,
        baseUrl = "",
        model = id,
        providerType = ProviderType.OPENAI_COMPATIBLE,
        apiKey = "key",
        capabilities = setOf(ProviderCapability.CHAT),
    )

    private class FakeBotPort(
        private val bot: BotProfile,
    ) : BotRepositoryPort {
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(listOf(bot))
        override val selectedBotId: StateFlow<String> = MutableStateFlow(bot.id)
        override fun currentBot(): BotProfile = bot
        override fun snapshotProfiles(): List<BotProfile> = listOf(bot)
        override fun create(name: String): BotProfile = bot
        override suspend fun save(profile: BotProfile) = Unit
        override suspend fun create(profile: BotProfile) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun select(id: String) = Unit
    }

    private class FakeConfigPort(
        private val config: ConfigProfile,
    ) : ConfigRepositoryPort {
        override val profiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(listOf(config))
        override val selectedProfileId: StateFlow<String> = MutableStateFlow(config.id)
        override fun snapshotProfiles(): List<ConfigProfile> = listOf(config)
        override fun create(name: String): ConfigProfile = config
        override fun resolve(id: String): ConfigProfile = config
        override fun resolveExistingId(id: String?): String = config.id
        override suspend fun save(profile: ConfigProfile) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun select(id: String) = Unit
    }

    private class FakePersonaPort : PersonaRepositoryPort {
        override val personas: StateFlow<List<PersonaProfile>> = MutableStateFlow(emptyList())
        override fun snapshotProfiles(): List<PersonaProfile> = emptyList()
        override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot> = emptyList()
        override fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? = null
        override suspend fun add(profile: PersonaProfile) = Unit
        override suspend fun update(profile: PersonaProfile) = Unit
        override suspend fun toggleEnabled(id: String, enabled: Boolean) = Unit
        override suspend fun toggleEnabled(id: String) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private class FakeProviderPort(
        private val profiles: List<ProviderProfile>,
    ) : ProviderRepositoryPort {
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(profiles)
        override fun snapshotProfiles(): List<ProviderProfile> = profiles
        override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> {
            return profiles.filter { capability in it.capabilities }
        }

        override fun toggleEnabled(id: String) = Unit
        override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateSttProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) = Unit
        override suspend fun save(profile: ProviderProfile) = Unit
        override suspend fun delete(id: String) = Unit
    }
}
