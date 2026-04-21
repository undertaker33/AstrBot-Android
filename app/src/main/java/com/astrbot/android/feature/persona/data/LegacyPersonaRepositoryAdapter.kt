package com.astrbot.android.feature.persona.data

import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import kotlinx.coroutines.flow.StateFlow

@Suppress("DEPRECATION")
open class FeaturePersonaRepositoryPortAdapter : PersonaRepositoryPort {

    override val personas: StateFlow<List<PersonaProfile>>
        get() = FeaturePersonaRepository.personas

    override fun snapshotProfiles(): List<PersonaProfile> =
        FeaturePersonaRepository.snapshotProfiles()

    override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot> =
        FeaturePersonaRepository.snapshotProfiles().map { persona ->
            PersonaToolEnablementSnapshot(
                personaId = persona.id,
                enabled = persona.enabled,
                enabledTools = persona.enabledTools.toSet(),
            )
        }

    override fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? =
        FeaturePersonaRepository.snapshotToolEnablement(personaId)

    override suspend fun add(profile: PersonaProfile) {
        FeaturePersonaRepository.add(
            name = profile.name,
            tag = profile.tag,
            systemPrompt = profile.systemPrompt,
            enabledTools = profile.enabledTools,
            defaultProviderId = profile.defaultProviderId,
            maxContextMessages = profile.maxContextMessages,
        )
    }

    override suspend fun update(profile: PersonaProfile) {
        FeaturePersonaRepository.update(profile)
    }

    override suspend fun toggleEnabled(id: String, enabled: Boolean) {
        val current = FeaturePersonaRepository.snapshotToolEnablement(id)?.enabled ?: return
        if (current != enabled) {
            FeaturePersonaRepository.toggleEnabled(id)
        }
    }

    override suspend fun toggleEnabled(id: String) {
        FeaturePersonaRepository.toggleEnabled(id)
    }

    override suspend fun delete(id: String) {
        FeaturePersonaRepository.delete(id)
    }
}

/**
 * Compat-only adapter for targeted tests and transitional callers.
 * Production mainline uses [FeaturePersonaRepositoryPortAdapter].
 */
@Deprecated(
    "Compat-only seam. Production mainline uses FeaturePersonaRepositoryPortAdapter.",
    level = DeprecationLevel.WARNING,
)
class LegacyPersonaRepositoryAdapter : FeaturePersonaRepositoryPortAdapter()

