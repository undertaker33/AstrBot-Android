package com.astrbot.android.core.runtime.search.profile

import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.model.ProviderProfile

interface ConfiguredSearchProvider {
    val providerId: String
    val providerName: String

    fun supports(profile: ProviderProfile): Boolean

    suspend fun search(
        profile: ProviderProfile,
        request: UnifiedSearchRequest,
    ): SearchProviderResult
}
