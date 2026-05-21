package com.elymbot.android.feature.plugin.runtime

import java.util.LinkedHashMap
import kotlin.coroutines.cancellation.CancellationException

enum class PluginV2MessageStreamPlatformMode {
    Editable,
    Chunked,
    FinalOnClose,
}

data class PluginV2MessageStreamLimits(
    val maxDurationMs: Long = 120_000L,
    val maxChunks: Int = 128,
    val maxBytes: Int = 64 * 1024,
)

data class PluginV2MessageStreamOpenRequest(
    val markdown: Boolean = false,
)

data class PluginV2MessageStreamOpenResult(
    val streamId: String,
    val platformMode: PluginV2MessageStreamPlatformMode,
    val receiptId: String = "",
)

data class PluginV2MessageStreamPortOpenRequest(
    val pluginId: String,
    val requestId: String,
    val conversationId: String,
    val platformAdapterType: String,
    val markdown: Boolean,
)

data class PluginV2MessageStreamPortOpenResult(
    val streamId: String,
    val platformMode: PluginV2MessageStreamPlatformMode,
    val receiptId: String = "",
)

data class PluginV2MessageStreamPortChunkRequest(
    val streamId: String,
    val pluginId: String,
    val requestId: String,
    val conversationId: String,
    val platformAdapterType: String,
    val text: String,
)

data class PluginV2MessageStreamPortCloseRequest(
    val streamId: String,
    val pluginId: String,
    val requestId: String,
    val conversationId: String,
    val platformAdapterType: String,
)

data class PluginV2MessageStreamPortFailRequest(
    val streamId: String,
    val pluginId: String,
    val requestId: String,
    val conversationId: String,
    val platformAdapterType: String,
    val message: String,
)

data class PluginV2MessageStreamPortMutationResult(
    val success: Boolean,
    val errorCode: String = "",
    val errorSummary: String = "",
)

