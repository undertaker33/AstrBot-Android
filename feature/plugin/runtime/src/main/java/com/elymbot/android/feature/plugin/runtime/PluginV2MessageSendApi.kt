package com.elymbot.android.feature.plugin.runtime

import kotlin.coroutines.cancellation.CancellationException

data class PluginV2MessageAttachmentRef(
    val uri: String,
    val mimeType: String,
)

data class PluginV2MessageSendRequest(
    val text: String = "",
    val markdown: Boolean = false,
    val attachments: List<PluginV2MessageAttachmentRef> = emptyList(),
    val conversationId: String = "",
)

data class PluginV2MessageSendResult(
    val conversationId: String,
    val platformAdapterType: String,
    val receiptIds: List<String>,
    val messageLength: Int,
)

data class PluginV2MessageSendPortRequest(
    val pluginId: String,
    val requestId: String,
    val conversationId: String,
    val platformAdapterType: String,
    val text: String,
    val markdown: Boolean,
    val attachments: List<PluginV2MessageAttachmentRef>,
)

data class PluginV2MessageSendPortResult(
    val success: Boolean,
    val receiptIds: List<String> = emptyList(),
    val errorCode: String = "",
    val errorSummary: String = "",
)

fun interface PluginV2MessageSendPort {
    suspend fun send(request: PluginV2MessageSendPortRequest): PluginV2MessageSendPortResult
}

class PluginV2MessageSendApi(
    private val facade: PluginV2HostApiFacade,
    private val sendPort: PluginV2MessageSendPort,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun send(
        context: PluginV2HostApiRequestContext,
        request: PluginV2MessageSendRequest,
    ): PluginV2HostApiResult {
        return facade.call(
            context = context,
            api = HOST_API_MESSAGE_SEND,
            permissionId = PluginV2HostApiPermissions.SEND_MESSAGE,
            timeoutMs = timeoutMs,
        ) {
            val targetConversationId = resolveTargetConversationId(
                contextConversationId = context.conversationId,
                requestedConversationId = request.conversationId,
            )
            val attachments = sanitizeAttachments(request.attachments)
            val text = request.text.trim()
            if (text.isBlank() && attachments.isEmpty()) {
                throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                        message = "Message text or attachment is required.",
                    ),
                )
            }
            val platformAdapterType = context.platformAdapterType.trim()
            val portRequest = PluginV2MessageSendPortRequest(
                pluginId = context.pluginId,
                requestId = context.requestId,
                conversationId = targetConversationId,
                platformAdapterType = platformAdapterType,
                text = text,
                markdown = request.markdown,
                attachments = attachments,
            )
            val portResult = try {
                sendPort.send(portRequest)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                throw messageSendFailed(
                    conversationId = targetConversationId,
                    platformAdapterType = platformAdapterType,
                    failureCode = "",
                )
            }
            if (!portResult.success) {
                throw messageSendFailed(
                    conversationId = targetConversationId,
                    platformAdapterType = platformAdapterType,
                    failureCode = portResult.errorCode,
                )
            }
            PluginV2MessageSendResult(
                conversationId = targetConversationId,
                platformAdapterType = platformAdapterType,
                receiptIds = portResult.receiptIds.map(String::trim).filter(String::isNotBlank),
                messageLength = text.length,
            )
        }
    }

    private fun resolveTargetConversationId(
        contextConversationId: String,
        requestedConversationId: String,
    ): String {
        val current = contextConversationId.trim()
        if (current.isBlank()) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = MISSING_SESSION_SCOPE,
                    message = "Current conversation scope is required.",
                ),
            )
        }
        val requested = requestedConversationId.trim()
        if (requested.isBlank()) return current
        if (requested != current) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = CONVERSATION_SCOPE_VIOLATION,
                    message = "Message send target must match the current conversation scope.",
                    details = mapOf(
                        "currentConversationId" to current,
                        "requestedConversationId" to requested,
                    ),
                ),
            )
        }
        return current
    }

    private fun sanitizeAttachments(
        attachments: List<PluginV2MessageAttachmentRef>,
    ): List<PluginV2MessageAttachmentRef> {
        return attachments.map { attachment ->
            val uri = attachment.uri.trim()
            val mimeType = attachment.mimeType.trim().ifBlank { "application/octet-stream" }
            if (!isAllowedAttachmentUri(uri)) {
                throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = UNSUPPORTED_ATTACHMENT,
                        message = "Only plugin package or workspace attachment URIs are supported.",
                    ),
                )
            }
            PluginV2MessageAttachmentRef(
                uri = uri,
                mimeType = mimeType,
            )
        }
    }

    private fun isAllowedAttachmentUri(uri: String): Boolean {
        return uri.startsWith("plugin://package/") || uri.startsWith("plugin://workspace/")
    }

    private fun messageSendFailed(
        conversationId: String,
        platformAdapterType: String,
        failureCode: String,
    ): PluginV2HostApiException {
        return PluginV2HostApiException(
            PluginV2HostApiError(
                code = MESSAGE_SEND_FAILED,
                message = "Host message send failed.",
                details = linkedMapOf(
                    "conversationId" to conversationId,
                    "platformAdapterType" to platformAdapterType,
                ).also { details ->
                    if (failureCode.isNotBlank()) {
                        details["failureCode"] = failureCode.take(MAX_FAILURE_CODE_LENGTH)
                    }
                },
            ),
        )
    }

    companion object {
        const val HOST_API_MESSAGE_SEND = "hostApi.message.send"
        const val MISSING_SESSION_SCOPE = "missing_session_scope"
        const val CONVERSATION_SCOPE_VIOLATION = "conversation_scope_violation"
        const val UNSUPPORTED_ATTACHMENT = "unsupported_attachment"
        const val MESSAGE_SEND_FAILED = "message_send_failed"
        const val DEFAULT_TIMEOUT_MS = 5_000L

        private const val MAX_FAILURE_CODE_LENGTH = 80
    }
}
