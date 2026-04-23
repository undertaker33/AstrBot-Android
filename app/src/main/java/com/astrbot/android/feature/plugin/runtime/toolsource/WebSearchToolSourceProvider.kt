package com.astrbot.android.feature.plugin.runtime.toolsource
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility
import com.astrbot.android.feature.plugin.runtime.toolsource.search.SearchEngine
import com.astrbot.android.feature.plugin.runtime.toolsource.search.SearchIntent
import com.astrbot.android.feature.plugin.runtime.toolsource.search.SearchIntentClassifier
import com.astrbot.android.feature.plugin.runtime.toolsource.search.SearchPolicy
import com.astrbot.android.feature.plugin.runtime.toolsource.search.SearchPolicyResolver
import com.astrbot.android.feature.plugin.runtime.toolsource.search.SearchRelevanceAssessment
import com.astrbot.android.feature.plugin.runtime.toolsource.search.SearchRelevanceScorer
import com.astrbot.android.feature.plugin.runtime.toolsource.search.normalizeSearchText
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

class WebSearchToolSourceProvider @Inject constructor(
    private val transport: RuntimeNetworkTransport,
    override val contextResolver: FutureToolSourceContextResolver,
) : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.WEB_SEARCH

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        if (!context.toolSourceContext.webSearchEnabled) return emptyList()
        return listOf(buildWebSearchBinding())
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        return if (context.toolSourceContext.webSearchEnabled) {
            ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = true,
            )
        } else {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = false,
                detailCode = "web_search_disabled",
                detailMessage = "Web search is disabled in this config profile.",
            )
        }
    }

    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        val toolName = request.args.toolId.substringAfter(":")
        return try {
            val text = when (toolName) {
                "web_search" -> handleWebSearch(request.args.payload)
                else -> throw IllegalArgumentException("Unknown web search tool: $toolName")
            }
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = text,
                ),
            )
        } catch (e: Exception) {
            AppLogger.append("WebSearch invoke error: ${e.message}")
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "web_search_error",
                    text = "Web search failed: ${e.message}",
                ),
            )
        }
    }

    private suspend fun handleWebSearch(payload: Map<String, Any?>): String {
        val query = (payload["query"] as? String)?.trim()
            ?: throw IllegalArgumentException("'query' parameter is required.")
        val maxResults = ((payload["max_results"] as? Number)?.toInt() ?: 5).coerceIn(1, 10)
        val intent = SearchIntentClassifier.classify(query)
        val policy = SearchPolicyResolver.resolve(intent, query)

        AppLogger.append(
            "WebSearch: intent=${intent.name} engine_order=[${policy.engineOrder.joinToString(",") { it.name.lowercase() }}] " +
                "allow_low_relevance_fallback=${policy.allowLowRelevanceFallback} query='$query' max_results=$maxResults",
        )
        return executeWebSearchQuery(
            query = query,
            maxResults = maxResults,
            policy = policy,
            bingSearch = ::searchBing,
            sogouSearch = ::searchSogou,
        )
    }

    private suspend fun searchBing(query: String, maxResults: Int): List<WebSearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.bing.com/search?q=$encoded&count=$maxResults"
            val request = RuntimeNetworkRequest(
                capability = RuntimeNetworkCapability.WEB_SEARCH,
                method = "GET",
                url = url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                ),
                timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
            )
            val response = transport.execute(request)
            val body = response.bodyString
            if (body.isBlank()) throw IllegalStateException("Empty response from Bing")
            parseBingResults(body, maxResults).also { results ->
                AppLogger.append(
                    "WebSearch: engine=bing modules=[${results.moduleSummary()}] results=${results.size} query='$query'",
                )
            }
        }
    }

    private suspend fun searchSogou(query: String, maxResults: Int): List<WebSearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.sogou.com/web?query=$encoded"
            val request = RuntimeNetworkRequest(
                capability = RuntimeNetworkCapability.WEB_SEARCH,
                method = "GET",
                url = url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                ),
                timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
            )
            val response = transport.execute(request)
            val body = response.bodyString
            if (body.isBlank()) throw IllegalStateException("Empty response from Sogou")
            parseSogouResults(
                html = body,
                maxResults = maxResults,
                searchUrl = url,
            ).also { results ->
                AppLogger.append(
                    "WebSearch: engine=sogou modules=[${results.moduleSummary()}] results=${results.size} query='$query'",
                )
            }
        }
    }

    private fun buildWebSearchBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.websearch"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.WEB_SEARCH,
                ownerId = ownerId,
                sourceRef = "web_search",
                displayName = "Web Search",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "web_search",
                description = "Search the web for information. Returns titles, URLs, and snippets of matching results.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.WEB_SEARCH,
                inputSchema = mapOf(
                    "type" to "object" as Any,
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Search query"),
                        "max_results" to mapOf("type" to "integer", "description" to "Max results to return (1-10, default 5)"),
                    ),
                    "required" to listOf("query"),
                ),
            ),
        )
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

