package com.astrbot.android.core.runtime.network

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class RuntimeNetworkTransportTest {
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
    fun execute_throws_http_failure_on_4xx_with_real_okhttp_transport() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("rate limited"),
        )
        val transport = OkHttpRuntimeNetworkTransport()

        val error = runCatching {
            transport.execute(runtimeRequest("/too-many"))
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error is RuntimeNetworkException)
        val failure = (error as RuntimeNetworkException).failure
        assertTrue(failure is RuntimeNetworkFailure.Http)
        failure as RuntimeNetworkFailure.Http
        assertEquals(429, failure.statusCode)
        assertTrue(failure.bodyPreview.contains("rate limited"))
    }

    @Test
    fun openStream_returns_lines_from_real_http_response() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("line1\nline2\nline3\n"),
        )
        val transport = OkHttpRuntimeNetworkTransport()

        val lines = transport.openStream(runtimeRequest("/stream")).toList()

        assertEquals(listOf("line1", "line2", "line3"), lines)
    }

    @Test
    fun openSse_parses_events_from_real_http_response() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "id: evt-1\n" +
                        "event: message\n" +
                        "data: hello\n\n" +
                        "event: done\n" +
                        "data: bye\n\n",
                ),
        )
        val transport = OkHttpRuntimeNetworkTransport()

        val events = transport.openSse(runtimeRequest("/events")).toList()

        assertEquals(2, events.size)
        assertEquals("evt-1", events[0].id)
        assertEquals("message", events[0].event)
        assertEquals("hello", events[0].data)
        assertEquals("done", events[1].event)
        assertEquals("bye", events[1].data)
    }

    @Test
    fun openSse_throws_http_failure_on_5xx_with_real_okhttp_transport() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("service unavailable"),
        )
        val transport = OkHttpRuntimeNetworkTransport()

        val error = runCatching {
            transport.openSse(runtimeRequest("/events-error")).toList()
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error is RuntimeNetworkException)
        val failure = (error as RuntimeNetworkException).failure
        assertTrue(failure is RuntimeNetworkFailure.Http)
        failure as RuntimeNetworkFailure.Http
        assertEquals(503, failure.statusCode)
        assertTrue(failure.bodyPreview.contains("service unavailable"))
    }

    @Test
    fun execute_runs_blocking_network_work_off_caller_context() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok"),
        )
        val threads = CopyOnWriteArrayList<String>()
        val client = clientRecordingThreads(threads)
        val transport = OkHttpRuntimeNetworkTransport(client)

        newSingleThreadContext("runtime-caller").use { caller ->
            runBlocking(caller) {
                transport.execute(runtimeRequest("/thread-check"))
            }
        }

        assertTrue(threads.isNotEmpty())
        assertTrue(threads.all { it.isNotBlank() })
        assertNotEquals("runtime-caller", threads.first())
    }

    @Test
    fun openStream_runs_blocking_network_work_off_caller_context() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("only-line\n"),
        )
        val threads = CopyOnWriteArrayList<String>()
        val client = clientRecordingThreads(threads)
        val transport = OkHttpRuntimeNetworkTransport(client)

        newSingleThreadContext("runtime-caller").use { caller ->
            runBlocking(caller) {
                transport.openStream(runtimeRequest("/thread-stream")).toList()
            }
        }

        assertTrue(threads.isNotEmpty())
        assertTrue(threads.all { it.isNotBlank() })
        assertNotEquals("runtime-caller", threads.first())
    }

    @Test
    fun openSse_runs_blocking_network_work_off_caller_context() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("data: hello\n\n"),
        )
        val threads = CopyOnWriteArrayList<String>()
        val client = clientRecordingThreads(threads)
        val transport = OkHttpRuntimeNetworkTransport(client)

        newSingleThreadContext("runtime-caller").use { caller ->
            runBlocking(caller) {
                transport.openSse(runtimeRequest("/thread-sse")).toList()
            }
        }

        assertTrue(threads.isNotEmpty())
        assertTrue(threads.all { it.isNotBlank() })
        assertNotEquals("runtime-caller", threads.first())
    }

    @Test
    fun sse_parser_handles_multiline_data() {
        val input = "event: update\ndata: line1\ndata: line2\n\n"
        val events = mutableListOf<SseEvent>()

        OkHttpRuntimeNetworkTransport.parseSseStream(
            input.byteInputStream().bufferedReader(),
        ) { events.add(it) }

        assertEquals(1, events.size)
        assertEquals("update", events[0].event)
        assertEquals("line1\nline2", events[0].data)
    }

    @Test
    fun sse_parser_handles_id_field() {
        val input = "id: 42\ndata: test\n\n"
        val events = mutableListOf<SseEvent>()

        OkHttpRuntimeNetworkTransport.parseSseStream(
            input.byteInputStream().bufferedReader(),
        ) { events.add(it) }

        assertEquals(1, events.size)
        assertEquals("42", events[0].id)
    }

    @Test
    fun sse_parser_flushes_trailing_event_without_blank_line() {
        val input = "data: trailing"
        val events = mutableListOf<SseEvent>()

        OkHttpRuntimeNetworkTransport.parseSseStream(
            input.byteInputStream().bufferedReader(),
        ) { events.add(it) }

        assertEquals(1, events.size)
        assertEquals("trailing", events[0].data)
    }

    private fun runtimeRequest(path: String) = RuntimeNetworkRequest(
        capability = RuntimeNetworkCapability.WEB_SEARCH,
        method = "GET",
        url = server.url(path).toString(),
        timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
    )

    private fun clientRecordingThreads(threads: MutableList<String>): OkHttpClient {
        return OkHttpClient.Builder()
            .addNetworkInterceptor(
                Interceptor { chain ->
                    threads += Thread.currentThread().name
                    chain.proceed(chain.request())
                },
            )
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
    }
}
