package com.astrbot.android.runtime.search

import com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent
import com.astrbot.android.core.runtime.search.SearchNaturalLanguageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNaturalLanguageParserTest {
    @Test
    fun parses_news_intent_and_explicit_year() {
        val parsed = SearchNaturalLanguageParser.parse("\u4eca\u65e5\u65b0\u95fb 2026\u5e744\u670826\u65e5")

        assertEquals(NaturalLanguageSearchIntent.NEWS, parsed.intent)
        assertEquals(setOf(2026), parsed.explicitYears)
        assertTrue(parsed.containsCjkCharacters)
    }

    @Test
    fun parses_weather_intent() {
        val parsed = SearchNaturalLanguageParser.parse("\u798f\u5dde\u660e\u5929\u5929\u6c14\u9884\u62a5")

        assertEquals(NaturalLanguageSearchIntent.WEATHER, parsed.intent)
    }

    @Test
    fun extracts_latin_tokens_and_cjk_bigrams() {
        assertEquals(setOf("latest", "news"), SearchNaturalLanguageParser.extractLatinTokens("latest news"))
        assertTrue(SearchNaturalLanguageParser.extractCjkBigrams("\u4eca\u65e5\u65b0\u95fb").contains("\u4eca\u65e5"))
    }
}
