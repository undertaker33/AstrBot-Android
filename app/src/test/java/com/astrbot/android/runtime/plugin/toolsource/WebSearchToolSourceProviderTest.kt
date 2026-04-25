package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.core.runtime.search.SearchAttemptDiagnostic
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.UnifiedSearchException
import com.astrbot.android.core.runtime.search.UnifiedSearchPort
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.UnifiedSearchResponse
import com.astrbot.android.core.runtime.search.UnifiedSearchResult
import com.astrbot.android.feature.plugin.runtime.PluginToolArgs
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolSourceProviderTest {
    @Test
    fun web_search_success_returns_unified_json_shape() = runBlocking {
        var capturedRequest: UnifiedSearchRequest? = null
        val provider = WebSearchToolSourceProvider(
            searchPort = fakeSearchPort {
                capturedRequest = it
                UnifiedSearchResponse(
                    query = it.query,
                    provider = "openai_native",
                    fallbackUsed = false,
                    diagnostics = listOf(diagnostic("openai_native", SearchAttemptStatus.SUCCESS)),
                    results = listOf(
                        UnifiedSearchResult(
                            index = 1,
                            title = "Official result",
                            url = "https://example.test/result",
                            snippet = "Verified snippet",
                            source = "example",
                        ),
                    ),
                )
            },
            contextResolver = resolver(webSearchEnabled = true),
        )

        val result = provider.invoke(invokeRequest(query = "latest android hilt")).result
        val json = JSONObject(result.text.orEmpty())
        val first = json.getJSONArray("results").getJSONObject(0)

        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        assertEquals("latest android hilt", json.getString("query"))
        assertEquals("openai_native", json.getString("provider"))
        assertEquals(false, json.getBoolean("fallbackUsed"))
        assertEquals("Official result", first.getString("title"))
        assertEquals("https://example.test/result", first.getString("url"))
        assertEquals("Verified snippet", first.getString("snippet"))
        assertEquals("example", first.getString("source"))
        assertEquals(1, first.getInt("index"))
        assertTrue(capturedRequest?.metadata?.get("currentDate").orEmpty().matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun web_search_fallback_success_marks_fallback_used() = runBlocking {
        val provider = WebSearchToolSourceProvider(
            searchPort = fakeSearchPort {
                UnifiedSearchResponse(
                    query = it.query,
                    provider = "html_fallback",
                    fallbackUsed = true,
                    diagnostics = listOf(
                        diagnostic("native", SearchAttemptStatus.EMPTY_RESULTS),
                        diagnostic("html_fallback", SearchAttemptStatus.SUCCESS),
                    ),
                    results = listOf(
                        UnifiedSearchResult(
                            index = 1,
                            title = "Fallback result",
                            url = "https://fallback.test/result",
                            snippet = "Fallback snippet",
                            source = "fallback",
                        ),
                    ),
                )
            },
            contextResolver = resolver(webSearchEnabled = true),
        )

        val result = provider.invoke(invokeRequest(query = "needs fallback")).result
        val json = JSONObject(result.text.orEmpty())

        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        assertEquals("html_fallback", json.getString("provider"))
        assertTrue(json.getBoolean("fallbackUsed"))
    }

    @Test
    fun web_search_failure_returns_error_json_with_diagnostics() = runBlocking {
        val provider = WebSearchToolSourceProvider(
            searchPort = fakeSearchPort {
                throw UnifiedSearchException(
                    query = it.query,
                    diagnostics = listOf(
                        diagnostic("native", SearchAttemptStatus.UNSUPPORTED),
                        diagnostic("html_fallback", SearchAttemptStatus.NETWORK_ERROR, "dns_failed"),
                    ),
                )
            },
            contextResolver = resolver(webSearchEnabled = true),
        )

        val result = provider.invoke(invokeRequest(query = "no network")).result
        val json = JSONObject(result.text.orEmpty())

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("web_search_error", result.errorCode)
        assertEquals("no network", json.getString("query"))
        assertTrue(json.getBoolean("fallbackUsed"))
        assertEquals(2, json.getJSONArray("diagnostics").length())
        assertEquals("NETWORK_ERROR", json.getJSONArray("diagnostics").getJSONObject(1).getString("status"))
    }

    @Test
    fun web_search_disabled_does_not_expose_descriptor() = runBlocking {
        val provider = WebSearchToolSourceProvider(
            searchPort = fakeSearchPort {
                error("unused")
            },
            contextResolver = resolver(webSearchEnabled = false),
        )

        val bindings = provider.listBindings(
            ToolSourceRegistryIngestContext(toolSourceContext = toolSourceContext(webSearchEnabled = false)),
        )

        assertTrue(bindings.isEmpty())
    }

    @Test
    fun web_search_disabled_rejects_direct_invoke() = runBlocking {
        var called = false
        val provider = WebSearchToolSourceProvider(
            searchPort = fakeSearchPort {
                called = true
                error("unused")
            },
            contextResolver = resolver(webSearchEnabled = false),
        )

        val result = provider.invoke(
            invokeRequest(
                query = "should not run",
                webSearchEnabled = false,
            ),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("web_search_disabled", result.errorCode)
        assertTrue(!called)
    }

    private fun fakeSearchPort(
        block: suspend (UnifiedSearchRequest) -> UnifiedSearchResponse,
    ): UnifiedSearchPort = object : UnifiedSearchPort {
        override suspend fun search(request: UnifiedSearchRequest): UnifiedSearchResponse {
            return block(request)
        }
    }

    private fun invokeRequest(
        query: String,
        webSearchEnabled: Boolean = true,
    ): ToolSourceInvokeRequest {
        return ToolSourceInvokeRequest(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.WEB_SEARCH,
                ownerId = "cap.websearch",
                sourceRef = "web_search",
                displayName = "Web Search",
            ),
            args = PluginToolArgs(
                toolCallId = "call-1",
                requestId = "request-1",
                toolId = "cap.websearch:web_search",
                payload = mapOf("query" to query, "max_results" to 5),
            ),
            timeoutMs = 10_000,
            toolSourceContext = toolSourceContext(webSearchEnabled = webSearchEnabled),
        )
    }

    private fun resolver(webSearchEnabled: Boolean): FutureToolSourceContextResolver {
        return object : FutureToolSourceContextResolver {
            override fun resolveForConfig(configProfileId: String): ToolSourceContext {
                return toolSourceContext(webSearchEnabled = webSearchEnabled)
            }
        }
    }

    private fun toolSourceContext(webSearchEnabled: Boolean): ToolSourceContext {
        return ToolSourceContext(
            requestId = "request-1",
            platform = RuntimePlatform.APP_CHAT,
            configProfileId = "config-1",
            webSearchEnabled = webSearchEnabled,
            activeCapabilityEnabled = false,
            mcpServers = emptyList(),
            promptSkills = emptyList(),
            toolSkills = emptyList(),
            conversationId = "conversation-1",
        )
    }

    private fun diagnostic(
        providerId: String,
        status: SearchAttemptStatus,
        reason: String = status.name.lowercase(),
    ): SearchAttemptDiagnostic {
        return SearchAttemptDiagnostic(
            providerId = providerId,
            providerName = providerId,
            status = status,
            reason = reason,
        )
    }
}
