package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.MessageType
import java.util.LinkedHashMap

enum class PluginMessageStage {
    AdapterMessage,
    Command,
    Regex,
}

typealias AllowedValue = Any?

internal const val HOST_SKIP_COMMAND_STAGE_EXTRA_KEY = "__host_skip_command_stage"

sealed interface PluginErrorEventPayload

data class PluginRawPayloadRef(
    val refId: String,
)

internal class MessagePropagationState {
    var stopped: Boolean = false

    fun stop() {
        stopped = true
    }
}

internal data class PluginV2PendingCommandAttachment(
    val source: String,
    val mimeType: String = "",
    val label: String = "",
)

internal data class PluginV2PendingCommandResponse(
    val text: String = "",
    val attachments: List<PluginV2PendingCommandAttachment> = emptyList(),
) {
    init {
        require(text.isNotBlank() || attachments.isNotEmpty()) {
            "command reply must include text or attachments."
        }
    }
}

data class PluginV2CommandResponseAttachment(
    val source: String,
    val mimeType: String = "",
    val label: String = "",
)

data class PluginV2CommandResponse(
    val pluginId: String,
    val extractedDir: String,
    val text: String = "",
    val attachments: List<PluginV2CommandResponseAttachment> = emptyList(),
)

class PluginMessageEvent(
    val eventId: String,
    val platformAdapterType: String,
    val messageType: MessageType,
    val conversationId: String,
    val senderId: String,
    val timestampEpochMillis: Long,
    val rawText: String,
    rawMentions: List<String> = emptyList(),
    val rawPayloadRef: PluginRawPayloadRef? = null,
    initialWorkingText: String = rawText,
    normalizedMentions: List<String> = emptyList(),
    extras: Map<String, AllowedValue> = emptyMap(),
) : PluginErrorEventPayload {
    val stage: PluginMessageStage = PluginMessageStage.AdapterMessage
    internal val propagationStopped: MessagePropagationState = MessagePropagationState()

    val rawMentions: List<String> = rawMentions.toList()

    var workingText: String = initialWorkingText

    var normalizedMentions: List<String> = normalizedMentions.toList()
        set(value) {
            field = value.toList()
        }

    var extras: Map<String, AllowedValue> = PluginV2ValueSanitizer.requireAllowedMap(extras)
        set(value) {
            field = PluginV2ValueSanitizer.requireAllowedMap(value)
        }

    val isPropagationStopped: Boolean
        get() = propagationStopped.stopped

    fun stopPropagation() {
        propagationStopped.stop()
    }
}

class PluginCommandEvent(
    baseEvent: PluginMessageEvent,
    commandPath: List<String>,
    val matchedAlias: String,
    args: List<String>,
    val remainingText: String,
    val invocationText: String,
) : PluginErrorEventPayload {
    val eventId: String = baseEvent.eventId
    val platformAdapterType: String = baseEvent.platformAdapterType
    val messageType: MessageType = baseEvent.messageType
    val conversationId: String = baseEvent.conversationId
    val senderId: String = baseEvent.senderId
    val timestampEpochMillis: Long = baseEvent.timestampEpochMillis
    val rawText: String = baseEvent.rawText
    val rawMentions: List<String> = baseEvent.rawMentions.toList()
    val rawPayloadRef: PluginRawPayloadRef? = baseEvent.rawPayloadRef
    val commandPath: List<String> = commandPath.toList()
    val args: List<String> = args.toList()
    private val workingTextSnapshot: String = baseEvent.workingText
    private val normalizedMentionsSnapshot: List<String> = baseEvent.normalizedMentions.toList()
    private val extrasSnapshot: Map<String, AllowedValue> = PluginV2ValueSanitizer.requireAllowedMap(baseEvent.extras)
    val stage: PluginMessageStage = PluginMessageStage.Command
    val workingText: String
        get() = workingTextSnapshot
    val normalizedMentions: List<String>
        get() = normalizedMentionsSnapshot
    val extras: Map<String, AllowedValue>
        get() = extrasSnapshot
    val isPropagationStopped: Boolean
        get() = propagationStopped.stopped

    internal val propagationStopped: MessagePropagationState = baseEvent.propagationStopped
    internal var pendingCommandReply: PluginV2PendingCommandResponse? = null

    fun stopPropagation() {
        propagationStopped.stop()
    }

    fun replyResult(payload: Any?): Boolean {
        pendingCommandReply = PluginV2CommandReplyParser.parse(payload)
        return true
    }

    fun sendResult(payload: Any?): Boolean = replyResult(payload)

    fun reply(payload: Any?): Boolean = replyResult(payload)

    fun respond(payload: Any?): Boolean = replyResult(payload)

    fun replyText(text: String): Boolean {
        pendingCommandReply = PluginV2PendingCommandResponse(text = text.trim())
        return true
    }

    fun sendText(text: String): Boolean = replyText(text)

    fun respondText(text: String): Boolean = replyText(text)
}

