package com.astrbot.android.runtime.plugin.toolsource.search

internal object NewsSearchPolicy {
    fun preferredEngines(query: String): List<SearchEngine> {
        return if (containsCjkCharacters(query)) {
            listOf(SearchEngine.SOGOU, SearchEngine.BING)
        } else {
            listOf(SearchEngine.BING, SearchEngine.SOGOU)
        }
    }

    fun allowLowRelevanceFallback(): Boolean {
        return false
    }

    fun requiresRelevantFinalEngine(): Boolean {
        return true
    }

    fun toSearchPolicy(query: String): SearchPolicy {
        return SearchPolicy(
            intent = SearchIntent.NEWS,
            engineOrder = preferredEngines(query),
            allowLowRelevanceFallback = allowLowRelevanceFallback(),
            requiresRelevantFinalEngine = requiresRelevantFinalEngine(),
        )
    }

    private fun containsCjkCharacters(query: String): Boolean {
        return query.any { char ->
            val code = char.code
            code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF
        }
    }
}
