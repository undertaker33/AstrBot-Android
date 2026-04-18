package com.astrbot.android.feature.provider.domain

import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.flow.StateFlow

interface ProviderRepositoryPort {
    val providers: StateFlow<List<ProviderProfile>>
    fun snapshotProfiles(): List<ProviderProfile>
    fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile>
    suspend fun save(profile: ProviderProfile)
    suspend fun delete(id: String)
}
