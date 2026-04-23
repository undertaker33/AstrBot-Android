package com.astrbot.android.feature.provider.data

import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeatureProviderRepositoryPortAdapter @Inject constructor(
    private val repository: FeatureProviderRepositoryStore,
) : ProviderRepositoryPort {

    override val providers: StateFlow<List<ProviderProfile>>
        get() = repository.providers

    override fun snapshotProfiles(): List<ProviderProfile> =
        repository.snapshotProfiles()

    override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> =
        repository.providers.value.filter { capability in it.capabilities }

    override fun toggleEnabled(id: String) {
        repository.toggleEnabled(id)
    }

    override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) {
        repository.updateMultimodalProbeSupport(id, support)
    }

    override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) {
        repository.updateNativeStreamingProbeSupport(id, support)
    }

    override fun updateSttProbeSupport(id: String, support: FeatureSupportState) {
        repository.updateSttProbeSupport(id, support)
    }

    override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) {
        repository.updateTtsProbeSupport(id, support)
    }

    override suspend fun save(profile: ProviderProfile) {
        repository.save(
            id = profile.id,
            name = profile.name,
            baseUrl = profile.baseUrl,
            model = profile.model,
            providerType = profile.providerType,
            apiKey = profile.apiKey,
            capabilities = profile.capabilities,
            enabled = profile.enabled,
            multimodalRuleSupport = profile.multimodalRuleSupport,
            multimodalProbeSupport = profile.multimodalProbeSupport,
            nativeStreamingRuleSupport = profile.nativeStreamingRuleSupport,
            nativeStreamingProbeSupport = profile.nativeStreamingProbeSupport,
            sttProbeSupport = profile.sttProbeSupport,
            ttsProbeSupport = profile.ttsProbeSupport,
            ttsVoiceOptions = profile.ttsVoiceOptions,
        )
    }

    override suspend fun delete(id: String) {
        repository.delete(id)
    }
}
