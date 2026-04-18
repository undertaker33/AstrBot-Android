package com.astrbot.android.core.runtime.network

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Shared runtime network transport.
 *
 * All runtime capabilities (provider, web search, MCP, active capability)
 * share this single transport layer. Each request carries a
 * [RuntimeTimeoutProfile] and [RuntimeNetworkCapability] so the transport
 * can apply per-capability timeouts and produce unified trace/log entries.
 *
 * This replaces the previous pattern where each capability created its own
 * private [OkHttpClient].
 */
interface RuntimeNetworkTransport {
    /**
     * Execute a standard HTTP request and return the full response.
     *
     * @throws RuntimeNetworkException on any network-level or HTTP error failure.
     */
    suspend fun execute(request: RuntimeNetworkRequest): RuntimeNetworkResponse

    /**
     * Open a streaming HTTP connection, returning a [Flow] of text lines.
     * Used for provider streaming responses. The underlying connection is
     * closed when the flow completes or the collector cancels.
     *
     * @throws RuntimeNetworkException on any network-level or HTTP error failure.
     */
    fun openStream(request: RuntimeNetworkRequest): Flow<String>

    /**
     * Open an SSE (Server-Sent Events) session, returning a [Flow] of
     * [SseEvent]s. Used for MCP SSE transport and future SSE-based protocols.
     * The underlying connection is closed when the flow completes or the
     * collector cancels.
     *
     * @throws RuntimeNetworkException on any network-level or HTTP error failure.
     */
    fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent>
}

/**
 * A single Server-Sent Event parsed from an SSE stream.
 */
data class SseEvent(
    val event: String = "message",
    val data: String = "",
    val id: String = "",
)

/**
 * Exception wrapper around [RuntimeNetworkFailure] for use in suspend
 * functions that need to propagate failures through the call stack.
 */
class RuntimeNetworkException(
    val failure: RuntimeNetworkFailure,
) : IOException(failure.summary, failure.cause)

/**
 * Default implementation backed by a shared [OkHttpClient] base.
 *
 * Per-request timeouts are applied by deriving a new client from the shared
 * base via [OkHttpClient.newBuilder], which reuses the connection pool and
 * dispatcher.
 */
