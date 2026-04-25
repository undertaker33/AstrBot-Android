package com.astrbot.android.core.runtime.search.html

import com.astrbot.android.core.runtime.search.UnifiedSearchResult

internal enum class SearchIntent {
    WEATHER,
    NEWS,
    GENERAL,
}

enum class SearchEngine {
    BING,
    SOGOU,
}

data class HtmlSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val engine: String = "",
    val module: String = "",
    val source: String = "",
)

internal fun HtmlSearchResult.toUnifiedResult(index: Int): UnifiedSearchResult {
    return UnifiedSearchResult(
        index = index,
        title = title,
        url = url,
        snippet = snippet,
        source = source,
        providerId = engine,
        metadata = mapOf(
            "engine" to engine,
            "module" to module,
        ).filterValues(String::isNotBlank),
    )
}
