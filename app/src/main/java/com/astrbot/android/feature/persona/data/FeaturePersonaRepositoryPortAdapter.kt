package com.astrbot.android.feature.persona.data

import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeaturePersonaRepositoryPortAdapter @Inject constructor(
    private val repository: FeaturePersonaRepositoryStore,
) : PersonaRepositoryPort {

    override val personas: StateFlow<List<PersonaProfile>>
        get() = repository.personas

    override fun snapshotProfiles(): List<PersonaProfile> =
        repository.snapshotProfiles()

    override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot> =
        repository.snapshotProfiles().map { persona ->
            PersonaToolEnablementSnapshot(
                personaId = persona.id,
                enabled = persona.enabled,
                enabledTools = persona.enabledTools.toSet(),
            )
    }

    override fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? =
        repository.snapshotToolEnablement(personaId)

    override suspend fun add(profile: PersonaProfile) {
        repository.add(
            name = profile.name,
            tag = profile.tag,
            systemPrompt = profile.systemPrompt,
            enabledTools = profile.enabledTools,
            defaultProviderId = profile.defaultProviderId,
            maxContextMessages = profile.maxContextMessages,
        )
    }

    override suspend fun update(profile: PersonaProfile) {
        repository.update(profile)
    }

    override suspend fun toggleEnabled(id: String, enabled: Boolean) {
        val current = repository.snapshotToolEnablement(id)?.enabled ?: return
        if (current != enabled) {
            repository.toggleEnabled(id)
        }
    }

    override suspend fun toggleEnabled(id: String) {
        repository.toggleEnabled(id)
    }

    override suspend fun delete(id: String) {
        repository.delete(id)
    }
}
