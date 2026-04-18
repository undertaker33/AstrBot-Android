package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationToolCall
import com.astrbot.android.feature.plugin.runtime.MessageConverters.toPluginProviderMessages
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageConvertersTest {

    @Test
    fun tool_role_maps_to_TOOL_not_USER() {
        val messages = listOf(
            ConversationMessage(
                id = "tool-1",
                role = "tool",
                content = "result from tool",
                timestamp = 1L,
                toolCallId = "call_abc",
            ),
        )

        val converted = messages.toPluginProviderMessages()

        assertEquals(1, converted.size)
        assertEquals(PluginProviderMessageRole.TOOL, converted[0].role)
        assertEquals("call_abc", converted[0].name)
    }

    @Test
    fun assistant_tool_calls_preserve_tool_call_ids() {
        val messages = listOf(
            ConversationMessage(
                id = "asst-1",
                role = "assistant",
                content = "",
                timestamp = 1L,
                assistantToolCalls = listOf(
                    ConversationToolCall(
                        id = "call_xyz",
                        name = "web_search",
                        arguments = """{"q":"test"}""",
                    ),
                ),
            ),
            ConversationMessage(
                id = "tool-1",
                role = "tool",
                content = "search result",
                timestamp = 2L,
                toolCallId = "call_xyz",
            ),
        )

        val converted = messages.toPluginProviderMessages()

        assertEquals(PluginProviderMessageRole.ASSISTANT, converted[0].role)
        assertEquals(1, converted[0].toolCalls.size)
        assertEquals("call_xyz", converted[0].toolCalls[0].normalizedId)
        assertEquals(PluginProviderMessageRole.TOOL, converted[1].role)
        assertEquals("call_xyz", converted[1].name)
    }
}
