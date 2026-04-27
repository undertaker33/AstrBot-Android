package com.astrbot.android.feature.persona.domain

import com.astrbot.android.feature.persona.domain.model.PersonaProfile
import com.astrbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import kotlinx.coroutines.flow.StateFlow

interface PersonaRepositoryPort {
    val personas: StateFlow<List<PersonaProfile>>
    fun snapshotProfiles(): List<PersonaProfile>
    fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot>
    fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot?
    suspend fun add(profile: PersonaProfile)
    suspend fun update(profile: PersonaProfile)
    suspend fun toggleEnabled(id: String, enabled: Boolean)
    suspend fun toggleEnabled(id: String)
    suspend fun delete(id: String)
}
