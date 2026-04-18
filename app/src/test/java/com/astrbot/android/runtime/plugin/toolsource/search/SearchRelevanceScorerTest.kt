package com.astrbot.android.feature.plugin.runtime.toolsource.search

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.feature.plugin.runtime.toolsource.WebSearchResult
import com.astrbot.android.feature.plugin.runtime.toolsource.assessBatchRelevance
import com.astrbot.android.feature.plugin.runtime.toolsource.executeWebSearchQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRelevanceScorerTest {

    @Test
    fun low_relevance_weather_results_are_rejected() {
        val query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5"
        val results = listOf(
            WebSearchResult(
                title = "\u798f\u5dde\u65c5\u6e38\u653b\u7565",
                url = "https://example.com/travel",
                snippet = "\u4e09\u574a\u4e03\u5df7\u548c\u4e0a\u4e0b\u676d\u90fd\u5f88\u9002\u5408\u5468\u672b\u6e38\u73a9\u3002",
                module = "bing_b_algo",
            ),
            WebSearchResult(
                title = "\u798f\u5dde\u7f8e\u98df\u63a8\u8350",
                url = "https://example.com/food",
                snippet = "\u9c7c\u4e38\u548c\u8089\u71d5\u662f\u798f\u5dde\u4ee3\u8868\u6027\u5c0f\u5403\u3002",
                module = "bing_b_algo",
            ),
        )

        assertFalse(assessBatchRelevance(query, results))
        val assessment = SearchRelevanceScorer.assess(query, results)
        assertFalse(assessment.isRelevant)
        assertEquals("no_weather_result_passed_threshold", assessment.reason)
        assertEquals("bing_b_algo", assessment.bestModule)
    }

    @Test
    fun low_relevance_diagnostics_include_relevance_and_fallback_reason() = runBlocking {
        RuntimeLogRepository.clear()

        executeWebSearchQuery(
            query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5",
            maxResults = 5,
            bingSearch = { _, _ ->
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u65c5\u6e38\u653b\u7565",
                        url = "https://example.com/travel",
                        snippet = "\u4e09\u574a\u4e03\u5df7\u548c\u4e0a\u4e0b\u676d\u90fd\u5f88\u9002\u5408\u5468\u672b\u6e38\u73a9\u3002",
                        module = "bing_b_algo",
                    ),
                    WebSearchResult(
                        title = "\u798f\u5dde\u7f8e\u98df\u63a8\u8350",
                        url = "https://example.com/food",
                        snippet = "\u9c7c\u4e38\u548c\u8089\u71d5\u662f\u798f\u5dde\u4ee3\u8868\u6027\u5c0f\u5403\u3002",
                        module = "bing_b_algo",
                    ),
                )
            },
            sogouSearch = { _, _ -> emptyList() },
        )

        val logs = RuntimeLogRepository.logs.value.joinToString("\n")
        assertTrue(logs.contains("intent=GENERAL"))
        assertTrue(logs.contains("relevance_reason=no_result_passed_threshold"))
        assertTrue(logs.contains("fallback_reason=next_engine"))
        assertTrue(logs.contains("fallback_reason=low_relevance_last_resort"))
        assertTrue(logs.contains("query='\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5'"))
    }

    @Test
    fun weather_policy_prefers_sogou_before_bing() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5",
            maxResults = 5,
            policy = WeatherSearchPolicy.toSearchPolicy(),
            bingSearch = { _, _ ->
                called += "bing"
                emptyList()
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u5929\u6c14",
                        url = "https://www.sogou.com/web?query=fuzhou-weather",
                        snippet = "\u798f\u5dde\u660e\u5929\u5929\u6c14\uff1a18-24\u5ea6\uff0c\u5c0f\u96e8\u8f6c\u9634\u3002",
                        module = "sogou_weather_card",
                    ),
                )
            },
        )

        assertEquals(listOf("sogou"), called)
        assertTrue(text.contains("\u798f\u5dde\u5929\u6c14"))
    }

    @Test
    fun weather_policy_rejects_low_relevance_last_resort_payload() = runBlocking {
        val error = runCatching {
            executeWebSearchQuery(
                query = "\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5",
                maxResults = 5,
                policy = WeatherSearchPolicy.toSearchPolicy(),
                bingSearch = { _, _ ->
                    listOf(
                        WebSearchResult(
                            title = "\u798f\u5dde\u65c5\u6e38\u653b\u7565",
                            url = "https://example.com/travel",
                            snippet = "\u4e09\u574a\u4e03\u5df7\u548c\u4e0a\u4e0b\u676d\u90fd\u5f88\u9002\u5408\u5468\u672b\u6e38\u73a9\u3002",
                            module = "bing_b_algo",
                        ),
                    )
                },
                sogouSearch = { _, _ ->
                    listOf(
                        WebSearchResult(
                            title = "\u798f\u5dde\u7f8e\u98df\u63a8\u8350",
                            url = "https://example.com/food",
                            snippet = "\u9c7c\u4e38\u548c\u8089\u71d5\u662f\u798f\u5dde\u4ee3\u8868\u6027\u5c0f\u5403\u3002",
                            module = "sogou_rb",
                        ),
                    )
                },
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("intent=WEATHER"))
    }

    @Test
    fun low_relevance_news_results_are_rejected() {
        val query = "\u798f\u5dde\u4eca\u65e5\u65b0\u95fb"
        val results = listOf(
            WebSearchResult(
                title = "\u798f\u5dde\u4e09\u574a\u4e03\u5df7\u6e38\u73a9\u653b\u7565",
                url = "https://example.com/travel",
                snippet = "\u5468\u672b\u9002\u5408\u6253\u5361\u4e09\u574a\u4e03\u5df7\u548c\u4e0a\u4e0b\u676d\u3002",
                module = "bing_b_algo",
            ),
            WebSearchResult(
                title = "\u798f\u5dde\u7f8e\u98df\u63a8\u8350",
                url = "https://example.com/food",
                snippet = "\u9c7c\u4e38\u548c\u8089\u71d5\u662f\u798f\u5dde\u4ee3\u8868\u6027\u5c0f\u5403\u3002",
                module = "bing_b_algo",
            ),
        )

        val assessment = SearchRelevanceScorer.assess(
            query = query,
            results = results,
            intent = SearchIntent.NEWS,
        )

        assertFalse(assessment.isRelevant)
        assertEquals("no_news_result_passed_threshold", assessment.reason)
    }

    @Test
    fun relevant_news_results_are_accepted() {
        val query = "\u798f\u5dde\u4eca\u65e5\u65b0\u95fb"
        val results = listOf(
            WebSearchResult(
                title = "\u798f\u5dde\u4eca\u65e5\u65b0\u95fb\uff1a\u53f0\u98ce\u9884\u8b66\u53d1\u5e03",
                url = "https://news.example.com/fuzhou-alert",
                snippet = "\u4eca\u65e5\u798f\u5dde\u53d1\u5e03\u53f0\u98ce\u9884\u8b66\uff0c\u5f53\u5730\u65b0\u95fb\u901a\u62a5\u9632\u5fa1\u63aa\u65bd\u3002",
                module = "bing_b_algo",
            ),
        )

        val assessment = SearchRelevanceScorer.assess(
            query = query,
            results = results,
            intent = SearchIntent.NEWS,
        )

        assertTrue(assessment.isRelevant)
        assertEquals("news_result_passed_threshold", assessment.reason)
    }

    @Test
    fun news_policy_prefers_sogou_before_bing_for_chinese_queries() = runBlocking {
        val called = mutableListOf<String>()

        val text = executeWebSearchQuery(
            query = "\u798f\u5dde\u4eca\u65e5\u65b0\u95fb",
            maxResults = 5,
            policy = NewsSearchPolicy.toSearchPolicy("\u798f\u5dde\u4eca\u65e5\u65b0\u95fb"),
            bingSearch = { _, _ ->
                called += "bing"
                emptyList()
            },
            sogouSearch = { _, _ ->
                called += "sogou"
                listOf(
                    WebSearchResult(
                        title = "\u798f\u5dde\u4eca\u65e5\u65b0\u95fb\uff1a\u9632\u6d5a\u63d0\u793a\u53d1\u5e03",
                        url = "https://news.example.com/fuzhou-alert",
                        snippet = "\u4eca\u65e5\u798f\u5dde\u53d1\u5e03\u6700\u65b0\u901a\u62a5\uff0c\u63d0\u9192\u5e02\u6c11\u6ce8\u610f\u5f3a\u964d\u96e8\u3002",
                        module = "sogou_rb",
                    ),
                )
            },
        )

        assertEquals(listOf("sogou"), called)
        assertTrue(text.contains("\u4eca\u65e5\u65b0\u95fb"))
    }

    @Test
    fun news_policy_rejects_low_relevance_last_resort_payload() = runBlocking {
        val error = runCatching {
            executeWebSearchQuery(
                query = "\u798f\u5dde\u4eca\u65e5\u65b0\u95fb",
                maxResults = 5,
                policy = NewsSearchPolicy.toSearchPolicy("\u798f\u5dde\u4eca\u65e5\u65b0\u95fb"),
                bingSearch = { _, _ ->
                    listOf(
                        WebSearchResult(
                            title = "\u798f\u5dde\u65c5\u6e38\u653b\u7565",
                            url = "https://example.com/travel",
                            snippet = "\u4e09\u574a\u4e03\u5df7\u548c\u4e0a\u4e0b\u676d\u662f\u70ed\u95e8\u666f\u70b9\u3002",
                            module = "bing_b_algo",
                        ),
                    )
                },
                sogouSearch = { _, _ ->
                    listOf(
                        WebSearchResult(
                            title = "\u798f\u5dde\u7f8e\u98df\u63a8\u8350",
                            url = "https://example.com/food",
                            snippet = "\u9c7c\u4e38\u548c\u8089\u71d5\u662f\u798f\u5dde\u4ee3\u8868\u6027\u5c0f\u5403\u3002",
                            module = "sogou_rb",
                        ),
                    )
                },
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("intent=NEWS"))
    }
}