internal data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val engine: String = "",
    val module: String = "",
    val diagnosticReason: String = "",
)

private data class SearchExecutionFallback(
    val results: List<WebSearchResult>,
    val engine: SearchEngine,
    val assessment: SearchRelevanceAssessment,
)

internal suspend fun executeWebSearchQuery(
    query: String,
    maxResults: Int,
    bingSearch: suspend (String, Int) -> List<WebSearchResult>,
    sogouSearch: suspend (String, Int) -> List<WebSearchResult>,
): String {
    return executeWebSearchQuery(
        query = query,
        maxResults = maxResults,
        policy = SearchPolicyResolver.resolve(SearchIntent.GENERAL, query),
        bingSearch = bingSearch,
        sogouSearch = sogouSearch,
    )
}

internal suspend fun executeWebSearchQuery(
    query: String,
    maxResults: Int,
    policy: SearchPolicy,
    bingSearch: suspend (String, Int) -> List<WebSearchResult>,
    sogouSearch: suspend (String, Int) -> List<WebSearchResult>,
): String {
    val attempts = mutableListOf<String>()
    var lowRelevanceFallback: SearchExecutionFallback? = null

    for ((index, engine) in policy.engineOrder.withIndex()) {
        val results = runCatching {
            when (engine) {
                SearchEngine.BING -> bingSearch(query, maxResults)
                SearchEngine.SOGOU -> sogouSearch(query, maxResults)
            }
        }.onFailure { error ->
            AppLogger.append(
                "WebSearch: intent=${policy.intent.name} engine=${engine.name.lowercase()} failed reason=${error.message ?: error.javaClass.simpleName} query='$query'",
            )
        }.getOrDefault(emptyList()).filterUsefulSearchResults(query, engine)
        attempts += "${engine.name.lowercase()}:${results.size}"

        if (results.isEmpty()) {
            continue
        }

        val assessment = SearchRelevanceScorer.assess(
            query = query,
            results = results,
            intent = policy.intent,
        )
        val isLastEngine = index == policy.engineOrder.lastIndex
        val module = assessment.bestModule ?: results.firstOrNull()?.module?.ifBlank { null } ?: "unknown"

        if (assessment.isRelevant || (isLastEngine && !policy.requiresRelevantFinalEngine)) {
            val payload = formatSearchResults(query, results.take(maxResults))
            AppLogger.append(
                "WebSearch: intent=${policy.intent.name} engine=${engine.name.lowercase()} module=$module modules=[${results.moduleSummary()}] " +
                    "relevance_reason=${assessment.reason} fallback_reason=accepted payload=${payload.toSingleLineLog()}",
            )
            AppLogger.flush()
            return payload
        }

        if (policy.allowLowRelevanceFallback) {
            lowRelevanceFallback = lowRelevanceFallback ?: SearchExecutionFallback(results = results, engine = engine, assessment = assessment)
        }

        val fallbackReason = when {
            isLastEngine && !policy.allowLowRelevanceFallback -> "policy_disallows_low_relevance_fallback"
            isLastEngine -> "last_engine_below_threshold"
            else -> "next_engine"
        }
        AppLogger.append(
            "WebSearch: intent=${policy.intent.name} engine=${engine.name.lowercase()} module=$module modules=[${results.moduleSummary()}] " +
                "relevance_reason=${assessment.reason} fallback_reason=$fallbackReason query='$query'",
        )
    }

    if (policy.allowLowRelevanceFallback) {
        lowRelevanceFallback?.let { fallback ->
            val payload = formatSearchResults(query, fallback.results.take(maxResults))
            AppLogger.append(
                "WebSearch: intent=${policy.intent.name} engine=${fallback.engine.name.lowercase()} module=${fallback.assessment.bestModule ?: "unknown"} " +
                    "modules=[${fallback.results.moduleSummary()}] relevance_reason=${fallback.assessment.reason} " +
                    "fallback_reason=low_relevance_last_resort payload=${payload.toSingleLineLog()}",
            )
            AppLogger.flush()
            return payload
        }
    }

    AppLogger.flush()
    throw IllegalStateException(
        "No search results found for query='$query' after ${attempts.joinToString()} intent=${policy.intent.name}.",
    )
}

