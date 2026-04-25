package com.astrbot.android.core.runtime.search

enum class WebSearchTriggerIntent {
    NONE,
    NEWS,
    WEATHER,
    REALTIME,
}

data class WebSearchTriggerRuleResult(
    val intent: WebSearchTriggerIntent,
    val matchedKeyword: String = "",
) {
    val shouldInjectSearchPrompt: Boolean
        get() = intent != WebSearchTriggerIntent.NONE
}

object WebSearchTriggerRules {
    fun evaluate(text: String): WebSearchTriggerRuleResult {
        val parsed = SearchNaturalLanguageParser.parse(text)
        return WebSearchTriggerRuleResult(
            intent = when (parsed.intent) {
                NaturalLanguageSearchIntent.NEWS -> WebSearchTriggerIntent.NEWS
                NaturalLanguageSearchIntent.WEATHER -> WebSearchTriggerIntent.WEATHER
                NaturalLanguageSearchIntent.REALTIME -> WebSearchTriggerIntent.REALTIME
                NaturalLanguageSearchIntent.NONE -> WebSearchTriggerIntent.NONE
            },
            matchedKeyword = parsed.matchedKeyword,
        )
    }
}
