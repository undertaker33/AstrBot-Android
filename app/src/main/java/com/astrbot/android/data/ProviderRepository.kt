package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.defaultCapability
import com.astrbot.android.model.inferMultimodalRuleSupport
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ProviderRepository {
    private const val PREFS_NAME = "provider_profiles"
    private const val KEY_PROVIDERS_JSON = "providers_json"

    private var preferences: SharedPreferences? = null
    private val _providers = MutableStateFlow(defaultProviders())

    val providers: StateFlow<List<ProviderProfile>> = _providers.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedProviders()?.let { savedProviders ->
            val normalizedProviders = savedProviders.map(::normalizeProvider)
            _providers.value = normalizedProviders
            if (normalizedProviders != savedProviders) {
                persistProviders()
            }
        }
        RuntimeLogRepository.append("Provider catalog loaded: count=${_providers.value.size}")
    }

    fun save(
        id: String?,
        name: String,
        baseUrl: String,
        model: String,
        providerType: ProviderType,
        apiKey: String,
        capabilities: Set<ProviderCapability>,
        enabled: Boolean = true,
        multimodalRuleSupport: FeatureSupportState = inferMultimodalRuleSupport(providerType, model),
        multimodalProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
    ): ProviderProfile {
        val resolvedId = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val profile = normalizeProvider(
            ProviderProfile(
                id = resolvedId,
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                model = model.trim(),
                providerType = providerType,
                apiKey = apiKey.trim(),
                capabilities = capabilities,
                enabled = enabled,
                multimodalRuleSupport = multimodalRuleSupport,
                multimodalProbeSupport = multimodalProbeSupport,
            ),
        )
        val exists = _providers.value.any { it.id == resolvedId }
        _providers.value = if (exists) {
            _providers.value.map { item -> if (item.id == resolvedId) profile else item }
        } else {
            _providers.value + profile
        }
        persistProviders()
        RuntimeLogRepository.append(
            if (exists) {
                "Provider updated: ${profile.name} (${profile.providerType.name}, key=${maskState(profile.apiKey)})"
            } else {
                "Provider added: ${profile.name} (${profile.providerType.name}, key=${maskState(profile.apiKey)})"
            },
        )
        return profile
    }

    fun toggleEnabled(id: String) {
        _providers.value = _providers.value.map { item ->
            if (item.id == id) {
                val updated = item.copy(enabled = !item.enabled)
                RuntimeLogRepository.append("Provider toggled: ${updated.name} enabled=${updated.enabled}")
                updated
            } else {
                item
            }
        }
        persistProviders()
    }

    fun delete(id: String) {
        val removed = _providers.value.firstOrNull { it.id == id }
        _providers.value = _providers.value.filterNot { it.id == id }
        persistProviders()
        if (removed != null) {
            RuntimeLogRepository.append("Provider deleted: ${removed.name}")
        }
    }

    private fun loadSavedProviders(): List<ProviderProfile>? {
        val raw = preferences?.getString(KEY_PROVIDERS_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val providerType = runCatching {
                        ProviderType.valueOf(item.optString("providerType"))
                    }.getOrDefault(ProviderType.OPENAI_COMPATIBLE)
                    val model = item.optString("model")
                    add(
                        ProviderProfile(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            baseUrl = item.optString("baseUrl"),
                            model = model,
                            providerType = providerType,
                            apiKey = item.optString("apiKey"),
                            capabilities = parseCapabilities(item.optJSONArray("capabilities"), providerType),
                            enabled = item.optBoolean("enabled", true),
                            multimodalRuleSupport = parseFeatureSupportState(
                                item.optString("multimodalRuleSupport"),
                                inferMultimodalRuleSupport(providerType, model),
                            ),
                            multimodalProbeSupport = parseFeatureSupportState(
                                item.optString("multimodalProbeSupport"),
                                FeatureSupportState.UNKNOWN,
                            ),
                        ),
                    )
                }
            }
        }.onFailure { error ->
            RuntimeLogRepository.append("Provider catalog load failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun persistProviders() {
        val json = JSONArray().apply {
            _providers.value.forEach { provider ->
                put(
                    JSONObject().apply {
                        put("id", provider.id)
                        put("name", provider.name)
                        put("baseUrl", provider.baseUrl)
                        put("model", provider.model)
                        put("providerType", provider.providerType.name)
                        put("apiKey", provider.apiKey)
                        put("enabled", provider.enabled)
                        put("multimodalRuleSupport", provider.multimodalRuleSupport.name)
                        put("multimodalProbeSupport", provider.multimodalProbeSupport.name)
                        put(
                            "capabilities",
                            JSONArray().apply {
                                provider.capabilities.forEach { capability ->
                                    put(capability.name)
                                }
                            },
                        )
                    },
                )
            }
        }
        preferences?.edit()?.putString(KEY_PROVIDERS_JSON, json.toString())?.apply()
    }

    private fun normalizeProvider(provider: ProviderProfile): ProviderProfile {
        val normalizedName = when {
            provider.id == "openai-chat" && provider.name.isBlank() -> "OpenAI Chat"
            provider.id == "openai-chat" && provider.name.contains("鐎电鐦?") -> "OpenAI Chat"
            provider.id == "openai-chat" && provider.name.contains("閻庣數顢婇惁") -> "OpenAI Chat"
            else -> provider.name
        }
        val normalizedModel = provider.model.trim()
        return provider.copy(
            name = normalizedName,
            model = normalizedModel,
            capabilities = provider.capabilities.ifEmpty { setOf(provider.providerType.defaultCapability()) },
            multimodalRuleSupport = inferMultimodalRuleSupport(provider.providerType, normalizedModel),
        )
    }

    private fun parseCapabilities(
        capabilityArray: JSONArray?,
        providerType: ProviderType,
    ): Set<ProviderCapability> {
        val parsed = buildSet {
            val source = capabilityArray ?: JSONArray()
            for (index in 0 until source.length()) {
                when (source.optString(index)) {
                    "ASR" -> add(ProviderCapability.STT)
                    else -> runCatching {
                        ProviderCapability.valueOf(source.getString(index))
                    }.getOrNull()?.let(::add)
                }
            }
        }
        return parsed.ifEmpty { setOf(providerType.defaultCapability()) }
    }

    private fun parseFeatureSupportState(raw: String, defaultValue: FeatureSupportState): FeatureSupportState {
        return runCatching { FeatureSupportState.valueOf(raw) }.getOrDefault(defaultValue)
    }

    private fun maskState(apiKey: String): String {
        return if (apiKey.isBlank()) "empty" else "configured"
    }

    private fun defaultProviders() = listOf(
        ProviderProfile(
            id = "openai-chat",
            name = "OpenAI Chat",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4.1-mini",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.SUPPORTED,
        ),
        ProviderProfile(
            id = "deepseek-chat",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            providerType = ProviderType.DEEPSEEK,
            apiKey = "",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.UNSUPPORTED,
        ),
    )
}