internal fun parseBingResults(
    html: String,
    maxResults: Int,
): List<WebSearchResult> {
    val doc = Jsoup.parse(html)
    val results = mutableListOf<WebSearchResult>()

    // Priority 1: Rich results (weather card, answer card)
    extractBingWeatherCard(doc)?.let(results::add)
    if (results.size < maxResults) {
        extractBingAnswerCard(doc)?.let(results::add)
    }
    if (results.size < maxResults) {
        extractBingContextCard(doc)?.let(results::add)
    }

    // Priority 2: Regular b_algo results
    val items = doc.select("li.b_algo, div.b_algo")
    for (item in items) {
        if (results.size >= maxResults) break
        val headerLink = sequenceOf(
            item.selectFirst(".b_algoheader a[href]"),
            item.selectFirst("h2 a[href]"),
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
            results += WebSearchResult(
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

private fun extractBingWeatherCard(doc: Document): WebSearchResult? {
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

    return WebSearchResult(
        title = title,
        url = "https://www.bing.com/search",
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "bing",
        module = "bing_weather_card",
    )
}

private fun extractBingAnswerCard(doc: Document): WebSearchResult? {
    val answerContainers = doc.select("div.b_ans, li.b_ans")
    for (container in answerContainers) {
        // Skip weather cards (handled by extractBingWeatherCard)
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

        if (title.isNotBlank() && snippet.isNotBlank()) {
            return WebSearchResult(
                title = title,
                url = "https://www.bing.com/search",
                snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
                engine = "bing",
                module = "bing_answer_card",
            )
        }
    }
    return null
}

private fun extractBingContextCard(doc: Document): WebSearchResult? {
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

    return WebSearchResult(
        title = title,
        url = "https://www.bing.com/search",
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "bing",
        module = "bing_context_card",
    )
}

internal fun parseSogouResults(
    html: String,
    maxResults: Int,
    searchUrl: String,
): List<WebSearchResult> {
    val doc = Jsoup.parse(html)
    val results = mutableListOf<WebSearchResult>()

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
            results += WebSearchResult(
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
): WebSearchResult? {
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

    if (location.isBlank() && temperature.isBlank() && condition.isBlank()) {
        return null
    }

    val weatherSuffix = "\u5929\u6c14"
    val title = buildString {
        append(location.ifBlank { weatherSuffix })
        if (!endsWith(weatherSuffix)) {
            append(weatherSuffix)
        }
    }
    val snippet = listOf(temperature, condition)
        .filter(String::isNotBlank)
        .joinToString("\uff0c")
        .ifBlank { "\u5df2\u63d0\u53d6\u5230\u5929\u6c14\u5361\u7247\u3002" }

    return WebSearchResult(
        title = title,
        url = searchUrl,
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "sogou",
        module = "sogou_weather_card",
    )
}

private fun extractSogouSupListResults(html: String): List<WebSearchResult> {
    val arrayText = extractJsonArrayByKey(html, "supList") ?: return emptyList()
    val array = runCatching { JSONArray(arrayText) }.getOrNull() ?: return emptyList()
    val results = mutableListOf<WebSearchResult>()
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
            results += WebSearchResult(
                title = normalizedTitle,
                url = url,
                snippet = snippet,
                engine = "sogou",
                module = "sogou_sup_list",
            )
        }
    }
    return results
}

private fun extractSogouAnswerSummaryResult(
    html: String,
    searchUrl: String,
): WebSearchResult? {
    val escapedSummary = Regex("\"answer_summary\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)
        ?: return null
    val summary = decodeJsonString(escapedSummary).trim()
    if (summary.isBlank()) return null
    return WebSearchResult(
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
                    if (depth == 0) {
                        return html.substring(arrayStart, index + 1)
                    }
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

private fun formatSearchResults(
    query: String,
    results: List<WebSearchResult>,
): String {
    val arr = JSONArray()
    results.forEach { result ->
        arr.put(
            JSONObject().apply {
                put("engine", result.engine)
                put("title", result.title)
                put("url", result.url)
                put("snippet", result.snippet)
            },
        )
    }
    return JSONObject().apply {
        put("query", query)
        put("count", results.size)
        put("results", arr)
    }.toString(2)
}

private fun List<WebSearchResult>.filterUsefulSearchResults(
    query: String,
    engine: SearchEngine,
): List<WebSearchResult> {
    val filtered = filterNot { result -> result.looksLikeSearchPortalPlaceholder(query, engine) }
    if (filtered.size != size) {
        AppLogger.append(
            "WebSearch: filtered ${size - filtered.size} placeholder result(s) for engine=${engine.name.lowercase()} query='$query'",
        )
    }
    return filtered
}

private fun WebSearchResult.looksLikeSearchPortalPlaceholder(
    query: String,
    engine: SearchEngine,
): Boolean {
    val normalizedTitle = title.normalizeSearchText()
    val normalizedQuery = query.normalizeSearchText()
    val normalizedSnippet = snippet.normalizeSearchText()
    val normalizedUrl = url.lowercase()

    val titleEqualsQuery = normalizedTitle.isNotBlank() && normalizedTitle == normalizedQuery
    val genericSnippet = normalizedSnippet in setOf(
        "\u0051\u0051\u6d4f\u89c8\u5668\u641c\u7d22".normalizeSearchText(),
        "\u641c\u72d7\u641c\u7d22".normalizeSearchText(),
        "bing",
        "\u641c\u7d22".normalizeSearchText(),
    )
    val searchPortalUrl = when (engine) {
        SearchEngine.BING -> normalizedUrl.contains("bing.com/search?")
        SearchEngine.SOGOU -> normalizedUrl.contains("sogou.com/web?")
    }

    return searchPortalUrl && (titleEqualsQuery || genericSnippet)
}

private fun String.toSingleLineLog(maxLength: Int = 4000): String {
    val compact = replace("\r", " ")
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (compact.length <= maxLength) {
        compact
    } else {
        compact.take(maxLength) + "...(truncated)"
    }
}

internal fun assessBatchRelevance(
    query: String,
    results: List<WebSearchResult>,
): Boolean {
    return SearchRelevanceScorer.assess(query, results).isRelevant
}

private const val MAX_SEARCH_SNIPPET_LENGTH = 700

private fun List<WebSearchResult>.moduleSummary(): String {
    return if (isEmpty()) {
        "none"
    } else {
        groupingBy { it.module.ifBlank { "${it.engine}_unknown" } }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value}" }
    }
}

