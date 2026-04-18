package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolSourceProviderTest {

    @Test
    fun default_search_engine_prefers_bing_before_sogou() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "fuzhou weather tomorrow",
            maxResults = 3,
            bingSearch = { _, _ ->
                called += "bing"
                listOf(
                    WebSearchResult(
                        title = "Bing forecast",
                        url = "https://bing.test/weather",
                        snippet = "18~25 light rain",
                    ),
                )
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                listOf(
                    WebSearchResult(
                        title = "Fuzhou weather",
                        url = "https://sogou.test/weather",
                        snippet = "18~25 light rain",
                    ),
                )
            },
        )

        assertEquals(listOf("bing"), called)
        assertTrue(text.contains("Bing forecast"))
        assertTrue(text.contains("18~25"))
    }

    @Test
    fun falls_back_to_sogou_when_bing_is_empty() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "\u5f02\u73af \u516c\u6d4b\u65f6\u95f4 \u6e38\u620f \u4e0a\u7ebf",
            maxResults = 5,
            bingSearch = { _, _ ->
                called += "bing"
                emptyList()
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                listOf(
                    WebSearchResult(
                        title = "\u5f02\u73af\u516c\u6d4b\u65f6\u95f4",
                        url = "https://sogou.test/game",
                        snippet = "\u300a\u5f02\u73af\u300b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u516c\u6d4b",
                    ),
                )
            },
        )

        assertEquals(listOf("bing", "sogou"), called)
        assertTrue(text.contains("\u5f02\u73af\u516c\u6d4b\u65f6\u95f4"))
    }

    @Test
    fun falls_back_when_first_engine_only_returns_search_portal_placeholder() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "\u5f02\u73af \u6e38\u620f \u516c\u6d4b\u65f6\u95f4",
            maxResults = 5,
            bingSearch = { _, _ ->
                called += "bing"
                listOf(
                    WebSearchResult(
                        title = "\u5f02\u73af \u6e38\u620f \u516c\u6d4b\u65f6\u95f4",
                        url = "https://www.bing.com/search?q=%E5%BC%82%E7%8E%AF",
                        snippet = "\u641c\u7d22",
                    ),
                )
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                listOf(
                    WebSearchResult(
                        title = "\u5f02\u73af\u516c\u6d4b\u65f6\u95f4",
                        url = "https://sogou.test/game",
                        snippet = "\u300a\u5f02\u73af\u300b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u516c\u6d4b",
                    ),
                )
            },
        )

        assertEquals(listOf("bing", "sogou"), called)
        assertTrue(text.contains("\u5f02\u73af\u516c\u6d4b\u65f6\u95f4"))
        assertFalse(text.contains("\u641c\u7d22"))
    }

    @Test
    fun parse_bing_results_supports_b_algoheader_link_structure() {
        val title = "\u5982\u4f55\u8bc4\u4ef7\u5b8c\u7f8e\u65d7\u4e0b\u6e38\u620f\u300a\u5f02\u73af\u300b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u5168\u5e73\u53f0\u516c\u6d4b\uff1f"
        val snippet = "\u5b8c\u7f8e\u592a\u7740\u6025\u4e86\uff0c\u5f02\u73af\u6b63\u5f0f\u516c\u6d4b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u5f00\u542f\u3002"
        val html = """
            <html>
              <body>
                <li class="b_algo" data-id="SERP.1">
                  <div class="b_tpcn">
                    <a class="tilk" href="https://www.zhihu.com/question/2010323597332336787">zhihu.com</a>
                  </div>
                  <div class="b_algoheader">
                    <a href="https://www.zhihu.com/question/2010323597332336787">
                      <h2>$title</h2>
                    </a>
                  </div>
                  <div class="b_caption">
                    <p class="b_lineclamp3">
                      <span class="news_dt">2026年2月26日</span>
                      $snippet
                    </p>
                  </div>
                </li>
              </body>
            </html>
        """.trimIndent()

        val results = parseBingResults(html, maxResults = 5)

        assertEquals(1, results.size)
        assertEquals("https://www.zhihu.com/question/2010323597332336787", results.first().url)
        assertTrue(results.first().title.contains("2026\u5e744\u670823\u65e5"))
        assertTrue(results.first().snippet.contains("2026\u5e744\u670823\u65e5"))
    }

    @Test
    fun sogou_sup_list_answer_is_extracted_from_embedded_state() {
        val html = """
            <html>
              <body>
                <script>
                  window.__DATA__ = {
                    "supList": [{
                      "sup_passage": "\u300a\u5f02\u73af\u300b\u662fHotta Studio\u81ea\u4e3b\u7814\u53d1\u7684\u8d85\u81ea\u7136\u90fd\u5e02\u5f00\u653e\u4e16\u754c\u89d2\u8272\u626e\u6f14\u6e38\u620f\uff0c\u6b63\u5f0f\u516c\u6d4b\u4e8e2026\u5e744\u670823\u65e5\u767b\u9646PC\u3001PlayStation 5\u3001iOS\u548c\u5b89\u5353\u79fb\u52a8\u5e73\u53f0\u5f00\u542f\u3002",
                      "sup_source": "\u767e\u5ea6\u767e\u79d1",
                      "sup_title": "\u5f02\u73af_\u767e\u5ea6\u767e\u79d1",
                      "sup_url": "https:\\/\\/baike.baidu.hk\\/item\\/%E7%95%B0%E7%92%B0\\/64656910"
                    }]
                  };
                </script>
              </body>
            </html>
        """.trimIndent()

        val results = parseSogouResults(
            html = html,
            maxResults = 5,
            searchUrl = "https://www.sogou.com/web?query=%E5%BC%82%E7%8E%AF",
        )

        assertEquals(1, results.size)
        assertEquals("\u5f02\u73af_\u767e\u5ea6\u767e\u79d1", results.first().title)
        assertTrue(results.first().snippet.contains("2026\u5e744\u670823\u65e5"))
        assertEquals("https://baike.baidu.hk/item/%E7%95%B0%E7%92%B0/64656910", results.first().url)
    }

    @Test
    fun sogou_weather_card_is_extracted_from_special_module() {
        val city = "\u798f\u5dde"
        val condition = "\u5c0f\u96e8\u8f6c\u9634"
        val html = """
            <html>
              <body>
                <div class="location-module">
                  <div class="location-tab">
                    <ul><li>$city</li></ul>
                  </div>
                </div>
                <div class="weather210208 special-subject">
                  <div class="content-main">
                    <div class="content-mes">
                      <div class="temperature js_shikuang"><p>18~25</p></div>
                      <div class="wind-box"><span class="weath">$condition</span></div>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val results = parseSogouResults(
            html = html,
            maxResults = 5,
            searchUrl = "https://www.sogou.com/web?query=%E7%A6%8F%E5%B7%9E%E6%98%8E%E5%A4%A9%E5%A4%A9%E6%B0%94%E9%A2%84%E6%8A%A5",
        )

        assertTrue(results.isNotEmpty())
        assertEquals("\u798f\u5dde\u5929\u6c14", results.first().title)
        assertTrue(results.first().snippet.contains("18~25"))
        assertTrue(results.first().snippet.contains("\u5c0f\u96e8\u8f6c\u9634"))
    }

    @Test
    fun sogou_placeholder_page_does_not_return_fake_result() {
        val html = """
            <html>
              <head><title>\u0051\u0051\u6d4f\u89c8\u5668\u641c\u7d22</title></head>
              <body></body>
            </html>
        """.trimIndent()

        val results = parseSogouResults(
            html = html,
            maxResults = 5,
            searchUrl = "https://www.sogou.com/web?query=%E5%BC%82%E7%8E%AF",
        )

        assertTrue(results.isEmpty())
    }

    // ── Direction A: Bing rich result parsing ──────────────────────────

    @Test
    fun bing_weather_card_is_extracted_and_prioritized_over_b_algo() {
        val loc = "\u798f\u5dde, \u798f\u5efa\u7701"
        val deg = "\u00b0"
        val travel = "\u798f\u5dde\u65c5\u6e38\u653b\u7565"
        val travelSnippet = "\u798f\u5dde\u662f\u4e00\u4e2a\u7f8e\u4e3d\u7684\u57ce\u5e02"
        val cloudy = "\u591a\u4e91"
        val highLow = "\u9ad8\u6e29 25$deg / \u4f4e\u6e29 18$deg"
        val html = """
            <html><body>
            <div class="b_ans" data-tag="wea.cur">
              <div class="wtr_hero">
                <div class="wtr_locTitle">$loc</div>
                <div class="wtr_currTemp">22$deg</div>
                <div class="wtr_condi">$cloudy</div>
                <div class="wtr_fctxt">$highLow</div>
              </div>
            </div>
            <li class="b_algo">
              <div class="b_algoheader"><a href="https://zhihu.com/travel"><h2>$travel</h2></a></div>
              <div class="b_caption"><p>$travelSnippet</p></div>
            </li>
            </body></html>
        """.trimIndent()

        val results = parseBingResults(html, maxResults = 5)

        assertTrue(results.size >= 2)
        // Weather card should come first
        assertTrue(results.first().title.contains("\u5929\u6c14"))
        assertTrue(results.first().snippet.contains("22$deg") || results.first().snippet.contains(cloudy))
        // b_algo result should still be present
        assertTrue(results.any { it.title.contains("\u65c5\u6e38") })
    }

    @Test
    fun bing_answer_card_is_extracted() {
        val ansTitle = "\u798f\u5dde\u660e\u5929\u5929\u6c14"
        val ansSnippet = "\u660e\u5929\u798f\u5dde\u6c14\u6e2918-25\u5ea6\uff0c\u5c0f\u96e8\u8f6c\u9634"
        val html = """
            <html><body>
            <div class="b_ans">
              <div class="b_rich">
                <div class="b_vPanel">
                  <h2>$ansTitle</h2>
                  <div class="b_factrow">$ansSnippet</div>
                </div>
              </div>
            </div>
            </body></html>
        """.trimIndent()

        val results = parseBingResults(html, maxResults = 5)

        assertTrue(results.isNotEmpty())
        assertTrue(results.first().title.contains(ansTitle))
        assertTrue(results.first().snippet.contains("18-25\u5ea6") || results.first().snippet.contains("\u5c0f\u96e8"))
    }

    @Test
    fun bing_context_card_is_extracted_as_rich_result() {
        val title = "\u798f\u5dde\u660e\u5929\u5929\u6c14"
        val snippet = "\u660e\u5929\u798f\u5dde18-25\u5ea6\uff0c\u5c0f\u96e8\u8f6c\u9634\uff0c\u4f53\u611f\u6e29\u5ea620\u5ea6"
        val html = """
            <html><body>
            <div id="b_context">
              <div class="b_entityTitle">$title</div>
              <div class="b_caption"><p>$snippet</p></div>
            </div>
            </body></html>
        """.trimIndent()

        val results = parseBingResults(html, maxResults = 5)

        assertTrue(results.isNotEmpty())
        assertTrue(results.first().title.contains(title))
        assertTrue(results.first().snippet.contains("18-25\u5ea6"))
    }

    @Test
    fun bing_weather_card_not_duplicated_by_answer_card_extraction() {
        val loc = "\u798f\u5dde"
        val deg = "\u00b0"
        val cloudy = "\u591a\u4e91"
        val html = """
            <html><body>
            <div class="b_ans" data-tag="wea.cur">
              <div class="wtr_hero">
                <div class="wtr_locTitle">$loc</div>
                <div class="wtr_currTemp">22$deg</div>
                <div class="wtr_condi">$cloudy</div>
              </div>
            </div>
            </body></html>
        """.trimIndent()

        val results = parseBingResults(html, maxResults = 5)

        // Should have exactly 1 result (weather card), not duplicated by answer card
        assertEquals(1, results.size)
        assertTrue(results.first().title.contains("\u5929\u6c14"))
    }

    // ── Direction B: Relevance checking ───────────────────────────────

    @Test
    fun assess_batch_relevance_detects_off_topic_weather_results() {
        val query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5"
        val results = listOf(
            WebSearchResult(
                title = "\u5982\u4f55\u8bc4\u4ef7\u798f\u5dde\u8fd9\u5ea7\u57ce\u5e02\uff1f",
                url = "https://zhihu.com/1",
                snippet = "\u798f\u5dde\u662f\u798f\u5efa\u7701\u7684\u7701\u4f1a\u57ce\u5e02",
            ),
            WebSearchResult(
                title = "\u798f\u5dde\u65c5\u6e38\u666f\u70b9",
                url = "https://travel.com/1",
                snippet = "\u798f\u5dde\u4e09\u574a\u4e03\u5df7",
            ),
            WebSearchResult(
                title = "\u798f\u5dde\u7f8e\u98df",
                url = "https://food.com/1",
                snippet = "\u4f5b\u8df3\u5899\u662f\u798f\u5dde\u540d\u83dc",
            ),
        )
        assertFalse(assessBatchRelevance(query, results))
    }

    @Test
    fun assess_batch_relevance_passes_relevant_weather_results() {
        val query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5"
        val results = listOf(
            WebSearchResult(
                title = "\u798f\u5dde\u5929\u6c14\u9884\u62a5",
                url = "https://weather.com/1",
                snippet = "\u798f\u5dde\u660e\u5929\u5929\u6c14\uff1a18-25\u5ea6\uff0c\u5c0f\u96e8\u8f6c\u9634",
            ),
            WebSearchResult(
                title = "\u798f\u5dde\u4e00\u5468\u5929\u6c14",
                url = "https://weather.com/2",
                snippet = "\u798f\u5dde\u672a\u6765\u4e00\u5468\u5929\u6c14\u9884\u62a5\u8be6\u60c5",
            ),
        )
        assertTrue(assessBatchRelevance(query, results))
    }

    @Test
    fun assess_batch_relevance_passes_game_query() {
        val query = "\u5f02\u73af \u516c\u6d4b\u65f6\u95f4 \u6e38\u620f \u4e0a\u7ebf\u65e5\u671f"
        val results = listOf(
            WebSearchResult(
                title = "\u5982\u4f55\u8bc4\u4ef7\u5b8c\u7f8e\u65d7\u4e0b\u6e38\u620f\u300a\u5f02\u73af\u300b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u516c\u6d4b\uff1f",
                url = "https://zhihu.com/q/12345",
                snippet = "\u5b8c\u7f8e\u592a\u7740\u6025\u4e86\uff0c\u5f02\u73af\u6b63\u5f0f\u516c\u6d4b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u5f00\u542f\u3002",
            ),
        )
        assertTrue(assessBatchRelevance(query, results))
    }

    @Test
    fun assess_batch_relevance_short_general_query_always_passes() {
        // General queries with < 3 bigrams should always pass (too short to assess).
        val query = "\u798f\u5dde"
        val results = listOf(
            WebSearchResult(title = "foo", url = "https://example.com", snippet = "bar"),
        )
        assertTrue(assessBatchRelevance(query, results))
    }

    // ── Integration: Bing off-topic triggers Sogou fallback ──────────

    @Test
    fun bing_off_topic_results_trigger_sogou_fallback() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5",
            maxResults = 5,
            bingSearch = { _, _ ->
                called += "bing"
                listOf(
                    WebSearchResult(
                        title = "\u5982\u4f55\u8bc4\u4ef7\u798f\u5dde\u8fd9\u5ea7\u57ce\u5e02\uff1f - \u77e5\u4e4e",
                        url = "https://zhihu.com/q/1",
                        snippet = "\u798f\u5dde\u662f\u798f\u5efa\u7701\u7684\u7701\u4f1a\u57ce\u5e02\uff0c\u5386\u53f2\u60a0\u4e45\u3002",
                    ),
                    WebSearchResult(
                        title = "\u798f\u5dde\u65c5\u6e38\u666f\u70b9\u5927\u5168",
                        url = "https://travel.com/fuzhou",
                        snippet = "\u798f\u5dde\u62e5\u6709\u4f17\u591a\u65c5\u6e38\u666f\u70b9\uff0c\u4e09\u574a\u4e03\u5df7\u662f\u5fc5\u53bb\u7684\u5730\u65b9\u3002",
                    ),
                    WebSearchResult(
                        title = "\u798f\u5dde\u7f8e\u98df\u653b\u7565",
                        url = "https://food.com/fuzhou",
                        snippet = "\u798f\u5dde\u7684\u4f5b\u8df3\u5899\u662f\u8457\u540d\u7684\u95fd\u83dc\u4ee3\u8868\u3002",
                    ),
                )
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u5929\u6c14",
                        url = "https://weather.com/fuzhou",
                        snippet = "\u798f\u5dde\u660e\u5929\u5929\u6c14\uff1a18-25\u00b0C\uff0c\u5c0f\u96e8\u8f6c\u9634\u3002",
                    ),
                )
            },
        )

        assertEquals(listOf("bing", "sogou"), called)
        assertTrue(text.contains("\u5929\u6c14"))
        assertFalse(text.contains("\u65c5\u6e38\u666f\u70b9"))
    }

    @Test
    fun bing_relevant_game_results_do_not_false_fallback() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "\u5f02\u73af \u516c\u6d4b\u65f6\u95f4 \u6e38\u620f \u4e0a\u7ebf\u65e5\u671f",
            maxResults = 5,
            bingSearch = { _, _ ->
                called += "bing"
                listOf(
                    WebSearchResult(
                        title = "\u5982\u4f55\u8bc4\u4ef7\u5b8c\u7f8e\u65d7\u4e0b\u6e38\u620f\u300a\u5f02\u73af\u300b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u516c\u6d4b\uff1f",
                        url = "https://zhihu.com/q/12345",
                        snippet = "\u5b8c\u7f8e\u592a\u7740\u6025\u4e86\uff0c\u5f02\u73af\u6b63\u5f0f\u516c\u6d4b\u5c06\u4e8e2026\u5e744\u670823\u65e5\u5f00\u542f\u3002",
                    ),
                    WebSearchResult(
                        title = "\u5f02\u73af\u516c\u6d4b\u65e5\u671f\u786e\u5b9a - \u6e38\u620f\u8d44\u8baf",
                        url = "https://game.com/wuthering",
                        snippet = "\u5f02\u73af\u516c\u6d4b\u65f6\u95f4\u4e3a2026\u5e744\u670823\u65e5\uff0c\u5c4a\u65f6\u5c06\u767b\u9646PC\u548c\u79fb\u52a8\u5e73\u53f0\u3002",
                    ),
                )
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                emptyList()
            },
        )

        assertEquals(listOf("bing"), called)
        assertTrue(text.contains("\u5f02\u73af"))
        assertTrue(text.contains("\u516c\u6d4b"))
    }

    @Test
    fun bing_weather_fallback_with_mixed_query() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "\u798f\u5dde\u5929\u6c14\u9884\u62a5 \u660e\u5929 \u6e29\u5ea6 \u5929\u6c14\u60c5\u51b5",
            maxResults = 5,
            bingSearch = { _, _ ->
                called += "bing"
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u65c5\u6e38",
                        url = "https://travel.com/1",
                        snippet = "\u798f\u5dde\u662f\u7f8e\u4e3d\u7684\u57ce\u5e02",
                    ),
                    WebSearchResult(
                        title = "\u798f\u5dde\u666f\u70b9\u63a8\u8350",
                        url = "https://travel.com/2",
                        snippet = "\u798f\u5dde\u4e09\u574a\u4e03\u5df7\u503c\u5f97\u4e00\u6e38",
                    ),
                )
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u5929\u6c14",
                        url = "https://weather.sogou.com",
                        snippet = "\u798f\u5dde\u660e\u5929\u6c14\u6e2918-25\u5ea6\uff0c\u5c0f\u96e8",
                    ),
                )
            },
        )

        assertEquals(listOf("bing", "sogou"), called)
        assertTrue(text.contains("\u5929\u6c14"))
    }

    @Test
    fun low_relevance_fallback_log_contains_query_module_reason_and_fallback_engine() = runBlocking {
        RuntimeLogRepository.clear()

        executeWebSearchQuery(
            query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5",
            maxResults = 5,
            bingSearch = { _, _ ->
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u65c5\u6e38\u666f\u70b9\u5927\u5168",
                        url = "https://travel.com/fuzhou",
                        snippet = "\u4e09\u574a\u4e03\u5df7\u662f\u798f\u5dde\u6700\u8457\u540d\u7684\u666f\u70b9\u3002",
                        engine = "bing",
                        module = "bing_b_algo",
                    ),
                    WebSearchResult(
                        title = "\u798f\u5dde\u7f8e\u98df\u653b\u7565",
                        url = "https://food.com/fuzhou",
                        snippet = "\u4f5b\u8df3\u5899\u662f\u798f\u5dde\u540d\u83dc\u3002",
                        engine = "bing",
                        module = "bing_b_algo",
                    ),
                )
            },
            sogouSearch = { _, _ ->
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u5929\u6c14",
                        url = "https://weather.com/fuzhou",
                        snippet = "\u798f\u5dde\u660e\u5929\u5929\u6c14\uff1a18-25\u5ea6\uff0c\u5c0f\u96e8\u8f6c\u9634\u3002",
                        engine = "sogou",
                        module = "sogou_weather_card",
                    ),
                )
            },
        )

        val logs = RuntimeLogRepository.logs.value.joinToString("\n")
        assertTrue(logs.contains("query='\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5'"))
        assertTrue(logs.contains("modules=[bing_b_algo:2]"))
        assertTrue(logs.contains("relevance_reason=no_result_passed_threshold"))
        assertTrue(logs.contains("fallback_reason=next_engine"))
        assertTrue(logs.contains("engine=sogou"))
    }

    @Test
    fun low_relevance_bing_results_returned_as_last_resort_when_sogou_also_empty() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5",
            maxResults = 5,
            bingSearch = { _, _ ->
                called += "bing"
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u65c5\u6e38",
                        url = "https://travel.com/1",
                        snippet = "\u798f\u5dde\u662f\u7f8e\u4e3d\u7684\u57ce\u5e02",
                    ),
                )
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                emptyList()
            },
        )

        // Both engines tried, Sogou empty, falls back to low-relevance Bing results
        assertEquals(listOf("bing", "sogou"), called)
        assertTrue(text.contains("\u798f\u5dde\u65c5\u6e38"))
    }
}
