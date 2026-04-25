package com.astrbot.android.runtime.search

import com.astrbot.android.core.runtime.search.WebSearchTriggerIntent
import com.astrbot.android.core.runtime.search.WebSearchTriggerRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTriggerRulesTest {
    @Test
    fun detects_news_queries() {
        val result = WebSearchTriggerRules.evaluate("今天新闻有哪些")

        assertEquals(WebSearchTriggerIntent.NEWS, result.intent)
        assertTrue(result.shouldInjectSearchPrompt)
    }

    @Test
    fun detects_weather_queries() {
        val result = WebSearchTriggerRules.evaluate("福州今天天气")

        assertEquals(WebSearchTriggerIntent.WEATHER, result.intent)
        assertTrue(result.shouldInjectSearchPrompt)
    }

    @Test
    fun ignores_plain_chat() {
        val result = WebSearchTriggerRules.evaluate("晚上好呀")

        assertEquals(WebSearchTriggerIntent.NONE, result.intent)
        assertFalse(result.shouldInjectSearchPrompt)
    }
}
