package com.elymbot.android.feature.plugin.runtime

import kotlin.coroutines.cancellation.CancellationException
import java.util.Locale

data class PluginV2ConversationHistoryRequest(
    val limit: Int = 0,
    val beforeMessageId: String = "",
    val includeAttachments: Boolean = false,
    val conversationId: String = "",
)

data class PluginV2ConversationHistoryResult(
    val conversationId: String,
    val messages: List<PluginV2ConversationHistoryMessage>,
)

data class PluginV2ConversationHistoryMessage(
    val messageId: String,
    val role: String,
    val senderId: String,
    val messageType: String,
    val text: String,
    val timestampEpochMillis: Long,
    val attachmentRefs: List<PluginV2ConversationHistoryAttachmentRef> = emptyList(),
)

data class PluginV2ConversationHistoryAttachmentRef(
    val ref: String = "",
    val uri: String = "",
    val mimeType: String = "",
    val type: String = "",
)

data class PluginV2ConversationHistoryPortRequest(
    val pluginId: String,
    val requestId: String,
    val conversationId: String,
    val limit: Int,
    val beforeMessageId: String,
    val includeAttachments: Boolean,
)

fun interface PluginV2ConversationHistoryReadPort {
    suspend fun history(
        request: PluginV2ConversationHistoryPortRequest,
    ): List<PluginV2ConversationHistoryMessage>
}

class PluginV2ConversationHistoryApi(
    private val facade: PluginV2HostApiFacade,
    private val historyReader: PluginV2ConversationHistoryReadPort,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun history(
        context: PluginV2HostApiRequestContext,
        request: PluginV2ConversationHistoryRequest,
    ): PluginV2HostApiResult {
        return facade.call(
            context = context,
            api = HOST_API_CONVERSATION_HISTORY,
            permissionId = PluginV2HostApiPermissions.CONVERSATION_READ,
            timeoutMs = timeoutMs,
        ) {
            val targetConversationId = resolveTargetConversationId(
                contextConversationId = context.conversationId,
                requestedConversationId = request.conversationId,
            )
            val limit = normalizeLimit(request.limit)
            val beforeMessageId = request.beforeMessageId.trim()
            val rawMessages = try {
                historyReader.history(
                    PluginV2ConversationHistoryPortRequest(
                        pluginId = context.pluginId,
                        requestId = context.requestId,
                        conversationId = targetConversationId,
                        limit = limit,
                        beforeMessageId = beforeMessageId,
                        includeAttachments = request.includeAttachments,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = HISTORY_READ_FAILED,
                        message = "Host conversation history read failed.",
                        details = mapOf("conversationId" to targetConversationId),
                    ),
                )
            }

            val sanitizedMessages = rawMessages
                .sortedByDescending { it.timestampEpochMillis }
                .map { message -> sanitizeMessage(message, includeAttachments = request.includeAttachments) }
                .filter { message -> message.messageId.isNotBlank() }
            val window = applyBeforeMessageId(
                messages = sanitizedMessages,
                beforeMessageId = sanitizeText(beforeMessageId),
            ).take(limit)
            PluginV2ConversationHistoryResult(
                conversationId = targetConversationId,
                messages = window,
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
                    message = "Conversation history target must match the current conversation scope.",
                    details = mapOf(
                        "currentConversationId" to current,
                        "requestedConversationId" to requested,
                    ),
                ),
            )
        }
        return current
    }

    private fun applyBeforeMessageId(
        messages: List<PluginV2ConversationHistoryMessage>,
        beforeMessageId: String,
    ): List<PluginV2ConversationHistoryMessage> {
        if (beforeMessageId.isBlank()) return messages
        val matchedIndex = messages.indexOfFirst { message -> message.messageId == beforeMessageId }
        if (matchedIndex < 0) return emptyList()
        return messages.drop(matchedIndex + 1)
    }

    private fun sanitizeMessage(
        message: PluginV2ConversationHistoryMessage,
        includeAttachments: Boolean,
    ): PluginV2ConversationHistoryMessage {
        return PluginV2ConversationHistoryMessage(
            messageId = sanitizeText(message.messageId),
            role = sanitizeText(message.role),
            senderId = sanitizeText(message.senderId),
            messageType = sanitizeText(message.messageType),
            text = sanitizeText(message.text),
            timestampEpochMillis = message.timestampEpochMillis.coerceAtLeast(0L),
            attachmentRefs = if (includeAttachments) {
                message.attachmentRefs.mapNotNull(::sanitizeAttachmentRef)
            } else {
                emptyList()
            },
        )
    }

    private fun sanitizeAttachmentRef(
        attachmentRef: PluginV2ConversationHistoryAttachmentRef,
    ): PluginV2ConversationHistoryAttachmentRef? {
        val ref = sanitizeText(attachmentRef.ref)
        val uri = sanitizeText(attachmentRef.uri)
        val mimeType = sanitizeText(attachmentRef.mimeType).ifBlank { "application/octet-stream" }
        val type = sanitizeText(attachmentRef.type)
        if (ref.isBlank() && uri.isBlank()) return null
        if (uri.isNotBlank() && !isHostSafeAttachmentUri(uri)) return null
        return PluginV2ConversationHistoryAttachmentRef(
            ref = ref,
            uri = uri,
            mimeType = mimeType,
            type = type,
        )
    }

    private fun isHostSafeAttachmentUri(uri: String): Boolean {
        return uri.startsWith("host://") ||
            uri.startsWith("plugin://package/") ||
            uri.startsWith("plugin://workspace/")
    }

    companion object {
        const val HOST_API_CONVERSATION_HISTORY = "hostApi.conversation.history"
        const val MISSING_SESSION_SCOPE = "missing_session_scope"
        const val CONVERSATION_SCOPE_VIOLATION = "conversation_scope_violation"
        const val HISTORY_READ_FAILED = "conversation_history_read_failed"
        const val DEFAULT_TIMEOUT_MS = 5_000L

        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 100

        private val SENSITIVE_TOKENS = listOf(
            "room",
            "dao",
            "entity",
            "sql",
            "apikey",
            "baseurl",
            "credential",
        )

        private fun normalizeLimit(limit: Int): Int {
            return when {
                limit <= 0 -> DEFAULT_LIMIT
                limit > MAX_LIMIT -> MAX_LIMIT
                else -> limit
            }
        }

        private fun sanitizeText(value: String): String {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return ""
            val normalized = trimmed.lowercase(Locale.US).filter { it.isLetterOrDigit() }
            if (SENSITIVE_TOKENS.any { token -> normalized.contains(token) }) {
                return ""
            }
            return trimmed
        }
    }
}
