package com.astrbot.android.runtime.search.html

import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkFailure
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkResponse
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.astrbot.android.core.runtime.network.SseEvent
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.html.BingHtmlSearchProvider
import com.astrbot.android.core.runtime.search.html.HtmlFallbackSearchProvider
import com.astrbot.android.core.runtime.search.html.HtmlSearchResult
import com.astrbot.android.core.runtime.search.html.SearchEngine
import com.astrbot.android.core.runtime.search.html.SogouHtmlSearchProvider
import com.astrbot.android.core.runtime.search.html.assessBatchRelevance
import com.astrbot.android.core.runtime.search.html.parseBingResults
import com.astrbot.android.core.runtime.search.html.parseSogouResults
import com.astrbot.android.core.runtime.search.html.toUnifiedResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSearchProviderTest {
    @Test
    fun html_fallback_prefers_bing_for_general_queries_before_sogou() = runBlocking {
        val called = mutableListOf<String>()
        val provider = HtmlFallbackSearchProvider(
            bingProvider = fakeEngineProvider(SearchEngine.BING, called) {
                listOf(
                    HtmlSearchResult(
                        title = "Bing forecast",
                        url = "https://bing.test/weather",
                        snippet = "18~25 light rain",
                        engine = "bing",
                        module = "bing_b_algo",
                    ),
                )
            },
            sogouProvider = fakeEngineProvider(SearchEngine.SOGOU, called) {
                listOf(
                    HtmlSearchResult(
                        title = "Fuzhou weather",
                        url = "https://sogou.test/weather",
                        snippet = "18~25 light rain",
                        engine = "sogou",
                        module = "sogou_rb",
                    ),
                )
            },
        )

        val result = provider.search(UnifiedSearchRequest(query = "kotlin hilt guide", maxResults = 3))

        assertEquals(listOf("bing"), called)
        assertEquals("Bing forecast", result.results.single().title)
        assertEquals(SearchAttemptStatus.SUCCESS, result.diagnostics.single().status)
    }

    @Test
    fun html_fallback_uses_sogou_when_bing_is_empty() = runBlocking {
        val called = mutableListOf<String>()
        val provider = HtmlFallbackSearchProvider(
            bingProvider = fakeEngineProvider(SearchEngine.BING, called) { emptyList() },
            sogouProvider = fakeEngineProvider(SearchEngine.SOGOU, called) {
                listOf(
                    HtmlSearchResult(
                        title = "\u5f02\u73af\u516c\u6d4b\u65f6\u95f4",
                        url = "https://sogou.test/game",
                        snippet = "\u300a\u5f02\u73af\u300b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u516c\u6d4b",
                        engine = "sogou",
                        module = "sogou_rb",
                    ),
                )
            },
        )

        val result = provider.search(
            UnifiedSearchRequest(query = "\u5f02\u73af \u516c\u6d4b\u65f6\u95f4 \u6e38\u620f \u4e0a\u7ebf", maxResults = 5),
        )

        assertEquals(listOf("bing", "sogou"), called)
        assertEquals("\u5f02\u73af\u516c\u6d4b\u65f6\u95f4", result.results.single().title)
        assertEquals(
            listOf(SearchAttemptStatus.EMPTY_RESULTS, SearchAttemptStatus.SUCCESS),
            result.diagnostics.map { it.status },
        )
    }

    @Test
    fun html_fallback_filters_search_portal_placeholders_before_accepting_results() = runBlocking {
        val called = mutableListOf<String>()
        val provider = HtmlFallbackSearchProvider(
            bingProvider = fakeEngineProvider(SearchEngine.BING, called) {
                listOf(
                    HtmlSearchResult(
                        title = "\u5f02\u73af \u6e38\u620f \u516c\u6d4b\u65f6\u95f4",
                        url = "https://www.bing.com/search?q=%E5%BC%82%E7%8E%AF",
                        snippet = "\u641c\u7d22",
                        engine = "bing",
                        module = "bing_b_algo",
                    ),
                )
            },
            sogouProvider = fakeEngineProvider(SearchEngine.SOGOU, called) {
                listOf(
                    HtmlSearchResult(
                        title = "\u5f02\u73af\u516c\u6d4b\u65f6\u95f4",
                        url = "https://sogou.test/game",
                        snippet = "\u300a\u5f02\u73af\u300b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u516c\u6d4b",
                        engine = "sogou",
                        module = "sogou_rb",
                    ),
                )
            },
        )

        val result = provider.search(
            UnifiedSearchRequest(query = "\u5f02\u73af \u6e38\u620f \u516c\u6d4b\u65f6\u95f4", maxResults = 5),
        )

        assertEquals(listOf("bing", "sogou"), called)
        assertEquals("\u5f02\u73af\u516c\u6d4b\u65f6\u95f4", result.results.single().title)
        assertFalse(result.results.any { it.snippet == "\u641c\u7d22" })
    }

    @Test
    fun html_result_does_not_fabricate_source_from_engine() {
        val result = HtmlSearchResult(
            title = "Example title",
            url = "https://example.test/a",
            snippet = "Example snippet",
            engine = "bing",
            module = "bing_b_algo",
        ).toUnifiedResult(index = 1)

        assertEquals("", result.source)
        assertEquals("bing", result.providerId)
        assertEquals("bing", result.metadata["engine"])
    }

    @Test
    fun html_fallback_preserves_runtime_network_failure_status() = runBlocking {
        val provider = HtmlFallbackSearchProvider(
            bingProvider = fakeEngineProvider(SearchEngine.BING, mutableListOf()) {
                throw RuntimeNetworkException(
                    RuntimeNetworkFailure.Http(
                        statusCode = 429,
                        url = "https://bing.test/search",
                        bodyPreview = "rate limited",
                    ),
                )
            },
            sogouProvider = fakeEngineProvider(SearchEngine.SOGOU, mutableListOf()) {
                listOf(
                    HtmlSearchResult(
                        title = "Fallback result",
                        url = "https://sogou.test/result",
                        snippet = "Fallback snippet",
                        engine = "sogou",
                        module = "sogou_rb",
                    ),
                )
            },
        )

        val result = provider.search(UnifiedSearchRequest(query = "kotlin hilt", maxResults = 3))

        assertEquals(SearchAttemptStatus.HTTP_ERROR, result.diagnostics.first().status)
        assertEquals("429", result.diagnostics.first().errorCode)
        assertEquals(SearchAttemptStatus.SUCCESS, result.diagnostics.last().status)
    }

    @Test
    fun news_query_accepts_last_engine_organic_results_when_strict_news_signal_is_missing() = runBlocking {
        val called = mutableListOf<String>()
        val provider = HtmlFallbackSearchProvider(
            bingProvider = fakeEngineProvider(SearchEngine.BING, called) {
                listOf(
                    HtmlSearchResult(
                        title = "\u5168\u7403\u5e02\u573a\u89c2\u5bdf",
                        url = "https://bing.test/market",
                        snippet = "\u5e02\u573a\u57282026\u5e744\u670826\u65e5\u7ee7\u7eed\u9707\u8361",
                        engine = "bing",
                        module = "bing_b_algo",
                    ),
                )
            },
            sogouProvider = fakeEngineProvider(SearchEngine.SOGOU, called) { emptyList() },
        )

        val result = provider.search(UnifiedSearchRequest(query = "\u4eca\u65e5\u65b0\u95fb 2026\u5e744\u670826\u65e5", maxResults = 5))

        assertEquals(listOf("sogou", "bing"), called)
        assertEquals("https://bing.test/market", result.results.single().url)
        assertEquals(SearchAttemptStatus.SUCCESS, result.diagnostics.last().status)
        assertEquals("organic_results_last_resort", result.diagnostics.last().reason)
        assertTrue(result.relevanceAccepted)
    }

    @Test
    fun news_query_rejects_results_from_a_different_explicit_year() = runBlocking {
        val called = mutableListOf<String>()
        val provider = HtmlFallbackSearchProvider(
            bingProvider = fakeEngineProvider(SearchEngine.BING, called) {
                listOf(
                    HtmlSearchResult(
                        title = "2025\u5e744\u670826\u65e5\u65b0\u95fb\u6458\u8981",
                        url = "https://bing.test/news-2025",
                        snippet = "\u4eca\u65e5\u65b0\u95fb\u66f4\u65b0\uff1a2025\u5e744\u670826\u65e5\u591a\u5730\u6d88\u606f\u6c47\u603b",
                        engine = "bing",
                        module = "bing_b_algo",
                    ),
                )
            },
            sogouProvider = fakeEngineProvider(SearchEngine.SOGOU, called) { emptyList() },
        )

        val result = provider.search(UnifiedSearchRequest(query = "\u4eca\u65e5\u65b0\u95fb 2026\u5e744\u670826\u65e5", maxResults = 5))

        assertEquals(listOf("sogou", "bing"), called)
        assertTrue(result.results.isEmpty())
        assertFalse(result.relevanceAccepted)
        assertEquals(SearchAttemptStatus.LOW_RELEVANCE, result.diagnostics.last().status)
        assertEquals("target_date_mismatch", result.diagnostics.last().reason)
    }

    @Test
    fun weather_query_rejects_last_engine_organic_results_when_weather_card_is_missing() = runBlocking {
        val called = mutableListOf<String>()
        val provider = HtmlFallbackSearchProvider(
            bingProvider = fakeEngineProvider(SearchEngine.BING, called) {
                listOf(
                    HtmlSearchResult(
                        title = "\u798f\u5dde\u751f\u6d3b\u6307\u5357",
                        url = "https://bing.test/fuzhou",
                        snippet = "2026-04-27 \u51fa\u884c\u63d0\u9192\u548c\u57ce\u5e02\u670d\u52a1\u4fe1\u606f",
                        engine = "bing",
                        module = "bing_b_algo",
                    ),
                )
            },
            sogouProvider = fakeEngineProvider(SearchEngine.SOGOU, called) { emptyList() },
        )

        val result = provider.search(UnifiedSearchRequest(query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5 2026-04-27", maxResults = 5))

        assertEquals(listOf("sogou", "bing"), called)
        assertTrue(result.results.isEmpty())
        assertEquals(SearchAttemptStatus.LOW_RELEVANCE, result.diagnostics.last().status)
        assertEquals("no_weather_result_passed_threshold", result.diagnostics.last().reason)
        assertFalse(result.relevanceAccepted)
    }

    @Test
    fun bing_provider_uses_runtime_network_transport_with_web_search_timeout() = runBlocking {
        val transport = RecordingRuntimeNetworkTransport(
            body = """
                <html><body>
                  <li class="b_algo">
                    <h2><a href="https://example.test/a">Example title</a></h2>
                    <div class="b_caption"><p>Example snippet</p></div>
                  </li>
                </body></html>
            """.trimIndent(),
        )

        val results = BingHtmlSearchProvider(transport).fetch("kotlin hilt", maxResults = 3)

        assertEquals(1, results.size)
        assertEquals(RuntimeNetworkCapability.WEB_SEARCH, transport.requests.single().capability)
        assertEquals(RuntimeTimeoutProfile.WEB_SEARCH, transport.requests.single().timeoutProfile)
        assertTrue(transport.requests.single().url.startsWith("https://www.bing.com/search?q=kotlin+hilt"))
    }

    @Test
    fun sogou_provider_uses_runtime_network_transport_with_web_search_timeout() = runBlocking {
        val transport = RecordingRuntimeNetworkTransport(
            body = """
                <html><body>
                  <div class="rb">
                    <h3><a href="https://example.test/a">Example title</a></h3>
                    <p class="str_info">Example snippet</p>
                  </div>
                </body></html>
            """.trimIndent(),
        )

        val results = SogouHtmlSearchProvider(transport).fetch("kotlin hilt", maxResults = 3)

        assertEquals(1, results.size)
        assertEquals(RuntimeNetworkCapability.WEB_SEARCH, transport.requests.single().capability)
        assertEquals(RuntimeTimeoutProfile.WEB_SEARCH, transport.requests.single().timeoutProfile)
        assertTrue(transport.requests.single().url.startsWith("https://www.sogou.com/web?query=kotlin+hilt"))
    }

    @Test
    fun parse_bing_results_supports_rich_result_priority_weather_answer_context_then_b_algo() {
        val html = """
            <html><body>
            <div id="b_context">
              <div class="b_entityTitle">\u798f\u5dde\u660e\u5929\u5929\u6c14 context</div>
              <div class="b_caption"><p>context 18-25\u5ea6</p></div>
            </div>
            <div class="b_ans" data-tag="wea.cur">
              <div class="wtr_hero">
                <div class="wtr_locTitle">\u798f\u5dde</div>
                <div class="wtr_currTemp">22\u00b0</div>
                <div class="wtr_condi">\u591a\u4e91</div>
              </div>
            </div>
            <div class="b_ans">
              <div class="b_rich">
                <h2>\u798f\u5dde\u660e\u5929\u5929\u6c14 answer</h2>
                <div class="b_factrow">answer 18-25\u5ea6</div>
              </div>
            </div>
            <li class="b_algo">
              <h2><a href="https://example.test/weather">\u798f\u5dde\u65c5\u6e38</a></h2>
              <div class="b_caption"><p>\u798f\u5dde\u662f\u4e00\u4e2a\u7f8e\u4e3d\u7684\u57ce\u5e02</p></div>
            </li>
            </body></html>
        """.trimIndent()

        val results = parseBingResults(html, maxResults = 5)

        assertEquals("bing_weather_card", results[0].module)
        assertEquals("bing_answer_card", results[1].module)
        assertEquals("bing_context_card", results[2].module)
        assertEquals("bing_b_algo", results[3].module)
    }

    @Test
    fun parse_sogou_results_supports_weather_sup_list_answer_summary_then_rb() {
        val html = """
            <html>
              <body>
                <div class="location-module"><div class="location-tab"><ul><li>\u798f\u5dde</li></ul></div></div>
                <div class="weather210208"><div class="temperature js_shikuang"><p>18~25</p></div></div>
                <script>
                  window.__DATA__ = {
                    "supList": [{
                      "sup_passage": "\u300a\u5f02\u73af\u300b\u516c\u6d4b\u65f6\u95f4",
                      "sup_source": "\u767e\u5ea6\u767e\u79d1",
                      "sup_title": "\u5f02\u73af_\u767e\u5ea6\u767e\u79d1",
                      "sup_url": "https:\\/\\/baike.baidu.hk\\/item\\/64656910"
                    }],
                    "answer_summary": "\u641c\u72d7 answer summary"
                  };
                </script>
                <div class="rb">
                  <h3><a href="https://example.test/rb">RB title</a></h3>
                  <p class="str_info">RB snippet</p>
                </div>
              </body>
            </html>
        """.trimIndent()

        val results = parseSogouResults(
            html = html,
            maxResults = 5,
            searchUrl = "https://www.sogou.com/web?query=%E7%A6%8F%E5%B7%9E",
        )

        assertEquals("sogou_weather_card", results[0].module)
        assertEquals("sogou_sup_list", results[1].module)
        assertEquals("sogou_answer_summary", results[2].module)
        assertEquals("sogou_rb", results[3].module)
    }

    @Test
    fun parser_truncates_snippets_to_seven_hundred_chars() {
        val longSnippet = "a".repeat(701)
        val html = """
            <html><body>
              <li class="b_algo">
                <h2><a href="https://example.test/a">Example title</a></h2>
                <div class="b_caption"><p>$longSnippet</p></div>
              </li>
            </body></html>
        """.trimIndent()

        val results = parseBingResults(html, maxResults = 5)

        assertEquals(700, results.single().snippet.length)
    }

    @Test
    fun assess_batch_relevance_keeps_weather_threshold_semantics() {
        val offTopicResults = listOf(
            HtmlSearchResult(
                title = "\u798f\u5dde\u65c5\u6e38\u666f\u70b9",
                url = "https://travel.com/1",
                snippet = "\u798f\u5dde\u4e09\u574a\u4e03\u5df7",
            ),
        )
        val relevantResults = listOf(
            HtmlSearchResult(
                title = "\u798f\u5dde\u5929\u6c14\u9884\u62a5",
                url = "https://weather.com/1",
                snippet = "\u798f\u5dde\u660e\u5929\u5929\u6c14\uff1a18-25\u5ea6\uff0c\u5c0f\u96e8\u8f6c\u9634",
            ),
        )

        assertFalse(assessBatchRelevance("\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5", offTopicResults))
        assertTrue(assessBatchRelevance("\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5", relevantResults))
    }

    private fun fakeEngineProvider(
        engine: SearchEngine,
        called: MutableList<String>,
        results: suspend () -> List<HtmlSearchResult>,
    ) = object : HtmlFallbackSearchProvider.EngineProvider {
        override val engine: SearchEngine = engine

        override suspend fun fetch(query: String, maxResults: Int): List<HtmlSearchResult> {
            called += engine.name.lowercase()
            return results()
        }
    }

    private class RecordingRuntimeNetworkTransport(
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
                durationMs = 12,
            )
        }

        override fun openStream(request: RuntimeNetworkRequest): Flow<String> {
            error("unused")
        }

        override fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent> {
            error("unused")
        }
    }
}
