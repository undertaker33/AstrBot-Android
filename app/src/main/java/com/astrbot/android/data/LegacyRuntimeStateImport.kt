package com.astrbot.android.data

import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.TtsVoiceReferenceClip
import org.json.JSONArray

fun parseLegacyNapCatBridgeConfig(
    defaults: NapCatBridgeConfig = NapCatBridgeConfig(),
    values: Map<String, Any?>,
): NapCatBridgeConfig {
    return mergeNapCatBridgeConfig(defaults, values)
}

internal fun mergeNapCatBridgeConfig(
    defaults: NapCatBridgeConfig = NapCatBridgeConfig(),
    values: Map<String, Any?>,
): NapCatBridgeConfig {
    return defaults.copy(
        runtimeMode = values["runtime_mode"]?.toString().orEmpty().ifBlank { defaults.runtimeMode },
        endpoint = values["endpoint"]?.toString().orEmpty().ifBlank { defaults.endpoint },
        healthUrl = values["health_url"]?.toString().orEmpty().ifBlank { defaults.healthUrl },
        autoStart = (values["auto_start"] as? Boolean) ?: defaults.autoStart,
        startCommand = sanitizeBridgeCommand(
            candidate = values["start_command"]?.toString(),
            fallback = defaults.startCommand,
        ),
        stopCommand = sanitizeBridgeCommand(
            candidate = values["stop_command"]?.toString(),
            fallback = defaults.stopCommand,
        ),
        statusCommand = sanitizeBridgeCommand(
            candidate = values["status_command"]?.toString(),
            fallback = defaults.statusCommand,
        ),
        commandPreview = sanitizeBridgeCommand(
            candidate = values["command_preview"]?.toString(),
            fallback = defaults.commandPreview,
        ),
    )
}

private fun sanitizeBridgeCommand(candidate: String?, fallback: String): String {
    val value = candidate.orEmpty().trim()
    if (value.isBlank()) return fallback
    return if (value.contains("/data/local/tmp/napcat/", ignoreCase = true)) {
        fallback
    } else {
        value
    }
}

fun parseLegacyTtsVoiceAssets(raw: String?): List<TtsVoiceReferenceAsset> {
    val source = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
    val array = JSONArray(source)
    return buildList {
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
                    clips = buildList {
                        val clipsArray = item.optJSONArray("clips")
                        if (clipsArray != null && clipsArray.length() > 0) {
                            for (clipIndex in 0 until clipsArray.length()) {
                                val clip = clipsArray.optJSONObject(clipIndex) ?: continue
                                add(
                                    TtsVoiceReferenceClip(
                                        id = clip.optString("id"),
                                        localPath = clip.optString("localPath"),
                                        durationMs = clip.optLong("durationMs"),
                                        sampleRateHz = clip.optInt("sampleRateHz"),
                                        createdAt = clip.optLong("createdAt"),
                                    ),
                                )
                            }
                        } else if (item.optString("localPath").isNotBlank()) {
                            add(
                                TtsVoiceReferenceClip(
                                    id = "${item.optString("id")}-legacy",
                                    localPath = item.optString("localPath"),
                                    durationMs = item.optLong("durationMs"),
                                    sampleRateHz = item.optInt("sampleRateHz"),
                                    createdAt = item.optLong("createdAt"),
                                ),
                            )
                        }
                    },
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
}
