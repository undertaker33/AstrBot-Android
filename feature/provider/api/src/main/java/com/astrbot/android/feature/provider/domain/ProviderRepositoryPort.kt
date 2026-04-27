package com.astrbot.android.feature.provider.domain

import com.astrbot.android.feature.provider.domain.model.FeatureSupportState
import com.astrbot.android.feature.provider.domain.model.ProviderCapability
import com.astrbot.android.feature.provider.domain.model.ProviderProfile
import kotlinx.coroutines.flow.StateFlow

interface ProviderRepositoryPort {
    val providers: StateFlow<List<ProviderProfile>>
    fun snapshotProfiles(): List<ProviderProfile>
    fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile>
    fun toggleEnabled(id: String)
    fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState)
    fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState)
    fun updateSttProbeSupport(id: String, support: FeatureSupportState)
    fun updateTtsProbeSupport(id: String, support: FeatureSupportState)
    suspend fun save(profile: ProviderProfile)
    suspend fun delete(id: String)
}
