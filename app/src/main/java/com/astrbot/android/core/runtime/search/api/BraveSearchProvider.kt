package com.astrbot.android.core.runtime.search.api

import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.profile.ConfiguredSearchProvider
import com.astrbot.android.core.runtime.search.profile.encodeQuery
import com.astrbot.android.core.runtime.search.profile.emptySearchSuccess
import com.astrbot.android.core.runtime.search.profile.networkFailureResult
import com.astrbot.android.core.runtime.search.profile.normalizedBaseUrl
import com.astrbot.android.core.runtime.search.profile.objects
import com.astrbot.android.core.runtime.search.profile.optArray
import com.astrbot.android.core.runtime.search.profile.parseFailureResult
import com.astrbot.android.core.runtime.search.profile.renumbered
import com.astrbot.android.core.runtime.search.profile.unifiedResult
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import org.json.JSONObject

class BraveSearchProvider(
    private val transport: RuntimeNetworkTransport,
) : ConfiguredSearchProvider {
    override val providerId: String = "brave_search"
    override val providerName: String = "Brave Search"

    override fun supports(profile: ProviderProfile): Boolean {
        return profile.enabled && profile.providerType == ProviderType.BRAVE_SEARCH && profile.apiKey.isNotBlank()
    }

    override suspend fun search(
        profile: ProviderProfile,
        request: UnifiedSearchRequest,
    ): SearchProviderResult {
        return try {
            val url = profile.normalizedBaseUrl(DEFAULT_BASE_URL) +
                "/res/v1/web/search?q=${encodeQuery(request.query)}&count=${request.maxResults.coerceIn(1, 20)}"
            val response = transport.execute(
                RuntimeNetworkRequest(
                    capability = RuntimeNetworkCapability.WEB_SEARCH,
                    method = "GET",
                    url = url,
                    headers = mapOf(
                        "Accept" to "application/json",
                        "X-Subscription-Token" to profile.apiKey.trim(),
                    ),
                    timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
                ),
            )
            val root = JSONObject(response.bodyString)
            val results = root.optArray("web.results")
                ?.objects()
                ?.mapNotNull { item ->
                    unifiedResult(
                        index = 1,
                        title = item.optString("title"),
                        url = item.optString("url"),
                        snippet = item.optString("description").ifBlank { item.optString("snippet") },
                        providerId = providerId,
                    )
                }
                ?.toList()
                ?.take(request.maxResults)
                ?.renumbered()
                .orEmpty()
            if (results.isEmpty()) {
                emptySearchSuccess(providerId, providerName)
            } else {
                SearchProviderResult.Success(results = results, providerOverride = providerId)
            }
        } catch (e: RuntimeNetworkException) {
            networkFailureResult(providerId, providerName, e)
        } catch (e: Exception) {
            parseFailureResult(providerId, providerName, e)
        }
    }

    private companion object {
        private const val DEFAULT_BASE_URL = "https://api.search.brave.com"
    }
}
