package com.astrbot.android.feature.persona.data

import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import kotlinx.coroutines.flow.StateFlow

class LegacyPersonaRepositoryAdapter : PersonaRepositoryPort {

    override val personas: StateFlow<List<PersonaProfile>>
        get() = PersonaRepository.personas

    override fun snapshotProfiles(): List<PersonaProfile> =
        PersonaRepository.snapshotProfiles()

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
