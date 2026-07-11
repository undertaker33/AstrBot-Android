package com.elymbot.android.feature.persona.data

import com.elymbot.android.feature.persona.domain.model.PersonaCoverMetadata
import javax.inject.Inject

class FeaturePersonaCoverMetadataStore @Inject constructor(
    private val repository: FeaturePersonaRepositoryStore,
) : PersonaCoverMetadataStore {
    override fun coverForPersona(personaId: String): PersonaCoverMetadata? =
        repository.snapshotProfiles().firstOrNull { it.id == personaId }?.cover

    override fun updateCover(personaId: String, metadata: PersonaCoverMetadata?) {
        val profile = repository.snapshotProfiles().firstOrNull { it.id == personaId }
            ?: error("Persona does not exist: $personaId")
        repository.update(profile.copy(cover = metadata))
    }

    override fun validAssetRefs(): Set<String> = repository.snapshotProfiles().mapNotNull { it.cover?.assetRef }.toSet()
}
