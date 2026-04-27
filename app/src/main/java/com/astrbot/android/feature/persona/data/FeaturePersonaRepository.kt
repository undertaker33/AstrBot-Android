
package com.astrbot.android.feature.persona.data

import android.content.SharedPreferences
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.common.profile.PersonaReferenceGuard
import com.astrbot.android.core.common.profile.ProfileCatalogKind
import com.astrbot.android.core.common.profile.ProfileDeletionGuard
import com.astrbot.android.data.db.PersonaAggregateDao
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.data.parseLegacyPersonaProfiles
import com.astrbot.android.feature.persona.domain.defaultPersonaEnabledTools
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
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

@Deprecated("Use PersonaRepositoryPort from feature/persona/domain. Direct access will be removed.")
object FeaturePersonaRepository {
    private const val KEY_PERSONAS_JSON = "personas_json"

    @Volatile
    private var delegate: FeaturePersonaRepositoryStore? = null

    internal fun installDelegate(store: FeaturePersonaRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeaturePersonaRepositoryStore {
        return checkNotNull(delegate) {
            "FeaturePersonaRepository was accessed before the Hilt graph created FeaturePersonaRepositoryStore."
        }
    }

    val personas: StateFlow<List<PersonaProfile>>
        get() = repository().personas

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) = repository().add(name, tag, systemPrompt, enabledTools, defaultProviderId, maxContextMessages)

    fun update(profile: PersonaProfile) = repository().update(profile)

    fun toggleEnabled(id: String) = repository().toggleEnabled(id)

    fun delete(id: String) = repository().delete(id)

    fun snapshotProfiles(): List<PersonaProfile> = repository().snapshotProfiles()

    fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? =
        repository().snapshotToolEnablement(personaId)

    fun restoreProfiles(profiles: List<PersonaProfile>) = repository().restoreProfiles(profiles)

    fun defaultEnabledTools(): Set<String> = defaultPersonaEnabledTools()

    internal fun legacyPersonasJson(preferences: SharedPreferences): String? =
        preferences.getString(KEY_PERSONAS_JSON, null)
}

@Singleton
class FeaturePersonaRepositoryStore @Inject constructor(
    private val personaDao: PersonaAggregateDao,
    @Named("personaProfilesPreferences") private val preferences: SharedPreferences,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _personas = MutableStateFlow(defaultPersonas())

    val personas: StateFlow<List<PersonaProfile>> = _personas.asStateFlow()

    init {
        FeaturePersonaRepository.installDelegate(this)
        runBlocking(Dispatchers.IO) {
            seedStorageIfNeeded()
        }
        repositoryScope.launch {
            personaDao.observePersonaAggregates().collect { aggregates ->
                val loaded = aggregates.map { aggregate -> normalizePersona(aggregate.toProfile()) }.ifEmpty { defaultPersonas() }
                _personas.value = loaded
                AppLogger.append("Persona catalog loaded: count=${loaded.size}")
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
                tag = tag.trim(),
                systemPrompt = systemPrompt,
                enabledTools = enabledTools,
                defaultProviderId = defaultProviderId,
                maxContextMessages = maxContextMessages,
            ),
        )
        val updated = _personas.value + persona
        _personas.value = updated
        persistPersonas(updated)
        AppLogger.append(
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
        AppLogger.append("Persona updated: ${normalized.name}")
    }

    fun toggleEnabled(id: String) {
        val updated = _personas.value.map { item ->
            if (item.id == id) item.copy(enabled = !item.enabled) else item
        }
        _personas.value = updated
        persistPersonas(updated)
        updated.firstOrNull { it.id == id }?.let { persona ->
            AppLogger.append("Persona toggled: ${persona.name} enabled=${persona.enabled}")
        }
    }

    fun delete(id: String) {
        val removed = _personas.value.firstOrNull { it.id == id } ?: return
        ProfileDeletionGuard.requireCanDelete(
            remainingCount = _personas.value.size,
            kind = ProfileCatalogKind.PERSONA,
        )
        PersonaReferenceGuard.requireNotInUse(id)
        val updated = _personas.value.filterNot { it.id == id }
        _personas.value = updated
        persistPersonas(updated)
        AppLogger.append("Persona deleted: ${removed.name}")
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
        AppLogger.append("Persona profiles restored: count=${restored.size}")
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
            parseLegacyPersonaProfiles(FeaturePersonaRepository.legacyPersonasJson(preferences))
        }.onFailure { error ->
            AppLogger.append("Persona catalog legacy import failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrDefault(emptyList())
        val seeded = imported.map(::normalizePersona).ifEmpty { defaultPersonas() }
        personaDao.replaceAll(
            seeded.mapIndexed { index, persona -> persona.toWriteModel(sortIndex = index) },
        )
        AppLogger.append(
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
            tag = profile.tag.trim(),
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
            tag = "Default",
            systemPrompt = "You are a concise, reliable QQ assistant.",
            enabledTools = defaultEnabledTools(),
            maxContextMessages = 12,
        ),
    )

    private fun defaultEnabledTools(): Set<String> {
        return defaultPersonaEnabledTools()
    }
}
