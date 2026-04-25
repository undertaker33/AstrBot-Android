package com.astrbot.android.core.runtime.search

interface UnifiedSearchPort {
    suspend fun search(request: UnifiedSearchRequest): UnifiedSearchResponse
}

data class UnifiedSearchRequest(
    val query: String,
    val maxResults: Int = 5,
    val locale: String? = null,
    val freshOnly: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
)

data class UnifiedSearchResponse(
    val query: String,
    val provider: String,
    val results: List<UnifiedSearchResult>,
    val diagnostics: List<SearchAttemptDiagnostic>,
    val fallbackUsed: Boolean = false,
) {
    val success: Boolean get() = results.isNotEmpty()

    val allFailed: Boolean
        get() = results.isEmpty() &&
            diagnostics.isNotEmpty() &&
            diagnostics.none { it.status == SearchAttemptStatus.SUCCESS }
}

data class UnifiedSearchResult(
    val index: Int,
    val title: String,
    val url: String,
    val snippet: String = "",
    val source: String = "",
    val providerId: String = "",
    val publishedAt: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(index >= 1) { "UnifiedSearchResult.index must be one-based." }
    }
}

data class SearchAttemptDiagnostic(
    val providerId: String,
    val providerName: String,
    val status: SearchAttemptStatus,
    val reason: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val traceId: String? = null,
    val durationMs: Long? = null,
    val resultCount: Int = 0,
    val relevanceAccepted: Boolean = false,
)

enum class SearchAttemptStatus {
    SUCCESS,
    UNSUPPORTED,
    TIMEOUT,
    NETWORK_ERROR,
    HTTP_ERROR,
    EMPTY_RESULTS,
    NO_VERIFIABLE_SOURCE,
    LOW_RELEVANCE,
    PARSE_ERROR,
}

class UnifiedSearchException(
    val query: String,
    val diagnostics: List<SearchAttemptDiagnostic>,
) : IllegalStateException(
    "Unified search failed for query='$query' after ${diagnostics.size} attempt(s).",
)
