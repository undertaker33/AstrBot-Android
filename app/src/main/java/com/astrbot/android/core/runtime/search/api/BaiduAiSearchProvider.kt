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
import org.json.JSONArray
import org.json.JSONObject

class BaiduAiSearchProvider(
    private val transport: RuntimeNetworkTransport,
) : ConfiguredSearchProvider {
    override val providerId: String = "baidu_ai_search"
    override val providerName: String = "Baidu AI Search"

    override fun supports(profile: ProviderProfile): Boolean {
        return profile.enabled && profile.providerType == ProviderType.BAIDU_AI_SEARCH && profile.apiKey.isNotBlank()
    }

    override suspend fun search(
        profile: ProviderProfile,
        request: UnifiedSearchRequest,
    ): SearchProviderResult {
        return try {
            val body = JSONObject().apply {
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", request.query)
                        },
                    ),
                )
                put("stream", false)
                put("model", profile.model.ifBlank { DEFAULT_MODEL })
                put("search_source", "baidu_search_v2")
                put(
                    "resource_type_filter",
                    JSONArray().put(
                        JSONObject().apply {
                            put("type", "web")
                            put("top_k", request.maxResults)
                        },
                    ),
                )
                put("search_mode", "auto")
                put("enable_deep_search", false)
            }.toString()
            val response = transport.execute(
                RuntimeNetworkRequest(
                    capability = RuntimeNetworkCapability.WEB_SEARCH,
                    method = "POST",
                    url = profile.normalizedBaseUrl(DEFAULT_BASE_URL).appendPath("v2/ai_search/chat/completions"),
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "X-Appbuilder-Authorization" to "Bearer ${profile.apiKey.trim()}",
                    ),
                    body = body.encodeToByteArray(),
                    contentType = "application/json",
                    timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
                ),
            )
            val root = JSONObject(response.bodyString)
            val references = root.optArray("references")
                ?: root.optArray("data.references")
                ?: root.optArray("choices.0.message.references")
            val results = references
                ?.objects()
                ?.mapNotNull { item ->
                    unifiedResult(
                        index = 1,
                        title = item.optString("title").ifBlank { item.optString("name") },
                        url = item.optString("url").ifBlank { item.optString("link") },
                        snippet = item.optString("content")
                            .ifBlank { item.optString("snippet") }
                            .ifBlank { item.optString("summary") },
                        source = item.optString("website")
                            .ifBlank { item.optString("source") }
                            .ifBlank { item.optString("site_name") },
                        providerId = providerId,
                        publishedAt = item.optString("date")
                            .ifBlank { item.optString("publish_time") }
                            .ifBlank { item.optString("published_at") }
                            .takeIf(String::isNotBlank),
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
        private const val DEFAULT_BASE_URL = "https://qianfan.baidubce.com"
        private const val DEFAULT_MODEL = "ernie-4.5-turbo-32k"
    }
}
