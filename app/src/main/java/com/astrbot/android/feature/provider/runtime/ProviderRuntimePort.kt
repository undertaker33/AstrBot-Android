package com.astrbot.android.feature.provider.runtime

import android.content.Context
import android.net.Uri
import com.astrbot.android.core.runtime.audio.SherpaOnnxAssetManager
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.core.runtime.llm.SttProbeResult
import com.astrbot.android.data.RuntimeAssetStateOwner
import com.astrbot.android.di.hilt.TtsVoiceAssetStateOwner
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.chat.ConversationAttachment
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val EmptyProviderProfilesStateFlow = MutableStateFlow<List<ProviderProfile>>(emptyList())
private val EmptyVoiceAssetsStateFlow = MutableStateFlow<List<TtsVoiceReferenceAsset>>(emptyList())

data class VoiceAssetImportResult(
    val asset: TtsVoiceReferenceAsset,
    val warning: String?,
)

interface ProviderRuntimePort {
    val providers: StateFlow<List<ProviderProfile>>
        get() = EmptyProviderProfilesStateFlow
    val voiceAssets: StateFlow<List<TtsVoiceReferenceAsset>>
        get() = EmptyVoiceAssetsStateFlow

    fun fetchModels(provider: ProviderProfile): List<String>

    fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState

    fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState

    fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState

    fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState

    fun probeSttSupport(provider: ProviderProfile): SttProbeResult

    fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>>

    fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String = "",
        assetId: String? = null,
    ): VoiceAssetImportResult

    fun saveVoiceBinding(
        assetId: String,
        providerId: String,
        providerType: ProviderType,
        model: String,
        voiceId: String,
        displayName: String,
    )

    fun renameVoiceBinding(assetId: String, bindingId: String, displayName: String)

    fun clearReferenceAudio(assetId: String)

    fun deleteReferenceClip(assetId: String, clipId: String)

    fun deleteVoiceBinding(assetId: String, bindingId: String)

    fun ttsAssetState(context: Context): SherpaOnnxAssetManager.TtsAssetState

    fun isSherpaFrameworkReady(): Boolean

    fun isSherpaSttReady(): Boolean

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment
}

internal class DefaultProviderRuntimePort @Inject constructor(
    private val providerRepositoryPort: ProviderRepositoryPort,
    private val probePort: LlmProviderProbePort,
    private val runtimeAssetStateOwner: RuntimeAssetStateOwner,
    private val ttsVoiceAssetStateOwner: TtsVoiceAssetStateOwner,
) : ProviderRuntimePort {
    override val providers: StateFlow<List<ProviderProfile>> = providerRepositoryPort.providers
    override val voiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = ttsVoiceAssetStateOwner.assets

    override fun fetchModels(provider: ProviderProfile): List<String> {
        return probePort.fetchModels(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            providerType = provider.providerType,
        )
    }

    override fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
        return probePort.detectMultimodalRule(provider)
    }

    override fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
        return probePort.probeMultimodalSupport(provider)
    }

    override fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState {
        return probePort.detectNativeStreamingRule(provider)
    }

    override fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState {
        return probePort.probeNativeStreamingSupport(provider)
    }

    override fun probeSttSupport(provider: ProviderProfile): SttProbeResult {
        return probePort.probeSttSupport(provider)
    }

    override fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState {
        return probePort.probeTtsSupport(provider)
    }

    override fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        return ttsVoiceAssetStateOwner.listVoiceChoicesFor(provider)
    }

    override fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String,
        assetId: String?,
    ): VoiceAssetImportResult {
        val result = ttsVoiceAssetStateOwner.importReferenceAudio(
            context = context,
            sourceUri = sourceUri,
            name = name,
            assetId = assetId,
        )
        return VoiceAssetImportResult(
            asset = result.asset,
            warning = result.warning,
        )
    }

    override fun saveVoiceBinding(
        assetId: String,
        providerId: String,
        providerType: ProviderType,
        model: String,
        voiceId: String,
        displayName: String,
    ) {
        ttsVoiceAssetStateOwner.saveProviderBinding(
            assetId = assetId,
            providerId = providerId,
            providerType = providerType,
            model = model,
            voiceId = voiceId,
            displayName = displayName,
        )
    }

    override fun renameVoiceBinding(assetId: String, bindingId: String, displayName: String) {
        ttsVoiceAssetStateOwner.renameBinding(
            assetId = assetId,
            bindingId = bindingId,
            displayName = displayName,
        )
    }

    override fun clearReferenceAudio(assetId: String) {
        ttsVoiceAssetStateOwner.clearReferenceAudio(assetId)
    }

    override fun deleteReferenceClip(assetId: String, clipId: String) {
        ttsVoiceAssetStateOwner.deleteReferenceClip(assetId, clipId)
    }

    override fun deleteVoiceBinding(assetId: String, bindingId: String) {
        ttsVoiceAssetStateOwner.deleteBinding(assetId, bindingId)
    }

    override fun ttsAssetState(context: Context): SherpaOnnxAssetManager.TtsAssetState {
        return runtimeAssetStateOwner.ttsAssetState(context)
    }

    override fun isSherpaFrameworkReady(): Boolean {
        return SherpaOnnxBridge.isFrameworkReady()
    }

    override fun isSherpaSttReady(): Boolean {
        return SherpaOnnxBridge.isSttReady()
    }

    override fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return probePort.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
    }
}
