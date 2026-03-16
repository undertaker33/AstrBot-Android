package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ConfigRepository {
    private const val PREFS_NAME = "config_profiles"
    private const val KEY_PROFILES_JSON = "profiles_json"
    private const val KEY_SELECTED_ID = "selected_id"
    const val DEFAULT_CONFIG_ID = "default"

    private var preferences: SharedPreferences? = null

    private val _profiles = MutableStateFlow(defaultProfiles())
    private val _selectedProfileId = MutableStateFlow(DEFAULT_CONFIG_ID)

    val profiles: StateFlow<List<ConfigProfile>> = _profiles.asStateFlow()
    val selectedProfileId: StateFlow<String> = _selectedProfileId.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedProfiles()?.takeIf { it.isNotEmpty() }?.let { saved ->
            _profiles.value = saved.map(::normalizeProfile)
        }
        val storedSelectedId = preferences?.getString(KEY_SELECTED_ID, DEFAULT_CONFIG_ID).orEmpty()
        _selectedProfileId.value = resolveExistingId(storedSelectedId)
        persist()
        RuntimeLogRepository.append("Config profiles loaded: count=${_profiles.value.size}")
    }

    fun select(profileId: String) {
        val resolvedId = resolveExistingId(profileId)
        _selectedProfileId.value = resolvedId
        preferences?.edit()?.putString(KEY_SELECTED_ID, resolvedId)?.apply()
        RuntimeLogRepository.append("Config profile selected: $resolvedId")
    }

    fun save(profile: ConfigProfile): ConfigProfile {
        val normalized = normalizeProfile(
            profile.copy(
                id = profile.id.ifBlank { UUID.randomUUID().toString() },
            ),
        )
        val exists = _profiles.value.any { it.id == normalized.id }
        _profiles.value = if (exists) {
            _profiles.value.map { if (it.id == normalized.id) normalized else it }
        } else {
            _profiles.value + normalized
        }
        if (_selectedProfileId.value.isBlank()) {
            _selectedProfileId.value = normalized.id
        }
        persist()
        RuntimeLogRepository.append(
            if (exists) "Config profile updated: ${normalized.name}" else "Config profile created: ${normalized.name}",
        )
        return normalized
    }

    fun create(name: String = "New Config"): ConfigProfile {
        val created = ConfigProfile(
            id = UUID.randomUUID().toString(),
            name = name,
        )
        return save(created)
    }

    fun delete(profileId: String): String {
        if (_profiles.value.size == 1) {
            return DEFAULT_CONFIG_ID
        }
        _profiles.value = _profiles.value.filterNot { it.id == profileId }
        val fallbackId = resolveExistingId(_selectedProfileId.value)
        if (_selectedProfileId.value == profileId) {
            _selectedProfileId.value = fallbackId
        }
        persist()
        RuntimeLogRepository.append("Config profile deleted: $profileId")
        return fallbackId
    }

    fun resolve(profileId: String): ConfigProfile {
        return _profiles.value.firstOrNull { it.id == profileId }
            ?: _profiles.value.firstOrNull()
            ?: defaultProfiles().first()
    }

    fun resolveExistingId(profileId: String?): String {
        val requestedId = profileId?.takeIf { it.isNotBlank() }
        return when {
            requestedId != null && _profiles.value.any { it.id == requestedId } -> requestedId
            else -> _profiles.value.firstOrNull()?.id ?: DEFAULT_CONFIG_ID
        }
    }

    private fun loadSavedProfiles(): List<ConfigProfile>? {
        val raw = preferences?.getString(KEY_PROFILES_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        ConfigProfile(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            defaultChatProviderId = item.optString("defaultChatProviderId"),
                            defaultVisionProviderId = item.optString("defaultVisionProviderId"),
                            sttEnabled = item.optBoolean("sttEnabled", false),
                            ttsEnabled = item.optBoolean("ttsEnabled", false),
                            realWorldTimeAwarenessEnabled = item.optBoolean("realWorldTimeAwarenessEnabled", false),
                            imageCaptionPrompt = item.optString(
                                "imageCaptionPrompt",
                                defaultProfiles().first().imageCaptionPrompt,
                            ),
                        ),
                    )
                }
            }
        }.onFailure { error ->
            RuntimeLogRepository.append("Config profiles load failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun persist() {
        val json = JSONArray().apply {
            _profiles.value.forEach { profile ->
                put(
                    JSONObject().apply {
                        put("id", profile.id)
                        put("name", profile.name)
                        put("defaultChatProviderId", profile.defaultChatProviderId)
                        put("defaultVisionProviderId", profile.defaultVisionProviderId)
                        put("sttEnabled", profile.sttEnabled)
                        put("ttsEnabled", profile.ttsEnabled)
                        put("realWorldTimeAwarenessEnabled", profile.realWorldTimeAwarenessEnabled)
                        put("imageCaptionPrompt", profile.imageCaptionPrompt)
                    },
                )
            }
        }
        preferences?.edit()
            ?.putString(KEY_PROFILES_JSON, json.toString())
            ?.putString(KEY_SELECTED_ID, resolveExistingId(_selectedProfileId.value))
            ?.apply()
    }

    private fun normalizeProfile(profile: ConfigProfile): ConfigProfile {
        return profile.copy(
            id = profile.id.ifBlank { DEFAULT_CONFIG_ID },
            name = profile.name.trim().ifBlank { "Unnamed Config" },
            imageCaptionPrompt = profile.imageCaptionPrompt.trim().ifBlank { defaultProfiles().first().imageCaptionPrompt },
        )
    }

    private fun defaultProfiles(): List<ConfigProfile> {
        return listOf(
            ConfigProfile(
                id = DEFAULT_CONFIG_ID,
                name = "Default Config",
            ),
        )
    }
}
