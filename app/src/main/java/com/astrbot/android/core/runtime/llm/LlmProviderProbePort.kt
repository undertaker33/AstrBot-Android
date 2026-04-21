package com.astrbot.android.core.runtime.llm

import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment

/**
 * Standalone result type for STT probe operations.
 *
 * Extracted from [ChatCompletionService.SttProbeResult] so that feature-layer
 * code can depend on this port type without importing the static service.
 */
data class SttProbeResult(
    val state: FeatureSupportState,
    val transcript: String,
)

/**
 * Port interface that abstracts provider capability detection and media
 * operations previously accessed via [ChatCompletionService] static methods.
 *
 * Feature code should depend on this port (provided by Hilt) instead of
 * calling [ChatCompletionService] directly.
 */
interface LlmProviderProbePort {
    fun fetchModels(baseUrl: String, apiKey: String, providerType: com.astrbot.android.model.ProviderType): List<String>

    fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState

    fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState

    fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState

    fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState

    fun probeSttSupport(provider: ProviderProfile): SttProbeResult

    fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState

    fun transcribeAudio(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment
}
