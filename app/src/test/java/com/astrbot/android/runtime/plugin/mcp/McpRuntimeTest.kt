package com.astrbot.android.feature.plugin.runtime.mcp

import com.astrbot.android.model.McpServerEntry
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class McpRuntimeTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamable_http_initializes_once_and_reuses_session_header_for_list_and_call() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "sess-1")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"result\":{\"tools\":[{\"name\":\"weather\",\"description\":\"Weather\",\"inputSchema\":{\"type\":\"object\"}}]}}"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"4\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"structuredContent\":{\"ok\":true}}}"),
        )

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "weather",
            name = "Weather",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        val tools = manager.discoverTools("config-a", entry)
        val call = manager.callTool("config-a", entry, "weather", mapOf("q" to "beijing"))

        assertEquals(1, tools.size)
        assertEquals("weather", tools.single().name)
        assertEquals("ok", call.text)

        val initRequest = server.takeRequest()
        assertTrue(initRequest.body.readUtf8().contains("\"method\":\"initialize\""))
        val initializedRequest = server.takeRequest()
        assertTrue(initializedRequest.body.readUtf8().contains("notifications/initialized"))
        val listRequest = server.takeRequest()
        assertEquals("sess-1", listRequest.getHeader("Mcp-Session-Id"))
        assertTrue(listRequest.body.readUtf8().contains("\"method\":\"tools/list\""))
        val callRequest = server.takeRequest()
        assertEquals("sess-1", callRequest.getHeader("Mcp-Session-Id"))
        assertTrue(callRequest.body.readUtf8().contains("\"method\":\"tools/call\""))
    }

    @Test
    fun streamable_http_sends_mcp_protocol_version_header() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val entry = McpServerEntry(
            serverId = "proto",
            name = "Proto",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        client.initialize(entry)

        val initRequest = server.takeRequest()
        assertEquals("2024-11-05", initRequest.getHeader("MCP-Protocol-Version"))
    }

    @Test
    fun streamable_http_405_is_reported_as_error_without_legacy_fallback() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(405).setBody("Method Not Allowed"))

        val client = McpTransportClient()
        val entry = McpServerEntry(
            serverId = "reject",
            name = "Reject",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        try {
            client.initialize(entry)
            fail("Expected HTTP 405 to fail")
        } catch (e: McpHttpException) {
            assertEquals(405, e.code)
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun streamable_http_rejects_legacy_sse_transport_without_fallback() = kotlinx.coroutines.runBlocking {
        val client = McpTransportClient()
        val entry = McpServerEntry(
            serverId = "legacy",
            name = "Legacy",
            url = server.url("/sse").toString(),
            transport = "sse",
        )

        try {
            client.initialize(entry)
            fail("Expected unsupported transport to be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("streamable_http"))
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun streamable_http_rebuilds_session_on_404() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "old-sess")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(404).setBody("Session not found"))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "new-sess")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"4\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"6\",\"result\":{\"tools\":[{\"name\":\"rebuilt\",\"description\":\"Rebuilt\",\"inputSchema\":{\"type\":\"object\"}}]}}"),
        )

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "rebuild",
            name = "Rebuild",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        val tools = manager.discoverTools("config-a", entry)
        assertEquals(1, tools.size)
        assertEquals("rebuilt", tools.single().name)
    }

    @Test
    fun streamable_http_delete_session_on_close() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "del-sess")
                .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Error"))
        server.enqueue(MockResponse().setResponseCode(202))

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "deltest",
            name = "DelTest",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        try {
            manager.discoverTools("config-a", entry)
            fail("Should throw on 500")
        } catch (e: McpHttpException) {
            assertEquals(500, e.code)
        }

        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        val deleteRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Expected DELETE cleanup request", deleteRequest)
        assertEquals("DELETE", deleteRequest!!.method)
        assertEquals("del-sess", deleteRequest.getHeader("Mcp-Session-Id"))
    }

    @Test
    fun session_cache_is_scoped_by_config_profile_id() = kotlinx.coroutines.runBlocking {
        repeat(2) { idx ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Mcp-Session-Id", "sess-$idx")
                    .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"${idx * 3 + 1}\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
            )
            server.enqueue(MockResponse().setResponseCode(202))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"${idx * 3 + 3}\",\"result\":{\"tools\":[]}}"),
            )
        }

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "shared",
            name = "Shared",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        manager.discoverTools("config-a", entry)
        manager.discoverTools("config-b", entry)

        assertEquals(6, server.requestCount)
    }

    @Test
    fun profile_conflict_same_server_id_different_profiles_get_separate_sessions() = kotlinx.coroutines.runBlocking {
        repeat(2) { idx ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Mcp-Session-Id", "sess-profile-$idx")
                    .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"${idx * 3 + 1}\",\"result\":{\"capabilities\":{\"tools\":{}}}}"),
            )
            server.enqueue(MockResponse().setResponseCode(202))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"jsonrpc\":\"2.0\",\"id\":\"${idx * 3 + 3}\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"result-from-profile-$idx\"}]}}"),
            )
        }

        var idCounter = 0
        val client = McpTransportClient(requestIdGenerator = { (++idCounter).toString() })
        val manager = McpSessionManager(client = client, ttlMs = 60_000L)
        val entry = McpServerEntry(
            serverId = "shared-server",
            name = "Shared",
            url = server.url("/mcp").toString(),
            transport = "streamable_http",
        )

        val resultA = manager.callTool("profile-a", entry, "test", emptyMap())
        val resultB = manager.callTool("profile-b", entry, "test", emptyMap())

        assertEquals(6, server.requestCount)
        assertEquals("result-from-profile-0", resultA.text)
        assertEquals("result-from-profile-1", resultB.text)

        server.takeRequest()
        server.takeRequest()
        val callA = server.takeRequest()
        assertEquals("sess-profile-0", callA.getHeader("Mcp-Session-Id"))
        server.takeRequest()
        server.takeRequest()
        val callB = server.takeRequest()
        assertEquals("sess-profile-1", callB.getHeader("Mcp-Session-Id"))
        assertNotEquals(callA.getHeader("Mcp-Session-Id"), callB.getHeader("Mcp-Session-Id"))
    }
}
