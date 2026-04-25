package com.astrbot.android.core.runtime.search.profile

import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkFailure
import com.astrbot.android.core.runtime.search.SearchAttemptDiagnostic
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchResult
import com.astrbot.android.model.ProviderProfile
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal fun ProviderProfile.normalizedBaseUrl(defaultBaseUrl: String): String {
    return baseUrl.trim().ifBlank { defaultBaseUrl }.trimEnd('/')
}

internal fun String.appendPath(path: String): String {
    val normalizedPath = path.trim()
    if (normalizedPath.isBlank()) return this
    return trimEnd('/') + "/" + normalizedPath.trimStart('/')
}

internal fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")

internal fun JSONObject.optArray(path: String): JSONArray? {
    return optPath(path) as? JSONArray
}

internal fun JSONObject.optObject(path: String): JSONObject? {
    return optPath(path) as? JSONObject
}

private fun JSONObject.optPath(path: String): Any? {
    var current: Any? = this
    for (segment in path.split('.')) {
        val node = current
        current = when (node) {
            is JSONObject -> node.opt(segment)
            is JSONArray -> segment.toIntOrNull()?.let { index -> node.opt(index) }
            else -> null
        }
    }
    return current
}

internal fun JSONArray.objects(): Sequence<JSONObject> = sequence {
    for (index in 0 until length()) {
        (opt(index) as? JSONObject)?.let { yield(it) }
    }
}

internal fun JSONArray.strings(): Sequence<String> = sequence {
    for (index in 0 until length()) {
        optString(index).trim().takeIf(String::isNotBlank)?.let { yield(it) }
    }
}

internal fun unifiedResult(
    index: Int,
    title: String,
    url: String,
    snippet: String,
    source: String = "",
    providerId: String,
    publishedAt: String? = null,
    metadata: Map<String, String> = emptyMap(),
): UnifiedSearchResult? {
    val normalizedUrl = url.trim()
    if (normalizedUrl.isBlank()) return null
    val normalizedTitle = title.trim().ifBlank { normalizedUrl }
    return UnifiedSearchResult(
        index = index,
        title = normalizedTitle.take(MAX_SEARCH_TITLE_LENGTH),
        url = normalizedUrl,
        snippet = snippet.trim().take(MAX_SEARCH_SNIPPET_LENGTH),
        source = source.trim(),
        providerId = providerId,
        publishedAt = publishedAt?.trim()?.takeIf(String::isNotBlank),
        metadata = metadata.filterValues(String::isNotBlank),
    )
}

internal fun List<UnifiedSearchResult>.renumbered(): List<UnifiedSearchResult> {
    return mapIndexed { index, result -> result.copy(index = index + 1) }
}

internal fun emptySearchSuccess(
    providerId: String,
    providerName: String,
    reason: String = "empty_results",
): SearchProviderResult.Success {
    return SearchProviderResult.Success(
        results = emptyList(),
        diagnostics = listOf(
            SearchAttemptDiagnostic(
                providerId = providerId,
                providerName = providerName,
                status = SearchAttemptStatus.EMPTY_RESULTS,
                reason = reason,
            ),
        ),
        relevanceAccepted = false,
        providerOverride = providerId,
    )
}

internal fun unsupportedProvider(
    providerId: String,
    providerName: String,
    reason: String,
): SearchProviderResult.Unavailable {
    return SearchProviderResult.Unavailable(
        status = SearchAttemptStatus.UNSUPPORTED,
        reason = reason,
        diagnostics = listOf(
            SearchAttemptDiagnostic(
                providerId = providerId,
                providerName = providerName,
                status = SearchAttemptStatus.UNSUPPORTED,
                reason = reason,
            ),
        ),
    )
}

internal fun networkFailureResult(
    @Suppress("UNUSED_PARAMETER") providerId: String,
    @Suppress("UNUSED_PARAMETER") providerName: String,
    error: RuntimeNetworkException,
): SearchProviderResult.Failure {
    val failure = error.failure
    return SearchProviderResult.Failure(
        status = failure.searchAttemptStatus(),
        reason = failure.summary,
        errorCode = failure.searchErrorCode(),
        errorMessage = error.message,
    )
}

internal fun parseFailureResult(
    @Suppress("UNUSED_PARAMETER") providerId: String,
    @Suppress("UNUSED_PARAMETER") providerName: String,
    error: Exception,
): SearchProviderResult.Failure {
    return SearchProviderResult.Failure(
        status = SearchAttemptStatus.PARSE_ERROR,
        reason = error.message ?: error::class.java.simpleName,
        errorCode = error::class.java.simpleName,
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

internal const val MAX_SEARCH_SNIPPET_LENGTH = 700
private const val MAX_SEARCH_TITLE_LENGTH = 300
