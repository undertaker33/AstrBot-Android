package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object PersonaRepository {
    private const val PREFS_NAME = "persona_profiles"
    private const val KEY_PERSONAS_JSON = "personas_json"

    private var preferences: SharedPreferences? = null
    private val _personas = MutableStateFlow(defaultPersonas())

    val personas: StateFlow<List<PersonaProfile>> = _personas.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedPersonas()?.let { savedPersonas ->
            _personas.value = savedPersonas
        }
        RuntimeLogRepository.append("Persona catalog loaded: count=${_personas.value.size}")
    }

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        val persona = PersonaProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            tag = tag.trim(),
            systemPrompt = systemPrompt,
            enabledTools = enabledTools,
            defaultProviderId = defaultProviderId,
            maxContextMessages = maxContextMessages,
        )
        _personas.value = _personas.value + persona
        persistPersonas()
        RuntimeLogRepository.append(
            "Persona added: ${persona.name}, defaultProvider=${persona.defaultProviderId.ifBlank { "none" }}",
        )
    }

    fun update(profile: PersonaProfile) {
        _personas.value = _personas.value.map { current ->
            if (current.id == profile.id) profile else current
        }
        persistPersonas()
        RuntimeLogRepository.append("Persona updated: ${profile.name}")
    }

    fun toggleEnabled(id: String) {
        _personas.value = _personas.value.map { item ->
            if (item.id == id) {
                val updated = item.copy(enabled = !item.enabled)
                RuntimeLogRepository.append("Persona toggled: ${updated.name} enabled=${updated.enabled}")
                updated
            } else {
                item
            }
        }
        persistPersonas()
    }

    fun delete(id: String) {
        val removed = _personas.value.firstOrNull { it.id == id }
        _personas.value = _personas.value.filterNot { it.id == id }
        persistPersonas()
        if (removed != null) {
            RuntimeLogRepository.append("Persona deleted: ${removed.name}")
        }
    }

    fun snapshotProfiles(): List<PersonaProfile> {
        return _personas.value.map { persona ->
            persona.copy(enabledTools = persona.enabledTools.toSet())
        }
    }

    fun restoreProfiles(profiles: List<PersonaProfile>) {
        val restored = profiles
            .map { persona ->
                persona.copy(
                    name = persona.name.trim(),
                    tag = persona.tag.trim(),
                    systemPrompt = persona.systemPrompt,
                    enabledTools = persona.enabledTools.map(String::trim).filter(String::isNotBlank).toSet(),
                )
            }
            .distinctBy { it.id }
            .ifEmpty { defaultPersonas() }
        _personas.value = restored
        persistPersonas()
        RuntimeLogRepository.append("Persona profiles restored: count=${restored.size}")
    }

    private fun loadSavedPersonas(): List<PersonaProfile>? {
        val raw = preferences?.getString(KEY_PERSONAS_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        PersonaProfile(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            tag = item.optString("tag"),
                            systemPrompt = item.optString("systemPrompt"),
                            enabledTools = buildSet {
                                val enabledToolsArray = item.optJSONArray("enabledTools") ?: JSONArray()
                                for (toolIndex in 0 until enabledToolsArray.length()) {
                                    enabledToolsArray.optString(toolIndex)
                                        .takeIf { it.isNotBlank() }
                                        ?.let(::add)
                                }
                            },
                            defaultProviderId = item.optString("defaultProviderId"),
                            maxContextMessages = item.optInt("maxContextMessages", 12),
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }.onFailure { error ->
            RuntimeLogRepository.append("Persona catalog load failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun persistPersonas() {
        val json = JSONArray().apply {
            _personas.value.forEach { persona ->
                put(
                    JSONObject().apply {
                        put("id", persona.id)
                        put("name", persona.name)
                        put("tag", persona.tag)
                        put("systemPrompt", persona.systemPrompt)
                        put("defaultProviderId", persona.defaultProviderId)
                        put("maxContextMessages", persona.maxContextMessages)
                        put("enabled", persona.enabled)
                        put(
                            "enabledTools",
                            JSONArray().apply {
                                persona.enabledTools.forEach { tool ->
                                    put(tool)
                                }
                            },
                        )
                    },
                )
            }
        }
        preferences?.edit()?.putString(KEY_PERSONAS_JSON, json.toString())?.apply()
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
