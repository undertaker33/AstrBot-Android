package com.astrbot.android.di.hilt

import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.search.SearchProvider
import com.astrbot.android.core.runtime.search.UnifiedSearchCoordinator
import com.astrbot.android.core.runtime.search.UnifiedSearchPort
import com.astrbot.android.core.runtime.search.api.BaiduAiSearchProvider
import com.astrbot.android.core.runtime.search.api.BoChaSearchProvider
import com.astrbot.android.core.runtime.search.api.BraveSearchProvider
import com.astrbot.android.core.runtime.search.api.TavilySearchProvider
import com.astrbot.android.core.runtime.search.html.HtmlFallbackSearchProvider
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.provider.runtime.search.ProviderRepositorySearchProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object SearchRuntimeModule {

    @Provides
    @Singleton
    fun provideProviderRepositorySearchProvider(
        providerRepository: ProviderRepositoryPort,
        transport: RuntimeNetworkTransport,
    ): ProviderRepositorySearchProvider {
        return ProviderRepositorySearchProvider(
            providerRepository = providerRepository,
            nativeProviders = emptyList(),
            apiProviders = listOf(
                TavilySearchProvider(transport),
                BraveSearchProvider(transport),
                BoChaSearchProvider(transport),
                BaiduAiSearchProvider(transport),
            ),
        )
    }

    @Provides
    @Singleton
    fun provideUnifiedSearchPort(
        providerRepositorySearchProvider: ProviderRepositorySearchProvider,
        transport: RuntimeNetworkTransport,
    ): UnifiedSearchPort {
        val providers: List<SearchProvider> = listOf(
            providerRepositorySearchProvider,
            HtmlFallbackSearchProvider(transport),
        )
        return UnifiedSearchCoordinator(providers)
    }
}
