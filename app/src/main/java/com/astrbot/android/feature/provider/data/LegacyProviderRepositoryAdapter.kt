package com.astrbot.android.feature.provider.data

import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.flow.StateFlow

class LegacyProviderRepositoryAdapter : ProviderRepositoryPort {

    override val providers: StateFlow<List<ProviderProfile>>
        get() = ProviderRepository.providers

    override fun snapshotProfiles(): List<ProviderProfile> =
        ProviderRepository.snapshotProfiles()

    override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> =
        ProviderRepository.providers.value.filter { capability in it.capabilities }

    override suspend fun save(profile: ProviderProfile) {
        ProviderRepository.save(
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
        ProviderRepository.delete(id)
    }
}
