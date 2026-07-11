
package com.elymbot.android.feature.persona.data

import android.content.SharedPreferences
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.common.profile.PersonaInUseException
import com.elymbot.android.core.common.profile.PersonaReferenceChecker
import com.elymbot.android.core.common.profile.ProfileCatalogKind
import com.elymbot.android.core.common.profile.ProfileDeletionGuard
import com.elymbot.android.data.db.PersonaAggregateDao
import com.elymbot.android.data.db.toProfile
import com.elymbot.android.data.db.toWriteModel
import com.elymbot.android.feature.persona.domain.defaultPersonaEnabledTools
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import com.elymbot.android.feature.persona.domain.model.normalizePersonaTags
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val KEY_PERSONAS_JSON = "personas_json"

@Singleton
class FeaturePersonaRepositoryStore @Inject constructor(
    private val personaDao: PersonaAggregateDao,
    @Named("personaProfilesPreferences") private val preferences: SharedPreferences,
    private val personaReferenceChecker: PersonaReferenceChecker = PersonaReferenceChecker { false },
    private val runtimeLogger: RuntimeLogger = RuntimeLogger { },
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _personas = MutableStateFlow(defaultPersonas())

    val personas: StateFlow<List<PersonaProfile>> = _personas.asStateFlow()

    init {
        runBlocking(Dispatchers.IO) {
            seedStorageIfNeeded()
        }
        repositoryScope.launch {
            personaDao.observePersonaAggregates().collect { aggregates ->
                val loaded = aggregates.map { aggregate -> normalizePersona(aggregate.toProfile()) }.ifEmpty { defaultPersonas() }
                _personas.value = loaded
                runtimeLogger.append("Persona catalog loaded: count=${loaded.size}")
            }
        }
    }

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        val persona = normalizePersona(
            PersonaProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                tags = normalizePersonaTags(tag),
                systemPrompt = systemPrompt,
                enabledTools = enabledTools,
                defaultProviderId = defaultProviderId,
                maxContextMessages = maxContextMessages,
            ),
        )
        val updated = _personas.value + persona
        _personas.value = updated
        persistPersonas(updated)
        runtimeLogger.append(
            "Persona added: ${persona.name}, defaultProvider=${persona.defaultProviderId.ifBlank { "none" }}",
        )
    }

    fun update(profile: PersonaProfile) {
        val normalized = normalizePersona(profile)
        val updated = _personas.value.map { current ->
            if (current.id == normalized.id) normalized else current
        }
        _personas.value = updated
        persistPersonas(updated)
        runtimeLogger.append("Persona updated: ${normalized.name}")
    }

    fun toggleEnabled(id: String) {
        val updated = _personas.value.map { item ->
            if (item.id == id) item.copy(enabled = !item.enabled) else item
        }
        _personas.value = updated
        persistPersonas(updated)
        updated.firstOrNull { it.id == id }?.let { persona ->
            runtimeLogger.append("Persona toggled: ${persona.name} enabled=${persona.enabled}")
        }
    }

    fun delete(id: String) {
        val removed = _personas.value.firstOrNull { it.id == id } ?: return
        ProfileDeletionGuard.requireCanDelete(
            remainingCount = _personas.value.size,
            kind = ProfileCatalogKind.PERSONA,
        )
        if (personaReferenceChecker.isInUse(id)) {
            throw PersonaInUseException(id)
        }
        val updated = _personas.value.filterNot { it.id == id }
        _personas.value = updated
        persistPersonas(updated)
        runtimeLogger.append("Persona deleted: ${removed.name}")
    }

    fun snapshotProfiles(): List<PersonaProfile> {
        return _personas.value.map { persona ->
            persona.copy(enabledTools = persona.enabledTools.toSet())
        }
    }

    fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? {
        return snapshotProfiles().firstOrNull { persona -> persona.id == personaId }?.let { persona ->
            PersonaToolEnablementSnapshot(
                personaId = persona.id,
                enabled = persona.enabled,
                enabledTools = persona.enabledTools.toSet(),
            )
        }
    }

    fun restoreProfiles(profiles: List<PersonaProfile>) {
        val restored = profiles
            .map(::normalizePersona)
            .distinctBy { it.id }
            .ifEmpty { defaultPersonas() }
        _personas.value = restored
        persistPersonas(restored)
        runtimeLogger.append("Persona profiles restored: count=${restored.size}")
    }

    private fun persistPersonas(personas: List<PersonaProfile>) {
        runBlocking(Dispatchers.IO) {
            if (personas.isEmpty()) {
                personaDao.replaceAll(emptyList())
            } else {
                personaDao.replaceAll(
                    personas.mapIndexed { index, persona -> persona.toWriteModel(sortIndex = index) },
                )
            }
        }
    }

    private suspend fun seedStorageIfNeeded() {
        if (personaDao.count() > 0) return
        val imported = runCatching {
            parseLegacyPersonaProfiles(preferences.getString(KEY_PERSONAS_JSON, null))
        }.onFailure { error ->
            runtimeLogger.append("Persona catalog legacy import failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrDefault(emptyList())
        val seeded = imported.map(::normalizePersona).ifEmpty { defaultPersonas() }
        personaDao.replaceAll(
            seeded.mapIndexed { index, persona -> persona.toWriteModel(sortIndex = index) },
        )
        runtimeLogger.append(
            if (imported.isNotEmpty()) {
                "Persona catalog migrated from SharedPreferences: count=${seeded.size}"
            } else {
                "Persona catalog seeded with defaults: count=${seeded.size}"
            },
        )
    }

    private fun normalizePersona(profile: PersonaProfile): PersonaProfile {
        val normalizedTools = profile.enabledTools.map(String::trim).filter(String::isNotBlank).toSet()
        return profile.copy(
            name = profile.name.trim(),
            tags = normalizePersonaTags(profile.tags),
            systemPrompt = profile.systemPrompt,
            enabledTools = if (normalizedTools.isEmpty()) {
                defaultEnabledTools()
            } else {
                normalizedTools
            },
        )
    }

    private fun defaultPersonas() = listOf(
        PersonaProfile(
            id = "default",
            name = "Default Assistant",
            tags = listOf("Default"),
            systemPrompt = "You are a concise, reliable QQ assistant.",
            enabledTools = defaultEnabledTools(),
            maxContextMessages = 12,
        ),
    )

    private fun defaultEnabledTools(): Set<String> {
        return defaultPersonaEnabledTools()
    }
}