class OkHttpRuntimeNetworkTransport(
    internal val baseClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build(),
) : RuntimeNetworkTransport {

    override suspend fun execute(request: RuntimeNetworkRequest): RuntimeNetworkResponse {
        val startMs = System.currentTimeMillis()
        val traceId = request.traceContext.traceId

        return try {
            withContext(Dispatchers.IO) {
                val perRequestClient = deriveClient(request)
                val okRequest = buildOkHttpRequest(request)
                perRequestClient.newCall(okRequest).execute().use { response ->
                    val durationMs = System.currentTimeMillis() - startMs
                    val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                    val responseHeaders = response.headers.toMultimap()

                    if (!response.isSuccessful) {
                        val bodyPreview = bodyBytes.decodeToString()
                        val failure = RuntimeNetworkFailure.Http(response.code, request.url, bodyPreview)
                        RuntimeLogRepository.append(
                            "RuntimeNetwork HTTP-ERR: trace=$traceId capability=${request.capability} " +
                                "${request.method} ${sanitizeUrl(request.url)} " +
                                "status=${response.code} ${durationMs}ms: ${bodyPreview.take(200)}",
                        )
                        throw RuntimeNetworkException(failure)
                    }

                    RuntimeLogRepository.append(
                        "RuntimeNetwork OK: trace=$traceId capability=${request.capability} " +
                            "${request.method} ${sanitizeUrl(request.url)} " +
                            "status=${response.code} ${durationMs}ms bytes=${bodyBytes.size}",
                    )

                    RuntimeNetworkResponse(
                        statusCode = response.code,
                        headers = responseHeaders,
                        bodyBytes = bodyBytes,
                        traceId = traceId,
                        durationMs = durationMs,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeNetworkException) {
            throw e
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            val failure = classifyFailure(e, request)
            RuntimeLogRepository.append(
                "RuntimeNetwork FAIL: trace=$traceId capability=${request.capability} " +
                    "${request.method} ${sanitizeUrl(request.url)} " +
                    "${failure::class.simpleName} ${durationMs}ms: ${failure.summary.take(200)}",
            )
            throw RuntimeNetworkException(failure)
        }
    }

    override fun openStream(request: RuntimeNetworkRequest): Flow<String> = flow {
        val traceId = request.traceContext.traceId

        try {
            val perRequestClient = deriveClient(request)
            val okRequest = buildOkHttpRequest(request)
            perRequestClient.newCall(okRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw RuntimeNetworkException(
                        RuntimeNetworkFailure.Http(response.code, request.url, body),
                    )
                }
                RuntimeLogRepository.append(
                    "RuntimeNetwork STREAM-OPEN: trace=$traceId capability=${request.capability} " +
                        "${request.method} ${sanitizeUrl(request.url)} status=${response.code}",
                )
                val reader: BufferedReader = response.body?.charStream()?.buffered()
                    ?: throw RuntimeNetworkException(
                        RuntimeNetworkFailure.Protocol("Empty response body for stream request", null),
                    )
                reader.use { buffered ->
                    while (true) {
                        val line = buffered.readLine() ?: break
                        emit(line)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeNetworkException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeNetworkException(classifyFailure(e, request))
        } finally {
            RuntimeLogRepository.append(
                "RuntimeNetwork STREAM-CLOSE: trace=$traceId capability=${request.capability}",
            )
        }
    }.flowOn(Dispatchers.IO)

    override fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent> = flow {
        val traceId = request.traceContext.traceId

        try {
            val perRequestClient = deriveClient(request)
            val okRequest = buildOkHttpRequest(request)
            perRequestClient.newCall(okRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw RuntimeNetworkException(
                        RuntimeNetworkFailure.Http(response.code, request.url, body),
                    )
                }
                RuntimeLogRepository.append(
                    "RuntimeNetwork SSE-OPEN: trace=$traceId capability=${request.capability} " +
                        "${request.method} ${sanitizeUrl(request.url)} status=${response.code}",
                )
                val reader: BufferedReader = response.body?.charStream()?.buffered()
                    ?: throw RuntimeNetworkException(
                        RuntimeNetworkFailure.Protocol("Empty response body for SSE request", null),
                    )
                reader.use { buffered ->
                    var eventType = "message"
                    var dataLines = StringBuilder()
                    var lastId = ""

                    suspend fun flushEvent() {
                        if (dataLines.isEmpty()) return
                        emit(
                            SseEvent(
                            event = eventType,
                            data = dataLines.toString(),
                            id = lastId,
                            ),
                        )
                        eventType = "message"
                        dataLines = StringBuilder()
                    }

                    while (true) {
                        val rawLine = buffered.readLine() ?: break
                        val line = rawLine.trimEnd()
                        when {
                            line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> {
                                if (dataLines.isNotEmpty()) dataLines.append('\n')
                                dataLines.append(line.removePrefix("data:").trimStart())
                            }
                            line.startsWith("id:") -> lastId = line.removePrefix("id:").trim()
                            line.isEmpty() -> flushEvent()
                        }
                    }
                    flushEvent()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeNetworkException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeNetworkException(classifyFailure(e, request))
        } finally {
            RuntimeLogRepository.append(
                "RuntimeNetwork SSE-CLOSE: trace=$traceId capability=${request.capability}",
            )
        }
    }.flowOn(Dispatchers.IO)

    // ── Internal ──

    private fun deriveClient(request: RuntimeNetworkRequest): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(request.connectTimeoutMs ?: request.timeoutProfile.connectMs, TimeUnit.MILLISECONDS)
            .readTimeout(request.readTimeoutMs ?: request.timeoutProfile.readMs, TimeUnit.MILLISECONDS)
            .writeTimeout(request.writeTimeoutMs ?: request.timeoutProfile.writeMs, TimeUnit.MILLISECONDS)
            .followRedirects(request.followRedirects)
            .build()
    }

    private fun buildOkHttpRequest(request: RuntimeNetworkRequest): Request {
        val builder = Request.Builder().url(request.url)

        for ((name, value) in request.headers) {
            builder.addHeader(name, value)
        }

        val body = request.body
        val method = request.method.uppercase()
        when {
            body != null -> {
                val mediaType = request.contentType?.toMediaTypeOrNull()
                builder.method(method, body.toRequestBody(mediaType))
            }
            method == "POST" || method == "PUT" || method == "PATCH" -> {
                builder.method(method, ByteArray(0).toRequestBody(null))
            }
            else -> {
                builder.method(method, null)
            }
        }

        return builder.build()
    }

    private fun classifyFailure(
        e: Exception,
        request: RuntimeNetworkRequest,
    ): RuntimeNetworkFailure {
        return when {
            e is UnknownHostException -> {
                val host = runCatching { java.net.URL(request.url).host }.getOrElse { request.url }
                RuntimeNetworkFailure.Dns(host, e.message, e)
            }
            e is SocketTimeoutException -> {
                val message = e.message.orEmpty().lowercase()
                if ("connect" in message) {
                    RuntimeNetworkFailure.ConnectTimeout(request.url, e)
                } else {
                    RuntimeNetworkFailure.ReadTimeout(request.url, e)
                }
            }
            e is SSLException -> {
                RuntimeNetworkFailure.Tls(request.url, e.message, e)
            }
            e is IOException && e.message?.contains("cancel", ignoreCase = true) == true -> {
                RuntimeNetworkFailure.Cancelled(e.message)
            }
            e is IOException -> {
                RuntimeNetworkFailure.Unknown(e.message, e)
            }
            else -> {
                RuntimeNetworkFailure.Unknown(e.message, e)
            }
        }
    }

    companion object {
        /**
         * Redact query parameters from URLs for safe logging.
         */
        internal fun sanitizeUrl(url: String): String {
            val qIndex = url.indexOf('?')
            return if (qIndex >= 0) "${url.substring(0, qIndex)}?…" else url
        }

        /**
         * Parse an SSE stream from a [BufferedReader], invoking [onEvent] for
         * each complete event.
         */
        internal fun parseSseStream(reader: BufferedReader, onEvent: (SseEvent) -> Unit) {
            var eventType = "message"
            var dataLines = StringBuilder()
            var lastId = ""

            reader.forEachLine { rawLine ->
                val line = rawLine.trimEnd()
                when {
                    line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (dataLines.isNotEmpty()) dataLines.append('\n')
                        dataLines.append(line.removePrefix("data:").trimStart())
                    }
                    line.startsWith("id:") -> lastId = line.removePrefix("id:").trim()
                    line.isEmpty() && dataLines.isNotEmpty() -> {
                        onEvent(SseEvent(event = eventType, data = dataLines.toString(), id = lastId))
                        eventType = "message"
                        dataLines = StringBuilder()
                    }
                }
            }
            // Flush any trailing event without a final blank line
            if (dataLines.isNotEmpty()) {
                onEvent(SseEvent(event = eventType, data = dataLines.toString(), id = lastId))
            }
        }
    }
}

/**
 * Singleton access to the shared runtime transport.
 */
object SharedRuntimeNetworkTransport {
    @Volatile
    private var instance: RuntimeNetworkTransport = OkHttpRuntimeNetworkTransport()

    fun get(): RuntimeNetworkTransport = instance

    /** Access the underlying [OkHttpClient] base for transport-aligned adapters. */
    fun sharedBaseClient(): OkHttpClient {
        val transport = instance
        return if (transport is OkHttpRuntimeNetworkTransport) {
            transport.baseClient
        } else {
            OkHttpClient()
        }
    }

    /**
     * For tests only — replace the shared transport with a fake/mock.
     */
    internal fun setOverrideForTests(transport: RuntimeNetworkTransport?) {
        instance = transport ?: OkHttpRuntimeNetworkTransport()
    }
}
