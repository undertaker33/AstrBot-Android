package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.runtime.RuntimeLogRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object TtsVoiceAssetRepository {
    private const val PREFS_NAME = "tts_voice_assets"
    private const val KEY_ASSETS_JSON = "assets_json"

    private var preferences: SharedPreferences? = null
    private val _assets = MutableStateFlow<List<TtsVoiceReferenceAsset>>(emptyList())

    val assets: StateFlow<List<TtsVoiceReferenceAsset>> = _assets.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _assets.value = loadAssets().orEmpty()
        RuntimeLogRepository.append("TTS voice assets loaded: count=${_assets.value.size}")
    }

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        if (provider == null) return emptyList()
        return _assets.value
            .flatMap { asset ->
                asset.providerBindings.mapNotNull { binding ->
                    if (binding.providerId == provider.id) {
                        binding.voiceId to binding.displayName
                    } else {
                        null
                    }
                }
            }
            .distinctBy { it.first }
    }

    fun upsertReferenceAsset(
        id: String? = null,
        name: String,
        source: String = "",
        localPath: String = "",
        remoteUrl: String = "",
        durationMs: Long = 0L,
        sampleRateHz: Int = 0,
    ): TtsVoiceReferenceAsset {
        val resolvedId = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val current = _assets.value.firstOrNull { it.id == resolvedId }
        val updated = (current ?: TtsVoiceReferenceAsset(id = resolvedId, name = name)).copy(
            name = name.trim().ifBlank { "Unnamed voice asset" },
            source = source.trim(),
            localPath = localPath.trim(),
            remoteUrl = remoteUrl.trim(),
            durationMs = durationMs.coerceAtLeast(0L),
            sampleRateHz = sampleRateHz.coerceAtLeast(0),
        )
        _assets.value = _assets.value
            .filterNot { it.id == resolvedId }
            .plus(updated)
            .sortedByDescending { it.createdAt }
        persist()
        return updated
    }

    fun saveProviderBinding(
        assetId: String,
        providerId: String,
        providerType: ProviderType,
        model: String,
        voiceId: String,
        displayName: String,
    ) {
        if (assetId.isBlank() || providerId.isBlank() || voiceId.isBlank()) return
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) {
                asset
            } else {
                val bindingId = "${providerId}:${model.trim()}:${voiceId.trim()}"
                val newBinding = ClonedVoiceBinding(
                    id = bindingId,
                    providerId = providerId,
                    providerType = providerType,
                    model = model.trim(),
                    voiceId = voiceId.trim(),
                    displayName = displayName.trim().ifBlank { voiceId.trim() },
                    createdAt = System.currentTimeMillis(),
                    lastVerifiedAt = System.currentTimeMillis(),
                )
                asset.copy(
                    providerBindings = asset.providerBindings
                        .filterNot { it.id == bindingId }
                        .plus(newBinding)
                        .sortedByDescending { it.createdAt },
                )
            }
        }
        persist()
    }

    fun renameBinding(assetId: String, bindingId: String, displayName: String) {
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) {
                asset
            } else {
                asset.copy(
                    providerBindings = asset.providerBindings.map { binding ->
                        if (binding.id == bindingId) binding.copy(displayName = displayName.trim().ifBlank { binding.voiceId }) else binding
                    },
                )
            }
        }
        persist()
    }

    fun deleteReferenceAsset(assetId: String) {
        _assets.value = _assets.value.filterNot { it.id == assetId }
        persist()
    }

    fun deleteBinding(assetId: String, bindingId: String) {
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) {
                asset
            } else {
                asset.copy(providerBindings = asset.providerBindings.filterNot { it.id == bindingId })
            }
        }
        persist()
    }

    private fun loadAssets(): List<TtsVoiceReferenceAsset>? {
        val raw = preferences?.getString(KEY_ASSETS_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        TtsVoiceReferenceAsset(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            source = item.optString("source"),
                            localPath = item.optString("localPath"),
                            remoteUrl = item.optString("remoteUrl"),
                            durationMs = item.optLong("durationMs"),
                            sampleRateHz = item.optInt("sampleRateHz"),
                            createdAt = item.optLong("createdAt"),
                            providerBindings = buildList {
                                val bindings = item.optJSONArray("providerBindings") ?: JSONArray()
                                for (bindingIndex in 0 until bindings.length()) {
                                    val binding = bindings.optJSONObject(bindingIndex) ?: continue
                                    val providerType = runCatching {
                                        ProviderType.valueOf(binding.optString("providerType"))
                                    }.getOrDefault(ProviderType.OPENAI_TTS)
                                    add(
                                        ClonedVoiceBinding(
                                            id = binding.optString("id"),
                                            providerId = binding.optString("providerId"),
                                            providerType = providerType,
                                            model = binding.optString("model"),
                                            voiceId = binding.optString("voiceId"),
                                            displayName = binding.optString("displayName"),
                                            createdAt = binding.optLong("createdAt"),
                                            lastVerifiedAt = binding.optLong("lastVerifiedAt"),
                                            status = binding.optString("status").ifBlank { "ready" },
                                        ),
                                    )
                                }
                            },
                        ),
                    )
                }
            }
        }.onFailure { error ->
            RuntimeLogRepository.append("TTS voice assets load failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun persist() {
        val json = JSONArray().apply {
            _assets.value.forEach { asset ->
                put(
                    JSONObject().apply {
                        put("id", asset.id)
                        put("name", asset.name)
                        put("source", asset.source)
                        put("localPath", asset.localPath)
                        put("remoteUrl", asset.remoteUrl)
                        put("durationMs", asset.durationMs)
                        put("sampleRateHz", asset.sampleRateHz)
                        put("createdAt", asset.createdAt)
                        put(
                            "providerBindings",
                            JSONArray().apply {
                                asset.providerBindings.forEach { binding ->
                                    put(
                                        JSONObject().apply {
                                            put("id", binding.id)
                                            put("providerId", binding.providerId)
                                            put("providerType", binding.providerType.name)
                                            put("model", binding.model)
                                            put("voiceId", binding.voiceId)
                                            put("displayName", binding.displayName)
                                            put("createdAt", binding.createdAt)
                                            put("lastVerifiedAt", binding.lastVerifiedAt)
                                            put("status", binding.status)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
        preferences?.edit()?.putString(KEY_ASSETS_JSON, json.toString())?.apply()
    }
}
