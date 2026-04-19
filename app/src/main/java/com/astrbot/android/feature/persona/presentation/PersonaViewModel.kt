package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.di.PersonaViewModelDependencies
import com.astrbot.android.model.PersonaProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val dependencies: PersonaViewModelDependencies,
) : ViewModel() {
    val personas: StateFlow<List<PersonaProfile>> = dependencies.personas

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        dependencies.add(name, tag, systemPrompt, enabledTools, defaultProviderId, maxContextMessages)
    }

    fun update(profile: PersonaProfile) {
        dependencies.update(profile)
    }

    fun toggleEnabled(id: String) {
        dependencies.toggleEnabled(id)
    }

    fun delete(id: String): Result<Unit> {
        return runCatching {
            dependencies.delete(id)
        }
    }
}
