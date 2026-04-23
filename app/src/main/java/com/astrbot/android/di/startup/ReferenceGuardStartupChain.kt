package com.astrbot.android.di.startup

import com.astrbot.android.core.common.profile.PersonaReferenceGuard
import com.astrbot.android.core.common.profile.ProviderReferenceGuard
import com.astrbot.android.feature.bot.data.FeatureBotRepository as BotRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository as ConfigRepository
import javax.inject.Inject

internal class ReferenceGuardStartupChain @Inject constructor() : AppStartupChain {

    override fun run() {
        PersonaReferenceGuard.register { personaId ->
            BotRepository.botProfiles.value.any { it.defaultPersonaId == personaId }
        }
        ProviderReferenceGuard.register { providerId ->
            ConfigRepository.profiles.value.any { config ->
                config.defaultChatProviderId == providerId ||
                    config.defaultVisionProviderId == providerId ||
                    config.defaultSttProviderId == providerId ||
                    config.defaultTtsProviderId == providerId
            } || BotRepository.botProfiles.value.any { it.defaultProviderId == providerId }
        }
    }
}
