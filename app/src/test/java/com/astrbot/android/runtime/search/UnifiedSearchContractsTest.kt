package com.astrbot.android.runtime.search

import com.astrbot.android.core.runtime.search.SearchAttemptDiagnostic
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.UnifiedSearchResponse
import com.astrbot.android.core.runtime.search.UnifiedSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSearchContractsTest {
    @Test
    fun result_index_can_start_from_one() {
        val result = UnifiedSearchResult(
            index = 1,
            title = "Official docs",
            url = "https://example.test/docs",
            snippet = "A verifiable result.",
            source = "example",
        )

        assertEquals(1, result.index)
        assertEquals("Official docs", result.title)
        assertEquals("example", result.source)
    }

    @Test
    fun response_expresses_direct_success_fallback_success_and_all_failed() {
        val direct = UnifiedSearchResponse(
            query = "android hilt",
            provider = "native",
            results = listOf(searchResult(index = 1, title = "Hilt")),
            diagnostics = listOf(diagnostic("native", SearchAttemptStatus.SUCCESS)),
            fallbackUsed = false,
        )
        val fallback = UnifiedSearchResponse(
            query = "android hilt",
            provider = "web",
            results = listOf(searchResult(index = 1, title = "Fallback Hilt")),
            diagnostics = listOf(
                diagnostic("native", SearchAttemptStatus.EMPTY_RESULTS),
                diagnostic("web", SearchAttemptStatus.SUCCESS),
            ),
            fallbackUsed = true,
        )
        val failed = UnifiedSearchResponse(
            query = "android hilt",
            provider = "",
            results = emptyList(),
            diagnostics = listOf(
                diagnostic("native", SearchAttemptStatus.UNSUPPORTED),
                diagnostic("web", SearchAttemptStatus.NETWORK_ERROR),
            ),
            fallbackUsed = true,
        )

        assertTrue(direct.success)
        assertEquals("native", direct.provider)
        assertFalse(direct.fallbackUsed)
        assertFalse(direct.allFailed)

        assertTrue(fallback.success)
        assertEquals("web", fallback.provider)
        assertTrue(fallback.fallbackUsed)
        assertFalse(fallback.allFailed)

        assertFalse(failed.success)
        assertTrue(failed.fallbackUsed)
        assertTrue(failed.allFailed)
    }

    @Test
    fun diagnostic_keeps_fields_without_exception_object() {
        val diagnostic = SearchAttemptDiagnostic(
            providerId = "web",
            providerName = "Web Search",
            status = SearchAttemptStatus.HTTP_ERROR,
            reason = "HTTP 429",
            errorCode = "429",
            errorMessage = "rate limited",
            traceId = "trace-1",
            durationMs = 42,
            resultCount = 0,
            relevanceAccepted = false,
        )

        assertEquals("web", diagnostic.providerId)
        assertEquals(SearchAttemptStatus.HTTP_ERROR, diagnostic.status)
        assertEquals("429", diagnostic.errorCode)
        assertEquals("rate limited", diagnostic.errorMessage)
        assertEquals("trace-1", diagnostic.traceId)
        assertEquals(42L, diagnostic.durationMs)
        assertEquals(0, diagnostic.resultCount)
        assertFalse(diagnostic.relevanceAccepted)
    }

    @Test
    fun attempt_status_covers_expected_fallback_reasons() {
        val statuses = SearchAttemptStatus.entries.toSet()

        assertTrue(statuses.contains(SearchAttemptStatus.UNSUPPORTED))
        assertTrue(statuses.contains(SearchAttemptStatus.TIMEOUT))
        assertTrue(statuses.contains(SearchAttemptStatus.NETWORK_ERROR))
        assertTrue(statuses.contains(SearchAttemptStatus.HTTP_ERROR))
        assertTrue(statuses.contains(SearchAttemptStatus.EMPTY_RESULTS))
        assertTrue(statuses.contains(SearchAttemptStatus.NO_VERIFIABLE_SOURCE))
        assertTrue(statuses.contains(SearchAttemptStatus.LOW_RELEVANCE))
        assertTrue(statuses.contains(SearchAttemptStatus.PARSE_ERROR))
    }

    private fun searchResult(index: Int, title: String) = UnifiedSearchResult(
        index = index,
        title = title,
        url = "https://example.test/$index",
        snippet = "snippet $index",
        source = "example",
    )

    private fun diagnostic(
        providerId: String,
        status: SearchAttemptStatus,
    ) = SearchAttemptDiagnostic(
        providerId = providerId,
        providerName = providerId,
        status = status,
        reason = status.name.lowercase(),
    )
}
