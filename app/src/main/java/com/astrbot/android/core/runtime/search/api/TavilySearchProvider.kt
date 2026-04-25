package com.astrbot.android.core.runtime.search.api

import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.profile.ConfiguredSearchProvider
import com.astrbot.android.core.runtime.search.profile.appendPath
import com.astrbot.android.core.runtime.search.profile.emptySearchSuccess
import com.astrbot.android.core.runtime.search.profile.networkFailureResult
import com.astrbot.android.core.runtime.search.profile.normalizedBaseUrl
import com.astrbot.android.core.runtime.search.profile.objects
import com.astrbot.android.core.runtime.search.profile.parseFailureResult
import com.astrbot.android.core.runtime.search.profile.renumbered
import com.astrbot.android.core.runtime.search.profile.unifiedResult
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import org.json.JSONObject

class TavilySearchProvider(
    private val transport: RuntimeNetworkTransport,
) : ConfiguredSearchProvider {
    override val providerId: String = "tavily_search"
    override val providerName: String = "Tavily Search"

    override fun supports(profile: ProviderProfile): Boolean {
        return profile.enabled && profile.providerType == ProviderType.TAVILY_SEARCH && profile.apiKey.isNotBlank()
    }

    override suspend fun search(
        profile: ProviderProfile,
        request: UnifiedSearchRequest,
    ): SearchProviderResult {
        return try {
            val body = JSONObject().apply {
                put("query", request.query)
                put("search_depth", "basic")
                put("max_results", request.maxResults)
                put("include_answer", false)
                put("include_images", false)
            }.toString()
            val response = transport.execute(
                RuntimeNetworkRequest(
                    capability = RuntimeNetworkCapability.WEB_SEARCH,
                    method = "POST",
                    url = profile.normalizedBaseUrl(DEFAULT_BASE_URL).appendPath("search"),
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer ${profile.apiKey.trim()}",
                    ),
                    body = body.encodeToByteArray(),
                    contentType = "application/json",
                    timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
                ),
            )
            val root = JSONObject(response.bodyString)
            val results = root.optJSONArray("results")
                ?.objects()
                ?.mapNotNull { item ->
                    unifiedResult(
                        index = 1,
                        title = item.optString("title"),
                        url = item.optString("url"),
                        snippet = item.optString("content"),
                        source = item.optString("domain"),
                        providerId = providerId,
                        publishedAt = item.optString("published_date").takeIf(String::isNotBlank),
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
        private const val DEFAULT_BASE_URL = "https://api.tavily.com"
    }
}
