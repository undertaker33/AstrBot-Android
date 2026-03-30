package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.di.DefaultProviderViewModelDependencies
import com.astrbot.android.di.ProviderViewModelDependencies
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class ProviderViewModel(
    private val dependencies: ProviderViewModelDependencies = DefaultProviderViewModelDependencies,
) : ViewModel() {
    data class SttProbeResult(
        val state: FeatureSupportState,
        val transcript: String,
    )

    val providers: StateFlow<List<ProviderProfile>> = dependencies.providers
    val configProfiles: StateFlow<List<ConfigProfile>> = dependencies.configProfiles
    val selectedConfigProfileId: StateFlow<String> = dependencies.selectedConfigProfileId

    fun save(
        id: String?,
        name: String,
        baseUrl: String,
        model: String,
        providerType: ProviderType,
        apiKey: String,
        capabilities: Set<ProviderCapability>,
        enabled: Boolean = true,
        multimodalRuleSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        multimodalProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        nativeStreamingRuleSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        nativeStreamingProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        sttProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        ttsProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        ttsVoiceOptions: List<String> = emptyList(),
    ) {
        dependencies.save(
            ProviderProfile(
                id = id ?: "",
                name = name,
                baseUrl = baseUrl,
                model = model,
                providerType = providerType,
                apiKey = apiKey,
                capabilities = capabilities,
                enabled = enabled,
                multimodalRuleSupport = multimodalRuleSupport,
                multimodalProbeSupport = multimodalProbeSupport,
                nativeStreamingRuleSupport = nativeStreamingRuleSupport,
                nativeStreamingProbeSupport = nativeStreamingProbeSupport,
                sttProbeSupport = sttProbeSupport,
                ttsProbeSupport = ttsProbeSupport,
                ttsVoiceOptions = ttsVoiceOptions,
            ),
        )
    }

    fun toggleEnabled(id: String) {
        dependencies.toggleEnabled(id)
    }

    fun delete(id: String) {
        dependencies.delete(id)
    }

    fun updateMultimodalProbeSupport(id: String, probeSupport: FeatureSupportState) {
        dependencies.updateMultimodalProbeSupport(id, probeSupport)
    }

    fun updateNativeStreamingProbeSupport(id: String, probeSupport: FeatureSupportState) {
        dependencies.updateNativeStreamingProbeSupport(id, probeSupport)
    }

    fun updateSttProbeSupport(id: String, probeSupport: FeatureSupportState) {
        dependencies.updateSttProbeSupport(id, probeSupport)
    }

    fun updateTtsProbeSupport(id: String, probeSupport: FeatureSupportState) {
        dependencies.updateTtsProbeSupport(id, probeSupport)
    }

    fun saveConfig(profile: ConfigProfile) {
        dependencies.saveConfig(profile)
    }

    suspend fun fetchModels(provider: ProviderProfile): List<String> {
        return withContext(Dispatchers.IO) {
            dependencies.fetchModels(provider)
        }
    }

    fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
        return dependencies.detectMultimodalRule(provider)
    }

    suspend fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
        return withContext(Dispatchers.IO) {
            dependencies.probeMultimodalSupport(provider)
        }
    }

    fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState {
        return dependencies.detectNativeStreamingRule(provider)
    }

    suspend fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState {
        return withContext(Dispatchers.IO) {
            dependencies.probeNativeStreamingSupport(provider)
        }
    }

    suspend fun probeSttSupport(provider: ProviderProfile): SttProbeResult {
        val result = withContext(Dispatchers.IO) {
            dependencies.probeSttSupport(provider)
        }
        return SttProbeResult(
            state = result.state,
            transcript = result.transcript,
        )
    }

    suspend fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState {
        return withContext(Dispatchers.IO) {
            dependencies.probeTtsSupport(provider)
        }
    }

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        return dependencies.listVoiceChoicesFor(provider)
    }

    fun ttsAssetState(context: android.content.Context): com.astrbot.android.data.SherpaOnnxAssetManager.TtsAssetState {
        return dependencies.ttsAssetState(context)
    }

    fun isSherpaFrameworkReady(): Boolean {
        return dependencies.isSherpaFrameworkReady()
    }

    fun isSherpaSttReady(): Boolean {
        return dependencies.isSherpaSttReady()
    }

    suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return withContext(Dispatchers.IO) {
            dependencies.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
        }
    }
}
