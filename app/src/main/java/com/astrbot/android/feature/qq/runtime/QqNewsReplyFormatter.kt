package com.astrbot.android.feature.qq.runtime

internal object QqNewsReplyFormatter {
    fun formatSearchResultFacts(
        structuredContent: Map<String, Any?>?,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): String {
        val results = structuredContent
            ?.get("results")
            ?.asResultMaps()
            .orEmpty()
            .take(maxResults.coerceAtLeast(1))
        if (results.isEmpty()) return NO_CONFIRMED_NEWS
        return results.mapIndexed { index, result ->
            val title = result["title"].asCleanString()
            val snippet = result["snippet"].asCleanString().takeClean(MAX_FACT_SNIPPET_CHARS)
            val source = result["source"].asCleanString()
            val body = when {
                title.isNotBlank() && snippet.isNotBlank() -> "$title: $snippet"
                title.isNotBlank() -> title
                snippet.isNotBlank() -> snippet
                else -> NO_TITLE_NEWS
            }
            buildString {
                append(index + 1).append(". ").append(body)
                if (source.isNotBlank()) {
                    append(" (").append(SOURCE_LABEL).append(": ").append(source).append(")")
                }
            }
        }.joinToString("\n")
    }

    fun formatSegments(
        text: String,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): List<String> {
        val points = extractPoints(text)
        if (points.isEmpty()) return listOf(text.trim()).filter(String::isNotBlank)
        val normalized = points.mapIndexed { index, point ->
            val withoutMarker = point.removeBulletMarker().trim()
            "${index + 1}. $withoutMarker"
        }
        return splitByPoints(normalized, maxChars.coerceAtLeast(40))
    }

    private fun extractPoints(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        val lines = trimmed.lines()
            .map(String::trim)
            .filter(String::isNotBlank)
        val bulletLines = lines.filter { it.hasBulletMarker() }
        if (bulletLines.isNotEmpty()) return bulletLines
        return trimmed
            .split(Regex("""(?<=[。！？.!?])\s*"""))
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun splitByPoints(points: List<String>, maxChars: Int): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        for (point in points) {
            if (point.length > maxChars) {
                if (current.isNotBlank()) {
                    segments += current.toString()
                    current.clear()
                }
                segments += point.chunked(maxChars)
                continue
            }
            val candidateLength = if (current.isEmpty()) point.length else current.length + 1 + point.length
            if (candidateLength > maxChars && current.isNotBlank()) {
                segments += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append('\n')
            current.append(point)
        }
        if (current.isNotBlank()) segments += current.toString()
        return segments
    }

    private fun String.hasBulletMarker(): Boolean {
        return trim().matches(Regex("""^(\d+[.)、]|[-*•])\s*.+"""))
    }

    private fun String.removeBulletMarker(): String {
        return replace(Regex("""^(\d+[.)、]|[-*•])\s*"""), "")
    }

    private fun StringBuilder.isNotBlank(): Boolean = toString().isNotBlank()

    private fun Any?.asCleanString(): String {
        return (this as? String)
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            .orEmpty()
    }

    private fun Any?.asResultMaps(): List<Map<String, Any?>> {
        return (this as? List<*>)
            ?.mapNotNull { item -> item as? Map<*, *> }
            ?.map { item ->
                item.entries.mapNotNull { (key, value) ->
                    (key as? String)?.let { typedKey -> typedKey to value }
                }.toMap()
            }
            .orEmpty()
    }

    private fun String.takeClean(limit: Int): String {
        if (length <= limit) return this
        return take(limit).trimEnd() + "..."
    }

    private const val DEFAULT_MAX_CHARS = 150
    private const val DEFAULT_MAX_RESULTS = 5
    private const val MAX_FACT_SNIPPET_CHARS = 120
    private const val SOURCE_LABEL = "\u6765\u6e90"
    private const val NO_CONFIRMED_NEWS = "\u672a\u68c0\u7d22\u5230\u53ef\u786e\u8ba4\u65b0\u95fb\u3002"
    private const val NO_TITLE_NEWS = "\u672a\u547d\u540d\u65b0\u95fb"
}
