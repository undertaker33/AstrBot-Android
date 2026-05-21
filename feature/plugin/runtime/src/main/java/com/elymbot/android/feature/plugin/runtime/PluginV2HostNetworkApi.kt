package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.core.runtime.network.RuntimeNetworkCapability
import com.elymbot.android.core.runtime.network.RuntimeNetworkException
import com.elymbot.android.core.runtime.network.RuntimeNetworkFailure
import com.elymbot.android.core.runtime.network.RuntimeNetworkRequest
import com.elymbot.android.core.runtime.network.RuntimeNetworkResponse
import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import com.elymbot.android.core.runtime.network.RuntimeTimeoutProfile
import com.elymbot.android.core.runtime.network.RuntimeTraceContext
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class PluginV2HostNetworkRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val bodyText: String? = null,
    val bodyBase64: String? = null,
    val timeoutMs: Long? = null,
)

data class PluginV2HostNetworkResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val bodyText: String?,
    val bodyBase64: String?,
    val contentType: String,
    val elapsedMs: Long,
)

class PluginV2HostNetworkApi(
    private val facade: PluginV2HostApiFacade,
    private val transport: RuntimeNetworkTransport,
    private val domainAllowlistProvider: (PluginV2HostApiRequestContext) -> Set<String> = {
        it.networkAllowedDomains
    },
    private val maxConcurrentRequestsPerPlugin: Int = DEFAULT_MAX_CONCURRENT_REQUESTS_PER_PLUGIN,
    private val maxTimeoutMs: Long = DEFAULT_MAX_TIMEOUT_MS,
    private val responseBodyLimitBytes: Int = DEFAULT_RESPONSE_BODY_LIMIT_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val pluginSemaphores = ConcurrentHashMap<String, Semaphore>()

    suspend fun fetch(
        context: PluginV2HostApiRequestContext,
        request: PluginV2HostNetworkRequest,
    ): PluginV2HostApiResult {
        val normalizedTimeoutMs = request.timeoutMs
            ?.takeIf { it > 0L }
            ?.coerceAtMost(maxTimeoutMs)
            ?: DEFAULT_TIMEOUT_MS
        return facade.call(
            context = context,
            api = HOST_API_FETCH,
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
            timeoutMs = normalizedTimeoutMs,
        ) {
            semaphoreFor(context.pluginId).withPermit {
                validateTimeoutMs(request.timeoutMs)
                executeNetworkRequest(
                    context = context,
                    request = request,
                    timeoutMs = normalizedTimeoutMs,
                )
            }
        }
    }

    suspend fun request(
        context: PluginV2HostApiRequestContext,
        request: PluginV2HostNetworkRequest,
    ): PluginV2HostApiResult = fetch(context = context, request = request)

    private suspend fun executeNetworkRequest(
        context: PluginV2HostApiRequestContext,
        request: PluginV2HostNetworkRequest,
        timeoutMs: Long,
    ): PluginV2HostNetworkResponse {
        val normalizedUrl = normalizeUrl(
            url = request.url,
            allowedDomains = domainAllowlistProvider(context),
        )
        val normalizedMethod = normalizeMethod(request.method)
        val normalizedHeaders = normalizeHeaders(request.headers)
        val body = normalizeBody(request.bodyText, request.bodyBase64)
        val startedAt = clock()
        val runtimeResponse = try {
            transport.execute(
                RuntimeNetworkRequest(
                    capability = RuntimeNetworkCapability.PLUGIN_HOST_API,
                    method = normalizedMethod,
                    url = normalizedUrl,
                    headers = normalizedHeaders,
                    body = body,
                    contentType = normalizedHeaders.contentType(),
                    timeoutProfile = RuntimeTimeoutProfile.PLUGIN_HOST_API,
                    connectTimeoutMs = timeoutMs,
                    readTimeoutMs = timeoutMs,
                    writeTimeoutMs = timeoutMs,
                    followRedirects = false,
                    traceContext = RuntimeTraceContext(
                        requestId = context.requestId,
                        parentCapability = "plugin_host_api",
                    ),
                ),
            )
        } catch (error: RuntimeNetworkException) {
            throw PluginV2HostApiException(error.toHostApiError())
        }
        if (runtimeResponse.bodyBytes.size > responseBodyLimitBytes) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = NETWORK_RESPONSE_TOO_LARGE,
                    message = "Network response body exceeded the host API size limit.",
                    details = mapOf(
                        "limitBytes" to responseBodyLimitBytes.toString(),
                        "actualBytes" to runtimeResponse.bodyBytes.size.toString(),
                    ),
                ),
            )
        }
        return runtimeResponse.toHostNetworkResponse(
            elapsedMs = runtimeResponse.durationMs.takeIf { it >= 0L }
                ?: (clock() - startedAt).coerceAtLeast(0L),
        )
    }

    private fun semaphoreFor(pluginId: String): Semaphore {
        return pluginSemaphores.getOrPut(pluginId) {
            Semaphore(maxConcurrentRequestsPerPlugin.coerceAtLeast(1))
        }
    }

    private fun normalizeTimeoutMs(rawTimeoutMs: Long?): Long {
        val requested = rawTimeoutMs ?: DEFAULT_TIMEOUT_MS
        if (requested <= 0L) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                    message = "Network timeoutMs must be greater than zero.",
                ),
            )
        }
        return requested.coerceAtMost(maxTimeoutMs)
    }

    private fun validateTimeoutMs(rawTimeoutMs: Long?) {
        normalizeTimeoutMs(rawTimeoutMs)
    }

    private fun normalizeMethod(method: String): String {
        val normalized = method.trim().uppercase(Locale.US).ifBlank { "GET" }
        if (normalized !in ALLOWED_METHODS) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                    message = "Unsupported network request method.",
                    details = mapOf("method" to normalized),
                ),
            )
        }
        return normalized
    }

    private fun normalizeHeaders(headers: Map<String, String>): Map<String, String> {
        return headers.entries.associate { (rawName, rawValue) ->
            val name = rawName.trim()
            val value = rawValue.trim()
            if (name.isBlank() || name.length > MAX_HEADER_NAME_LENGTH || !HEADER_NAME_PATTERN.matches(name)) {
                throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                        message = "Network request header name is invalid.",
                    ),
                )
            }
            if (value.length > MAX_HEADER_VALUE_LENGTH || value.any { it.code < 0x20 && it != '\t' }) {
                throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                        message = "Network request header value is invalid.",
                    ),
                )
            }
            name.lowercase(Locale.US) to value
        }
    }

    private fun normalizeBody(
        bodyText: String?,
        bodyBase64: String?,
    ): ByteArray? {
        val textPresent = bodyText != null
        val base64Present = bodyBase64 != null
        if (textPresent && base64Present) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                    message = "bodyText and bodyBase64 are mutually exclusive.",
                ),
            )
        }
        return when {
            bodyText != null -> bodyText.toByteArray()
            bodyBase64 != null -> runCatching { Base64.getDecoder().decode(bodyBase64) }
                .getOrElse {
                    throw PluginV2HostApiException(
                        PluginV2HostApiError(
                            code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                            message = "bodyBase64 is invalid.",
                        ),
                    )
                }

            else -> null
        }
    }

    private fun normalizeUrl(
        url: String,
        allowedDomains: Set<String>,
    ): String {
        val uri = runCatching { URI(url.trim()) }
            .getOrElse {
                throw blockedUrl("Network request URL is invalid.")
            }
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        if (scheme !in setOf("http", "https")) {
            throw blockedUrl("Only http and https network request URLs are allowed.")
        }
        val rawHost = uri.host?.trim().orEmpty()
        if (rawHost.isBlank()) {
            throw blockedUrl("Network request URL host is required.")
        }
        val host = normalizeHost(rawHost)
        if (isBlockedLocalHost(host)) {
            throw blockedUrl("Network request URL targets a local or private host.", host)
        }
        if (!isAllowedDomain(host, allowedDomains)) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = NETWORK_DOMAIN_NOT_ALLOWED,
                    message = "Network request host is outside the plugin domain allowlist.",
                    details = mapOf("host" to host),
                ),
            )
        }
        return uri.toASCIIString()
    }

    private fun blockedUrl(
        message: String,
        host: String = "",
    ): PluginV2HostApiException {
        return PluginV2HostApiException(
            PluginV2HostApiError(
                code = NETWORK_URL_BLOCKED,
                message = message,
                details = if (host.isBlank()) emptyMap() else mapOf("host" to host),
            ),
        )
    }

    private fun normalizeHost(host: String): String {
        val trimmed = host.trim().trim('[', ']')
        return runCatching { IDN.toASCII(trimmed).lowercase(Locale.US) }
            .getOrDefault(trimmed.lowercase(Locale.US))
            .removeSuffix(".")
    }

    private fun isAllowedDomain(
        host: String,
        allowedDomains: Set<String>,
    ): Boolean {
        if (allowedDomains.isEmpty()) return false
        return allowedDomains
            .map(::normalizeHost)
            .any { allowed ->
                host == allowed || host.endsWith(".$allowed")
            }
    }

    private fun isBlockedLocalHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host == "local") return true
        if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan")) return true
        if (!host.isIpLiteral()) return false
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            (address is Inet4Address && address.isPrivateIpv4()) ||
            (address is Inet6Address && address.isPrivateIpv6())
    }

    private fun Inet4Address.isPrivateIpv4(): Boolean {
        val bytes = address.map { it.toInt() and 0xff }
        return bytes[0] == 10 ||
            (bytes[0] == 172 && bytes[1] in 16..31) ||
            (bytes[0] == 192 && bytes[1] == 168) ||
            (bytes[0] == 169 && bytes[1] == 254) ||
            bytes[0] == 127 ||
            bytes[0] == 0
    }

    private fun Inet6Address.isPrivateIpv6(): Boolean {
        val bytes = address.map { it.toInt() and 0xff }
        return bytes[0] == 0xfe && (bytes[1] and 0xc0) == 0x80 ||
            (bytes[0] and 0xfe) == 0xfc ||
            isLoopbackAddress ||
            isAnyLocalAddress ||
            isLinkLocalAddress ||
            isSiteLocalAddress
    }

    private fun String.isIpLiteral(): Boolean {
        return all { it.isDigit() || it == '.' } || contains(":")
    }

    private fun Map<String, String>.contentType(): String? {
        return this["content-type"] ?: this.entries.firstOrNull { (key, _) ->
            key.equals("content-type", ignoreCase = true)
        }?.value
    }

    private fun RuntimeNetworkResponse.toHostNetworkResponse(elapsedMs: Long): PluginV2HostNetworkResponse {
        val contentType = headers.entries.firstOrNull { (key, _) ->
            key.equals("content-type", ignoreCase = true)
        }?.value?.firstOrNull().orEmpty()
        val textBody = runCatching { bodyBytes.decodeToString() }.getOrDefault("")
        return PluginV2HostNetworkResponse(
            status = statusCode,
            headers = headers,
            bodyText = textBody,
            bodyBase64 = Base64.getEncoder().encodeToString(bodyBytes),
            contentType = contentType,
            elapsedMs = elapsedMs,
        )
    }

    private fun RuntimeNetworkException.toHostApiError(): PluginV2HostApiError {
        return when (failure) {
            is RuntimeNetworkFailure.ConnectTimeout,
            is RuntimeNetworkFailure.ReadTimeout,
            -> PluginV2HostApiError(
                code = PluginV2HostApiErrorCodes.TIMEOUT,
                message = "Network request timed out.",
            )

            is RuntimeNetworkFailure.Cancelled -> PluginV2HostApiError(
                code = PluginV2HostApiErrorCodes.CANCELLED,
                message = "Network request was cancelled.",
            )

            else -> PluginV2HostApiError(
                code = NETWORK_FAILURE,
                message = "Network request failed.",
                details = mapOf("failureType" to failure::class.java.simpleName),
            )
        }
    }

    companion object {
        const val HOST_API_FETCH = "hostApi.fetch"
        const val NETWORK_URL_BLOCKED = "network_url_blocked"
        const val NETWORK_DOMAIN_NOT_ALLOWED = "network_domain_not_allowed"
        const val NETWORK_RESPONSE_TOO_LARGE = "network_response_too_large"
        const val NETWORK_FAILURE = "network_failure"
        const val DEFAULT_RESPONSE_BODY_LIMIT_BYTES: Int = 1_048_576
        const val DEFAULT_MAX_CONCURRENT_REQUESTS_PER_PLUGIN: Int = 4
        const val DEFAULT_TIMEOUT_MS: Long = 10_000L
        const val DEFAULT_MAX_TIMEOUT_MS: Long = 15_000L

        private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
        private val HEADER_NAME_PATTERN = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
        private const val MAX_HEADER_NAME_LENGTH = 128
        private const val MAX_HEADER_VALUE_LENGTH = 8_192
    }
}
