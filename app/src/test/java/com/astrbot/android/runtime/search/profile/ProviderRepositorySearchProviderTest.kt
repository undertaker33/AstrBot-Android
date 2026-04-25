package com.astrbot.android.runtime.search.profile

import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchCoordinator
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.UnifiedSearchResult
import com.astrbot.android.core.runtime.search.profile.ConfiguredSearchProvider
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.provider.runtime.search.ProviderRepositorySearchProvider
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderRepositorySearchProviderTest {
    @Test
    fun chat_provider_is_ignored_when_native_providers_are_empty() = runBlocking {
        val calls = mutableListOf<String>()
        val api = fakeConfiguredProvider("api") {
            calls += "api"
            SearchProviderResult.Success(
                results = listOf(searchResult("https://api.test")),
                providerOverride = "api",
            )
        }
        val provider = ProviderRepositorySearchProvider(
            providerRepository = FakeProviderRepository(
                listOf(
                    profile("chat", ProviderType.GEMINI, ProviderCapability.CHAT),
                    profile("api", ProviderType.TAVILY_SEARCH, ProviderCapability.SEARCH),
                ),
            ),
            nativeProviders = emptyList(),
            apiProviders = listOf(api),
        )

        val response = UnifiedSearchCoordinator(listOf(provider)).search(
            UnifiedSearchRequest(query = "api only", maxResults = 5),
        )

        assertEquals(listOf("api"), calls)
        assertEquals("api", response.provider)
        assertEquals("https://api.test", response.results.single().url)
    }

    @Test
    fun search_capability_provider_is_used_without_native_attempt() = runBlocking {
        val calls = mutableListOf<String>()
        val api = fakeConfiguredProvider("api") {
            calls += "api"
            SearchProviderResult.Success(
                results = listOf(searchResult("https://api.test")),
                providerOverride = "api",
            )
        }
        val provider = ProviderRepositorySearchProvider(
            providerRepository = FakeProviderRepository(
                listOf(
                    profile("chat", ProviderType.GEMINI, ProviderCapability.CHAT),
                    profile("api", ProviderType.TAVILY_SEARCH, ProviderCapability.SEARCH),
                ),
            ),
            nativeProviders = emptyList(),
            apiProviders = listOf(api),
        )

        val response = UnifiedSearchCoordinator(listOf(provider)).search(
            UnifiedSearchRequest(query = "api fallback", maxResults = 5),
        )

        assertEquals(listOf("api"), calls)
        assertEquals("api", response.provider)
        assertEquals(false, response.fallbackUsed)
        assertEquals("https://api.test", response.results.single().url)
    }

    @Test
    fun url_less_configured_api_result_is_accepted() = runBlocking {
        val calls = mutableListOf<String>()
        val api = fakeConfiguredProvider("api") {
            calls += "api"
            SearchProviderResult.Success(
                results = listOf(
                    UnifiedSearchResult(
                        index = 1,
                        title = "Configured searched answer",
                        url = "",
                        snippet = "Answer from configured search",
                        source = "Configured search",
                    ),
                ),
                providerOverride = "api",
            )
        }
        val provider = ProviderRepositorySearchProvider(
            providerRepository = FakeProviderRepository(
                listOf(
                    profile("api", ProviderType.TAVILY_SEARCH, ProviderCapability.SEARCH),
                ),
            ),
            nativeProviders = emptyList(),
            apiProviders = listOf(api),
        )

        val response = UnifiedSearchCoordinator(listOf(provider)).search(
            UnifiedSearchRequest(query = "configured no urls", maxResults = 5),
        )

        assertEquals(listOf("api"), calls)
        assertEquals("api", response.provider)
        assertEquals("", response.results.single().url)
        assertEquals("Configured search", response.results.single().source)
    }

    private fun fakeConfiguredProvider(
        id: String,
        block: suspend () -> SearchProviderResult,
    ): ConfiguredSearchProvider {
        return object : ConfiguredSearchProvider {
            override val providerId: String = id
            override val providerName: String = id

            override fun supports(profile: ProviderProfile): Boolean = true

            override suspend fun search(
                profile: ProviderProfile,
                request: UnifiedSearchRequest,
            ): SearchProviderResult = block()
        }
    }

    private fun profile(
        id: String,
        type: ProviderType,
        capability: ProviderCapability,
    ) = ProviderProfile(
        id = id,
        name = id,
        baseUrl = "",
        model = "",
        providerType = type,
        apiKey = "key-1",
        capabilities = setOf(capability),
    )

    private fun searchResult(url: String) = UnifiedSearchResult(
        index = 1,
        title = "Result",
        url = url,
        snippet = "Snippet",
    )

    private class FakeProviderRepository(
        initialProfiles: List<ProviderProfile>,
    ) : ProviderRepositoryPort {
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(initialProfiles)

        override fun snapshotProfiles(): List<ProviderProfile> = providers.value

        override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> {
            return providers.value.filter { capability in it.capabilities }
        }

        override fun toggleEnabled(id: String) = Unit
        override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateSttProbeSupport(id: String, support: FeatureSupportState) = Unit
        override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) = Unit
        override suspend fun save(profile: ProviderProfile) = Unit
        override suspend fun delete(id: String) = Unit
    }
}