class PluginRegexEvent(
    baseEvent: PluginMessageEvent,
    val patternKey: String,
    val matchedText: String,
    groups: List<String>,
    namedGroups: Map<String, String>,
) : PluginErrorEventPayload {
    val eventId: String = baseEvent.eventId
    val platformAdapterType: String = baseEvent.platformAdapterType
    val messageType: MessageType = baseEvent.messageType
    val conversationId: String = baseEvent.conversationId
    val senderId: String = baseEvent.senderId
    val timestampEpochMillis: Long = baseEvent.timestampEpochMillis
    val rawText: String = baseEvent.rawText
    val rawMentions: List<String> = baseEvent.rawMentions.toList()
    val rawPayloadRef: PluginRawPayloadRef? = baseEvent.rawPayloadRef
    val groups: List<String> = groups.toList()
    val namedGroups: Map<String, String> = namedGroups.toMap()
    private val workingTextSnapshot: String = baseEvent.workingText
    private val normalizedMentionsSnapshot: List<String> = baseEvent.normalizedMentions.toList()
    private val extrasSnapshot: Map<String, AllowedValue> = PluginV2ValueSanitizer.requireAllowedMap(baseEvent.extras)
    val stage: PluginMessageStage = PluginMessageStage.Regex
    val workingText: String
        get() = workingTextSnapshot
    val normalizedMentions: List<String>
        get() = normalizedMentionsSnapshot
    val extras: Map<String, AllowedValue>
        get() = extrasSnapshot
    val isPropagationStopped: Boolean
        get() = propagationStopped.stopped

    internal val propagationStopped: MessagePropagationState = baseEvent.propagationStopped

    fun stopPropagation() {
        propagationStopped.stop()
    }
}

object PluginV2ValueSanitizer {
    fun requireAllowed(value: AllowedValue, path: String = "value"): AllowedValue {
        return sanitizeValue(value, path)
    }

    fun requireAllowedMap(
        values: Map<String, AllowedValue>,
        path: String = "extras",
    ): Map<String, AllowedValue> {
        val sanitized = LinkedHashMap<String, AllowedValue>(values.size)
        values.forEach { (key, value) ->
            sanitized[key] = sanitizeValue(value, "$path['$key']")
        }
        return sanitized
    }

    private fun sanitizeValue(value: AllowedValue, path: String): AllowedValue {
        return when (value) {
            null,
            is String,
            is Boolean,
            is Int,
            is Long,
            is Double,
            -> value

            is List<*> -> value.mapIndexed { index, item ->
                sanitizeValue(item, "$path[$index]")
            }

            is Map<*, *> -> {
                val sanitized = LinkedHashMap<String, AllowedValue>(value.size)
                value.forEach { (key, item) ->
                    require(key is String) {
                        "$path contains a non-string key: ${key?.javaClass?.name ?: "null"}"
                    }
                    sanitized[key] = sanitizeValue(item, "$path['$key']")
                }
                sanitized
            }

            else -> throw IllegalArgumentException(
                "$path contains unsupported value type: ${value::class.java.name}",
            )
        }
    }
}

private object PluginV2CommandReplyParser {
    fun parse(payload: Any?): PluginV2PendingCommandResponse {
        val sanitizedPayload = PluginV2ValueSanitizer.requireAllowed(payload, "commandReply")
        return when (sanitizedPayload) {
            is String -> PluginV2PendingCommandResponse(
                text = sanitizedPayload.trim(),
            )

            is Map<*, *> -> parseMap(sanitizedPayload)

            else -> throw IllegalArgumentException(
                "commandReply must be a string or object payload.",
            )
        }
    }

    private fun parseMap(payload: Map<*, *>): PluginV2PendingCommandResponse {
        val text = (payload["text"] as? String).orEmpty().trim()
        val attachments = parseAttachments(payload["attachments"])
        return PluginV2PendingCommandResponse(
            text = text,
            attachments = attachments,
        )
    }

    private fun parseAttachments(value: Any?): List<PluginV2PendingCommandAttachment> {
        if (value == null) {
            return emptyList()
        }
        require(value is List<*>) {
            "commandReply.attachments must be a list."
        }
        return value.mapIndexed { index, item ->
            require(item is Map<*, *>) {
                "commandReply.attachments[$index] must be an object."
            }
            val source = listOf(
                item["source"] as? String,
                item["uri"] as? String,
                item["assetPath"] as? String,
                item["path"] as? String,
            ).firstOrNull { candidate -> !candidate.isNullOrBlank() }
                ?.trim()
                ?: throw IllegalArgumentException(
                    "commandReply.attachments[$index] requires source/uri/assetPath/path.",
                )
            PluginV2PendingCommandAttachment(
                source = source,
                mimeType = (item["mimeType"] as? String).orEmpty().trim(),
                label = listOf(
                    item["label"] as? String,
                    item["altText"] as? String,
                    item["fileName"] as? String,
                ).firstOrNull { candidate -> !candidate.isNullOrBlank() }
                    ?.trim()
                    .orEmpty(),
            )
        }
    }
}
