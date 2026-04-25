package com.astrbot.android.core.runtime.search.html

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class BingHtmlSearchProvider(
    private val transport: RuntimeNetworkTransport,
) : HtmlFallbackSearchProvider.EngineProvider {
    override val engine: SearchEngine = SearchEngine.BING

    override suspend fun fetch(query: String, maxResults: Int): List<HtmlSearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.bing.com/search?q=$encoded&count=$maxResults"
            val response = transport.execute(
                RuntimeNetworkRequest(
                    capability = RuntimeNetworkCapability.WEB_SEARCH,
                    method = "GET",
                    url = url,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                    ),
                    timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
                ),
            )
            val body = response.bodyString
            if (body.isBlank()) throw IllegalStateException("Empty response from Bing")
            parseBingResults(body, maxResults).also { results ->
                AppLogger.append(
                    "WebSearch: engine=bing modules=[${results.moduleSummary()}] results=${results.size} query='$query'",
                )
            }
        }
    }

    private companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

internal fun List<HtmlSearchResult>.moduleSummary(): String {
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
