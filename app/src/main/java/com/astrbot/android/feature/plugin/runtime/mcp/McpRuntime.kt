package com.astrbot.android.feature.plugin.runtime.mcp

import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.feature.plugin.runtime.AllowedValue
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class McpDiscoveredTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, AllowedValue>,
)

data class McpToolCallOutput(
    val text: String,
    val structuredContent: Map<String, AllowedValue>?,
    val isError: Boolean,
)

private data class McpSessionKey(
    val configProfileId: String,
    val serverId: String,
)

data class McpSession(
    val sessionId: String?,
    val messageEndpoint: String,
    /** Streamable HTTP sessions do not use an SSE reader. */
    val sseReader: LegacySseReader? = null,
    val expiresAt: Long,
)

private data class McpRpcEnvelope(
    val result: JSONObject,
    val sessionId: String?,
    val messageEndpoint: String?,
)

internal class McpHttpException(val code: Int, message: String) : Exception(message)

/**
 * Legacy SSE parsing helper retained only for historical reference.
 * The active transport client no longer uses it.
 */
class LegacySseReader(
    private val reader: BufferedReader,
    private val response: okhttp3.Response,
) : Closeable {
    /**
     * Block until an SSE event whose JSON payload matches [predicate] arrives.
     * Returns the parsed JSON object.  Throws on stream end or timeout.
     */
    fun awaitEvent(predicate: (JSONObject) -> Boolean): JSONObject {
        val dataLines = mutableListOf<String>()
        while (true) {
            val rawLine = reader.readLine()
                ?: error("MCP SSE stream closed before matching event arrived")
            val line = rawLine.trimEnd('\r')
            when {
                line.isBlank() -> {
                    if (dataLines.isNotEmpty()) {
                        val data = dataLines.joinToString("\n").trim()
                        dataLines.clear()
                        if (data.isNotBlank()) {
                            runCatching { JSONObject(data) }.getOrNull()?.let { json ->
                                if (predicate(json)) return json
                            }
                        }
                    }
                }
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
            }
        }
    }

    /**
     * Read the first endpoint event from the SSE stream.
     * Returns the message endpoint path or null.
     */
    fun awaitEndpoint(): String? {
        var currentEvent: String? = null
        val dataLines = mutableListOf<String>()
        while (true) {
            val rawLine = reader.readLine()
                ?: error("MCP SSE stream closed before endpoint event")
            val line = rawLine.trimEnd('\r')
            when {
                line.isBlank() -> {
                    if (dataLines.isNotEmpty()) {
                        val data = dataLines.joinToString("\n").trim()
                        dataLines.clear()
                        if (currentEvent == "endpoint" && data.isNotBlank()) {
                            return data
                        }
                        if (data.isNotBlank()) {
                            runCatching { JSONObject(data) }.getOrNull()?.let { json ->
                                json.optString("endpoint").takeIf { it.isNotBlank() }?.let { return it }
                                json.optString("messageEndpoint").takeIf { it.isNotBlank() }?.let { return it }
                            }
                        }
                        currentEvent = null
                    }
                }
                line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
            }
        }
    }

    override fun close() {
        runCatching { reader.close() }
        runCatching { response.close() }
    }
}

