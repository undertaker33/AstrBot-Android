package com.astrbot.android.feature.provider.runtime.search

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.search.SearchAttemptDiagnostic
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.SearchProvider
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.profile.ConfiguredSearchProvider
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.CancellationException

class ProviderRepositorySearchProvider(
    private val providerRepository: ProviderRepositoryPort,
    private val nativeProviders: List<ConfiguredSearchProvider>,
    private val apiProviders: List<ConfiguredSearchProvider>,
) : SearchProvider {
    override val providerId: String = "configured_search_providers"
    override val providerName: String = "Configured search providers"

    override suspend fun search(request: UnifiedSearchRequest): SearchProviderResult {
        val candidates = searchCandidates()
        if (candidates.isEmpty()) {
            return SearchProviderResult.Unavailable(
                status = SearchAttemptStatus.UNSUPPORTED,
                reason = "no_configured_search_providers",
                diagnostics = listOf(
                    diagnostic(
                        providerId = providerId,
                        providerName = providerName,
                        status = SearchAttemptStatus.UNSUPPORTED,
                        reason = "no_configured_search_providers",
                    ),
                ),
            )
        }

        val diagnostics = mutableListOf<SearchAttemptDiagnostic>()
        for ((profile, provider) in candidates) {
            if (!provider.supports(profile)) {
                diagnostics += diagnostic(
                    providerId = provider.providerId,
                    providerName = provider.providerName,
                    status = SearchAttemptStatus.UNSUPPORTED,
                    reason = "unsupported_profile:${profile.providerType.name}",
                )
                continue
            }

            when (val result = trySearch(provider, profile, request)) {
                is SearchProviderResult.Success -> {
                    diagnostics += result.diagnostics
                    val acceptedResults = result.results.filter { searchResult ->
                        searchResult.url.isNotBlank() ||
                            searchResult.title.isNotBlank() ||
                            searchResult.snippet.isNotBlank() ||
                            searchResult.source.isNotBlank()
                    }
                    val status = when {
                        result.results.isEmpty() -> SearchAttemptStatus.EMPTY_RESULTS
                        acceptedResults.isEmpty() -> SearchAttemptStatus.NO_VERIFIABLE_SOURCE
                        !result.relevanceAccepted -> SearchAttemptStatus.LOW_RELEVANCE
                        else -> SearchAttemptStatus.SUCCESS
                    }
                    val reason = if (status == SearchAttemptStatus.SUCCESS && acceptedResults.all { it.url.isBlank() }) {
                        "success_without_url"
                    } else {
                        status.name.lowercase()
                    }
                    diagnostics += diagnostic(
                        providerId = provider.providerId,
                        providerName = provider.providerName,
                        status = status,
                        reason = reason,
                        resultCount = acceptedResults.size,
                        relevanceAccepted = result.relevanceAccepted,
                    )
                    AppLogger.append(
                        "WebSearch: configured_provider=${provider.providerId} " +
                            "profile=${profile.id} type=${profile.providerType.name} status=${status.name} " +
                            "results=${acceptedResults.size} diagnostics=[" +
                            diagnostics
                                .filter { diagnostic -> diagnostic.providerId == provider.providerId }
                                .joinToString(",") { diagnostic -> "${diagnostic.status.name}:${diagnostic.reason}" } +
                            "]",
                    )
                    AppLogger.flush()
                    if (status == SearchAttemptStatus.SUCCESS) {
                        return result.copy(
                            results = acceptedResults,
                            diagnostics = diagnostics.toList(),
                            providerOverride = result.providerOverride ?: provider.providerId,
                        )
                    }
                }
                is SearchProviderResult.Unavailable -> {
                    AppLogger.append(
                        "WebSearch: configured_provider=${provider.providerId} " +
                            "profile=${profile.id} type=${profile.providerType.name} status=${result.status.name} " +
                            "reason=${result.reason}",
                    )
                    AppLogger.flush()
                    diagnostics += diagnostic(
                        providerId = provider.providerId,
                        providerName = provider.providerName,
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
                    AppLogger.append(
                        "WebSearch: configured_provider=${provider.providerId} " +
                            "profile=${profile.id} type=${profile.providerType.name} status=${result.status.name} " +
                            "reason=${result.reason}",
                    )
                    AppLogger.flush()
                    diagnostics += diagnostic(
                        providerId = provider.providerId,
                        providerName = provider.providerName,
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

        return SearchProviderResult.Failure(
            status = SearchAttemptStatus.EMPTY_RESULTS,
            reason = "configured_search_providers_unavailable",
            diagnostics = diagnostics.toList(),
        )
    }

    private fun searchCandidates(): List<Pair<ProviderProfile, ConfiguredSearchProvider>> {
        val profiles = providerRepository.snapshotProfiles().filter(ProviderProfile::enabled)
        val nativeCandidates = nativeProviders.flatMap { provider ->
            profiles
                .filter { profile -> ProviderCapability.CHAT in profile.capabilities }
                .map { profile -> profile to provider }
        }
        val apiCandidates = apiProviders.flatMap { provider ->
            profiles
                .filter { profile -> ProviderCapability.SEARCH in profile.capabilities }
                .map { profile -> profile to provider }
        }
        return nativeCandidates + apiCandidates
    }

    private suspend fun trySearch(
        provider: ConfiguredSearchProvider,
        profile: ProviderProfile,
        request: UnifiedSearchRequest,
    ): SearchProviderResult {
        return try {
            provider.search(profile, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SearchProviderResult.Failure(
                status = SearchAttemptStatus.NETWORK_ERROR,
                reason = e.message ?: e::class.java.simpleName,
                errorCode = e::class.java.simpleName,
                errorMessage = e.message,
            )
        }
    }

    private fun diagnostic(
        providerId: String,
        providerName: String,
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
}
