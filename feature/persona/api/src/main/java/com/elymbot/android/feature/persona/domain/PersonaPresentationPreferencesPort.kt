package com.elymbot.android.feature.persona.domain

import kotlinx.coroutines.flow.StateFlow

enum class PersonaBrowseMode { IMMERSIVE_CARD, STAGGERED_GRID }

interface PersonaPresentationPreferencesPort {
    val browseMode: StateFlow<PersonaBrowseMode>
    fun setBrowseMode(mode: PersonaBrowseMode)
}
