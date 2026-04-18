package com.astrbot.android.runtime.context

import com.astrbot.android.data.http.HttpMethod
import com.astrbot.android.data.http.HttpRequestSpec
import com.astrbot.android.data.http.MultipartPartSpec
import com.astrbot.android.data.http.OkHttpAstrBotHttpClient
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.runtime.network.RuntimeNetworkFailure
import com.astrbot.android.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.runtime.network.RuntimeNetworkResponse
import com.astrbot.android.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.runtime.network.RuntimeTimeoutProfile
import com.astrbot.android.runtime.network.SharedRuntimeNetworkTransport
import com.astrbot.android.runtime.network.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1-2 architectural verification tests.
 *
 * These tests verify structural properties required by the Task8 design:
 * - Phase 1: All entries use unified runtime, context window from config only
 * - Phase 2: Unified transport contract, HTTP failure model, streaming/SSE interfaces
 */
class Task8Phase1And2VerificationTest {

    @Test
    fun context_window_derived_from_config_only() {
        val method = RuntimeContextResolver::class.java.getDeclaredMethod(
            "resolveContextWindow",
            ConfigProfile::class.java,
        )

        assertNotNull("resolveContextWindow(ConfigProfile) must exist", method)
        assertEquals(
            "resolveContextWindow must take exactly 1 parameter (ConfigProfile)",
            1,
            method.parameterTypes.size,
        )
    }

    @Test
    fun resolved_runtime_context_carries_all_phase1_fields() {
        val fields = ResolvedRuntimeContext::class.java.declaredFields.map { it.name }.toSet()
        val required = listOf(
            "requestId",
            "ingressEvent",
            "bot",
            "config",
            "persona",
            "provider",
            "availableProviders",
            "conversationId",
            "messageWindow",
            "contextPolicy",
            "personaToolSnapshot",
            "providerCapabilities",
            "webSearchEnabled",
            "proactiveEnabled",
            "mcpServers",
            "skills",
            "deliveryPolicy",
            "realWorldTimeAwarenessEnabled",
        )

        for (field in required) {
            assertTrue(
                "ResolvedRuntimeContext must have field '$field'",
                fields.contains(field),
            )
        }
    }

    @Test
    fun runtime_ingress_event_has_all_trigger_types() {
        val triggers = IngressTrigger.entries.map { it.name }

        assertTrue("Must have USER_MESSAGE", "USER_MESSAGE" in triggers)
        assertTrue("Must have COMMAND", "COMMAND" in triggers)
        assertTrue("Must have SCHEDULED_TASK", "SCHEDULED_TASK" in triggers)
        assertTrue("Must have PLUGIN_EVENT", "PLUGIN_EVENT" in triggers)
    }

    @Test
    fun runtime_platform_enum_covers_app_and_qq() {
        val platforms = RuntimePlatform.entries.map { it.name }

        assertTrue("Must have APP_CHAT", "APP_CHAT" in platforms)
        assertTrue("Must have QQ_ONEBOT", "QQ_ONEBOT" in platforms)
    }

    @Test
    fun chat_view_model_has_no_track_b_legacy_path() {
        val vmClass = Class.forName("com.astrbot.android.ui.viewmodel.ChatViewModel")
        val methods = vmClass.declaredMethods.map { it.name }

        assertTrue(
            "deliverViaRuntimePort must NOT exist (send path moved to feature/chat use case)",
            !methods.contains("deliverViaRuntimePort"),
        )
        assertTrue(
            "deliverAppChatLlmPipeline must NOT exist (moved to AppChatRuntimeService)",
            !methods.contains("deliverAppChatLlmPipeline"),
        )
        assertTrue(
            "deliverAppChatLlmPipelineIfSupported must NOT exist (Track B removed)",
            !methods.contains("deliverAppChatLlmPipelineIfSupported"),
        )
    }

    @Test
    fun chat_view_model_has_no_build_system_prompt() {
        val vmClass = Class.forName("com.astrbot.android.ui.viewmodel.ChatViewModel")
        val methods = vmClass.declaredMethods.map { it.name }

        assertTrue(
            "buildSystemPrompt must NOT exist (Track B removed)",
            !methods.contains("buildSystemPrompt"),
        )
    }

