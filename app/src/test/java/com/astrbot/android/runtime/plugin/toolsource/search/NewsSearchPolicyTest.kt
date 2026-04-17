package com.astrbot.android.runtime.plugin.toolsource.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsSearchPolicyTest {

    @Test
    fun chinese_news_queries_prefer_sogou_before_bing() {
        assertEquals(
            listOf(SearchEngine.SOGOU, SearchEngine.BING),
            NewsSearchPolicy.preferredEngines("\u798f\u5dde\u4eca\u65e5\u65b0\u95fb"),
        )
    }

    @Test
    fun latin_news_queries_prefer_bing_before_sogou() {
        assertEquals(
            listOf(SearchEngine.BING, SearchEngine.SOGOU),
            NewsSearchPolicy.preferredEngines("latest fuzhou news"),
        )
    }

    @Test
    fun news_queries_do_not_allow_low_relevance_fallback() {
        assertFalse(
            NewsSearchPolicy.allowLowRelevanceFallback(),
        )
    }

    @Test
    fun news_queries_require_the_final_engine_to_pass_relevance_threshold() {
        assertTrue(
            NewsSearchPolicy.requiresRelevantFinalEngine(),
        )
    }
}
