package com.astrbot.android.feature.plugin.runtime.toolsource.search

import com.astrbot.android.feature.plugin.runtime.toolsource.WebSearchResult

internal data class SearchRelevanceAssessment(
    val isRelevant: Boolean,
    val reason: String,
    val bestModule: String? = null,
    val bestRatio: Double = 0.0,
    val matchedResults: Int = 0,
)

internal object SearchRelevanceScorer {
    fun assess(
        query: String,
        results: List<WebSearchResult>,
        intent: SearchIntent = SearchIntentClassifier.classify(query),
    ): SearchRelevanceAssessment {
        if (results.isEmpty()) {
            return SearchRelevanceAssessment(
                isRelevant = false,
                reason = "no_results",
            )
        }

        return when (intent) {
            SearchIntent.WEATHER -> assessWeatherIntent(query, results)
            SearchIntent.NEWS -> assessNewsIntent(query, results)
            SearchIntent.GENERAL -> assessGeneralIntent(query, results)
        }
    }

    private fun assessGeneralIntent(
        query: String,
        results: List<WebSearchResult>,
    ): SearchRelevanceAssessment {
        // Only assess queries with significant CJK content; Latin bigrams are too noisy.
        val cjkCount = query.count { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        if (cjkCount < 2) {
            return SearchRelevanceAssessment(
                isRelevant = true,
                reason = "non_cjk_query_bypass",
                bestModule = results.firstOrNull()?.module,
                bestRatio = 1.0,
                matchedResults = results.size,
            )
        }

        val queryBigrams = extractQueryBigrams(query)
        if (queryBigrams.size < 3) {
            return SearchRelevanceAssessment(
                isRelevant = true,
                reason = "short_query_bypass",
                bestModule = results.firstOrNull()?.module,
                bestRatio = 1.0,
                matchedResults = results.size,
            )
        }

        var bestModule: String? = null
        var bestRatio = 0.0
        var matchedResults = 0

        for (result in results) {
            val text = "${result.title} ${result.snippet}"
            val matchCount = queryBigrams.count { bigram -> text.contains(bigram) }
            val ratio = matchCount.toDouble() / queryBigrams.size
            if (ratio >= RELEVANCE_PER_RESULT_THRESHOLD) {
                matchedResults += 1
            }
            if (ratio > bestRatio) {
                bestRatio = ratio
                bestModule = result.module.ifBlank { result.engine.ifBlank { "unknown" } }
            }
        }

        return if (matchedResults > 0) {
            SearchRelevanceAssessment(
                isRelevant = true,
                reason = "passed_threshold",
                bestModule = bestModule,
                bestRatio = bestRatio,
                matchedResults = matchedResults,
            )
        } else {
            SearchRelevanceAssessment(
                isRelevant = false,
                reason = "no_result_passed_threshold",
                bestModule = bestModule,
                bestRatio = bestRatio,
                matchedResults = 0,
            )
        }
    }

    private fun assessWeatherIntent(
        query: String,
        results: List<WebSearchResult>,
    ): SearchRelevanceAssessment {
        val cjkBigrams = extractQueryBigrams(query)
        val latinTokens = extractLatinTokens(query)

        var bestModule: String? = null
        var bestScore = 0.0
        var matchedResults = 0

        for (result in results) {
            val text = "${result.title} ${result.snippet}"
            val lowerText = text.lowercase()
            val hasWeatherSignal = weatherSignalTerms.any { term ->
                if (term.any { it.code > 127 }) {
                    text.contains(term)
                } else {
                    lowerText.containsWholeWord(term)
                }
            } || result.module.contains("weather")

            val cjkRatio = if (cjkBigrams.isEmpty()) {
                0.0
            } else {
                cjkBigrams.count { bigram -> text.contains(bigram) }.toDouble() / cjkBigrams.size
            }
            val latinMatches = latinTokens.count { token -> lowerText.containsWholeWord(token) }
            val latinRatio = if (latinTokens.isEmpty()) {
                0.0
            } else {
                latinMatches.toDouble() / latinTokens.size
            }

            val score = when {
                cjkBigrams.isNotEmpty() && latinTokens.isNotEmpty() -> maxOf(cjkRatio, latinRatio)
                cjkBigrams.isNotEmpty() -> cjkRatio
                latinTokens.isNotEmpty() -> latinRatio
                else -> 0.0
            }

            val passesQueryMatch = when {
                cjkBigrams.isNotEmpty() -> cjkRatio >= RELEVANCE_PER_RESULT_THRESHOLD
                latinTokens.isNotEmpty() -> latinMatches > 0
                else -> false
            }

            if (hasWeatherSignal && passesQueryMatch) {
                matchedResults += 1
            }
            if (score > bestScore) {
                bestScore = score
                bestModule = result.module.ifBlank { result.engine.ifBlank { "unknown" } }
            }
        }

        return if (matchedResults > 0) {
            SearchRelevanceAssessment(
                isRelevant = true,
                reason = "weather_result_passed_threshold",
                bestModule = bestModule,
                bestRatio = bestScore,
                matchedResults = matchedResults,
            )
        } else {
            SearchRelevanceAssessment(
                isRelevant = false,
                reason = "no_weather_result_passed_threshold",
                bestModule = bestModule,
                bestRatio = bestScore,
                matchedResults = 0,
            )
        }
    }

    private fun assessNewsIntent(
        query: String,
        results: List<WebSearchResult>,
    ): SearchRelevanceAssessment {
        val cjkBigrams = extractQueryBigrams(query)
        val latinTokens = extractLatinTokens(query)

        var bestModule: String? = null
        var bestScore = 0.0
        var matchedResults = 0

        for (result in results) {
            val text = "${result.title} ${result.snippet}"
            val lowerText = text.lowercase()
            val hasNewsSignal = newsSignalTerms.any { term ->
                if (term.any { it.code > 127 }) {
                    text.contains(term)
                } else {
                    lowerText.containsWholeWord(term)
                }
            } || result.module.contains("news", ignoreCase = true)

            val hasTimelinessSignal = newsTimelinessTerms.any { term ->
                if (term.any { it.code > 127 }) {
                    text.contains(term)
                } else {
                    lowerText.containsWholeWord(term)
                }
            } || newsDatePatterns.any { pattern -> pattern.containsMatchIn(text) }

            val cjkRatio = if (cjkBigrams.isEmpty()) {
                0.0
            } else {
                cjkBigrams.count { bigram -> text.contains(bigram) }.toDouble() / cjkBigrams.size
            }
            val latinMatches = latinTokens.count { token -> lowerText.containsWholeWord(token) }
            val latinRatio = if (latinTokens.isEmpty()) {
                0.0
            } else {
                latinMatches.toDouble() / latinTokens.size
            }

            val score = when {
                cjkBigrams.isNotEmpty() && latinTokens.isNotEmpty() -> maxOf(cjkRatio, latinRatio)
                cjkBigrams.isNotEmpty() -> cjkRatio
                latinTokens.isNotEmpty() -> latinRatio
                else -> 0.0
            }

            val passesQueryMatch = when {
                cjkBigrams.isNotEmpty() -> cjkRatio >= RELEVANCE_PER_RESULT_THRESHOLD
                latinTokens.isNotEmpty() -> latinMatches > 0
                else -> false
            }

            if (hasNewsSignal && hasTimelinessSignal && passesQueryMatch) {
                matchedResults += 1
            }
            if (score > bestScore) {
                bestScore = score
                bestModule = result.module.ifBlank { result.engine.ifBlank { "unknown" } }
            }
        }

        return if (matchedResults > 0) {
            SearchRelevanceAssessment(
                isRelevant = true,
                reason = "news_result_passed_threshold",
                bestModule = bestModule,
                bestRatio = bestScore,
                matchedResults = matchedResults,
            )
        } else {
            SearchRelevanceAssessment(
                isRelevant = false,
                reason = "no_news_result_passed_threshold",
                bestModule = bestModule,
                bestRatio = bestScore,
                matchedResults = 0,
            )
        }
    }
}

internal fun extractQueryBigrams(query: String): Set<String> {
    val segments = query.split(Regex("\\s+")).filter(String::isNotBlank)
    val bigrams = mutableSetOf<String>()
    for (segment in segments) {
        val cleaned = segment.replace(Regex("[\\p{Punct}]+"), "")
        if (cleaned.length >= 2) {
            for (i in 0 until cleaned.length - 1) {
                bigrams.add(cleaned.substring(i, i + 2))
            }
        }
    }
    return bigrams
}

internal fun String.normalizeSearchText(): String {
    return lowercase()
        .replace(Regex("\\s+"), "")
        .trim()
}

private fun extractLatinTokens(query: String): Set<String> {
    return Regex("[a-zA-Z]{2,}")
        .findAll(query.lowercase())
        .map { it.value }
        .toSet()
}

private fun String.containsWholeWord(word: String): Boolean {
    return Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(this)
}

private val newsSignalTerms = setOf(
    "\u65b0\u95fb",
    "\u5feb\u8baf",
    "\u5934\u6761",
    "\u8d44\u8baf",
    "\u901a\u62a5",
    "\u62a5\u9053",
    "\u6d88\u606f",
    "news",
    "headline",
    "headlines",
    "breaking news",
    "news update",
    "report",
    "reports",
)

private val newsTimelinessTerms = setOf(
    "\u4eca\u5929",
    "\u4eca\u65e5",
    "\u521a\u521a",
    "\u6700\u65b0",
    "\u5b9e\u65f6",
    "\u5206\u949f\u524d",
    "\u5c0f\u65f6\u524d",
    "\u6628\u65e5",
    "\u6628\u665a",
    "\u672c\u5468",
    "\u672c\u6708",
    "today",
    "latest",
    "current",
    "recent",
    "breaking",
    "just in",
    "minutes ago",
    "hours ago",
    "updated",
)

private val newsDatePatterns = listOf(
    Regex("""\b(19|20)\d{2}[-/\.]\d{1,2}[-/\.]\d{1,2}\b"""),
    Regex("""(19|20)\d{2}\u5e74\d{1,2}\u6708\d{1,2}\u65e5"""),
    Regex("""\b\d{1,2}:\d{2}\b"""),
)

private val weatherSignalTerms = setOf(
    "\u5929\u6c14",
    "\u9884\u62a5",
    "\u6c14\u6e29",
    "\u6e29\u5ea6",
    "\u5c0f\u96e8",
    "\u4e2d\u96e8",
    "\u5927\u96e8",
    "\u66b4\u96e8",
    "\u96f7\u66b4",
    "\u9634",
    "\u591a\u4e91",
    "\u6674",
    "\u98ce",
    "\u6e7f\u5ea6",
    "\u96fe\u973e",
    "weather",
    "forecast",
    "temperature",
    "temp",
    "rain",
    "rainy",
    "storm",
    "snow",
    "cloudy",
    "sunny",
    "humidity",
    "wind",
)

private const val RELEVANCE_PER_RESULT_THRESHOLD = 0.25
