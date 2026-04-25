package com.astrbot.android.core.runtime.search.html

import com.astrbot.android.core.runtime.search.NaturalLanguageSearchIntent
import com.astrbot.android.core.runtime.search.SearchNaturalLanguageParser

internal data class SearchPolicy(
    val intent: SearchIntent,
    val engineOrder: List<SearchEngine>,
    val allowLowRelevanceFallback: Boolean,
    val requiresRelevantFinalEngine: Boolean,
    val acceptOrganicLastResort: Boolean = false,
)

internal object SearchIntentClassifier {
    fun classify(query: String): SearchIntent {
        return when (SearchNaturalLanguageParser.parse(query).intent) {
            NaturalLanguageSearchIntent.NEWS -> SearchIntent.NEWS
            NaturalLanguageSearchIntent.WEATHER -> SearchIntent.WEATHER
            else -> SearchIntent.GENERAL
        }
    }
}

internal object SearchPolicyResolver {
    fun resolve(
        intent: SearchIntent,
        query: String = "",
    ): SearchPolicy {
        return when (intent) {
            SearchIntent.WEATHER -> SearchPolicy(
                intent = intent,
                engineOrder = listOf(SearchEngine.SOGOU, SearchEngine.BING),
                allowLowRelevanceFallback = false,
                requiresRelevantFinalEngine = true,
                acceptOrganicLastResort = false,
            )
            SearchIntent.NEWS -> SearchPolicy(
                intent = intent,
                engineOrder = if (SearchNaturalLanguageParser.parse(query).containsCjkCharacters) {
                    listOf(SearchEngine.SOGOU, SearchEngine.BING)
                } else {
                    listOf(SearchEngine.BING, SearchEngine.SOGOU)
                },
                allowLowRelevanceFallback = false,
                requiresRelevantFinalEngine = true,
                acceptOrganicLastResort = true,
            )
            SearchIntent.GENERAL -> SearchPolicy(
                intent = intent,
                engineOrder = listOf(SearchEngine.BING, SearchEngine.SOGOU),
                allowLowRelevanceFallback = true,
                requiresRelevantFinalEngine = false,
            )
        }
    }
}

internal fun String.normalizeSearchText(): String {
    return lowercase()
        .replace(Regex("\\s+"), "")
        .trim()
}
