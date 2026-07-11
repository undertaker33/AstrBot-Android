package com.elymbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elymbot.android.feature.persona.domain.PersonaRepositoryPort
import com.elymbot.android.feature.persona.domain.PersonaBrowseMode
import com.elymbot.android.feature.persona.domain.PersonaCoverAssetPort
import com.elymbot.android.feature.persona.domain.PersonaPresentationPreferencesPort
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val personaRepository: PersonaRepositoryPort,
    private val presentationPreferences: PersonaPresentationPreferencesPort,
    private val coverAssetPort: PersonaCoverAssetPort,
) : ViewModel() {
    val personas: StateFlow<List<PersonaProfile>> = personaRepository.personas
    val browseMode: StateFlow<PersonaBrowseMode> = presentationPreferences.browseMode

    fun setBrowseMode(mode: PersonaBrowseMode) = presentationPreferences.setBrowseMode(mode)
    fun resolveCover(assetRef: String): String? = coverAssetPort.resolveReadableFile(assetRef)
    fun stageCover(personaId: String, uri: String) = coverAssetPort.stageImport(personaId, uri)
    fun commitCover(draftId: String, portrait: com.elymbot.android.feature.persona.domain.model.PersonaCropSpec, square: com.elymbot.android.feature.persona.domain.model.PersonaCropSpec) = coverAssetPort.commit(draftId, portrait, square)
    fun discardCover(draftId: String) = coverAssetPort.discard(draftId)

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            personaRepository.add(
                PersonaProfile(
                    id = "",
                    name = name,
                    tag = tag,
                    systemPrompt = systemPrompt,
                    enabledTools = enabledTools,
                    defaultProviderId = defaultProviderId,
                    maxContextMessages = maxContextMessages,
                ),
            )
        }
    }

    fun update(profile: PersonaProfile) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            personaRepository.update(profile)
        }
    }

    fun toggleEnabled(id: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            personaRepository.toggleEnabled(id)
        }
    }

    fun delete(id: String): Result<Unit> {
        return runCatching {
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                personaRepository.delete(id)
            }
        }
    }
}

