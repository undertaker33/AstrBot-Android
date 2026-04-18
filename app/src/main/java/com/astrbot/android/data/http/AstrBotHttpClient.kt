package com.astrbot.android.data.http

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.core.runtime.network.RuntimeNetworkException
import com.astrbot.android.core.runtime.network.RuntimeNetworkFailure
import com.astrbot.android.core.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.core.runtime.network.RuntimeNetworkResponse
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.astrbot.android.core.runtime.network.SharedRuntimeNetworkTransport
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import java.net.SocketTimeoutException

interface AstrBotHttpClient {
    fun execute(requestSpec: HttpRequestSpec): HttpResponsePayload

    fun executeBytes(requestSpec: HttpRequestSpec): ByteArray

    suspend fun executeStream(
        requestSpec: HttpRequestSpec,
        onLine: suspend (String) -> Unit,
    )

    fun executeMultipart(
        requestSpec: HttpRequestSpec,
        parts: List<MultipartPartSpec>,
    ): HttpResponsePayload
}

class OkHttpAstrBotHttpClient(
    private val transport: RuntimeNetworkTransport = SharedRuntimeNetworkTransport.get(),
    private val logger: (String) -> Unit = RuntimeLogRepository::append,
) : AstrBotHttpClient {
    override fun execute(requestSpec: HttpRequestSpec): HttpResponsePayload {
        val sanitizedUrl = sanitizeUrlForLogs(requestSpec.url)
        logger("HTTP request: method=${requestSpec.method.value} endpoint=$sanitizedUrl")
        return try {
            when (val result = executeRuntimeRequest(requestSpec, streaming = false)) {
                is RuntimeExecuteResult.Success -> {
                    logger("HTTP response code: code=${result.response.statusCode} endpoint=$sanitizedUrl")
                    result.response.toHttpResponsePayload(requestSpec.url)
                }
                is RuntimeExecuteResult.HttpFailure -> {
                    logger(
                        "HTTP response error: code=${result.failure.statusCode} endpoint=$sanitizedUrl body=${sanitizeErrorBody(result.failure.bodyPreview)}",
                    )
                    HttpResponsePayload(
                        code = result.failure.statusCode,
                        body = result.failure.bodyPreview,
                        headers = emptyMap(),
                        url = requestSpec.url,
                    )
                }
            }
        } catch (error: Throwable) {
            throw mapFailure(sanitizedUrl, error)
        }
    }

    override fun executeBytes(requestSpec: HttpRequestSpec): ByteArray {
        val sanitizedUrl = sanitizeUrlForLogs(requestSpec.url)
        logger("HTTP binary request: method=${requestSpec.method.value} endpoint=$sanitizedUrl")
        return try {
            when (val result = executeRuntimeRequest(requestSpec, streaming = false)) {
                is RuntimeExecuteResult.Success -> {
                    logger("HTTP binary response code: code=${result.response.statusCode} endpoint=$sanitizedUrl")
                    result.response.bodyBytes
                }
                is RuntimeExecuteResult.HttpFailure -> throw mapRuntimeHttpFailure(sanitizedUrl, result.failure)
            }
        } catch (error: Throwable) {
            throw mapFailure(sanitizedUrl, error)
        }
    }

    override suspend fun executeStream(
        requestSpec: HttpRequestSpec,
        onLine: suspend (String) -> Unit,
    ) {
        val sanitizedUrl = sanitizeUrlForLogs(requestSpec.url)
        logger("HTTP streaming request: method=${requestSpec.method.value} endpoint=$sanitizedUrl")
        val runtimeRequest = requestSpec.toRuntimeRequest(streaming = true)
        try {
            transport.openStream(runtimeRequest).collect { line ->
                onLine(line)
            }
            logger("HTTP streaming response complete: endpoint=$sanitizedUrl")
        } catch (error: Throwable) {
            throw mapFailure(sanitizedUrl, error)
        }
    }

    override fun executeMultipart(
        requestSpec: HttpRequestSpec,
        parts: List<MultipartPartSpec>,
    ): HttpResponsePayload {
        val sanitizedUrl = sanitizeUrlForLogs(requestSpec.url)
        logger("HTTP multipart request: method=${requestSpec.method.value} endpoint=$sanitizedUrl")
        return try {
            when (val result = executeRuntimeMultipart(requestSpec, parts)) {
                is RuntimeExecuteResult.Success -> {
                    logger("HTTP multipart response code: code=${result.response.statusCode} endpoint=$sanitizedUrl")
                    result.response.toHttpResponsePayload(requestSpec.url)
                }
                is RuntimeExecuteResult.HttpFailure -> {
                    logger(
                        "HTTP multipart response error: code=${result.failure.statusCode} endpoint=$sanitizedUrl body=${sanitizeErrorBody(result.failure.bodyPreview)}",
                    )
                    HttpResponsePayload(
                        code = result.failure.statusCode,
                        body = result.failure.bodyPreview,
                        headers = emptyMap(),
                        url = requestSpec.url,
                    )
                }
            }
        } catch (error: Throwable) {
            throw mapFailure(sanitizedUrl, error)
        }
    }

    private fun executeRuntimeRequest(
        requestSpec: HttpRequestSpec,
        streaming: Boolean,
    ): RuntimeExecuteResult {
        val runtimeRequest = requestSpec.toRuntimeRequest(streaming)
        return try {
            val response = runBlocking { transport.execute(runtimeRequest) }
            RuntimeExecuteResult.Success(response)
        } catch (error: RuntimeNetworkException) {
            when (val failure = error.failure) {
                is RuntimeNetworkFailure.Http -> RuntimeExecuteResult.HttpFailure(failure)
                else -> throw error
            }
        }
    }

    private fun executeRuntimeMultipart(
        requestSpec: HttpRequestSpec,
        parts: List<MultipartPartSpec>,
    ): RuntimeExecuteResult {
        val multipart = buildMultipartBody(parts)
        val runtimeRequest = requestSpec.toRuntimeRequest(streaming = false).copy(
            body = multipart.bytes,
            contentType = multipart.contentType,
        )
        return try {
            val response = runBlocking { transport.execute(runtimeRequest) }
            RuntimeExecuteResult.Success(response)
        } catch (error: RuntimeNetworkException) {
            when (val failure = error.failure) {
                is RuntimeNetworkFailure.Http -> RuntimeExecuteResult.HttpFailure(failure)
                else -> throw error
            }
        }
    }

    private fun buildMultipartBody(parts: List<MultipartPartSpec>): EncodedMultipart {
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                parts.forEach { part ->
                    when (part) {
                        is MultipartPartSpec.Text -> addFormDataPart(part.name, part.value)
                        is MultipartPartSpec.File -> addFormDataPart(
                            part.name,
                            part.fileName,
                            part.bytes.toRequestBody(part.contentType.toMediaTypeOrNull()),
                        )
                    }
                }
            }
            .build()

        val buffer = Buffer()
        multipartBody.writeTo(buffer)
        return EncodedMultipart(
            bytes = buffer.readByteArray(),
            contentType = multipartBody.contentType().toString(),
        )
    }

    private fun RuntimeNetworkResponse.toHttpResponsePayload(url: String): HttpResponsePayload {
        return HttpResponsePayload(
            code = statusCode,
            body = bodyString,
            headers = headers,
            url = url,
        )
    }

    private fun HttpRequestSpec.toRuntimeRequest(streaming: Boolean): RuntimeNetworkRequest {
        return RuntimeNetworkRequest(
            capability = RuntimeNetworkCapability.PROVIDER,
            method = method.value,
            url = url,
            headers = headers,
            body = body?.toByteArray(StandardCharsets.UTF_8),
            contentType = contentType,
            timeoutProfile = if (streaming) {
                RuntimeTimeoutProfile.PROVIDER_STREAMING
            } else {
                RuntimeTimeoutProfile.PROVIDER_NON_STREAMING
            },
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
        )
    }

    private fun mapFailure(
        sanitizedUrl: String,
        error: Throwable,
    ): AstrBotHttpException {
        if (error is AstrBotHttpException) return error

        val category = when (error) {
            is RuntimeNetworkException -> when (error.failure) {
                is RuntimeNetworkFailure.ConnectTimeout,
                is RuntimeNetworkFailure.ReadTimeout -> HttpFailureCategory.TIMEOUT
                else -> HttpFailureCategory.NETWORK
            }
            is SocketTimeoutException,
            is InterruptedIOException -> HttpFailureCategory.TIMEOUT
            else -> HttpFailureCategory.NETWORK
        }

        val message = when (error) {
            is RuntimeNetworkException -> error.failure.toMessage()
            else -> error.message ?: "HTTP request failed"
        }

        logger(
            "HTTP request failed: endpoint=$sanitizedUrl category=$category reason=${message}",
        )

        return AstrBotHttpException(
            category = category,
            message = message,
            cause = error,
        )
    }

    private fun mapRuntimeHttpFailure(
        sanitizedUrl: String,
        failure: RuntimeNetworkFailure.Http,
    ): AstrBotHttpException {
        val message = failure.toMessage()
        logger(
            "HTTP request failed: endpoint=$sanitizedUrl category=${HttpFailureCategory.NETWORK} reason=$message",
        )
        return AstrBotHttpException(
            category = HttpFailureCategory.NETWORK,
            message = message,
            cause = failure.cause,
        )
    }

    private fun RuntimeNetworkFailure.toMessage(): String {
        return when (this) {
            is RuntimeNetworkFailure.Http -> "HTTP $statusCode: ${sanitizeErrorBody(bodyPreview)}"
            else -> summary
        }
    }

    internal fun sanitizeErrorBody(raw: String): String {
        if (raw.isBlank()) return "(empty)"
        val truncated = raw.take(1000)
        val sensitiveKeys = Regex(
            """("(?:api_key|apikey|authorization|token|secret|password|key)")\s*:\s*"[^"]*"""",
            RegexOption.IGNORE_CASE,
        )
        return sensitiveKeys.replace(truncated) { match ->
            "${match.groupValues[1]}: \"***\""
        }
    }

    private fun sanitizeUrlForLogs(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val base = url.substring(0, queryStart)
        val query = url.substring(queryStart + 1)
        val sanitizedQuery = query.split("&")
            .filter { it.isNotBlank() }
            .joinToString("&") { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@joinToString pair
                val key = pair.substring(0, separator)
                val value = pair.substring(separator + 1)
                if (key.equals("key", ignoreCase = true) || key.equals("api_key", ignoreCase = true)) {
                    "$key=***"
                } else {
                    "$key=$value"
                }
            }
        return "$base?$sanitizedQuery"
    }

    private sealed class RuntimeExecuteResult {
        data class Success(val response: RuntimeNetworkResponse) : RuntimeExecuteResult()
        data class HttpFailure(val failure: RuntimeNetworkFailure.Http) : RuntimeExecuteResult()
    }

    private data class EncodedMultipart(
        val bytes: ByteArray,
        val contentType: String,
    )
}
