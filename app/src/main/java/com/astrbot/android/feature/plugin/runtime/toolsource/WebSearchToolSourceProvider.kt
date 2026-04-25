package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.search.SearchAttemptDiagnostic
import com.astrbot.android.core.runtime.search.UnifiedSearchException
import com.astrbot.android.core.runtime.search.UnifiedSearchPort
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.UnifiedSearchResponse
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility
import javax.inject.Inject
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class WebSearchToolSourceProvider @Inject constructor(
    private val searchPort: UnifiedSearchPort,
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
        if (request.toolSourceContext?.webSearchEnabled == false) {
            return errorResult(
                request = request,
                errorCode = "web_search_disabled",
                payload = errorPayload(
                    query = "",
                    message = "Web search is disabled in this config profile.",
                    diagnostics = emptyList(),
                ),
            )
        }
        val toolName = request.args.toolId.substringAfter(":")
        return if (toolName == "web_search") {
            invokeWebSearch(request)
        } else {
            errorResult(
                request = request,
                errorCode = "web_search_error",
                payload = errorPayload(
                    query = "",
                    message = "Unknown web search tool: $toolName",
                    diagnostics = emptyList(),
                ),
            )
        }
    }

    private suspend fun invokeWebSearch(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        val payload = request.args.payload
        val query = (payload["query"] as? String)?.trim().orEmpty()
        val maxResults = ((payload["max_results"] as? Number)?.toInt() ?: 5).coerceIn(1, 10)
        if (query.isBlank()) {
            return errorResult(
                request = request,
                errorCode = "web_search_invalid_request",
                payload = errorPayload(
                    query = query,
                    message = "'query' parameter is required.",
                    diagnostics = emptyList(),
                ),
            )
        }

        return try {
            val response = searchPort.search(
                UnifiedSearchRequest(
                    query = query,
                    maxResults = maxResults,
                    metadata = mapOf(
                        "currentDate" to LocalDate.now(ZoneId.systemDefault()).toString(),
                    ),
                ),
            )
            val responseText = formatSearchResponse(response)
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = responseText,
                    structuredContent = response.toStructuredMap(),
                ),
            )
        } catch (e: UnifiedSearchException) {
            AppLogger.append("WebSearch invoke error: ${e.message}")
            errorResult(
                request = request,
                errorCode = "web_search_error",
                payload = errorPayload(
                    query = e.query,
                    message = e.message.orEmpty(),
                    diagnostics = e.diagnostics,
                ),
            )
        } catch (e: Exception) {
            AppLogger.append("WebSearch invoke error: ${e.message}")
            errorResult(
                request = request,
                errorCode = "web_search_error",
                payload = errorPayload(
                    query = query,
                    message = e.message ?: e::class.java.simpleName,
                    diagnostics = emptyList(),
                ),
            )
        }
    }

    private fun errorResult(
        request: ToolSourceInvokeRequest,
        errorCode: String,
        payload: Map<String, Any?>,
    ): ToolSourceInvokeResult {
        return ToolSourceInvokeResult(
            result = PluginToolResult(
                toolCallId = request.args.toolCallId,
                requestId = request.args.requestId,
                toolId = request.args.toolId,
                status = PluginToolResultStatus.ERROR,
                errorCode = errorCode,
                text = JSONObject(payload).toString(2),
                structuredContent = payload,
            ),
        )
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
}

private fun formatSearchResponse(response: UnifiedSearchResponse): String {
    return JSONObject(response.toStructuredMap()).toString(2)
}

private fun UnifiedSearchResponse.toStructuredMap(): Map<String, Any?> {
    return mapOf(
        "query" to query,
        "provider" to provider,
        "fallbackUsed" to fallbackUsed,
        "results" to results.map { result ->
            mapOf(
                "title" to result.title,
                "url" to result.url,
                "snippet" to result.snippet,
                "source" to result.source,
                "index" to result.index,
            )
        },
        "diagnostics" to diagnostics.map(SearchAttemptDiagnostic::toStructuredMap),
    )
}

private fun errorPayload(
    query: String,
    message: String,
    diagnostics: List<SearchAttemptDiagnostic>,
): Map<String, Any?> {
    return mapOf(
        "query" to query,
        "provider" to "",
        "fallbackUsed" to diagnostics.any { diagnostic -> diagnostic.status.name != "SUCCESS" },
        "results" to emptyList<Map<String, Any?>>(),
        "error" to message,
        "diagnostics" to diagnostics.map(SearchAttemptDiagnostic::toStructuredMap),
    )
}

private fun SearchAttemptDiagnostic.toStructuredMap(): Map<String, Any?> {
    return mapOf(
        "providerId" to providerId,
        "providerName" to providerName,
        "status" to status.name,
        "reason" to reason,
        "errorCode" to errorCode,
        "errorMessage" to errorMessage,
        "traceId" to traceId,
        "durationMs" to durationMs,
        "resultCount" to resultCount,
        "relevanceAccepted" to relevanceAccepted,
    ).filterValues { value -> value != null }
}