    @Test
    fun transport_interface_has_execute_openStream_openSse() {
        val methods = RuntimeNetworkTransport::class.java.methods.map { it.name }.toSet()

        assertTrue("Must have execute()", "execute" in methods)
        assertTrue("Must have openStream()", "openStream" in methods)
        assertTrue("Must have openSse()", "openSse" in methods)
    }

    @Test
    fun openStream_returns_flow_of_string() {
        val method = RuntimeNetworkTransport::class.java.getMethod(
            "openStream",
            RuntimeNetworkRequest::class.java,
        )

        assertTrue(
            "openStream must return Flow",
            Flow::class.java.isAssignableFrom(method.returnType),
        )
    }

    @Test
    fun openSse_returns_flow_of_sse_event() {
        val method = RuntimeNetworkTransport::class.java.getMethod(
            "openSse",
            RuntimeNetworkRequest::class.java,
        )

        assertTrue(
            "openSse must return Flow",
            Flow::class.java.isAssignableFrom(method.returnType),
        )
    }

    @Test
    fun http_failure_model_covers_all_failure_types() {
        val failureClasses = listOf(
            "Dns", "ConnectTimeout", "ReadTimeout", "Tls", "Http", "Protocol", "Cancelled", "Unknown",
        )

        for (type in failureClasses) {
            val cls = runCatching {
                Class.forName("com.astrbot.android.runtime.network.RuntimeNetworkFailure\$$type")
            }.getOrNull()

            assertNotNull(
                "RuntimeNetworkFailure.$type must exist as nested class",
                cls,
            )
        }
    }

    @Test
    fun timeout_profiles_cover_all_runtime_capabilities() {
        val profiles = RuntimeTimeoutProfile.entries.map { it.name }

        assertTrue("PROVIDER_STREAMING", "PROVIDER_STREAMING" in profiles)
        assertTrue("PROVIDER_NON_STREAMING", "PROVIDER_NON_STREAMING" in profiles)
        assertTrue("WEB_SEARCH", "WEB_SEARCH" in profiles)
        assertTrue("MCP_RPC", "MCP_RPC" in profiles)
        assertTrue("MCP_SSE_DISCOVERY", "MCP_SSE_DISCOVERY" in profiles)
        assertTrue("MCP_SSE_PERSISTENT", "MCP_SSE_PERSISTENT" in profiles)
        assertTrue("ACTIVE_CAPABILITY_CALLBACK", "ACTIVE_CAPABILITY_CALLBACK" in profiles)
    }

