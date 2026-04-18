package com.astrbot.android.feature.plugin.runtime.toolsource.search

internal enum class SearchEngine {
    BING,
    SOGOU,
}

internal data class SearchPolicy(
    val intent: SearchIntent,
    val engineOrder: List<SearchEngine>,
    val allowLowRelevanceFallback: Boolean,
    val requiresRelevantFinalEngine: Boolean,
)

internal object SearchPolicyResolver {
    fun resolve(
        intent: SearchIntent,
        query: String = "",
    ): SearchPolicy {
        return when (intent) {
            SearchIntent.WEATHER -> WeatherSearchPolicy.toSearchPolicy()
            SearchIntent.NEWS -> NewsSearchPolicy.toSearchPolicy(query)
            SearchIntent.GENERAL -> SearchPolicy(
                intent = intent,
                engineOrder = listOf(SearchEngine.BING, SearchEngine.SOGOU),
                allowLowRelevanceFallback = true,
                requiresRelevantFinalEngine = false,
            )
        }
    }
}
