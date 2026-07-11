package com.elymbot.android.feature.persona.presentation

import com.elymbot.android.feature.persona.domain.PersonaBrowseMode
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.persona.domain.model.PersonaCoverMetadata

enum class PersonaTopOverlay { NONE, SEARCH, FILTER, MORE }
data class PersonaPageUiState(
    val personas: List<PersonaProfile> = emptyList(), val browseMode: PersonaBrowseMode = PersonaBrowseMode.IMMERSIVE_CARD,
    val visiblePersonaId: String? = null, val topOverlay: PersonaTopOverlay = PersonaTopOverlay.NONE,
    val searchQuery: String = "", val appliedTagFilters: Set<String> = emptySet(),
    val pendingTagFilters: Set<String> = appliedTagFilters,
)
sealed interface PersonaPageAction {
    data class OpenOverlay(val overlay: PersonaTopOverlay) : PersonaPageAction
    data object DismissOverlay : PersonaPageAction
    data class Search(val query: String) : PersonaPageAction
    data class TogglePendingFilter(val tag: String) : PersonaPageAction
    data object ResetPendingFilters : PersonaPageAction
    data object ApplyPendingFilters : PersonaPageAction
}
fun reducePersonaPage(state: PersonaPageUiState, action: PersonaPageAction): PersonaPageUiState = when (action) {
    is PersonaPageAction.OpenOverlay -> state.copy(topOverlay = action.overlay, pendingTagFilters = if (action.overlay == PersonaTopOverlay.FILTER) state.appliedTagFilters else state.pendingTagFilters)
    PersonaPageAction.DismissOverlay -> state.copy(topOverlay = PersonaTopOverlay.NONE, pendingTagFilters = state.appliedTagFilters)
    is PersonaPageAction.Search -> state.copy(searchQuery = action.query)
    is PersonaPageAction.TogglePendingFilter -> state.copy(pendingTagFilters = state.pendingTagFilters.toMutableSet().apply { if (!add(action.tag)) remove(action.tag) })
    PersonaPageAction.ResetPendingFilters -> state.copy(pendingTagFilters = emptySet())
    PersonaPageAction.ApplyPendingFilters -> state.copy(topOverlay = PersonaTopOverlay.NONE, appliedTagFilters = state.pendingTagFilters)
}
fun filterPersonas(personas: List<PersonaProfile>, query: String, tags: Set<String>): List<PersonaProfile> {
    val needle = query.trim()
    return personas.filter { persona ->
        (needle.isEmpty() || persona.name.contains(needle, true) || persona.tags.any { it.contains(needle, true) }) &&
            (tags.isEmpty() || persona.tags.any(tags::contains))
    }
}
data class PersonaPagerArrows(val previousVisible: Boolean, val nextVisible: Boolean)
fun personaPagerArrows(count: Int, index: Int) = PersonaPagerArrows(count > 1 && index > 0, count > 1 && index < count - 1)

enum class PersonaCoverViewport { PORTRAIT, SQUARE }
data class PersonaCoverRenderPolicy(val centerX: Float, val centerY: Float, val scale: Float)
fun personaCoverRenderPolicy(metadata: PersonaCoverMetadata?, viewport: PersonaCoverViewport): PersonaCoverRenderPolicy {
    val crop = metadata?.let { if (viewport == PersonaCoverViewport.PORTRAIT) it.portraitCrop else it.squareCrop }
    return PersonaCoverRenderPolicy(crop?.centerX ?: .5f, crop?.centerY ?: .5f, crop?.zoom ?: 1f)
}
fun squareCardHeight(width: Float): Float = width
