package com.astrbot.android.core.runtime.search.html

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkFailure
import com.astrbot.android.core.runtime.search.SearchAttemptDiagnostic
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.SearchProvider
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import kotlinx.coroutines.CancellationException

class HtmlFallbackSearchProvider(
    private val bingProvider: EngineProvider,
    private val sogouProvider: EngineProvider,
) : SearchProvider {
    constructor(transport: com.astrbot.android.core.runtime.network.RuntimeNetworkTransport) : this(
        bingProvider = BingHtmlSearchProvider(transport),
        sogouProvider = SogouHtmlSearchProvider(transport),
    )

    override val providerId: String = "html_fallback"
    override val providerName: String = "HTML fallback web search"

    override suspend fun search(request: UnifiedSearchRequest): SearchProviderResult.Success {
        val query = request.query.trim()
        val maxResults = request.maxResults.coerceIn(1, 10)
        val intent = SearchIntentClassifier.classify(query)
        val policy = SearchPolicyResolver.resolve(intent, query)
        val diagnostics = mutableListOf<SearchAttemptDiagnostic>()
        var lowRelevanceFallback: HtmlSearchExecutionFallback? = null

        AppLogger.append(
            "WebSearch: intent=${intent.name} engine_order=[${policy.engineOrder.joinToString(",") { it.name.lowercase() }}] " +
                "allow_low_relevance_fallback=${policy.allowLowRelevanceFallback} query='$query' max_results=$maxResults",
        )

        for ((index, engine) in policy.engineOrder.withIndex()) {
            val provider = engine.provider()
            val rawResults = try {
                provider.fetch(query, maxResults)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.append(
                    "WebSearch: intent=${policy.intent.name} engine=${engine.name.lowercase()} failed " +
                        "reason=${e.message ?: e.javaClass.simpleName} query='$query'",
                )
                val diagnostic = if (e is RuntimeNetworkException) {
                    engine.runtimeNetworkDiagnostic(e)
                } else {
                    engine.diagnostic(
                        status = SearchAttemptStatus.NETWORK_ERROR,
                        reason = e.message ?: e.javaClass.simpleName,
                        errorCode = e.javaClass.simpleName,
                        errorMessage = e.message,
                    )
                }
                diagnostics += diagnostic
                continue
            }
            val results = rawResults.filterUsefulSearchResults(query, engine)
            if (results.isEmpty()) {
                diagnostics += engine.diagnostic(
                    status = SearchAttemptStatus.EMPTY_RESULTS,
                    reason = "empty_results",
                )
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
                val acceptedResults = results.take(maxResults).mapIndexed { resultIndex, result ->
                    result.toUnifiedResult(resultIndex + 1)
                }
                diagnostics += engine.diagnostic(
                    status = SearchAttemptStatus.SUCCESS,
                    reason = assessment.reason,
                    resultCount = acceptedResults.size,
                    relevanceAccepted = true,
                )
                AppLogger.append(
                    "WebSearch: intent=${policy.intent.name} engine=${engine.name.lowercase()} module=$module " +
                        "modules=[${results.moduleSummary()}] relevance_reason=${assessment.reason} fallback_reason=accepted",
                )
                AppLogger.flush()
                return SearchProviderResult.Success(
                    results = acceptedResults,
                    diagnostics = diagnostics,
                    relevanceAccepted = true,
                )
            }

            if (isLastEngine && policy.acceptOrganicLastResort && assessment.reason != "target_date_mismatch") {
                val acceptedResults = results.take(maxResults).mapIndexed { resultIndex, result ->
                    result.toUnifiedResult(resultIndex + 1)
                }
                diagnostics += engine.diagnostic(
                    status = SearchAttemptStatus.SUCCESS,
                    reason = "organic_results_last_resort",
                    resultCount = acceptedResults.size,
                    relevanceAccepted = true,
                )
                AppLogger.append(
                    "WebSearch: intent=${policy.intent.name} engine=${engine.name.lowercase()} module=$module " +
                        "modules=[${results.moduleSummary()}] relevance_reason=${assessment.reason} " +
                        "fallback_reason=organic_results_last_resort query='$query'",
                )
                AppLogger.flush()
                return SearchProviderResult.Success(
                    results = acceptedResults,
                    diagnostics = diagnostics,
                    relevanceAccepted = true,
                )
            }

            diagnostics += engine.diagnostic(
                status = SearchAttemptStatus.LOW_RELEVANCE,
                reason = assessment.reason,
                resultCount = results.size,
                relevanceAccepted = false,
            )

            if (policy.allowLowRelevanceFallback) {
                lowRelevanceFallback = lowRelevanceFallback ?: HtmlSearchExecutionFallback(results, engine, assessment)
            }

            val fallbackReason = when {
                isLastEngine && !policy.allowLowRelevanceFallback -> "policy_disallows_low_relevance_fallback"
                isLastEngine -> "last_engine_below_threshold"
                else -> "next_engine"
            }
            AppLogger.append(
                "WebSearch: intent=${policy.intent.name} engine=${engine.name.lowercase()} module=$module " +
                    "modules=[${results.moduleSummary()}] relevance_reason=${assessment.reason} " +
                    "fallback_reason=$fallbackReason query='$query'",
            )
        }

        if (policy.allowLowRelevanceFallback) {
            lowRelevanceFallback?.let { fallback ->
                val acceptedResults = fallback.results.take(maxResults).mapIndexed { resultIndex, result ->
                    result.toUnifiedResult(resultIndex + 1)
                }
                diagnostics += fallback.engine.diagnostic(
                    status = SearchAttemptStatus.SUCCESS,
                    reason = "low_relevance_last_resort",
                    resultCount = acceptedResults.size,
                    relevanceAccepted = true,
                )
                AppLogger.append(
                    "WebSearch: intent=${policy.intent.name} engine=${fallback.engine.name.lowercase()} " +
                        "module=${fallback.assessment.bestModule ?: "unknown"} modules=[${fallback.results.moduleSummary()}] " +
                        "relevance_reason=${fallback.assessment.reason} fallback_reason=low_relevance_last_resort",
                )
                AppLogger.flush()
                return SearchProviderResult.Success(
                    results = acceptedResults,
                    diagnostics = diagnostics,
                    relevanceAccepted = true,
                )
            }
        }

        AppLogger.flush()
        return SearchProviderResult.Success(
            results = emptyList(),
            diagnostics = diagnostics,
            relevanceAccepted = false,
        )
    }

    private fun SearchEngine.provider(): EngineProvider {
        return when (this) {
            SearchEngine.BING -> bingProvider
            SearchEngine.SOGOU -> sogouProvider
        }
    }

    private fun SearchEngine.diagnostic(
        status: SearchAttemptStatus,
        reason: String,
        errorCode: String? = null,
        errorMessage: String? = null,
        resultCount: Int = 0,
        relevanceAccepted: Boolean = false,
    ): SearchAttemptDiagnostic {
        val id = name.lowercase()
        return SearchAttemptDiagnostic(
            providerId = id,
            providerName = "$id html",
            status = status,
            reason = reason,
            errorCode = errorCode,
            errorMessage = errorMessage,
            resultCount = resultCount,
            relevanceAccepted = relevanceAccepted,
        )
    }

    private fun SearchEngine.runtimeNetworkDiagnostic(error: RuntimeNetworkException): SearchAttemptDiagnostic {
        val failure = error.failure
        return diagnostic(
            status = failure.searchAttemptStatus(),
            reason = failure.summary,
            errorCode = failure.searchErrorCode(),
            errorMessage = error.message,
        )
    }

    private fun RuntimeNetworkFailure.searchAttemptStatus(): SearchAttemptStatus {
        return when (this) {
            is RuntimeNetworkFailure.ConnectTimeout,
            is RuntimeNetworkFailure.ReadTimeout,
            -> SearchAttemptStatus.TIMEOUT
            is RuntimeNetworkFailure.Http -> SearchAttemptStatus.HTTP_ERROR
            is RuntimeNetworkFailure.Protocol -> SearchAttemptStatus.PARSE_ERROR
            is RuntimeNetworkFailure.Cancelled,
            is RuntimeNetworkFailure.Dns,
            is RuntimeNetworkFailure.Tls,
            is RuntimeNetworkFailure.Unknown,
            -> SearchAttemptStatus.NETWORK_ERROR
        }
    }

    private fun RuntimeNetworkFailure.searchErrorCode(): String {
        return when (this) {
            is RuntimeNetworkFailure.Http -> statusCode.toString()
            else -> this::class.java.simpleName
        }
    }

    private fun List<HtmlSearchResult>.filterUsefulSearchResults(
        query: String,
        engine: SearchEngine,
    ): List<HtmlSearchResult> {
        val filtered = filterNot { result -> result.looksLikeSearchPortalPlaceholder(query, engine) }
        if (filtered.size != size) {
            AppLogger.append(
                "WebSearch: filtered ${size - filtered.size} placeholder result(s) " +
                    "for engine=${engine.name.lowercase()} query='$query'",
            )
        }
        return filtered
    }

    private fun HtmlSearchResult.looksLikeSearchPortalPlaceholder(
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

    interface EngineProvider {
        val engine: SearchEngine

        suspend fun fetch(query: String, maxResults: Int): List<HtmlSearchResult>
    }
}

private data class HtmlSearchExecutionFallback(
    val results: List<HtmlSearchResult>,
    val engine: SearchEngine,
    val assessment: SearchRelevanceAssessment,
)
