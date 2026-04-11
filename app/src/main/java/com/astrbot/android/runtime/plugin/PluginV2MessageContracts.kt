package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.chat.MessageType
import java.util.LinkedHashMap

enum class PluginMessageStage {
    AdapterMessage,
    Command,
    Regex,
}

typealias AllowedValue = Any?

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

    fun stopPropagation() {
        propagationStopped.stop()
    }
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
