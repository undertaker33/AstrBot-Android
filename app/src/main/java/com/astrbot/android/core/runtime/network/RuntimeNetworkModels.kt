package com.astrbot.android.core.runtime.network

import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Which runtime capability is making the request.
 * Used for logging, trace, timeout selection, and error attribution.
 */
enum class RuntimeNetworkCapability {
    PROVIDER,
    WEB_SEARCH,
    MCP_RPC,
    MCP_SSE,
    ACTIVE_CAPABILITY,
}

/**
 * Predefined timeout profiles for different runtime capabilities.
 * Each profile defines connect, read, and write timeouts.
 */
enum class RuntimeTimeoutProfile(
    val connectMs: Long,
    val readMs: Long,
    val writeMs: Long,
) {
    PROVIDER_STREAMING(connectMs = 15_000, readMs = 120_000, writeMs = 30_000),
    PROVIDER_NON_STREAMING(connectMs = 15_000, readMs = 60_000, writeMs = 30_000),
    WEB_SEARCH(connectMs = 10_000, readMs = 15_000, writeMs = 10_000),
    MCP_RPC(connectMs = 10_000, readMs = 60_000, writeMs = 30_000),
    MCP_SSE_DISCOVERY(connectMs = 10_000, readMs = 30_000, writeMs = 10_000),
    MCP_SSE_PERSISTENT(connectMs = 10_000, readMs = 0, writeMs = 10_000),
    ACTIVE_CAPABILITY_CALLBACK(connectMs = 10_000, readMs = 30_000, writeMs = 10_000),
}

/**
 * Unified runtime network request.
 */
data class RuntimeNetworkRequest(
    val capability: RuntimeNetworkCapability,
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
    val timeoutProfile: RuntimeTimeoutProfile,
    val connectTimeoutMs: Long? = null,
    val readTimeoutMs: Long? = null,
    val writeTimeoutMs: Long? = null,
    val traceContext: RuntimeTraceContext = RuntimeTraceContext(),
    val followRedirects: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuntimeNetworkRequest) return false
        val bodiesEqual = if (body == null) other.body == null else other.body != null && body.contentEquals(other.body)
        return capability == other.capability &&
            method == other.method &&
            url == other.url &&
            headers == other.headers &&
            bodiesEqual &&
            contentType == other.contentType &&
            timeoutProfile == other.timeoutProfile &&
            connectTimeoutMs == other.connectTimeoutMs &&
            readTimeoutMs == other.readTimeoutMs &&
            writeTimeoutMs == other.writeTimeoutMs &&
            followRedirects == other.followRedirects
    }

    override fun hashCode(): Int {
        var result = capability.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + timeoutProfile.hashCode()
        result = 31 * result + (connectTimeoutMs?.hashCode() ?: 0)
        result = 31 * result + (readTimeoutMs?.hashCode() ?: 0)
        result = 31 * result + (writeTimeoutMs?.hashCode() ?: 0)
        result = 31 * result + followRedirects.hashCode()
        return result
    }
}

/**
 * Unified runtime network response.
 */
data class RuntimeNetworkResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val bodyBytes: ByteArray,
    val traceId: String,
    val durationMs: Long,
) {
    val bodyString: String get() = bodyBytes.decodeToString()

    val isSuccessful: Boolean get() = statusCode in 200..299

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuntimeNetworkResponse) return false
        return statusCode == other.statusCode &&
            headers == other.headers &&
            bodyBytes.contentEquals(other.bodyBytes) &&
            traceId == other.traceId
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + bodyBytes.contentHashCode()
        result = 31 * result + traceId.hashCode()
        return result
    }
}

/**
 * Trace context carried through a runtime network request for observability.
 */
data class RuntimeTraceContext(
    val traceId: String = UUID.randomUUID().toString().take(12),
    val requestId: String = "",
    val parentCapability: String = "",
)

/**
 * Unified error model for all runtime network failures.
 * Allows answering: "is it DNS, connect, read, TLS, HTTP, or protocol?"
 */
sealed class RuntimeNetworkFailure(
    open val summary: String,
    open val cause: Throwable? = null,
) {
    data class Dns(
        val host: String,
        val causeMessage: String?,
        override val cause: Throwable? = null,
    ) : RuntimeNetworkFailure("DNS resolution failed for $host: ${causeMessage.orEmpty()}", cause)

    data class ConnectTimeout(
        val url: String,
        override val cause: Throwable? = null,
    ) : RuntimeNetworkFailure("Connect timeout: $url", cause)

    data class ReadTimeout(
        val url: String,
        override val cause: Throwable? = null,
    ) : RuntimeNetworkFailure("Read timeout: $url", cause)

    data class Tls(
        val url: String,
        val causeMessage: String?,
        override val cause: Throwable? = null,
    ) : RuntimeNetworkFailure("TLS error for $url: ${causeMessage.orEmpty()}", cause)

    data class Http(
        val statusCode: Int,
        val url: String,
        val bodyPreview: String,
    ) : RuntimeNetworkFailure("HTTP $statusCode from $url: ${bodyPreview.take(200)}")

    data class Protocol(
        override val summary: String,
        override val cause: Throwable? = null,
    ) : RuntimeNetworkFailure(summary, cause)

    data class Cancelled(
        val reason: String?,
    ) : RuntimeNetworkFailure("Cancelled: ${reason.orEmpty()}")

    data class Unknown(
        val causeMessage: String?,
        override val cause: Throwable? = null,
    ) : RuntimeNetworkFailure("Unknown network failure: ${causeMessage.orEmpty()}", cause)
}
