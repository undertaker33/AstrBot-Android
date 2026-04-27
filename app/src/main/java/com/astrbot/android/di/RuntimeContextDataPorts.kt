
package com.astrbot.android.di

import com.astrbot.android.core.runtime.context.RuntimeContextDataPort
import com.astrbot.android.feature.chat.data.FeatureConversationRepository as ConversationRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository as ConfigRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository as PersonaRepository
import com.astrbot.android.feature.provider.data.FeatureProviderRepository as ProviderRepository
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository as ResourceCenterRepository
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.chat.ConversationSession

internal object ProductionRuntimeContextDataPort : RuntimeContextDataPort {
    override fun resolveConfig(configProfileId: String): ConfigProfile = ConfigRepository.resolve(configProfileId)

    override fun listProviders(): List<ProviderProfile> = ProviderRepository.providers.value

    override fun findEnabledPersona(personaId: String): PersonaProfile? {
        return PersonaRepository.personas.value.firstOrNull { it.id == personaId && it.enabled }
    }

    override fun session(sessionId: String): ConversationSession = ConversationRepository.session(sessionId)

    override fun compatibilitySnapshotForConfig(config: ConfigProfile): ResourceCenterCompatibilitySnapshot {
        return ResourceCenterRepository.compatibilitySnapshotForConfig(config)
    }
}