class McpTransportClient(
    internal val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    internal val requestIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun initialize(server: McpServerEntry): McpSession = withContext(Dispatchers.IO) {
        when (normalizeTransport(server.transport)) {
            STREAMABLE_HTTP -> initializeStreamableHttp(server)
            else -> throw IllegalArgumentException(
                "Unsupported MCP transport '${server.transport}'. Only streamable_http is supported.",
            )
        }
    }

    suspend fun listTools(server: McpServerEntry, session: McpSession): List<McpDiscoveredTool> =
        withContext(Dispatchers.IO) {
            val result = rpcRequest(server, session, "tools/list", JSONObject(), true).result
            val tools = result.optJSONArray("tools") ?: JSONArray()
            buildList {
                for (index in 0 until tools.length()) {
                    val item = tools.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isBlank()) continue
                    val schemaObject = item.optJSONObject("inputSchema")
                        ?: item.optJSONObject("input_schema")
                        ?: JSONObject().put("type", "object")
                    add(
                        McpDiscoveredTool(
                            name = name,
                            description = item.optString("description").trim(),
                            inputSchema = jsonObjectToAllowedMap(schemaObject),
                        ),
                    )
                }
            }
        }

    suspend fun callTool(
        server: McpServerEntry,
        session: McpSession,
        toolName: String,
        arguments: Map<String, AllowedValue>,
    ): McpToolCallOutput = withContext(Dispatchers.IO) {
        val result = rpcRequest(
            server = server,
            session = session,
            method = "tools/call",
            params = JSONObject().apply {
                put("name", toolName)
                put("arguments", mapToJsonObject(arguments))
            },
            expectResult = true,
        ).result
        val isError = result.optBoolean("isError", result.optBoolean("is_error", false))
        val content = result.optJSONArray("content") ?: JSONArray()
        val text = buildList {
            for (index in 0 until content.length()) {
                when (val item = content.opt(index)) {
                    is JSONObject -> {
                        if (item.optString("type").equals("text", ignoreCase = true)) {
                            item.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    is String -> if (item.isNotBlank()) add(item)
                }
            }
        }.joinToString(separator = "\n").ifBlank { result.toString() }
        val structured = result.optJSONObject("structuredContent")
            ?: result.optJSONObject("structured_content")
        McpToolCallOutput(
            text = text,
            structuredContent = structured?.let(::jsonObjectToAllowedMap),
            isError = isError,
        )
    }

    internal fun deleteSession(session: McpSession) {
        if (session.sessionId.isNullOrBlank() || session.sseReader != null) return
        runCatching {
            val request = Request.Builder()
                .url(session.messageEndpoint)
                .delete()
                .header("Mcp-Session-Id", session.sessionId)
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .build()
            httpClient.newCall(request).execute().close()
        }
    }

    // ── Streamable HTTP ──────────────────────────────────────────────

    private fun initializeStreamableHttp(server: McpServerEntry): McpSession {
        require(server.url.isNotBlank()) { "MCP streamable_http requires a remote url." }
        val initEnvelope = streamableHttpRpc(
            server = server,
            endpoint = server.url,
            sessionId = null,
            method = "initialize",
            params = buildInitializeParams(),
            expectResult = true,
        )
        val session = McpSession(
            sessionId = initEnvelope.sessionId,
            messageEndpoint = initEnvelope.messageEndpoint ?: server.url,
            expiresAt = Long.MAX_VALUE,
        )
        streamableHttpRpc(
            server = server,
            endpoint = session.messageEndpoint,
            sessionId = session.sessionId,
            method = "notifications/initialized",
            params = JSONObject(),
            expectResult = false,
        )
        return session
    }

    private fun streamableHttpRpc(
        server: McpServerEntry,
        endpoint: String,
        sessionId: String?,
        method: String,
        params: JSONObject,
        expectResult: Boolean,
    ): McpRpcEnvelope {
        val requestId = UUID.randomUUID().toString()
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            if (expectResult) put("id", requestId)
            put("params", params)
        }
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
        sessionId?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Mcp-Session-Id", it) }
        applySafeHeaders(requestBuilder, server)

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw McpHttpException(response.code, "MCP HTTP ${response.code}: ${body.take(500)}")
            }
            if (!expectResult && body.isBlank()) {
                return McpRpcEnvelope(
                    result = JSONObject(),
                    sessionId = response.header("Mcp-Session-Id") ?: sessionId,
                    messageEndpoint = response.header("X-MCP-Message-Endpoint")
                        ?.let { resolveEndpoint(server.url, it) },
                )
            }
            val contentType = response.header("Content-Type").orEmpty()
            val rpcResult = if (contentType.contains("text/event-stream", ignoreCase = true)) {
                parseSseBody(body, requestId)
            } else {
                JSONObject(body)
            }
            checkRpcError(rpcResult)
            return McpRpcEnvelope(
                result = rpcResult.optJSONObject("result") ?: rpcResult,
                sessionId = response.header("Mcp-Session-Id") ?: sessionId,
                messageEndpoint = response.header("X-MCP-Message-Endpoint")
                    ?.let { resolveEndpoint(server.url, it) },
            )
        }
    }

    // Legacy SSE helpers are retained for historical compatibility tests only.

    /**
     * Legacy MCP SSE transport:
     * 1. GET server.url with Accept: text/event-stream -> persistent SSE stream.
     * 2. Read endpoint event from SSE to discover message endpoint.
     * 3. POST initialize to message endpoint (may return 202 or JSON).
     * 4. If POST returns 202/empty, await initialize result from SSE stream.
     * 5. POST notifications/initialized.
     * 6. Session holds the SSE reader for subsequent RPC calls.
     */
    private fun initializeLegacySse(server: McpServerEntry): McpSession {
        val sseClient = httpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
        val sseRequest = Request.Builder()
            .url(server.url)
            .get()
            .header("Accept", "text/event-stream")
        applySafeHeaders(sseRequest, server)
        val sseResponse = sseClient.newCall(sseRequest.build()).execute()
        if (!sseResponse.isSuccessful) {
            val code = sseResponse.code
            sseResponse.close()
            throw McpHttpException(code, "MCP SSE discovery failed: HTTP $code")
        }
        val bodySource = sseResponse.body?.byteStream()
            ?: run { sseResponse.close(); error("MCP SSE response has no body") }
        val sseReader = LegacySseReader(
            reader = BufferedReader(InputStreamReader(bodySource, Charsets.UTF_8)),
            response = sseResponse,
        )

        try {
            val endpointPath = sseReader.awaitEndpoint()
                ?: error("MCP SSE stream did not provide endpoint event")
            val messageEndpoint = resolveEndpoint(server.url, endpointPath)
            val headerSessionId = sseResponse.header("Mcp-Session-Id")

            val initRequestId = requestIdGenerator()
            val initPayload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", initRequestId)
                put("method", "initialize")
                put("params", buildInitializeParams())
            }
            val initBuilder = Request.Builder()
                .url(messageEndpoint)
                .post(initPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
            headerSessionId?.let { initBuilder.header("Mcp-Session-Id", it) }
            applySafeHeaders(initBuilder, server)
            val initResponse = httpClient.newCall(initBuilder.build()).execute()
            val initBody = initResponse.body?.string().orEmpty()
            val initSessionId = initResponse.header("Mcp-Session-Id") ?: headerSessionId
            val initSuccess = initResponse.isSuccessful
            val initContentType = initResponse.header("Content-Type").orEmpty()
            initResponse.close()

            if (!initSuccess) {
                throw McpHttpException(initResponse.code, "MCP SSE init POST failed: HTTP ${initResponse.code}")
            }

            val initResult: JSONObject = if (initBody.isNotBlank()
                && !initContentType.contains("text/event-stream", ignoreCase = true)
            ) {
                runCatching { JSONObject(initBody) }.getOrNull()
                    ?.takeIf { it.optString("id") == initRequestId }
                    ?: sseReader.awaitEvent { it.optString("id") == initRequestId }
            } else {
                sseReader.awaitEvent { it.optString("id") == initRequestId }
            }
            checkRpcError(initResult)

            val notifyPayload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
                put("params", JSONObject())
            }
            val notifyBuilder = Request.Builder()
                .url(messageEndpoint)
                .post(notifyPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
            initSessionId?.let { notifyBuilder.header("Mcp-Session-Id", it) }
            applySafeHeaders(notifyBuilder, server)
            httpClient.newCall(notifyBuilder.build()).execute().close()

            return McpSession(
                sessionId = initSessionId,
                messageEndpoint = messageEndpoint,
                sseReader = sseReader,
                expiresAt = Long.MAX_VALUE,
            )
        } catch (e: Exception) {
            sseReader.close()
            throw e
        }
    }

    // ── Unified RPC dispatch ─────────────────────────────────────────

    private fun rpcRequest(
        server: McpServerEntry,
        session: McpSession,
        method: String,
        params: JSONObject,
        expectResult: Boolean,
    ): McpRpcEnvelope {
        if (session.sseReader != null) {
            return legacySseRpc(server, session, method, params, expectResult)
        }
        return streamableHttpRpc(
            server = server,
            endpoint = session.messageEndpoint,
            sessionId = session.sessionId,
            method = method,
            params = params,
            expectResult = expectResult,
        )
    }

    private fun legacySseRpc(
        server: McpServerEntry,
        session: McpSession,
        method: String,
        params: JSONObject,
        expectResult: Boolean,
    ): McpRpcEnvelope {
        val requestId = requestIdGenerator()
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            if (expectResult) put("id", requestId)
            put("params", params)
        }
        val requestBuilder = Request.Builder()
            .url(session.messageEndpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
        session.sessionId?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Mcp-Session-Id", it) }
        applySafeHeaders(requestBuilder, server)

        val postResponse = httpClient.newCall(requestBuilder.build()).execute()
        val postBody = postResponse.body?.string().orEmpty()
        val postSuccess = postResponse.isSuccessful
        postResponse.close()

        if (!postSuccess) {
            throw McpHttpException(postResponse.code, "MCP Legacy SSE POST ${postResponse.code}: ${postBody.take(500)}")
        }

        if (!expectResult) {
            return McpRpcEnvelope(
                result = JSONObject(),
                sessionId = session.sessionId,
                messageEndpoint = null,
            )
        }

        if (postBody.isNotBlank()) {
            runCatching { JSONObject(postBody) }.getOrNull()?.let { json ->
                if (json.optString("id") == requestId) {
                    checkRpcError(json)
                    return McpRpcEnvelope(
                        result = json.optJSONObject("result") ?: json,
                        sessionId = session.sessionId,
                        messageEndpoint = null,
                    )
                }
            }
        }

        val sseReader = session.sseReader
            ?: error("Legacy SSE session has no SSE reader")
        val result = sseReader.awaitEvent { json ->
            json.optString("id") == requestId
        }
        checkRpcError(result)
        return McpRpcEnvelope(
            result = result.optJSONObject("result") ?: result,
            sessionId = session.sessionId,
            messageEndpoint = null,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun parseSseBody(body: String, requestId: String): JSONObject {
        val dataLines = mutableListOf<String>()
        val payloads = mutableListOf<JSONObject>()

        fun flush() {
            if (dataLines.isEmpty()) return
            val data = dataLines.joinToString("\n").trim()
            dataLines.clear()
            if (data.isNotBlank()) {
                runCatching { JSONObject(data) }.getOrNull()?.let { payloads += it }
            }
        }

        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.isBlank() -> flush()
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
            }
        }
        flush()
        return payloads.firstOrNull { p ->
            p.optString("id") == requestId
        } ?: error("MCP SSE response missing matching result for id=$requestId")
    }

    private fun checkRpcError(envelope: JSONObject) {
        val errorObj = envelope.optJSONObject("error") ?: return
        val code = errorObj.optInt("code", -1)
        val message = errorObj.optString("message").ifBlank { "Unknown MCP error" }
        error("MCP RPC error $code: $message")
    }

    private fun resolveEndpoint(baseUrl: String, endpoint: String): String {
        return baseUrl.toHttpUrl().resolve(endpoint)?.toString() ?: endpoint
    }

    private fun buildInitializeParams(): JSONObject {
        return JSONObject().apply {
            put("protocolVersion", PROTOCOL_VERSION)
            put("capabilities", JSONObject().put("tools", JSONObject()))
            put(
                "clientInfo",
                JSONObject().apply {
                    put("name", "ElymBot-Android-Native")
                    put("version", "0.6.3")
                },
            )
        }
    }

    private fun applySafeHeaders(builder: Request.Builder, server: McpServerEntry) {
        server.headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                builder.header(key, value)
            }
        }
    }

    private fun normalizeTransport(transport: String): String {
        return transport.trim().lowercase().replace('-', '_').ifBlank { STREAMABLE_HTTP }
    }

    private companion object {
        const val PROTOCOL_VERSION = "2024-11-05"
        const val STREAMABLE_HTTP = "streamable_http"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class McpSessionManager(
    private val client: McpTransportClient = McpTransportClient(),
    private val ttlMs: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class ToolCacheEntry(
        val cachedAt: Long,
        val tools: List<McpDiscoveredTool>,
    )

    private val sessions = ConcurrentHashMap<McpSessionKey, McpSession>()
    private val toolCache = ConcurrentHashMap<McpSessionKey, ToolCacheEntry>()

    suspend fun discoverTools(configProfileId: String, server: McpServerEntry): List<McpDiscoveredTool> {
        val key = McpSessionKey(configProfileId = configProfileId, serverId = server.serverId)
        val now = clock()
        val cachedTools = toolCache[key]
        if (cachedTools != null && now - cachedTools.cachedAt <= ttlMs) {
            return cachedTools.tools
        }
        return try {
            val session = ensureSession(key, server, now)
            client.listTools(server, session).also { tools ->
                toolCache[key] = ToolCacheEntry(cachedAt = now, tools = tools)
            }
        } catch (e: McpHttpException) {
            closeAndRemoveSession(key)
            if (e.code == 404) {
                val session = ensureSession(key, server, now)
                client.listTools(server, session).also { tools ->
                    toolCache[key] = ToolCacheEntry(cachedAt = now, tools = tools)
                }
            } else throw e
        } catch (e: Exception) {
            closeAndRemoveSession(key)
            throw e
        }
    }

    suspend fun callTool(
        configProfileId: String,
        server: McpServerEntry,
        toolName: String,
        arguments: Map<String, AllowedValue>,
    ): McpToolCallOutput {
        val key = McpSessionKey(configProfileId = configProfileId, serverId = server.serverId)
        val now = clock()
        return try {
            val session = ensureSession(key, server, now)
            client.callTool(server, session, toolName, arguments)
        } catch (e: McpHttpException) {
            closeAndRemoveSession(key)
            if (e.code == 404) {
                val session = ensureSession(key, server, now)
                client.callTool(server, session, toolName, arguments)
            } else throw e
        } catch (e: Exception) {
            closeAndRemoveSession(key)
            throw e
        }
    }

    private suspend fun ensureSession(
        key: McpSessionKey,
        server: McpServerEntry,
        now: Long,
    ): McpSession {
        val cached = sessions[key]
        if (cached != null && now <= cached.expiresAt) {
            return cached
        }
        cached?.sseReader?.runCatching { close() }
        return client.initialize(server).copy(expiresAt = now + ttlMs).also { session ->
            sessions[key] = session
        }
    }

    private fun closeAndRemoveSession(key: McpSessionKey) {
        val session = sessions.remove(key) ?: return
        session.sseReader?.runCatching { close() }
        toolCache.remove(key)
        if (session.sseReader == null && !session.sessionId.isNullOrBlank()) {
            runCatching { client.deleteSession(session) }
        }
    }
}

internal fun mapToJsonObject(values: Map<String, *>): JSONObject {
    return JSONObject().apply {
        values.forEach { (key, value) ->
            put(key, toJsonValue(value))
        }
    }
}

private fun toJsonValue(value: Any?): Any {
    return when (value) {
        null -> JSONObject.NULL
        is JSONObject, is JSONArray, is String, is Boolean,
        is Int, is Long, is Double, is Float,
        -> value
        is Number -> value
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (key, nested) ->
                val stringKey = key as? String ?: return@forEach
                put(stringKey, toJsonValue(nested))
            }
        }
        is Iterable<*> -> JSONArray().apply {
            value.forEach { item -> put(toJsonValue(item)) }
        }
        else -> value.toString()
    }
}

internal fun jsonObjectToAllowedMap(obj: JSONObject): Map<String, AllowedValue> {
    val result = linkedMapOf<String, AllowedValue>()
    obj.keys().forEach { key ->
        result[key] = jsonValueToAllowed(obj.opt(key))
    }
    return result
}

private fun jsonValueToAllowed(value: Any?): AllowedValue {
    return when (value) {
        null, JSONObject.NULL -> null
        is String, is Boolean, is Int, is Long, is Double, is Float -> value
        is Number -> value.toDouble()
        is JSONObject -> jsonObjectToAllowedMap(value)
        is JSONArray -> {
            buildList {
                for (index in 0 until value.length()) {
                    add(jsonValueToAllowed(value.opt(index)))
                }
            }
        }
        else -> value.toString()
    }
}
