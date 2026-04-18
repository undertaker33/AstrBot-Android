package com.astrbot.android.feature.plugin.runtime.toolsource.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherSearchPolicyTest {

    @Test
    fun chinese_weather_queries_prefer_sogou_before_bing() {
        assertEquals(
            listOf(SearchEngine.SOGOU, SearchEngine.BING),
            WeatherSearchPolicy.preferredEngines(),
        )
    }

    @Test
    fun chinese_weather_queries_do_not_allow_low_relevance_fallback() {
        assertFalse(
            WeatherSearchPolicy.allowLowRelevanceFallback(),
        )
    }

    @Test
    fun chinese_weather_queries_require_the_final_engine_to_pass_relevance_threshold() {
        assertTrue(
            WeatherSearchPolicy.requiresRelevantFinalEngine(),
        )
    }
}
