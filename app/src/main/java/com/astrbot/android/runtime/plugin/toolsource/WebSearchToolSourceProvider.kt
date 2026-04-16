package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolResult
import com.astrbot.android.runtime.plugin.PluginToolResultStatus
import com.astrbot.android.runtime.plugin.PluginToolSourceKind
import com.astrbot.android.runtime.plugin.PluginToolVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Web search tool source provider.
 *
 * When `webSearchEnabled` is set on the config profile, this provider exports
 * a `web_search` tool that the LLM can call to search the web.
 *
 * Uses Bing → Sogo fallback scraping, aligned with AstrBot-master's approach.
 */
class WebSearchToolSourceProvider : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.WEB_SEARCH

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        if (!configProfile.webSearchEnabled) return emptyList()

        return listOf(buildWebSearchBinding())
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        return if (configProfile.webSearchEnabled) {
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
            RuntimeLogRepository.append("WebSearch invoke error: ${e.message}")
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

    // ── Search implementation ───────────────────────────────────────────

    private suspend fun handleWebSearch(payload: Map<String, Any?>): String {
        val query = (payload["query"] as? String)?.trim()
            ?: throw IllegalArgumentException("'query' parameter is required.")
        val maxResults = ((payload["max_results"] as? Number)?.toInt() ?: 5).coerceIn(1, 10)

        RuntimeLogRepository.append("WebSearch: searching '$query' (max $maxResults)")

        // Try Bing first, fallback to Sogo
        val results = try {
            searchBing(query, maxResults)
        } catch (e: Exception) {
            RuntimeLogRepository.append("WebSearch: Bing failed (${e.message}), falling back to Sogo")
            try {
                searchSogo(query, maxResults)
            } catch (e2: Exception) {
                RuntimeLogRepository.append("WebSearch: Sogo also failed: ${e2.message}")
                throw IllegalStateException("Both Bing and Sogo search failed: ${e.message} / ${e2.message}")
            }
        }

        val arr = JSONArray()
        for (result in results) {
            arr.put(JSONObject().apply {
                put("title", result.title)
                put("url", result.url)
                put("snippet", result.snippet)
            })
        }
        return JSONObject().apply {
            put("query", query)
            put("count", results.size)
            put("results", arr)
        }.toString(2)
    }

    private suspend fun searchBing(query: String, maxResults: Int): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.bing.com/search?q=$encoded&count=$maxResults"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw IllegalStateException("Empty response from Bing")

            val doc = Jsoup.parse(body)
            val results = mutableListOf<SearchResult>()
            val items = doc.select("li.b_algo")
            for (item in items) {
                if (results.size >= maxResults) break
                val titleEl = item.selectFirst("h2 a") ?: continue
                val title = titleEl.text()
                val href = titleEl.attr("href")
                val snippetEl = item.selectFirst(".b_caption p") ?: item.selectFirst("p")
                val snippet = snippetEl?.text()?.take(MAX_SNIPPET_LENGTH) ?: ""
                if (href.isNotBlank()) {
                    results.add(SearchResult(title = title, url = href, snippet = snippet))
                }
            }
            results
        }
    }

    private suspend fun searchSogo(query: String, maxResults: Int): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.sogou.com/web?query=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw IllegalStateException("Empty response from Sogo")

            val doc = Jsoup.parse(body)
            val results = mutableListOf<SearchResult>()
            val items = doc.select("div.vrwrap, div.rb")
            for (item in items) {
                if (results.size >= maxResults) break
                val titleEl = item.selectFirst("h3 a") ?: continue
                val title = titleEl.text()
                val href = titleEl.attr("href")
                val snippetEl = item.selectFirst("p.str_info, div.str-text-info, p")
                val snippet = snippetEl?.text()?.take(MAX_SNIPPET_LENGTH) ?: ""
                if (href.isNotBlank()) {
                    results.add(SearchResult(title = title, url = href, snippet = snippet))
                }
            }
            results
        }
    }

    // ── Descriptor ──────────────────────────────────────────────────────

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

    private data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String,
    )

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val MAX_SNIPPET_LENGTH = 700
    }
}
