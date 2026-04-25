package com.astrbot.android.core.runtime.search.html

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal fun parseBingResults(
    html: String,
    maxResults: Int,
): List<HtmlSearchResult> {
    val doc = Jsoup.parse(html)
    val results = mutableListOf<HtmlSearchResult>()

    extractBingWeatherCard(doc)?.let(results::add)
    if (results.size < maxResults) {
        extractBingAnswerCard(doc)?.let(results::add)
    }
    if (results.size < maxResults) {
        extractBingContextCard(doc)?.let(results::add)
    }

    val items = doc.select("li.b_algo, div.b_algo").ifEmpty {
        doc.select("li:has(h2 a[href]), div:has(h2 a[href])")
    }
    for (item in items) {
        if (results.size >= maxResults) break
        val headerLink = sequenceOf(
            item.selectFirst(".b_algoheader a[href]"),
            item.selectFirst("h2 a[href]"),
            item.selectFirst("h2 > a[href]"),
            item.selectFirst("a.tilk[href]"),
            item.selectFirst("a[href]"),
        ).firstOrNull()
        val title = sequenceOf(
            item.selectFirst(".b_algoheader h2")?.text()?.trim(),
            item.selectFirst("h2")?.text()?.trim(),
            headerLink?.text()?.trim(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val href = headerLink?.attr("href")?.trim().orEmpty()
        val snippetEl = sequenceOf(
            item.selectFirst(".b_caption p"),
            item.selectFirst(".b_caption .b_lineclamp3"),
            item.selectFirst(".b_caption"),
            item.selectFirst("p"),
        ).firstOrNull()
        val snippet = snippetEl?.text()?.trim()?.take(MAX_SEARCH_SNIPPET_LENGTH).orEmpty()
        if (title.isNotBlank() && href.isNotBlank()) {
            results += HtmlSearchResult(
                title = title,
                url = href,
                snippet = snippet,
                engine = "bing",
                module = "bing_b_algo",
            )
        }
    }
    if (results.size < maxResults) {
        val knownUrls = results.mapTo(mutableSetOf()) { it.url }
        for (link in doc.select("h2 a[href]")) {
            if (results.size >= maxResults) break
            val href = link.attr("href").trim()
            if (href.isBlank() || !knownUrls.add(href)) continue
            val title = link.text().trim()
            if (title.isBlank()) continue
            val container = link.parents().firstOrNull { parent ->
                parent.selectFirst(".b_caption, p") != null
            }
            val snippet = sequenceOf(
                container?.selectFirst(".b_caption p"),
                container?.selectFirst(".b_caption .b_lineclamp3"),
                container?.selectFirst(".b_caption"),
                container?.selectFirst("p"),
            ).firstOrNull()?.text()?.trim()?.take(MAX_SEARCH_SNIPPET_LENGTH).orEmpty()
            results += HtmlSearchResult(
                title = title,
                url = href,
                snippet = snippet,
                engine = "bing",
                module = "bing_b_algo",
            )
        }
    }
    return results
}

private fun extractBingWeatherCard(doc: Document): HtmlSearchResult? {
    val weatherContainer = sequenceOf(
        doc.selectFirst("[data-tag^='wea']"),
        doc.selectFirst("#wtr_snc"),
        doc.selectFirst(".wtr_hero"),
        doc.selectFirst("#wtr_card"),
        doc.selectFirst(".b_ans:has(.wtr_currTemp)"),
    ).firstOrNull() ?: return null

    val location = sequenceOf(
        weatherContainer.selectFirst(".wtr_locTitle")?.text()?.trim(),
        weatherContainer.selectFirst(".wtr_loc")?.text()?.trim(),
        doc.selectFirst(".wtr_locTitle")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val temperature = sequenceOf(
        weatherContainer.selectFirst(".wtr_currTemp")?.text()?.trim(),
        weatherContainer.selectFirst(".wtr-currTemp")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val condition = sequenceOf(
        weatherContainer.selectFirst(".wtr_condi")?.text()?.trim(),
        weatherContainer.selectFirst(".wtr_cond")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val forecast = weatherContainer.selectFirst(".wtr_fctxt")?.text()?.trim().orEmpty()

    if (location.isBlank() && temperature.isBlank() && condition.isBlank()) return null

    val weatherSuffix = "\u5929\u6c14"
    val title = buildString {
        append(location.ifBlank { weatherSuffix })
        if (!endsWith(weatherSuffix)) append(weatherSuffix)
    }
    val snippet = listOf(temperature, condition, forecast)
        .filter(String::isNotBlank)
        .joinToString("\uff0c")
        .ifBlank { "\u5df2\u63d0\u53d6\u5230\u5929\u6c14\u5361\u7247\u3002" }

    return HtmlSearchResult(
        title = title,
        url = BING_SEARCH_URL,
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "bing",
        module = "bing_weather_card",
    )
}

private fun extractBingAnswerCard(doc: Document): HtmlSearchResult? {
    val answerContainers = doc.select("div.b_ans, li.b_ans")
    for (container in answerContainers) {
        if (container.selectFirst(".wtr_currTemp, .wtr_hero, .wtr_condi, [data-tag^='wea']") != null) continue

        val richContent = container.selectFirst(".b_rich, .b_vPanel, .b_promoteText") ?: continue
        val title = sequenceOf(
            richContent.selectFirst("h2")?.text()?.trim(),
            richContent.selectFirst(".b_entityTitle")?.text()?.trim(),
            container.selectFirst("h2")?.text()?.trim(),
        ).firstOrNull { !it.isNullOrBlank() } ?: continue
        val snippet = sequenceOf(
            richContent.selectFirst(".b_factrow")?.text()?.trim(),
            richContent.selectFirst(".b_paractl")?.text()?.trim(),
            richContent.selectFirst("p")?.text()?.trim(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        if (snippet.isNotBlank()) {
            return HtmlSearchResult(
                title = title,
                url = BING_SEARCH_URL,
                snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
                engine = "bing",
                module = "bing_answer_card",
            )
        }
    }
    return null
}

private fun extractBingContextCard(doc: Document): HtmlSearchResult? {
    val contextContainer = sequenceOf(
        doc.selectFirst("#b_context"),
        doc.selectFirst(".b_context"),
    ).firstOrNull() ?: return null
    val title = sequenceOf(
        contextContainer.selectFirst(".b_entityTitle")?.text()?.trim(),
        contextContainer.selectFirst("h2")?.text()?.trim(),
        contextContainer.selectFirst(".b_focusLabel")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() } ?: return null
    val snippet = sequenceOf(
        contextContainer.selectFirst(".b_caption p")?.text()?.trim(),
        contextContainer.selectFirst(".b_paractl")?.text()?.trim(),
        contextContainer.selectFirst(".b_snippet")?.text()?.trim(),
        contextContainer.selectFirst("p")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    if (snippet.isBlank()) return null

    return HtmlSearchResult(
        title = title,
        url = BING_SEARCH_URL,
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "bing",
        module = "bing_context_card",
    )
}

internal fun parseSogouResults(
    html: String,
    maxResults: Int,
    searchUrl: String,
): List<HtmlSearchResult> {
    val doc = Jsoup.parse(html)
    val results = mutableListOf<HtmlSearchResult>()

    extractSogouWeatherCardV2(doc, searchUrl)?.let(results::add)
    results += extractSogouSupListResults(html)
    extractSogouAnswerSummaryResult(html, searchUrl)?.let(results::add)

    val items = doc.select("div.vrwrap, div.rb")
    for (item in items) {
        if (results.size >= maxResults) break
        val titleEl = sequenceOf(
            item.selectFirst("h3 a[href]"),
            item.selectFirst(".vr-title a[href]"),
            item.selectFirst("a[href]"),
        ).firstOrNull() ?: continue
        val title = sequenceOf(
            item.selectFirst("h3")?.text()?.trim(),
            item.selectFirst(".vr-title")?.text()?.trim(),
            titleEl.text().trim(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val href = titleEl.attr("href").trim()
        val snippetEl = sequenceOf(
            item.selectFirst("p.str_info"),
            item.selectFirst("div.str-text-info"),
            item.selectFirst(".text-layout"),
            item.selectFirst("p"),
        ).firstOrNull()
        val snippet = snippetEl?.text()?.trim()?.take(MAX_SEARCH_SNIPPET_LENGTH).orEmpty()
        if (title.isNotBlank() && href.isNotBlank()) {
            results += HtmlSearchResult(
                title = title,
                url = href,
                snippet = snippet,
                engine = "sogou",
                module = "sogou_rb",
            )
        }
    }

    return results
        .distinctBy { "${it.engine}|${it.url}|${it.title}" }
        .take(maxResults)
}

private fun extractSogouWeatherCardV2(
    doc: Document,
    searchUrl: String,
): HtmlSearchResult? {
    val weatherRoot = doc.selectFirst("div.weather210208, div.weather210208-wrap") ?: return null
    val location = sequenceOf(
        doc.selectFirst(".location-tab li")?.text()?.trim(),
        doc.selectFirst(".location-module li")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val temperature = sequenceOf(
        weatherRoot.selectFirst(".temperature p")?.text()?.trim(),
        weatherRoot.selectFirst(".js_shikuang p")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val condition = sequenceOf(
        weatherRoot.selectFirst(".wind-box .weath")?.text()?.trim(),
        weatherRoot.selectFirst(".detail")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    if (location.isBlank() && temperature.isBlank() && condition.isBlank()) return null

    val weatherSuffix = "\u5929\u6c14"
    val title = buildString {
        append(location.ifBlank { weatherSuffix })
        if (!endsWith(weatherSuffix)) append(weatherSuffix)
    }
    val snippet = listOf(temperature, condition)
        .filter(String::isNotBlank)
        .joinToString("\uff0c")
        .ifBlank { "\u5df2\u63d0\u53d6\u5230\u5929\u6c14\u5361\u7247\u3002" }

    return HtmlSearchResult(
        title = title,
        url = searchUrl,
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "sogou",
        module = "sogou_weather_card",
    )
}

private fun extractSogouSupListResults(html: String): List<HtmlSearchResult> {
    val arrayText = extractJsonArrayByKey(html, "supList") ?: return emptyList()
    val array = runCatching { JSONArray(arrayText) }.getOrNull() ?: return emptyList()
    val results = mutableListOf<HtmlSearchResult>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val title = item.optString("sup_title").trim()
        val url = item.optString("sup_url").trim().replace("\\/", "/")
        val snippet = item.optString("sup_passage").trim().take(MAX_SEARCH_SNIPPET_LENGTH)
        val source = item.optString("sup_source").trim()
        val normalizedTitle = when {
            title.isNotBlank() -> title
            source.isNotBlank() -> source
            else -> ""
        }
        if (normalizedTitle.isNotBlank() && url.isNotBlank() && snippet.isNotBlank()) {
            results += HtmlSearchResult(
                title = normalizedTitle,
                url = url,
                snippet = snippet,
                engine = "sogou",
                module = "sogou_sup_list",
                source = source,
            )
        }
    }
    return results
}

private fun extractSogouAnswerSummaryResult(
    html: String,
    searchUrl: String,
): HtmlSearchResult? {
    val escapedSummary = Regex("\"answer_summary\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)
        ?: return null
    val summary = decodeJsonString(escapedSummary).trim()
    if (summary.isBlank()) return null
    return HtmlSearchResult(
        title = "\u641c\u72d7\u641c\u7d22\u6458\u8981",
        url = searchUrl,
        snippet = summary.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "sogou",
        module = "sogou_answer_summary",
    )
}

private fun extractJsonArrayByKey(
    html: String,
    key: String,
): String? {
    val keyIndex = html.indexOf("\"$key\"")
    if (keyIndex < 0) return null
    val arrayStart = html.indexOf('[', keyIndex)
    if (arrayStart < 0) return null

    var index = arrayStart
    var depth = 0
    var inString = false
    var escaping = false
    while (index < html.length) {
        val ch = html[index]
        if (escaping) {
            escaping = false
        } else if (ch == '\\') {
            escaping = true
        } else if (ch == '"') {
            inString = !inString
        } else if (!inString) {
            when (ch) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return html.substring(arrayStart, index + 1)
                }
            }
        }
        index++
    }
    return null
}

private fun decodeJsonString(value: String): String {
    return runCatching {
        JSONObject("""{"v":"$value"}""").getString("v")
    }.getOrDefault(value)
}

private const val BING_SEARCH_URL = "https://www.bing.com/search"
internal const val MAX_SEARCH_SNIPPET_LENGTH = 700
