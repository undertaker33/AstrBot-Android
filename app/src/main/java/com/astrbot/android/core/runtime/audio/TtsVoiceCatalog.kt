package com.astrbot.android.core.runtime.audio

import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType

object TtsVoiceCatalog {
    const val CUSTOM_VOICE_ID = "__custom__"

    fun optionsFor(provider: ProviderProfile?): List<Pair<String, String>> {
        if (provider == null) return emptyList()
        val presets = when (provider.providerType) {
            ProviderType.OPENAI_TTS -> listOf(
                "alloy",
                "echo",
                "fable",
                "onyx",
                "nova",
                "shimmer",
            )

            ProviderType.BAILIAN_TTS -> if (provider.model.trim().lowercase().contains("-vc")) {
                emptyList()
            } else {
                listOf("Cherry")
            }

            ProviderType.MINIMAX_TTS -> listOf("Chinese (Mandarin)_Warm_Girl")

            ProviderType.SHERPA_ONNX_TTS -> emptyList()

            else -> emptyList()
        }
        return buildList {
            if (provider.providerType == ProviderType.SHERPA_ONNX_TTS) {
                addAll(OnDeviceTtsCatalog.voicesFor(provider.model).map { it.id to it.label })
            } else {
                addAll(
                    presets.distinct().map { voiceId ->
                        voiceId to voiceId
                    },
                )
            }
        }
    }

    fun resolveSelectedVoiceChoice(
        provider: ProviderProfile?,
        voiceId: String,
    ): String {
        val options = optionsFor(provider)
        val normalized = voiceId.trim()
        if (normalized.isBlank()) {
            return options.firstOrNull()?.first.orEmpty()
        }
        val availableIds = options.map { it.first }.toSet()
        return if (normalized in availableIds) normalized else CUSTOM_VOICE_ID
    }
}
