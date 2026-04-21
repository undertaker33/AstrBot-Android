package com.astrbot.android.feature.provider.data

import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.flow.StateFlow

@Suppress("DEPRECATION")
open class FeatureProviderRepositoryPortAdapter : ProviderRepositoryPort {

    override val providers: StateFlow<List<ProviderProfile>>
        get() = FeatureProviderRepository.providers

    override fun snapshotProfiles(): List<ProviderProfile> =
        FeatureProviderRepository.snapshotProfiles()

    override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> =
        FeatureProviderRepository.providers.value.filter { capability in it.capabilities }

    override fun toggleEnabled(id: String) {
        FeatureProviderRepository.toggleEnabled(id)
    }

    override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) {
        FeatureProviderRepository.updateMultimodalProbeSupport(id, support)
    }

    override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) {
        FeatureProviderRepository.updateNativeStreamingProbeSupport(id, support)
    }

    override fun updateSttProbeSupport(id: String, support: FeatureSupportState) {
        FeatureProviderRepository.updateSttProbeSupport(id, support)
    }

    override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) {
        FeatureProviderRepository.updateTtsProbeSupport(id, support)
    }

    override suspend fun save(profile: ProviderProfile) {
        FeatureProviderRepository.save(
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
        FeatureProviderRepository.delete(id)
    }
}

/**
 * Compat-only adapter for targeted tests and transitional callers.
 * Production mainline uses [FeatureProviderRepositoryPortAdapter].
 */
@Deprecated(
    "Compat-only seam. Production mainline uses FeatureProviderRepositoryPortAdapter.",
    level = DeprecationLevel.WARNING,
)
class LegacyProviderRepositoryAdapter : FeatureProviderRepositoryPortAdapter()

