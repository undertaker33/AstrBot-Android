package com.astrbot.android.core.runtime.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeNetworkModelsTest {

    @Test
    fun timeout_profiles_have_expected_values() {
        val ws = RuntimeTimeoutProfile.WEB_SEARCH
        assertEquals(10_000L, ws.connectMs)
        assertEquals(15_000L, ws.readMs)
        assertEquals(10_000L, ws.writeMs)

        val mcpRpc = RuntimeTimeoutProfile.MCP_RPC
        assertEquals(10_000L, mcpRpc.connectMs)
        assertEquals(60_000L, mcpRpc.readMs)
        assertEquals(30_000L, mcpRpc.writeMs)
    }

    @Test
    fun network_failure_dns_includes_host() {
        val failure = RuntimeNetworkFailure.Dns(
            host = "example.com",
            causeMessage = "No address associated with hostname",
        )
        assertTrue(failure.summary.contains("example.com"))
        assertTrue(failure.summary.contains("DNS"))
    }

    @Test
    fun network_failure_http_includes_status_and_url() {
        val failure = RuntimeNetworkFailure.Http(
            statusCode = 429,
            url = "https://api.example.com/v1/chat",
            bodyPreview = "rate limit exceeded",
        )
        assertTrue(failure.summary.contains("429"))
        assertTrue(failure.summary.contains("api.example.com"))
    }

    @Test
    fun network_request_equality_considers_body() {
        val r1 = RuntimeNetworkRequest(
            capability = RuntimeNetworkCapability.WEB_SEARCH,
            method = "GET",
            url = "https://test.com",
            timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
            body = "hello".toByteArray(),
        )
        val r2 = r1.copy(body = "hello".toByteArray())
        assertEquals(r1, r2)
    }

    @Test
    fun network_response_body_string_decodes_utf8() {
        val resp = RuntimeNetworkResponse(
            statusCode = 200,
            headers = emptyMap(),
            bodyBytes = "你好世界".toByteArray(),
            traceId = "test-trace",
            durationMs = 100,
        )
        assertEquals("你好世界", resp.bodyString)
        assertTrue(resp.isSuccessful)
    }

    @Test
    fun sanitize_url_redacts_query_parameters() {
        assertEquals(
            "https://bing.com/search?…",
            OkHttpRuntimeNetworkTransport.sanitizeUrl("https://bing.com/search?q=hello&count=5"),
        )
        assertEquals(
            "https://bing.com/search",
            OkHttpRuntimeNetworkTransport.sanitizeUrl("https://bing.com/search"),
        )
    }

    @Test
    fun shared_transport_singleton_returns_instance() {
        assertNotNull(SharedRuntimeNetworkTransport.get())
    }
}
