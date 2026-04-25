package com.astrbot.android.core.runtime.search

import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkFailure
import kotlinx.coroutines.CancellationException

class UnifiedSearchCoordinator(
    private val providers: List<SearchProvider>,
) : UnifiedSearchPort {
    override suspend fun search(request: UnifiedSearchRequest): UnifiedSearchResponse {
        val normalizedRequest = request.normalized()
        val diagnostics = mutableListOf<SearchAttemptDiagnostic>()

        for (provider in providers) {
            val supported = try {
                provider.supports(normalizedRequest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.rethrowIfWrappedCancellation()
                diagnostics += provider.exceptionDiagnostic(e)
                continue
            }

            if (!supported) {
                diagnostics += provider.attemptDiagnostic(
                    status = SearchAttemptStatus.UNSUPPORTED,
                    reason = "unsupported",
                )
                continue
            }

            val result = try {
                provider.search(normalizedRequest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.rethrowIfWrappedCancellation()
                diagnostics += provider.exceptionDiagnostic(e)
                continue
            }

            when (result) {
                is SearchProviderResult.Success -> {
                    val priorAttemptCount = diagnostics.size
                    val providerDiagnostics = result.diagnostics
                    val rawResults = result.results.take(normalizedRequest.maxResults)
                    val results = rawResults
                        .filter { searchResult -> searchResult.hasUsableSearchContent() }
                    val attemptStatus = when {
                        rawResults.isEmpty() -> SearchAttemptStatus.EMPTY_RESULTS
                        results.isEmpty() -> SearchAttemptStatus.NO_VERIFIABLE_SOURCE
                        !result.relevanceAccepted -> SearchAttemptStatus.LOW_RELEVANCE
                        else -> SearchAttemptStatus.SUCCESS
                    }
                    val reason = if (attemptStatus == SearchAttemptStatus.SUCCESS && results.all { it.url.isBlank() }) {
                        "success_without_url"
                    } else {
                        attemptStatus.reason
                    }
                    diagnostics += providerDiagnostics
                    diagnostics += provider.attemptDiagnostic(
                        status = attemptStatus,
                        reason = reason,
                        resultCount = results.size,
                        relevanceAccepted = result.relevanceAccepted,
                    )

                    if (attemptStatus == SearchAttemptStatus.SUCCESS) {
                        return UnifiedSearchResponse(
                            query = normalizedRequest.query,
                            provider = result.providerOverride?.takeIf(String::isNotBlank) ?: provider.providerId,
                            results = results.normalizeIndexes(provider.providerId),
                            diagnostics = diagnostics,
                            fallbackUsed = priorAttemptCount > 0 || providerDiagnostics.any { diagnostic ->
                                diagnostic.status != SearchAttemptStatus.SUCCESS
                            },
                        )
                    }
                }
                is SearchProviderResult.Unavailable -> {
                    diagnostics += provider.attemptDiagnostic(
                        status = result.status,
                        reason = result.reason,
                        errorCode = result.errorCode,
                        errorMessage = result.errorMessage,
                        traceId = result.traceId,
                        durationMs = result.durationMs,
                    )
                    diagnostics += result.diagnostics
                }
                is SearchProviderResult.Failure -> {
                    diagnostics += provider.attemptDiagnostic(
                        status = result.status,
                        reason = result.reason,
                        errorCode = result.errorCode,
                        errorMessage = result.errorMessage,
                        traceId = result.traceId,
                        durationMs = result.durationMs,
                    )
                    diagnostics += result.diagnostics
                }
            }
        }

        throw UnifiedSearchException(
            query = normalizedRequest.query,
            diagnostics = diagnostics,
        )
    }

    private fun UnifiedSearchRequest.normalized(): UnifiedSearchRequest {
        val trimmedQuery = query.trim()
        require(trimmedQuery.isNotBlank()) { "Unified search query must not be blank." }
        return copy(
            query = trimmedQuery,
            maxResults = maxResults.coerceIn(1, 10),
        )
    }

    private fun List<UnifiedSearchResult>.normalizeIndexes(providerId: String): List<UnifiedSearchResult> {
        return mapIndexed { index, result ->
            result.copy(
                index = index + 1,
                providerId = result.providerId.ifBlank { providerId },
            )
        }
    }

    private fun SearchProvider.exceptionDiagnostic(error: Exception): SearchAttemptDiagnostic {
        val status = error.searchStatus()
        return attemptDiagnostic(
            status = status,
            reason = error.message ?: error::class.java.simpleName,
            errorCode = error.errorCode(),
            errorMessage = error.message,
            traceId = error.traceId(),
        )
    }

    private fun SearchProvider.attemptDiagnostic(
        status: SearchAttemptStatus,
        reason: String,
        errorCode: String? = null,
        errorMessage: String? = null,
        traceId: String? = null,
        durationMs: Long? = null,
        resultCount: Int = 0,
        relevanceAccepted: Boolean = false,
    ): SearchAttemptDiagnostic {
        return SearchAttemptDiagnostic(
            providerId = providerId,
            providerName = providerName,
            status = status,
            reason = reason,
            errorCode = errorCode,
            errorMessage = errorMessage,
            traceId = traceId,
            durationMs = durationMs,
            resultCount = resultCount,
            relevanceAccepted = relevanceAccepted,
        )
    }

    private fun Exception.searchStatus(): SearchAttemptStatus {
        return when (this) {
            is RuntimeNetworkException -> failure.searchStatus()
            else -> SearchAttemptStatus.NETWORK_ERROR
        }
    }

    private fun RuntimeNetworkFailure.searchStatus(): SearchAttemptStatus {
        return when (this) {
            is RuntimeNetworkFailure.ConnectTimeout,
            is RuntimeNetworkFailure.ReadTimeout,
            -> SearchAttemptStatus.TIMEOUT
            is RuntimeNetworkFailure.Http -> SearchAttemptStatus.HTTP_ERROR
            is RuntimeNetworkFailure.Protocol -> SearchAttemptStatus.PARSE_ERROR
            is RuntimeNetworkFailure.Dns,
            is RuntimeNetworkFailure.Tls,
            is RuntimeNetworkFailure.Cancelled,
            is RuntimeNetworkFailure.Unknown,
            -> SearchAttemptStatus.NETWORK_ERROR
        }
    }

    private fun Exception.rethrowIfWrappedCancellation() {
        if (this is RuntimeNetworkException && failure is RuntimeNetworkFailure.Cancelled) {
            throw CancellationException(failure.summary).also { cancellation ->
                cancellation.initCause(this)
            }
        }
    }

    private fun Exception.errorCode(): String? {
        return when (this) {
            is RuntimeNetworkException -> when (val runtimeFailure = failure) {
                is RuntimeNetworkFailure.Http -> runtimeFailure.statusCode.toString()
                else -> runtimeFailure::class.java.simpleName
            }
            else -> this::class.java.simpleName
        }
    }

    private fun Exception.traceId(): String? = null
}

private val SearchAttemptStatus.reason: String
    get() = name.lowercase()

private fun UnifiedSearchResult.hasUsableSearchContent(): Boolean {
    return url.isNotBlank() || title.isNotBlank() || snippet.isNotBlank() || source.isNotBlank()
}
