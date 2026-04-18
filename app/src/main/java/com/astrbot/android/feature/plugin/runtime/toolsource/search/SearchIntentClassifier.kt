package com.astrbot.android.feature.plugin.runtime.toolsource.search

internal object SearchIntentClassifier {
    private val chineseNewsKeywords = listOf(
        "\u65b0\u95fb",
        "\u5feb\u8baf",
        "\u5934\u6761",
        "\u65f6\u4e8b",
        "\u901a\u62a5",
        "\u6700\u65b0\u6d88\u606f",
        "\u65b0\u95fb\u62a5\u9053",
        "\u65b0\u95fb\u8054\u64ad",
    )

    private val englishNewsKeywords = listOf(
        "news",
        "headline",
        "headlines",
        "breaking news",
        "latest news",
        "news update",
    )

    private val chineseWeatherKeywords = listOf(
        "\u5929\u6c14",
        "\u5929\u6c14\u9884\u62a5",
        "\u6c14\u6e29",
        "\u6e29\u5ea6",
        "\u964d\u96e8",
        "\u4e0b\u96e8",
        "\u96f7\u9635\u96e8",
        "\u591a\u4e91",
        "\u98ce\u529b",
        "\u5927\u98ce",
        "\u6e7f\u5ea6",
        "\u7a7a\u6c14\u8d28\u91cf",
        "\u53f0\u98ce",
        "\u96fe\u973e",
        "\u66b4\u96e8",
        "\u96f7\u66b4",
        "\u964d\u96ea",
        "\u79ef\u96ea",
    )

    private val englishWeatherKeywords = listOf(
        "weather",
        "forecast",
        "temperature",
        "temp",
        "rain",
        "rainy",
        "storm",
        "thunder",
        "snow",
        "snowy",
        "cloudy",
        "sunny",
        "humidity",
        "wind",
        "windy",
        "aqi",
    )

    fun classify(query: String): SearchIntent {
        val normalizedQuery = query.trim()
        val lowerQuery = normalizedQuery.lowercase()
        return when {
            containsNewsSignal(normalizedQuery, lowerQuery) -> SearchIntent.NEWS
            containsWeatherSignal(normalizedQuery, lowerQuery) -> SearchIntent.WEATHER
            else -> SearchIntent.GENERAL
        }
    }

    private fun containsNewsSignal(
        normalizedQuery: String,
        lowerQuery: String,
    ): Boolean {
        return chineseNewsKeywords.any { normalizedQuery.contains(it) } ||
            englishNewsKeywords.any { keyword -> lowerQuery.containsWholeWord(keyword) }
    }

    private fun containsWeatherSignal(
        normalizedQuery: String,
        lowerQuery: String,
    ): Boolean {
        return chineseWeatherKeywords.any { normalizedQuery.contains(it) } ||
            englishWeatherKeywords.any { keyword -> lowerQuery.containsWholeWord(keyword) }
    }

    private fun String.containsWholeWord(word: String): Boolean {
        return Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(this)
    }
}
