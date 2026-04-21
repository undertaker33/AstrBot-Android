@file:Suppress("DEPRECATION")

package com.astrbot.android.runtime.llm

import android.content.Context
import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.core.runtime.llm.SttProbeResult
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment

/**
 * Adapter that bridges [LlmProviderProbePort] to the static
 * [ChatCompletionService] singleton.
 *
 * Lives in the `runtime/llm` adapter layer where direct access to
 * [ChatCompletionService] is permitted. Feature-layer code should
 * depend on [LlmProviderProbePort] (provided by Hilt) instead.
 */
internal class LegacyLlmProviderProbeAdapter(
    context: Context? = null,
) : LlmProviderProbePort {

    init {
        context?.let(ChatCompletionService::initialize)
    }

    override fun fetchModels(baseUrl: String, apiKey: String, providerType: ProviderType): List<String> {
        return ChatCompletionService.fetchModels(baseUrl, apiKey, providerType)
    }

    override fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.detectMultimodalRule(provider)
    }

    override fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.probeMultimodalSupport(provider)
    }

    override fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.detectNativeStreamingRule(provider)
    }

    override fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.probeNativeStreamingSupport(provider)
    }

    override fun probeSttSupport(provider: ProviderProfile): SttProbeResult {
        val result = ChatCompletionService.probeSttSupport(provider)
        return SttProbeResult(state = result.state, transcript = result.transcript)
    }

    override fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState {
        return ChatCompletionService.probeTtsSupport(provider)
    }

    override fun transcribeAudio(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String {
        return ChatCompletionService.transcribeAudio(provider, attachment)
    }

    override fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return ChatCompletionService.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
    }
}
