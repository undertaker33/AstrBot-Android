package com.astrbot.android.data.http

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkFailure
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkResponse
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.SharedRuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AstrBotHttpClientTest {

    @Before
    fun setUp() {
        RuntimeLogRepository.clear()
        SharedRuntimeNetworkTransport.setOverrideForTests(null)
    }

    @After
    fun tearDown() {
        SharedRuntimeNetworkTransport.setOverrideForTests(null)
    }

    @Test
    fun `execute delegates to runtime transport and preserves response payload contract`() {
        val transport = RecordingRuntimeTransport(
            executeResponse = RuntimeNetworkResponse(
                statusCode = 201,
                headers = mapOf("X-Test" to listOf("ok")),
                bodyBytes = """{"created":true}""".toByteArray(),
                traceId = "trace-1",
                durationMs = 5,
            ),
        )
        SharedRuntimeNetworkTransport.setOverrideForTests(transport)
        val client = OkHttpAstrBotHttpClient()

        val response = client.execute(
            HttpRequestSpec(
                method = HttpMethod.POST,
                url = "http://127.0.0.1:1/chat?key=secret-value",
                headers = mapOf("Authorization" to "Bearer test-token"),
                body = """{"message":"hello"}""",
                contentType = "application/json",
                connectTimeoutMs = 111,
                readTimeoutMs = 222,
            ),
        )

        assertEquals(201, response.code)
        assertEquals("""{"created":true}""", response.body)
        assertEquals("ok", response.headers["X-Test"]?.single())
        assertEquals(1, transport.executeRequests.size)
        val recorded = transport.executeRequests.single()
        assertEquals(RuntimeNetworkCapability.PROVIDER, recorded.capability)
        assertEquals("POST", recorded.method)
        assertEquals("http://127.0.0.1:1/chat?key=secret-value", recorded.url)
        assertEquals("application/json", recorded.contentType)
        assertEquals("""{"message":"hello"}""", recorded.body!!.decodeToString())
        assertTrue(RuntimeLogRepository.logs.value.any { it.contains("key=***") })
        assertFalse(RuntimeLogRepository.logs.value.any { it.contains("secret-value") })
    }

    @Test
    fun `execute converts runtime http failure into response payload`() {
        SharedRuntimeNetworkTransport.setOverrideForTests(
            RecordingRuntimeTransport(
                executeException = RuntimeNetworkException(
                    RuntimeNetworkFailure.Http(
                        statusCode = 404,
                        url = "http://127.0.0.1:1/missing",
                        bodyPreview = """{"error":"missing"}""",
                    ),
                ),
            ),
        )
        val client = OkHttpAstrBotHttpClient()

        val response = client.execute(
            HttpRequestSpec(
                method = HttpMethod.GET,
                url = "http://127.0.0.1:1/missing",
            ),
        )

        assertEquals(404, response.code)
        assertEquals("""{"error":"missing"}""", response.body)
        assertEquals("http://127.0.0.1:1/missing", response.url)
    }

    @Test
    fun `execute bytes delegates to runtime transport`() {
        val transport = RecordingRuntimeTransport(
            executeResponse = RuntimeNetworkResponse(
                statusCode = 200,
                headers = emptyMap(),
                bodyBytes = byteArrayOf(1, 2, 3, 4),
                traceId = "trace-2",
                durationMs = 2,
            ),
        )
        SharedRuntimeNetworkTransport.setOverrideForTests(transport)
        val client = OkHttpAstrBotHttpClient()

        val bytes = client.executeBytes(
            HttpRequestSpec(
                method = HttpMethod.GET,
                url = "http://127.0.0.1:1/binary",
            ),
        )

        assertEquals(listOf<Byte>(1, 2, 3, 4), bytes.toList())
        assertEquals(1, transport.executeRequests.size)
        assertEquals("http://127.0.0.1:1/binary", transport.executeRequests.single().url)
    }

    @Test
    fun `execute bytes on runtime http failure includes status and body`() {
        SharedRuntimeNetworkTransport.setOverrideForTests(
            RecordingRuntimeTransport(
                executeException = RuntimeNetworkException(
                    RuntimeNetworkFailure.Http(
                        statusCode = 400,
                        url = "http://127.0.0.1:1/audio",
                        bodyPreview = """{"error":"bad input"}""",
                    ),
                ),
            ),
        )
        val client = OkHttpAstrBotHttpClient()

        val error = runCatching {
            client.executeBytes(
                HttpRequestSpec(
                    method = HttpMethod.GET,
                    url = "http://127.0.0.1:1/audio",
                ),
            )
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error is AstrBotHttpException)
        assertTrue(error.message!!.contains("400"))
        assertTrue(error.message!!.contains("bad input"))
    }

    @Test
    fun `execute stream delegates to runtime transport lines`() {
        val transport = RecordingRuntimeTransport(
            streamLines = flowOf("data: first", "", "data: second", ""),
        )
        SharedRuntimeNetworkTransport.setOverrideForTests(transport)
        val client = OkHttpAstrBotHttpClient()
        val lines = mutableListOf<String>()

        runBlocking {
            client.executeStream(
                HttpRequestSpec(
                    method = HttpMethod.POST,
                    url = "http://127.0.0.1:1/stream",
                    body = "{}",
                    contentType = "application/json",
                ),
            ) { line ->
                lines += line
            }
        }

        assertEquals(listOf("data: first", "", "data: second", ""), lines)
        assertEquals(1, transport.streamRequests.size)
        assertEquals("http://127.0.0.1:1/stream", transport.streamRequests.single().url)
    }

    @Test
    fun `execute multipart delegates encoded body through runtime transport`() {
        val transport = RecordingRuntimeTransport(
            executeResponse = RuntimeNetworkResponse(
                statusCode = 200,
                headers = emptyMap(),
                bodyBytes = """{"ok":true}""".toByteArray(),
                traceId = "trace-3",
                durationMs = 1,
            ),
        )
        SharedRuntimeNetworkTransport.setOverrideForTests(transport)
        val client = OkHttpAstrBotHttpClient()

        val response = client.executeMultipart(
            HttpRequestSpec(
                method = HttpMethod.POST,
                url = "http://127.0.0.1:1/upload",
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

        assertEquals(200, response.code)
        assertEquals(1, transport.executeRequests.size)
        val recorded = transport.executeRequests.single()
        assertEquals("POST", recorded.method)
        assertNotNull(recorded.body)
        assertTrue(recorded.contentType!!.startsWith("multipart/form-data; boundary="))
        val bodyText = recorded.body!!.decodeToString()
        assertTrue(bodyText.contains("name=\"purpose\""))
        assertTrue(bodyText.contains("vision"))
        assertTrue(bodyText.contains("name=\"file\"; filename=\"photo.jpg\""))
    }

    @Test
    fun `sanitize error body redacts api keys`() {
        val client = OkHttpAstrBotHttpClient()
        val raw = """{"error":"fail","api_key":"sk-12345secret"}"""

        val sanitized = client.sanitizeErrorBody(raw)

        assertTrue(sanitized.contains("error"))
        assertFalse(sanitized.contains("sk-12345secret"))
    }

    @Test
    fun `sanitize error body truncates long bodies`() {
        val client = OkHttpAstrBotHttpClient()
        val raw = "x".repeat(2000)

        val sanitized = client.sanitizeErrorBody(raw)

        assertTrue(sanitized.length <= 1000)
    }

    private class RecordingRuntimeTransport(
        private val executeResponse: RuntimeNetworkResponse = RuntimeNetworkResponse(
            statusCode = 200,
            headers = emptyMap(),
            bodyBytes = ByteArray(0),
            traceId = "trace",
            durationMs = 1,
        ),
        private val executeException: RuntimeNetworkException? = null,
        private val streamLines: Flow<String> = flowOf(),
    ) : RuntimeNetworkTransport {
        val executeRequests = mutableListOf<RuntimeNetworkRequest>()
        val streamRequests = mutableListOf<RuntimeNetworkRequest>()

        override suspend fun execute(request: RuntimeNetworkRequest): RuntimeNetworkResponse {
            executeRequests += request
            executeException?.let { throw it }
            return executeResponse
        }

        override fun openStream(request: RuntimeNetworkRequest): Flow<String> {
            streamRequests += request
            return streamLines
        }

        override fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent> {
            throw UnsupportedOperationException("Not used in provider adapter tests.")
        }
    }
}
