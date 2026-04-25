package com.astrbot.android.core.runtime.search

enum class NaturalLanguageSearchIntent {
    NONE,
    NEWS,
    WEATHER,
    REALTIME,
}

data class ParsedSearchQuery(
    val rawText: String,
    val normalizedText: String,
    val intent: NaturalLanguageSearchIntent,
    val matchedKeyword: String = "",
    val explicitYears: Set<Int> = emptySet(),
) {
    val containsCjkCharacters: Boolean
        get() = normalizedText.any { char ->
            val code = char.code
            code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF
        }
}

object SearchNaturalLanguageParser {
    private val newsKeywords = listOf(
        "\u65b0\u95fb",
        "\u4eca\u65e5\u65b0\u95fb",
        "\u4eca\u5929\u65b0\u95fb",
        "\u6700\u65b0\u6d88\u606f",
        "\u5feb\u8baf",
        "\u5934\u6761",
        "\u65f6\u4e8b",
        "\u70ed\u641c",
        "news",
        "headline",
        "headlines",
        "breaking news",
        "latest news",
        "news update",
    )

    private val weatherKeywords = listOf(
        "\u5929\u6c14",
        "\u5929\u6c14\u9884\u62a5",
        "\u6c14\u6e29",
        "\u6e29\u5ea6",
        "\u4e0b\u96e8",
        "\u964d\u96e8",
        "\u6e7f\u5ea6",
        "\u98ce\u529b",
        "\u7a7a\u6c14\u8d28\u91cf",
        "weather",
        "forecast",
        "temperature",
        "humidity",
        "wind",
        "aqi",
    )

    private val realtimeKeywords = listOf(
        "\u4eca\u5929",
        "\u4eca\u65e5",
        "\u73b0\u5728",
        "\u6700\u65b0",
        "\u521a\u521a",
        "\u67e5\u4e00\u4e0b",
        "\u641c\u7d22",
        "\u641c\u4e00\u4e0b",
        "\u5b9e\u65f6",
        "\u5f53\u524d",
        "today",
        "now",
        "latest",
        "search",
        "current",
    )

    private val yearPattern = Regex("""(?<!\d)(19|20)\d{2}(?!\d)""")

    fun parse(text: String): ParsedSearchQuery {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return ParsedSearchQuery(
                rawText = text,
                normalizedText = normalized,
                intent = NaturalLanguageSearchIntent.NONE,
            )
        }
        val matched = firstMatch(normalized, newsKeywords)?.let { NaturalLanguageSearchIntent.NEWS to it }
            ?: firstMatch(normalized, weatherKeywords)?.let { NaturalLanguageSearchIntent.WEATHER to it }
            ?: firstMatch(normalized, realtimeKeywords)?.let { NaturalLanguageSearchIntent.REALTIME to it }

        return ParsedSearchQuery(
            rawText = text,
            normalizedText = normalized,
            intent = matched?.first ?: NaturalLanguageSearchIntent.NONE,
            matchedKeyword = matched?.second.orEmpty(),
            explicitYears = extractExplicitYears(normalized),
        )
    }

    fun extractExplicitYears(text: String): Set<Int> {
        return yearPattern.findAll(text)
            .mapNotNull { match -> match.value.toIntOrNull() }
            .toSet()
    }

    fun extractCjkBigrams(text: String): Set<String> {
        val segments = text.split(Regex("\\s+")).filter(String::isNotBlank)
        val bigrams = mutableSetOf<String>()
        for (segment in segments) {
            val cleaned = segment.replace(Regex("[\\p{Punct}]+"), "")
            if (cleaned.length >= 2) {
                for (index in 0 until cleaned.length - 1) {
                    bigrams.add(cleaned.substring(index, index + 2))
                }
            }
        }
        return bigrams
    }

    fun extractLatinTokens(text: String): Set<String> {
        return Regex("[a-zA-Z]{2,}")
            .findAll(text.lowercase())
            .map { it.value }
            .toSet()
    }

    fun containsWholeWord(text: String, word: String): Boolean {
        return Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(text)
    }

    private fun firstMatch(text: String, keywords: List<String>): String? {
        val lower = text.lowercase()
        return keywords.firstOrNull { keyword ->
            if (keyword.any { it.code > 127 }) {
                text.contains(keyword)
            } else {
                containsWholeWord(lower, keyword.lowercase())
            }
        }
    }
}
