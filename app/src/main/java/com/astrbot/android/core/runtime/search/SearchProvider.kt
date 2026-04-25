package com.astrbot.android.core.runtime.search

interface SearchProvider {
    val providerId: String

    val providerName: String
        get() = providerId

    suspend fun supports(request: UnifiedSearchRequest): Boolean = true

    suspend fun search(request: UnifiedSearchRequest): SearchProviderResult
}

sealed interface SearchProviderResult {
    data class Success(
        val results: List<UnifiedSearchResult>,
        val diagnostics: List<SearchAttemptDiagnostic> = emptyList(),
        val relevanceAccepted: Boolean = true,
        val providerOverride: String? = null,
    ) : SearchProviderResult

    data class Unavailable(
        val reason: String,
        val status: SearchAttemptStatus = SearchAttemptStatus.UNSUPPORTED,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val traceId: String? = null,
        val durationMs: Long? = null,
        val diagnostics: List<SearchAttemptDiagnostic> = emptyList(),
    ) : SearchProviderResult

    data class Failure(
        val status: SearchAttemptStatus,
        val reason: String,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val traceId: String? = null,
        val durationMs: Long? = null,
        val diagnostics: List<SearchAttemptDiagnostic> = emptyList(),
    ) : SearchProviderResult
}
