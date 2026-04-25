package com.astrbot.android.runtime.search.api

import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkResponse
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.SseEvent
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.api.BaiduAiSearchProvider
import com.astrbot.android.core.runtime.search.api.BoChaSearchProvider
import com.astrbot.android.core.runtime.search.api.BraveSearchProvider
import com.astrbot.android.core.runtime.search.api.TavilySearchProvider
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiProvidersTest {
    @Test
    fun tavily_maps_url_bearing_results_and_drops_missing_urls() = runBlocking {
        val transport = RecordingTransport(
            """
            {
              "results": [
                {"title": "One", "url": "https://one.test", "content": "Snippet one", "domain": "one.test", "published_date": "2026-04-24"},
                {"title": "No URL", "content": "Missing"}
              ]
            }
            """.trimIndent(),
        )

        val result = TavilySearchProvider(transport).search(
            searchProfile(ProviderType.TAVILY_SEARCH),
            UnifiedSearchRequest(query = "latest kotlin", maxResults = 5),
        ) as SearchProviderResult.Success

        assertEquals(RuntimeNetworkCapability.WEB_SEARCH, transport.requests.single().capability)
        assertEquals("POST", transport.requests.single().method)
        assertEquals("Bearer key-1", transport.requests.single().headers["Authorization"])
        assertEquals(1, result.results.size)
        assertEquals("One", result.results.single().title)
        assertEquals("https://one.test", result.results.single().url)
        assertEquals("one.test", result.results.single().source)
        assertEquals("2026-04-24", result.results.single().publishedAt)
    }

    @Test
    fun brave_uses_subscription_token_and_does_not_fabricate_source() = runBlocking {
        val transport = RecordingTransport(
            """
            {
              "web": {
                "results": [
                  {"title": "Brave item", "url": "https://brave.test/a", "description": "Brave snippet", "family_friendly": true}
                ]
              }
            }
            """.trimIndent(),
        )

        val result = BraveSearchProvider(transport).search(
            searchProfile(ProviderType.BRAVE_SEARCH),
            UnifiedSearchRequest(query = "android search", maxResults = 5),
        ) as SearchProviderResult.Success

        assertEquals("key-1", transport.requests.single().headers["X-Subscription-Token"])
        assertTrue(transport.requests.single().url.contains("/res/v1/web/search?q=android+search"))
        assertEquals("Brave snippet", result.results.single().snippet)
        assertEquals("", result.results.single().source)
    }

    @Test
    fun bocha_maps_nested_web_pages_value_results() = runBlocking {
        val transport = RecordingTransport(
            """
            {
              "data": {
                "webPages": {
                  "value": [
                    {"name": "BoCha item", "url": "https://bocha.test/a", "snippet": "BoCha snippet", "siteName": "BoCha Site"}
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        val result = BoChaSearchProvider(transport).search(
            searchProfile(ProviderType.BOCHA_SEARCH),
            UnifiedSearchRequest(query = "bocha", maxResults = 5),
        ) as SearchProviderResult.Success

        assertEquals("POST", transport.requests.single().method)
        assertEquals("https://api.bochaai.com/v1/web-search", transport.requests.single().url)
        assertEquals("BoCha item", result.results.single().title)
        assertEquals("BoCha Site", result.results.single().source)
    }

    @Test
    fun baidu_ai_search_posts_qianfan_request_and_maps_references() = runBlocking {
        val transport = RecordingTransport(
            """
            {
              "references": [
                {
                  "title": "Baidu item",
                  "url": "https://baidu.test/a",
                  "content": "Baidu snippet",
                  "website": "Baidu Site",
                  "date": "2026-04-24"
                }
              ]
            }
            """.trimIndent(),
        )

        val result = BaiduAiSearchProvider(transport).search(
            searchProfile(ProviderType.BAIDU_AI_SEARCH),
            UnifiedSearchRequest(query = "baidu", maxResults = 3),
        ) as SearchProviderResult.Success

        val networkRequest = transport.requests.single()
        assertEquals(RuntimeNetworkCapability.WEB_SEARCH, networkRequest.capability)
        assertEquals("POST", networkRequest.method)
        assertEquals("https://qianfan.baidubce.com/v2/ai_search/chat/completions", networkRequest.url)
        assertEquals("application/json", networkRequest.headers["Content-Type"])
        assertEquals("Bearer key-1", networkRequest.headers["X-Appbuilder-Authorization"])

        val body = JSONObject(networkRequest.body!!.decodeToString())
        assertEquals(false, body.getBoolean("stream"))
        assertEquals("ernie-4.5-turbo-32k", body.getString("model"))
        assertEquals("baidu_search_v2", body.getString("search_source"))
        assertEquals("auto", body.getString("search_mode"))
        assertEquals(false, body.getBoolean("enable_deep_search"))
        assertEquals("user", body.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("baidu", body.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("web", body.getJSONArray("resource_type_filter").getJSONObject(0).getString("type"))
        assertEquals(3, body.getJSONArray("resource_type_filter").getJSONObject(0).getInt("top_k"))

        assertEquals(1, result.results.size)
        assertEquals("Baidu item", result.results.single().title)
        assertEquals("https://baidu.test/a", result.results.single().url)
        assertEquals("Baidu snippet", result.results.single().snippet)
        assertEquals("Baidu Site", result.results.single().source)
        assertEquals("2026-04-24", result.results.single().publishedAt)
        assertEquals("baidu_ai_search", result.results.single().providerId)
    }

    @Test
    fun baidu_ai_search_does_not_fabricate_results_from_choice_content() = runBlocking {
        val transport = RecordingTransport(
            """
            {
              "choices": [
                {
                  "message": {
                    "content": "This is a narrative answer, not a search result.",
                    "references": []
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val result = BaiduAiSearchProvider(transport).search(
            searchProfile(ProviderType.BAIDU_AI_SEARCH),
            UnifiedSearchRequest(query = "baidu", maxResults = 5),
        ) as SearchProviderResult.Success

        assertTrue(result.results.isEmpty())
        assertEquals(SearchAttemptStatus.EMPTY_RESULTS, result.diagnostics.single().status)
        assertEquals("empty_results", result.diagnostics.single().reason)
    }

    private fun searchProfile(type: ProviderType) = ProviderProfile(
        id = type.name.lowercase(),
        name = type.name,
        baseUrl = "",
        model = "",
        providerType = type,
        apiKey = "key-1",
        capabilities = setOf(ProviderCapability.SEARCH),
    )

    private class RecordingTransport(
        private val body: String,
    ) : RuntimeNetworkTransport {
        val requests = mutableListOf<RuntimeNetworkRequest>()

        override suspend fun execute(request: RuntimeNetworkRequest): RuntimeNetworkResponse {
            requests += request
            return RuntimeNetworkResponse(
                statusCode = 200,
                headers = emptyMap(),
                bodyBytes = body.encodeToByteArray(),
                traceId = request.traceContext.traceId,
                durationMs = 1,
            )
        }

        override fun openStream(request: RuntimeNetworkRequest): Flow<String> = error("unused")

        override fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent> = error("unused")
    }
}
