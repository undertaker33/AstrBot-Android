package com.elymbot.android.feature.persona.data

import android.content.SharedPreferences
import com.elymbot.android.feature.persona.domain.PersonaBrowseMode
import com.elymbot.android.feature.persona.domain.PersonaPresentationPreferencesPort
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class AndroidPersonaPresentationPreferences @Inject constructor(
    @PersonaPresentationStorage private val preferences: SharedPreferences,
) : PersonaPresentationPreferencesPort {
    private val mutableBrowseMode = MutableStateFlow(readMode())
    override val browseMode: StateFlow<PersonaBrowseMode> = mutableBrowseMode

    override fun setBrowseMode(mode: PersonaBrowseMode) {
        preferences.edit().putString(KEY_BROWSE_MODE, mode.name).apply()
        mutableBrowseMode.value = mode
    }

    private fun readMode(): PersonaBrowseMode = runCatching {
        PersonaBrowseMode.valueOf(preferences.getString(KEY_BROWSE_MODE, null).orEmpty())
    }.getOrDefault(PersonaBrowseMode.IMMERSIVE_CARD)

    private companion object { const val KEY_BROWSE_MODE = "browse_mode" }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PersonaPresentationStorage
