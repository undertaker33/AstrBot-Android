package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.PersonaDao
import com.astrbot.android.data.db.PersonaEntity
import com.astrbot.android.data.db.toEntity
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object PersonaRepository {
    private const val PREFS_NAME = "persona_profiles"
    private const val KEY_PERSONAS_JSON = "personas_json"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private var preferences: SharedPreferences? = null
    private var personaDao: PersonaDao = PersonaDaoPlaceholder.instance
    private val _personas = MutableStateFlow(defaultPersonas())

    val personas: StateFlow<List<PersonaProfile>> = _personas.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        personaDao = AstrBotDatabase.get(context).personaDao()

        runBlocking(Dispatchers.IO) {
            seedStorageIfNeeded()
        }
        repositoryScope.launch {
            personaDao.observePersonas().collect { entities ->
                val loaded = entities.map { entity -> normalizePersona(entity.toProfile()) }.ifEmpty { defaultPersonas() }
                _personas.value = loaded
                RuntimeLogRepository.append("Persona catalog loaded: count=${loaded.size}")
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
        RuntimeLogRepository.append(
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
        RuntimeLogRepository.append("Persona updated: ${normalized.name}")
    }

    fun toggleEnabled(id: String) {
        val updated = _personas.value.map { item ->
            if (item.id == id) item.copy(enabled = !item.enabled) else item
        }
        _personas.value = updated
        persistPersonas(updated)
        updated.firstOrNull { it.id == id }?.let { persona ->
            RuntimeLogRepository.append("Persona toggled: ${persona.name} enabled=${persona.enabled}")
        }
    }

    fun delete(id: String) {
        val removed = _personas.value.firstOrNull { it.id == id }
        if (removed == null) return
        ProfileDeletionGuard.requireCanDelete(
            remainingCount = _personas.value.size,
            kind = ProfileCatalogKind.PERSONA,
        )
        val updated = _personas.value.filterNot { it.id == id }
        _personas.value = updated
        persistPersonas(updated)
        RuntimeLogRepository.append("Persona deleted: ${removed.name}")
    }

    fun snapshotProfiles(): List<PersonaProfile> {
        return _personas.value.map { persona ->
            persona.copy(enabledTools = persona.enabledTools.toSet())
        }
    }

    fun restoreProfiles(profiles: List<PersonaProfile>) {
        val restored = profiles
            .map(::normalizePersona)
            .distinctBy { it.id }
            .ifEmpty { defaultPersonas() }
        _personas.value = restored
        persistPersonas(restored)
        RuntimeLogRepository.append("Persona profiles restored: count=${restored.size}")
    }

    private fun persistPersonas(personas: List<PersonaProfile>) {
        runBlocking(Dispatchers.IO) {
            if (personas.isEmpty()) {
                personaDao.clearAll()
            } else {
                val entities = personas.mapIndexed { index, persona ->
                    persona.toEntity(sortIndex = index)
                }
                personaDao.upsertAll(entities)
                personaDao.deleteMissing(entities.map { it.id })
            }
        }
    }

    private suspend fun seedStorageIfNeeded() {
        if (personaDao.count() > 0) return
        val imported = runCatching {
            parseLegacyPersonaProfiles(preferences?.getString(KEY_PERSONAS_JSON, null))
        }.onFailure { error ->
            RuntimeLogRepository.append("Persona catalog legacy import failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrDefault(emptyList())
        val seeded = imported.map(::normalizePersona).ifEmpty { defaultPersonas() }
        personaDao.upsertAll(
            seeded.mapIndexed { index, persona -> persona.toEntity(sortIndex = index) },
        )
        RuntimeLogRepository.append(
            if (imported.isNotEmpty()) {
                "Persona catalog migrated from SharedPreferences: count=${seeded.size}"
            } else {
                "Persona catalog seeded with defaults: count=${seeded.size}"
            },
        )
    }

    private fun normalizePersona(profile: PersonaProfile): PersonaProfile {
        return profile.copy(
            name = profile.name.trim(),
            tag = profile.tag.trim(),
            systemPrompt = profile.systemPrompt,
            enabledTools = profile.enabledTools.map(String::trim).filter(String::isNotBlank).toSet(),
        )
    }

    private fun defaultPersonas() = listOf(
        PersonaProfile(
            id = "default",
            name = "Default Assistant",
            tag = "Default",
            systemPrompt = "You are a concise, reliable QQ assistant.",
            enabledTools = emptySet(),
            maxContextMessages = 12,
        ),
    )
}

private object PersonaDaoPlaceholder {
    val instance = object : PersonaDao {
        override fun observePersonas() = flowOf(emptyList<PersonaEntity>())

        override suspend fun listPersonas(): List<PersonaEntity> = emptyList()

        override suspend fun upsertAll(entities: List<PersonaEntity>) = Unit

        override suspend fun deleteMissing(ids: List<String>) = Unit

        override suspend fun clearAll() = Unit

        override suspend fun count(): Int = 0
    }
}
