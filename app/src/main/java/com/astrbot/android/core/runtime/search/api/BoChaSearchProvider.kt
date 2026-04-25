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
import com.astrbot.android.core.runtime.search.profile.optArray
import com.astrbot.android.core.runtime.search.profile.parseFailureResult
import com.astrbot.android.core.runtime.search.profile.renumbered
import com.astrbot.android.core.runtime.search.profile.unifiedResult
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import org.json.JSONObject

class BoChaSearchProvider(
    private val transport: RuntimeNetworkTransport,
) : ConfiguredSearchProvider {
    override val providerId: String = "bocha_search"
    override val providerName: String = "BoCha Search"

    override fun supports(profile: ProviderProfile): Boolean {
        return profile.enabled && profile.providerType == ProviderType.BOCHA_SEARCH && profile.apiKey.isNotBlank()
    }

    override suspend fun search(
        profile: ProviderProfile,
        request: UnifiedSearchRequest,
    ): SearchProviderResult {
        return try {
            val body = JSONObject().apply {
                put("query", request.query)
                put("count", request.maxResults)
            }.toString()
            val response = transport.execute(
                RuntimeNetworkRequest(
                    capability = RuntimeNetworkCapability.WEB_SEARCH,
                    method = "POST",
                    url = profile.normalizedBaseUrl(DEFAULT_BASE_URL).appendPath("v1/web-search"),
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
            val array = root.optArray("data.webPages.value")
                ?: root.optArray("webPages.value")
                ?: root.optJSONArray("results")
            val results = array
                ?.objects()
                ?.mapNotNull { item ->
                    unifiedResult(
                        index = 1,
                        title = item.optString("name").ifBlank { item.optString("title") },
                        url = item.optString("url"),
                        snippet = item.optString("snippet").ifBlank { item.optString("summary") },
                        source = item.optString("siteName").ifBlank { item.optString("source") },
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
        private const val DEFAULT_BASE_URL = "https://api.bochaai.com"
    }
}
