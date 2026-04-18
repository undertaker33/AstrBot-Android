package com.astrbot.android.feature.plugin.runtime

import java.io.File
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2MessageContractsTest {
    @Test
    fun message_contracts_expose_fixed_field_boundaries_and_adapter_writes() {
        assertEquals(
            listOf("AdapterMessage", "Command", "Regex"),
            enumConstantNames("PluginMessageStage"),
        )

        val event = PluginMessageEvent(
            eventId = "evt-1",
            platformAdapterType = "onebot",
            messageType = MessageType.GroupMessage,
            conversationId = "group-42",
            senderId = "user-1",
            timestampEpochMillis = 1710000000000,
            rawText = "  /help  ",
            normalizedMentions = listOf("@astrbot"),
            rawMentions = listOf("@astrbot"),
            rawPayloadRef = PluginRawPayloadRef("raw-1"),
            initialWorkingText = "  /help  ",
            extras = mapOf(
                "flags" to listOf("alpha", 2L, mapOf("nested" to true)),
                "nullable" to null,
            ),
        )

        assertEquals(
            setOf(
                "eventId",
                "platformAdapterType",
                "messageType",
                "conversationId",
                "senderId",
                "timestampEpochMillis",
                "rawText",
                "rawMentions",
                "rawPayloadRef",
                "workingText",
                "normalizedMentions",
                "extras",
                "stage",
                "propagationStopped",
            ),
            declaredFieldNames(event::class.java).toSet(),
        )
        assertNull(event.javaClass.methods.firstOrNull { it.name == "setEventId" })
        assertNull(event.javaClass.methods.firstOrNull { it.name == "setConversationId" })

        assertEquals("evt-1", event.eventId)
        assertEquals("onebot", event.platformAdapterType)
        assertEquals(MessageType.GroupMessage, event.messageType)
        assertEquals("group-42", event.conversationId)
        assertEquals("user-1", event.senderId)
        assertEquals(1710000000000L, event.timestampEpochMillis)
        assertEquals("  /help  ", event.rawText)
        assertEquals(listOf("@astrbot"), event.rawMentions)
        assertEquals(PluginRawPayloadRef("raw-1"), event.rawPayloadRef)
        assertEquals("AdapterMessage", event.stage.name)
        assertEquals("  /help  ", event.workingText)
        assertEquals(listOf("@astrbot"), event.normalizedMentions)
        assertEquals(
            mapOf(
                "flags" to listOf("alpha", 2L, mapOf("nested" to true)),
                "nullable" to null,
            ),
            event.extras,
        )
        assertFalse(event.isPropagationStopped)

        event.workingText = "/help"
        event.normalizedMentions = listOf("@astrbot", "@bot")
        event.extras = mapOf("count" to 3, "enabled" to true)
        event.stopPropagation()

        assertEquals("  /help  ", event.rawText)
        assertEquals(listOf("@astrbot"), event.rawMentions)
        assertEquals("/help", event.workingText)
        assertEquals(listOf("@astrbot", "@bot"), event.normalizedMentions)
        assertEquals(mapOf("count" to 3, "enabled" to true), event.extras)
        assertTrue(event.isPropagationStopped)
    }

    @Test
    fun command_and_regex_events_capture_immutable_snapshots_from_base_event() {
        val baseEvent = PluginMessageEvent(
            eventId = "evt-2",
            platformAdapterType = "qq",
            messageType = MessageType.GroupMessage,
            conversationId = "group-99",
            senderId = "user-2",
            timestampEpochMillis = 1710000000500L,
            rawText = "/astrbot help",
            normalizedMentions = listOf("@astrbot"),
            rawMentions = listOf("@astrbot"),
            rawPayloadRef = PluginRawPayloadRef("raw-2"),
            initialWorkingText = "/astrbot help",
            extras = mapOf("source" to "adapter"),
        )

        val commandEvent = PluginCommandEvent(
            baseEvent = baseEvent,
            commandPath = listOf("astrbot", "help"),
            matchedAlias = "/help",
            args = listOf("status"),
            remainingText = "status",
            invocationText = "/help status",
        )
        val regexEvent = PluginRegexEvent(
            baseEvent = baseEvent,
            patternKey = "help-command",
            matchedText = "/astrbot help",
            groups = listOf("/astrbot", "help"),
            namedGroups = mapOf("command" to "help"),
        )

        assertEquals(
            setOf(
                "eventId",
                "platformAdapterType",
                "messageType",
                "conversationId",
                "senderId",
                "timestampEpochMillis",
                "rawText",
                "rawMentions",
                "rawPayloadRef",
                "commandPath",
                "matchedAlias",
                "args",
                "remainingText",
                "invocationText",
                "workingTextSnapshot",
                "normalizedMentionsSnapshot",
                "extrasSnapshot",
                "pendingCommandReply",
                "stage",
                "propagationStopped",
            ),
            declaredFieldNames(commandEvent::class.java).toSet(),
        )
        assertEquals(
            setOf(
                "eventId",
                "platformAdapterType",
                "messageType",
                "conversationId",
                "senderId",
                "timestampEpochMillis",
                "rawText",
                "rawMentions",
                "rawPayloadRef",
                "patternKey",
                "matchedText",
                "groups",
                "namedGroups",
                "workingTextSnapshot",
                "normalizedMentionsSnapshot",
                "extrasSnapshot",
                "stage",
                "propagationStopped",
            ),
            declaredFieldNames(regexEvent::class.java).toSet(),
        )
        assertFalse(declaredFieldNames(commandEvent::class.java).contains("baseEvent"))
        assertFalse(declaredFieldNames(regexEvent::class.java).contains("baseEvent"))

        assertEquals("Command", commandEvent.stage.name)
        assertEquals("Regex", regexEvent.stage.name)
        assertEquals("evt-2", commandEvent.eventId)
        assertEquals("qq", commandEvent.platformAdapterType)
        assertEquals(MessageType.GroupMessage, commandEvent.messageType)
        assertEquals("group-99", commandEvent.conversationId)
        assertEquals("user-2", commandEvent.senderId)
        assertEquals(1710000000500L, commandEvent.timestampEpochMillis)
        assertEquals("/astrbot help", commandEvent.rawText)
        assertEquals(listOf("@astrbot"), commandEvent.rawMentions)
        assertEquals(PluginRawPayloadRef("raw-2"), commandEvent.rawPayloadRef)
        assertEquals("/astrbot help", commandEvent.workingText)
        assertEquals("/astrbot help", regexEvent.workingText)
        assertEquals(listOf("astrbot", "help"), commandEvent.commandPath)
        assertEquals("/help", commandEvent.matchedAlias)
        assertEquals(listOf("status"), commandEvent.args)
        assertEquals("status", commandEvent.remainingText)
        assertEquals("/help status", commandEvent.invocationText)
        assertEquals("help-command", regexEvent.patternKey)
        assertEquals("/astrbot help", regexEvent.matchedText)
        assertEquals(listOf("/astrbot", "help"), regexEvent.groups)
        assertEquals(mapOf("command" to "help"), regexEvent.namedGroups)

        val commandMethods = PluginCommandEvent::class.java.methods.map { method -> method.name }.toSet()
        assertTrue(commandMethods.contains("replyResult"))
        assertTrue(commandMethods.contains("replyText"))
        assertTrue(commandMethods.contains("sendResult"))
        assertTrue(commandMethods.contains("reply"))
        assertTrue(commandMethods.contains("respond"))
        assertTrue(commandMethods.contains("sendText"))
        assertTrue(commandMethods.contains("respondText"))

        assertFalse(PluginCommandEvent::class.java.methods.any { it.name == "setWorkingText" })
        assertFalse(PluginRegexEvent::class.java.methods.any { it.name == "setWorkingText" })

        baseEvent.workingText = "/astrbot changed"
        baseEvent.normalizedMentions = listOf("@changed")
        baseEvent.extras = mapOf("source" to "mutated")
        commandEvent.stopPropagation()
        assertEquals("/astrbot help", commandEvent.workingText)
        assertEquals(listOf("@astrbot"), commandEvent.normalizedMentions)
        assertEquals(mapOf("source" to "adapter"), commandEvent.extras)
        assertEquals("/astrbot help", regexEvent.workingText)
        assertEquals(listOf("@astrbot"), regexEvent.normalizedMentions)
        assertEquals(mapOf("source" to "adapter"), regexEvent.extras)
        assertEquals("/astrbot changed", baseEvent.workingText)
        assertEquals(listOf("@changed"), baseEvent.normalizedMentions)
        assertEquals(mapOf("source" to "mutated"), baseEvent.extras)
        assertTrue(baseEvent.isPropagationStopped)
        assertTrue(commandEvent.isPropagationStopped)
        assertTrue(regexEvent.isPropagationStopped)

        regexEvent.stopPropagation()
        assertTrue(baseEvent.isPropagationStopped)
        assertTrue(commandEvent.isPropagationStopped)
        assertTrue(regexEvent.isPropagationStopped)
    }

    @Test
    fun extras_sanitizer_accepts_json_like_values_and_rejects_host_objects() {
        val accepted = mapOf(
            "nullValue" to null,
            "text" to "alpha",
            "enabled" to true,
            "count" to 3,
            "largeCount" to 4L,
            "ratio" to 0.25,
            "nestedList" to listOf("x", 1, mapOf("y" to false)),
        )

        assertEquals(accepted, PluginV2ValueSanitizer.requireAllowedMap(accepted))

        val rejectedHostObject = runCatching {
            PluginV2ValueSanitizer.requireAllowed(File("blocked"))
        }.exceptionOrNull()
        assertTrue(rejectedHostObject is IllegalArgumentException)

        val rejectedLambda = runCatching {
            val callback: Any = { _: Int -> Unit }
            PluginV2ValueSanitizer.requireAllowed(callback)
        }.exceptionOrNull()
        assertTrue(rejectedLambda is IllegalArgumentException)
    }

    @Test
    fun lifecycle_contracts_keep_minimal_baseline_and_error_hook_boundary() {
        assertEquals(
            listOf(
                "on_astrbot_loaded",
                "on_platform_loaded",
                "on_plugin_loaded",
                "on_plugin_unloaded",
                "on_plugin_error",
            ),
            PluginLifecycleHookSurface.entries.map { it.wireValue },
        )
        assertEquals(
            setOf("pluginName", "pluginVersion"),
            pluginFieldNames(PluginLifecycleMetadata::class.java).toSet(),
        )
        assertEquals(
            listOf("event", "plugin_name", "handler_name", "error", "traceback_text"),
            pluginFieldNames(PluginErrorHookArgs::class.java),
        )

        val lifecycleMetadata = PluginLifecycleMetadata(
            pluginName = "com.example.lifecycle",
            pluginVersion = "1.0.0",
        )
        val messageEvent = PluginMessageEvent(
            eventId = "evt-3",
            platformAdapterType = "internal",
            messageType = MessageType.OtherMessage,
            conversationId = "conversation-3",
            senderId = "user-3",
            timestampEpochMillis = 1710000001234L,
            rawText = "hello",
            normalizedMentions = emptyList(),
            rawMentions = emptyList(),
            rawPayloadRef = PluginRawPayloadRef("raw-payload-ref"),
            initialWorkingText = "hello",
            extras = emptyMap(),
        )
        val errorArgs = PluginErrorHookArgs(
            event = lifecycleMetadata,
            plugin_name = "com.example.lifecycle",
            handler_name = "on_plugin_loaded",
            error = IllegalStateException("boom"),
            traceback_text = "stacktrace",
        )
        val messageErrorArgs = PluginErrorHookArgs(
            event = messageEvent,
            plugin_name = "com.example.lifecycle",
            handler_name = "on_message",
            error = IllegalStateException("boom"),
            traceback_text = "stacktrace",
        )

        assertEquals(lifecycleMetadata, errorArgs.event)
        assertEquals(messageEvent, messageErrorArgs.event)
    }

    private fun pluginFieldNames(type: Class<*>): List<String> {
        return type.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
    }

    private fun declaredFieldNames(type: Class<*>): List<String> {
        return type.declaredFields.map { it.name }.filterNot { it.startsWith("$") }
    }
}
