package com.astrbot.android.feature.plugin.runtime.toolsource.search

internal object WeatherSearchPolicy {
    fun preferredEngines(): List<SearchEngine> {
        return listOf(SearchEngine.SOGOU, SearchEngine.BING)
    }

    fun allowLowRelevanceFallback(): Boolean {
        return false
    }

    fun requiresRelevantFinalEngine(): Boolean {
        return true
    }

    fun toSearchPolicy(): SearchPolicy {
        return SearchPolicy(
            intent = SearchIntent.WEATHER,
            engineOrder = preferredEngines(),
            allowLowRelevanceFallback = allowLowRelevanceFallback(),
            requiresRelevantFinalEngine = requiresRelevantFinalEngine(),
        )
    }
}
