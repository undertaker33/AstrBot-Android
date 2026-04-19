package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile

internal class QqRuntimeProfileResolver(
    private val botPort: BotRepositoryPort,
    private val configPort: ConfigRepositoryPort,
    private val personaPort: PersonaRepositoryPort,
    private val providerPort: ProviderRepositoryPort,
) {
    fun availableBots(): List<BotProfile> = botPort.snapshotProfiles()

    fun availableChatProviders(): List<ProviderProfile> {
        return providerPort.snapshotProfiles().filter { provider ->
            provider.enabled && ProviderCapability.CHAT in provider.capabilities
        }
    }

    fun availablePersonas(): List<PersonaProfile> {
        return personaPort.snapshotProfiles().filter(PersonaProfile::enabled)
    }

    fun resolveProvider(
        bot: BotProfile,
        preferredProviderId: String = "",
    ): ProviderProfile? {
        val providers = availableChatProviders()
        val config = configPort.resolve(bot.configProfileId)
        val preferredIds = listOf(
            preferredProviderId,
            bot.defaultProviderId,
            config.defaultChatProviderId,
        ).filter(String::isNotBlank)
        return preferredIds.firstNotNullOfOrNull { preferredId ->
            providers.firstOrNull { provider -> provider.id == preferredId }
        } ?: providers.firstOrNull()
    }

    fun resolveSttProvider(providerId: String): ProviderProfile? {
        return providerPort.snapshotProfiles().firstOrNull { provider ->
            provider.id == providerId &&
                provider.enabled &&
                ProviderCapability.STT in provider.capabilities
        }
    }

    fun resolveTtsProvider(providerId: String): ProviderProfile? {
        return providerPort.snapshotProfiles().firstOrNull { provider ->
            provider.id == providerId &&
                provider.enabled &&
                ProviderCapability.TTS in provider.capabilities
        }
    }

    fun resolvePersona(
        bot: BotProfile,
        sessionPersonaId: String?,
    ): PersonaProfile? {
        val personas = availablePersonas()
        return personas.firstOrNull { persona ->
            persona.id == sessionPersonaId && !sessionPersonaId.isNullOrBlank()
        } ?: personas.firstOrNull { persona ->
            persona.id == bot.defaultPersonaId
        } ?: personas.firstOrNull()
    }
}
