package com.astrbot.android.core.runtime.search.html

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class SogouHtmlSearchProvider(
    private val transport: RuntimeNetworkTransport,
) : HtmlFallbackSearchProvider.EngineProvider {
    override val engine: SearchEngine = SearchEngine.SOGOU

    override suspend fun fetch(query: String, maxResults: Int): List<HtmlSearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.sogou.com/web?query=$encoded"
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

    private companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