    @Test
    fun provider_http_client_delegates_requests_to_runtime_transport() {
        val transport = RecordingRuntimeTransport(
            executeResponse = RuntimeNetworkResponse(
                statusCode = 200,
                headers = mapOf("X-Transport" to listOf("runtime")),
                bodyBytes = """{"ok":true}""".toByteArray(),
                traceId = "trace-provider",
                durationMs = 1,
            ),
            streamLines = flowOf("line-1", "line-2"),
        )
        SharedRuntimeNetworkTransport.setOverrideForTests(transport)
        try {
            val providerClient = OkHttpAstrBotHttpClient()

            val executeResponse = providerClient.execute(
                HttpRequestSpec(
                    method = HttpMethod.POST,
                    url = "http://127.0.0.1:1/provider",
                    headers = mapOf("Authorization" to "Bearer token"),
                    body = """{"hello":"world"}""",
                    contentType = "application/json",
                ),
            )

            val bytes = providerClient.executeBytes(
                HttpRequestSpec(
                    method = HttpMethod.GET,
                    url = "http://127.0.0.1:1/provider-bytes",
                ),
            )

            val streamed = mutableListOf<String>()
            runBlocking {
                providerClient.executeStream(
                    HttpRequestSpec(
                        method = HttpMethod.POST,
                        url = "http://127.0.0.1:1/provider-stream",
                        body = """{"stream":true}""",
                        contentType = "application/json",
                    ),
                ) { line -> streamed += line }
            }

            val multipartResponse = providerClient.executeMultipart(
                HttpRequestSpec(
                    method = HttpMethod.POST,
                    url = "http://127.0.0.1:1/provider-upload",
                    headers = mapOf("Authorization" to "Bearer token"),
                ),
                listOf(
                    MultipartPartSpec.Text(name = "purpose", value = "vision"),
                    MultipartPartSpec.File(
                        name = "file",
                        fileName = "photo.jpg",
                        contentType = "image/jpeg",
                        bytes = byteArrayOf(1, 2, 3),
                    ),
                ),
            )

            assertEquals(200, executeResponse.code)
            assertEquals("""{"ok":true}""", executeResponse.body)
            assertEquals("""{"ok":true}""", bytes.decodeToString())
            assertEquals(listOf("line-1", "line-2"), streamed)
            assertEquals(200, multipartResponse.code)
            assertEquals(3, transport.executeRequests.size)
            assertEquals(1, transport.streamRequests.size)

            val executeRequest = transport.executeRequests[0]
            assertEquals(RuntimeNetworkCapability.PROVIDER, executeRequest.capability)
            assertEquals("POST", executeRequest.method)
            assertEquals("http://127.0.0.1:1/provider", executeRequest.url)
            assertEquals("application/json", executeRequest.contentType)
            assertEquals("""{"hello":"world"}""", executeRequest.body!!.decodeToString())

            val bytesRequest = transport.executeRequests[1]
            assertEquals(RuntimeNetworkCapability.PROVIDER, bytesRequest.capability)
            assertEquals("GET", bytesRequest.method)
            assertEquals("http://127.0.0.1:1/provider-bytes", bytesRequest.url)

            val multipartRequest = transport.executeRequests[2]
            assertEquals(RuntimeNetworkCapability.PROVIDER, multipartRequest.capability)
            assertEquals("POST", multipartRequest.method)
            assertEquals("http://127.0.0.1:1/provider-upload", multipartRequest.url)
            assertTrue(multipartRequest.contentType!!.startsWith("multipart/form-data; boundary="))
            val bodyText = multipartRequest.body!!.decodeToString()
            assertTrue(bodyText.contains("name=\"purpose\""))
            assertTrue(bodyText.contains("vision"))
            assertTrue(bodyText.contains("name=\"file\"; filename=\"photo.jpg\""))
        } finally {
            SharedRuntimeNetworkTransport.setOverrideForTests(null)
        }
    }

    @Test
    fun network_capability_enum_covers_all_runtime_capabilities() {
        val capabilities = RuntimeNetworkCapability.entries.map { it.name }

        assertTrue("PROVIDER", "PROVIDER" in capabilities)
        assertTrue("WEB_SEARCH", "WEB_SEARCH" in capabilities)
        assertTrue("MCP_RPC", "MCP_RPC" in capabilities)
        assertTrue("MCP_SSE", "MCP_SSE" in capabilities)
        assertTrue("ACTIVE_CAPABILITY", "ACTIVE_CAPABILITY" in capabilities)
    }

    @Test
    fun sse_event_data_class_has_required_fields() {
        val event = SseEvent(event = "message", data = "test", id = "1")

        assertEquals("message", event.event)
        assertEquals("test", event.data)
        assertEquals("1", event.id)
    }

    private class RecordingRuntimeTransport : RuntimeNetworkTransport {
        private val executeResponse: RuntimeNetworkResponse
        private val streamLines: Flow<String>
        val executeRequests = mutableListOf<RuntimeNetworkRequest>()
        val streamRequests = mutableListOf<RuntimeNetworkRequest>()

        constructor(
            executeResponse: RuntimeNetworkResponse = RuntimeNetworkResponse(
                statusCode = 200,
                headers = emptyMap(),
                bodyBytes = """{"ok":true}""".toByteArray(),
                traceId = "trace",
                durationMs = 1,
            ),
            streamLines: Flow<String> = flowOf(),
        ) {
            this.executeResponse = executeResponse
            this.streamLines = streamLines
        }

        override suspend fun execute(request: RuntimeNetworkRequest): RuntimeNetworkResponse {
            executeRequests += request
            return executeResponse.copy(traceId = request.traceContext.traceId)
        }

        override fun openStream(request: RuntimeNetworkRequest): Flow<String> {
            streamRequests += request
            return streamLines
        }

        override fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent> {
            throw UnsupportedOperationException("Not used in verification test.")
        }
    }
}