interface PluginV2MessageStreamPort {
    suspend fun open(request: PluginV2MessageStreamPortOpenRequest): PluginV2MessageStreamPortOpenResult
    suspend fun append(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult
    suspend fun replace(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult
    suspend fun close(request: PluginV2MessageStreamPortCloseRequest): PluginV2MessageStreamPortMutationResult
    suspend fun fail(request: PluginV2MessageStreamPortFailRequest): PluginV2MessageStreamPortMutationResult
}

interface PluginV2MessageStreamFinalizer {
    suspend fun closeOpenStreamsForPlugin(
        pluginId: String,
        failureMessage: String,
    )
}

class PluginV2MessageStreamApi(
    private val facade: PluginV2HostApiFacade,
    private val streamPort: PluginV2MessageStreamPort,
    private val limits: PluginV2MessageStreamLimits = PluginV2MessageStreamLimits(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) : PluginV2MessageStreamFinalizer {
    private val streams = LinkedHashMap<String, StreamState>()

    suspend fun openStream(
        context: PluginV2HostApiRequestContext,
        request: PluginV2MessageStreamOpenRequest,
    ): PluginV2HostApiResult {
        return facade.call(
            context = context,
            api = HOST_API_OPEN_STREAM,
            permissionId = PluginV2HostApiPermissions.MESSAGE_STREAM,
            timeoutMs = timeoutMs,
        ) {
            val conversationId = context.conversationId.trim()
            if (conversationId.isBlank()) {
                throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = PluginV2MessageSendApi.MISSING_SESSION_SCOPE,
                        message = "Current conversation scope is required.",
                    ),
                )
            }
            val portResult = streamPort.open(
                PluginV2MessageStreamPortOpenRequest(
                    pluginId = context.pluginId,
                    requestId = context.requestId,
                    conversationId = conversationId,
                    platformAdapterType = context.platformAdapterType,
                    markdown = request.markdown,
                ),
            )
            val state = StreamState(
                streamId = portResult.streamId,
                pluginId = context.pluginId,
                requestId = context.requestId,
                conversationId = conversationId,
                platformAdapterType = context.platformAdapterType,
                openedAtEpochMillis = clock(),
            )
            streams[portResult.streamId] = state
            PluginV2MessageStreamOpenResult(
                streamId = portResult.streamId,
                platformMode = portResult.platformMode,
                receiptId = portResult.receiptId,
            )
        }
    }

    suspend fun append(streamId: String, text: String): PluginV2HostApiResult {
        return mutateChunk(streamId, text, HOST_API_STREAM_APPEND, streamPort::append)
    }

    suspend fun replace(streamId: String, text: String): PluginV2HostApiResult {
        return mutateChunk(streamId, text, HOST_API_STREAM_REPLACE, streamPort::replace)
    }

    suspend fun close(streamId: String): PluginV2HostApiResult {
        val state = streams[streamId] ?: return streamFailure(
            streamId = streamId,
            requestId = "",
            api = HOST_API_STREAM_CLOSE,
            code = UNKNOWN_STREAM,
            message = "Unknown message stream.",
        )
        if (state.closed) {
            return state.failure(HOST_API_STREAM_CLOSE, STREAM_ALREADY_CLOSED)
        }
        val result = runCatching {
            streamPort.close(state.toCloseRequest())
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            PluginV2MessageStreamPortMutationResult(false, "stream_close_failed")
        }
        if (!result.success) {
            return state.failure(HOST_API_STREAM_CLOSE, result.errorCode.ifBlank { "stream_close_failed" })
        }
        state.closed = true
        return state.success(HOST_API_STREAM_CLOSE)
    }

    suspend fun fail(streamId: String, message: String): PluginV2HostApiResult {
        val state = streams[streamId] ?: return streamFailure(
            streamId = streamId,
            requestId = "",
            api = HOST_API_STREAM_FAIL,
            code = UNKNOWN_STREAM,
            message = "Unknown message stream.",
        )
        if (state.closed) {
            return state.failure(HOST_API_STREAM_FAIL, STREAM_ALREADY_CLOSED)
        }
        val result = streamPort.fail(state.toFailRequest(message))
        if (!result.success) {
            return state.failure(HOST_API_STREAM_FAIL, result.errorCode.ifBlank { "stream_fail_failed" })
        }
        state.closed = true
        return state.success(HOST_API_STREAM_FAIL)
    }

    suspend fun closeOpenStreamsForRequest(
        requestId: String,
        failureMessage: String,
    ) {
        streams.values
            .filter { state -> state.requestId == requestId && !state.closed }
            .toList()
            .forEach { state ->
                if (failureMessage.isBlank()) {
                    close(state.streamId)
                } else {
                    fail(state.streamId, failureMessage)
                }
            }
    }

    override suspend fun closeOpenStreamsForPlugin(
        pluginId: String,
        failureMessage: String,
    ) {
        val normalizedPluginId = pluginId.trim()
        if (normalizedPluginId.isBlank()) {
            return
        }
        streams.values
            .filter { state -> state.pluginId == normalizedPluginId && !state.closed }
            .toList()
            .forEach { state ->
                if (failureMessage.isBlank()) {
                    close(state.streamId)
                } else {
                    fail(state.streamId, failureMessage)
                }
            }
    }

    private suspend fun mutateChunk(
        streamId: String,
        text: String,
        api: String,
        operation: suspend (PluginV2MessageStreamPortChunkRequest) -> PluginV2MessageStreamPortMutationResult,
    ): PluginV2HostApiResult {
        val state = streams[streamId] ?: return streamFailure(
            streamId = streamId,
            requestId = "",
            api = api,
            code = UNKNOWN_STREAM,
            message = "Unknown message stream.",
        )
        if (state.closed) {
            return state.failure(api, STREAM_ALREADY_CLOSED)
        }
        val normalizedText = text.takeIf(String::isNotEmpty).orEmpty()
        val nextChunkCount = state.chunkCount + 1
        val nextBytes = state.bytes + normalizedText.toByteArray(Charsets.UTF_8).size
        if (nextChunkCount > limits.maxChunks || nextBytes > limits.maxBytes) {
            return state.failure(api, STREAM_LIMIT_EXCEEDED)
        }
        if (clock() - state.openedAtEpochMillis > limits.maxDurationMs) {
            return state.failure(api, STREAM_LIMIT_EXCEEDED)
        }
        val result = operation(state.toChunkRequest(normalizedText))
        if (!result.success) {
            return state.failure(api, result.errorCode.ifBlank { "stream_mutation_failed" })
        }
        state.chunkCount = nextChunkCount
        state.bytes = nextBytes
        return state.success(api)
    }

    private fun StreamState.success(api: String): PluginV2HostApiResult.Success {
        return PluginV2HostApiResult.Success(
            requestId = requestId,
            api = api,
            value = true,
        )
    }

    private fun StreamState.failure(api: String, code: String): PluginV2HostApiResult.Failure {
        return streamFailure(
            streamId = streamId,
            requestId = requestId,
            api = api,
            code = code,
            message = "Message stream operation failed.",
        )
    }

    private fun streamFailure(
        streamId: String,
        requestId: String,
        api: String,
        code: String,
        message: String,
    ): PluginV2HostApiResult.Failure {
        return PluginV2HostApiResult.Failure(
            requestId = requestId,
            api = api,
            error = PluginV2HostApiError(
                code = code,
                message = message,
                details = mapOf("streamId" to streamId).filterValues(String::isNotBlank),
            ),
        )
    }

    private data class StreamState(
        val streamId: String,
        val pluginId: String,
        val requestId: String,
        val conversationId: String,
        val platformAdapterType: String,
        val openedAtEpochMillis: Long,
        var chunkCount: Int = 0,
        var bytes: Int = 0,
        var closed: Boolean = false,
    ) {
        fun toChunkRequest(text: String): PluginV2MessageStreamPortChunkRequest {
            return PluginV2MessageStreamPortChunkRequest(
                streamId = streamId,
                pluginId = pluginId,
                requestId = requestId,
                conversationId = conversationId,
                platformAdapterType = platformAdapterType,
                text = text,
            )
        }

        fun toCloseRequest(): PluginV2MessageStreamPortCloseRequest {
            return PluginV2MessageStreamPortCloseRequest(
                streamId = streamId,
                pluginId = pluginId,
                requestId = requestId,
                conversationId = conversationId,
                platformAdapterType = platformAdapterType,
            )
        }

        fun toFailRequest(message: String): PluginV2MessageStreamPortFailRequest {
            return PluginV2MessageStreamPortFailRequest(
                streamId = streamId,
                pluginId = pluginId,
                requestId = requestId,
                conversationId = conversationId,
                platformAdapterType = platformAdapterType,
                message = message,
            )
        }
    }

    companion object {
        const val HOST_API_OPEN_STREAM = "hostApi.message.openStream"
        const val HOST_API_STREAM_APPEND = "hostApi.message.stream.append"
        const val HOST_API_STREAM_REPLACE = "hostApi.message.stream.replace"
        const val HOST_API_STREAM_CLOSE = "hostApi.message.stream.close"
        const val HOST_API_STREAM_FAIL = "hostApi.message.stream.fail"
        const val STREAM_LIMIT_EXCEEDED = "stream_limit_exceeded"
        const val STREAM_ALREADY_CLOSED = "stream_already_closed"
        const val UNKNOWN_STREAM = "unknown_stream"
        const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}
