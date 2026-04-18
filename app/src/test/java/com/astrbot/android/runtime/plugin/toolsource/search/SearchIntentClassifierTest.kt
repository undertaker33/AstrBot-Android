package com.astrbot.android.feature.plugin.runtime.toolsource.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchIntentClassifierTest {

    @Test
    fun chinese_weather_query_is_classified_as_weather() {
        assertEquals(
            SearchIntent.WEATHER,
            SearchIntentClassifier.classify("\u660e\u5929\u798f\u5dde\u5929\u6c14\u9884\u62a5"),
        )
    }

    @Test
    fun plain_web_query_is_classified_as_general() {
        assertEquals(
            SearchIntent.GENERAL,
            SearchIntentClassifier.classify("\u798f\u5dde\u4e09\u574a\u4e03\u5df7\u653b\u7565"),
        )
    }

    @Test
    fun latin_weather_query_is_classified_as_weather() {
        assertEquals(
            SearchIntent.WEATHER,
            SearchIntentClassifier.classify("fuzhou weather tomorrow"),
        )
    }

    @Test
    fun chinese_news_query_is_classified_as_news() {
        assertEquals(
            SearchIntent.NEWS,
            SearchIntentClassifier.classify("\u798f\u5dde\u4eca\u65e5\u65b0\u95fb\u5934\u6761"),
        )
    }

    @Test
    fun latin_news_query_is_classified_as_news() {
        assertEquals(
            SearchIntent.NEWS,
            SearchIntentClassifier.classify("latest fuzhou news"),
        )
    }
}
