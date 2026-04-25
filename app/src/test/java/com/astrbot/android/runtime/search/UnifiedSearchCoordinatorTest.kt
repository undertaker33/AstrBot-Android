package com.astrbot.android.runtime.search

import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkFailure
import com.astrbot.android.core.runtime.search.SearchAttemptStatus
import com.astrbot.android.core.runtime.search.SearchProvider
import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchCoordinator
import com.astrbot.android.core.runtime.search.UnifiedSearchException
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest
import com.astrbot.android.core.runtime.search.UnifiedSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSearchCoordinatorTest {
    @Test
    fun native_provider_success_calls_only_first_provider() = runBlocking {
        val called = mutableListOf<String>()
        val native = fakeProvider("native", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 99, title = "Native result")),
            )
        }
        val web = fakeProvider("web", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Web result")),
            )
        }

        val response = UnifiedSearchCoordinator(listOf(native, web)).search(
            UnifiedSearchRequest(query = "hilt android", maxResults = 3),
        )

        assertEquals(listOf("native"), called)
        assertFalse(response.fallbackUsed)
        assertEquals("native", response.provider)
        assertEquals(SearchAttemptStatus.SUCCESS, response.diagnostics.single().status)
        assertEquals(1, response.results.single().index)
        assertEquals("Native result", response.results.single().title)
    }

    @Test
    fun first_empty_results_calls_second_provider_as_fallback() = runBlocking {
        val called = mutableListOf<String>()
        val native = fakeProvider("native", called) {
            SearchProviderResult.Success(results = emptyList())
        }
        val web = fakeProvider("web", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 7, title = "Fallback result")),
            )
        }

        val response = UnifiedSearchCoordinator(listOf(native, web)).search(
            UnifiedSearchRequest(query = "kotlin coroutine", maxResults = 3),
        )

        assertEquals(listOf("native", "web"), called)
        assertTrue(response.fallbackUsed)
        assertEquals("web", response.provider)
        assertEquals(
            listOf(SearchAttemptStatus.EMPTY_RESULTS, SearchAttemptStatus.SUCCESS),
            response.diagnostics.map { it.status },
        )
        assertEquals(1, response.results.single().index)
        assertEquals("Fallback result", response.results.single().title)
    }

    @Test
    fun runtime_network_exception_records_diagnostic_and_falls_back() = runBlocking {
        val called = mutableListOf<String>()
        val native = fakeProvider("native", called) {
            throw RuntimeNetworkException(RuntimeNetworkFailure.ReadTimeout("https://native.test"))
        }
        val web = fakeProvider("web", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Web result")),
            )
        }

        val response = UnifiedSearchCoordinator(listOf(native, web)).search(
            UnifiedSearchRequest(query = "latest kotlin release", maxResults = 3),
        )

        assertEquals(listOf("native", "web"), called)
        assertTrue(response.fallbackUsed)
        assertEquals(SearchAttemptStatus.TIMEOUT, response.diagnostics.first().status)
        assertTrue(response.diagnostics.first().errorMessage.orEmpty().contains("Read timeout"))
        assertEquals(SearchAttemptStatus.SUCCESS, response.diagnostics.last().status)
    }

    @Test
    fun wrapped_runtime_network_cancellation_is_not_swallowed() = runBlocking {
        val called = mutableListOf<String>()
        val native = fakeProvider("native", called) {
            throw RuntimeNetworkException(RuntimeNetworkFailure.Cancelled("caller cancelled"))
        }
        val web = fakeProvider("web", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Web result")),
            )
        }

        val error = runCatching {
            UnifiedSearchCoordinator(listOf(native, web)).search(
                UnifiedSearchRequest(query = "cancel me", maxResults = 3),
            )
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(listOf("native"), called)
    }

    @Test
    fun low_relevance_success_triggers_fallback() = runBlocking {
        val called = mutableListOf<String>()
        val native = fakeProvider("native", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Off topic")),
                relevanceAccepted = false,
            )
        }
        val web = fakeProvider("web", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Relevant fallback")),
            )
        }

        val response = UnifiedSearchCoordinator(listOf(native, web)).search(
            UnifiedSearchRequest(query = "android workmanager hilt worker", maxResults = 3),
        )

        assertEquals(listOf("native", "web"), called)
        assertTrue(response.fallbackUsed)
        assertEquals(SearchAttemptStatus.LOW_RELEVANCE, response.diagnostics.first().status)
        assertEquals("Relevant fallback", response.results.single().title)
    }

    @Test
    fun url_less_official_result_is_accepted_without_fallback() = runBlocking {
        val called = mutableListOf<String>()
        val native = fakeProvider("native", called) {
            SearchProviderResult.Success(
                results = listOf(
                    result(index = 1, title = "Official answer", url = "", source = "Qwen official search"),
                ),
            )
        }
        val web = fakeProvider("web", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Cited fallback")),
            )
        }

        val response = UnifiedSearchCoordinator(listOf(native, web)).search(
            UnifiedSearchRequest(query = "needs source", maxResults = 3),
        )

        assertEquals(listOf("native"), called)
        assertFalse(response.fallbackUsed)
        assertEquals(SearchAttemptStatus.SUCCESS, response.diagnostics.first().status)
        assertEquals("success_without_url", response.diagnostics.first().reason)
        assertEquals("", response.results.single().url)
        assertEquals("Official answer", response.results.single().title)
    }

    @Test
    fun url_less_items_are_preserved_without_fabricating_url() = runBlocking {
        val called = mutableListOf<String>()
        val native = fakeProvider("native", called) {
            SearchProviderResult.Success(
                results = listOf(
                    result(index = 1, title = "No URL", url = ""),
                    result(index = 2, title = "Has URL", url = "https://example.test/cited"),
                ),
            )
        }

        val response = UnifiedSearchCoordinator(listOf(native)).search(
            UnifiedSearchRequest(query = "mixed source", maxResults = 3),
        )

        assertEquals(listOf("native"), called)
        assertFalse(response.fallbackUsed)
        assertEquals(2, response.results.size)
        assertEquals("No URL", response.results[0].title)
        assertEquals("", response.results[0].url)
        assertEquals("Has URL", response.results[1].title)
        assertEquals(2, response.results[1].index)
    }

    @Test
    fun provider_diagnostics_can_mark_internal_fallback_used() = runBlocking {
        val called = mutableListOf<String>()
        val provider = fakeProvider("html", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Sogou fallback")),
                diagnostics = listOf(
                    diagnostic("bing", SearchAttemptStatus.EMPTY_RESULTS),
                    diagnostic("sogou", SearchAttemptStatus.SUCCESS),
                ),
            )
        }

        val response = UnifiedSearchCoordinator(listOf(provider)).search(
            UnifiedSearchRequest(query = "fallback inside provider", maxResults = 3),
        )

        assertTrue(response.fallbackUsed)
        assertEquals(
            listOf("bing", "sogou", "html"),
            response.diagnostics.map { it.providerId },
        )
        assertEquals(SearchAttemptStatus.SUCCESS, response.diagnostics.last().status)
    }

    @Test
    fun success_can_override_response_provider_name() = runBlocking {
        val provider = fakeProvider("configured", mutableListOf()) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Native result")),
                providerOverride = "openai_native",
            )
        }

        val response = UnifiedSearchCoordinator(listOf(provider)).search(
            UnifiedSearchRequest(query = "native search", maxResults = 3),
        )

        assertEquals("openai_native", response.provider)
        assertEquals("configured", response.results.single().providerId)
    }

    @Test
    fun all_provider_failures_throw_exception_with_all_diagnostics() = runBlocking {
        val called = mutableListOf<String>()
        val unsupported = fakeProvider("unsupported", called, supports = false) {
            error("should not search")
        }
        val parser = fakeProvider("parser", called) {
            SearchProviderResult.Failure(
                status = SearchAttemptStatus.PARSE_ERROR,
                reason = "invalid response",
                errorCode = "bad_payload",
                errorMessage = "missing results",
            )
        }
        val empty = fakeProvider("empty", called) {
            SearchProviderResult.Success(results = emptyList())
        }

        val error = runCatching {
            UnifiedSearchCoordinator(listOf(unsupported, parser, empty)).search(
                UnifiedSearchRequest(query = "rare search", maxResults = 3),
            )
        }.exceptionOrNull()

        assertTrue(error is UnifiedSearchException)
        error as UnifiedSearchException
        assertEquals("rare search", error.query)
        assertEquals(listOf("unsupported", "parser", "empty"), error.diagnostics.map { it.providerId })
        assertEquals(
            listOf(
                SearchAttemptStatus.UNSUPPORTED,
                SearchAttemptStatus.PARSE_ERROR,
                SearchAttemptStatus.EMPTY_RESULTS,
            ),
            error.diagnostics.map { it.status },
        )
    }

    @Test
    fun cancellation_exception_is_not_swallowed() = runBlocking {
        val called = mutableListOf<String>()
        val native = fakeProvider("native", called) {
            throw CancellationException("cancelled by caller")
        }
        val web = fakeProvider("web", called) {
            SearchProviderResult.Success(
                results = listOf(result(index = 1, title = "Web result")),
            )
        }

        val error = runCatching {
            UnifiedSearchCoordinator(listOf(native, web)).search(
                UnifiedSearchRequest(query = "cancel me", maxResults = 3),
            )
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(listOf("native"), called)
    }

    @Test
    fun coordinator_trims_query_and_coerces_max_results_for_providers() = runBlocking {
        var seenRequest: UnifiedSearchRequest? = null
        val provider = fakeProvider("native", mutableListOf()) { request ->
            seenRequest = request
            SearchProviderResult.Success(
                results = listOf(
                    result(index = 1, title = "One"),
                    result(index = 2, title = "Two"),
                    result(index = 3, title = "Three"),
                ),
            )
        }

        val response = UnifiedSearchCoordinator(listOf(provider)).search(
            UnifiedSearchRequest(query = "  android  ", maxResults = 99),
        )

        assertEquals("android", seenRequest?.query)
        assertEquals(10, seenRequest?.maxResults)
        assertEquals(3, response.results.size)
    }

    @Test
    fun blank_query_is_rejected_before_providers_are_called() = runBlocking {
        val called = mutableListOf<String>()
        val provider = fakeProvider("native", called) {
            SearchProviderResult.Success(results = listOf(result(index = 1, title = "Native result")))
        }

        val error = runCatching {
            UnifiedSearchCoordinator(listOf(provider)).search(
                UnifiedSearchRequest(query = "   ", maxResults = 3),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(called.isEmpty())
    }

    private fun fakeProvider(
        id: String,
        called: MutableList<String>,
        supports: Boolean = true,
        block: suspend (UnifiedSearchRequest) -> SearchProviderResult,
    ): SearchProvider = object : SearchProvider {
        override val providerId: String = id
        override val providerName: String = id

        override suspend fun supports(request: UnifiedSearchRequest): Boolean = supports

        override suspend fun search(request: UnifiedSearchRequest): SearchProviderResult {
            called += id
            return block(request)
        }
    }

    private fun result(
        index: Int,
        title: String,
        url: String = "https://example.test/$index",
        source: String = "example",
    ) = UnifiedSearchResult(
        index = index,
        title = title,
        url = url,
        snippet = "snippet $index",
        source = source,
    )

    private fun diagnostic(
        providerId: String,
        status: SearchAttemptStatus,
    ) = com.astrbot.android.core.runtime.search.SearchAttemptDiagnostic(
        providerId = providerId,
        providerName = providerId,
        status = status,
        reason = status.name.lowercase(),
    )
}
