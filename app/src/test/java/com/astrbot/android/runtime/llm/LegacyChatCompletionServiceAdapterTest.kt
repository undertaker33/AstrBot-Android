package com.astrbot.android.runtime.llm

import com.astrbot.android.core.runtime.llm.LlmToolCall
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionServiceLlmInteropTest {

    @Test
    fun tool_definition_conversion_accepts_blank_parameters_as_empty_object() {
        val definition = LlmToolDefinition(
            name = "test_tool",
            description = "A test tool",
            parametersJson = "",
        )
        val legacy = definition.toChatToolDefinitionForTest()
        assertEquals("test_tool", legacy.name)
        assertEquals("A test tool", legacy.description)
        assertEquals("{}", legacy.parameters.toString())
    }

    @Test
    fun tool_definition_conversion_preserves_valid_json_parameters() {
        val json = """{"type":"object","properties":{"query":{"type":"string"}}}"""
        val definition = LlmToolDefinition(
            name = "search",
            description = "Search the web",
            parametersJson = json,
        )
        val legacy = definition.toChatToolDefinitionForTest()
        assertEquals("search", legacy.name)
        assertEquals("Search the web", legacy.description)
        assertTrue(legacy.parameters.has("type"))
        assertEquals("object", legacy.parameters.getString("type"))
    }

    @Test
    fun completion_result_conversion_maps_text_and_empty_tool_calls() {
        val legacyResult = ChatCompletionService.ChatCompletionResult(
            text = "Hello world",
            toolCalls = emptyList(),
        )
        val result = legacyResult.toLlmInvocationResultForTest()
        assertEquals("Hello world", result.text)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals("stop", result.finishReason)
    }

    @Test
    fun completion_result_conversion_maps_tool_calls() {
        val legacyResult = ChatCompletionService.ChatCompletionResult(
            text = "",
            toolCalls = listOf(
                ChatCompletionService.ChatToolCall(
                    id = "call_123",
                    name = "get_weather",
                    arguments = """{"city":"Shanghai"}""",
                ),
            ),
        )
        val result = legacyResult.toLlmInvocationResultForTest()
        assertEquals("", result.text)
        assertEquals(1, result.toolCalls.size)
        assertEquals("tool_calls", result.finishReason)

        val toolCall = result.toolCalls.first()
        assertEquals("call_123", toolCall.id)
        assertEquals("get_weather", toolCall.name)
        assertEquals("""{"city":"Shanghai"}""", toolCall.arguments)
    }

    @Test
    fun completion_result_conversion_maps_multiple_tool_calls() {
        val legacyResult = ChatCompletionService.ChatCompletionResult(
            text = "Let me help",
            toolCalls = listOf(
                ChatCompletionService.ChatToolCall(id = "tc1", name = "tool_a", arguments = "{}"),
                ChatCompletionService.ChatToolCall(id = "tc2", name = "tool_b", arguments = """{"x":1}"""),
            ),
        )
        val result = legacyResult.toLlmInvocationResultForTest()
        assertEquals("Let me help", result.text)
        assertEquals(2, result.toolCalls.size)
        assertEquals("tool_calls", result.finishReason)
        assertEquals("tc1", result.toolCalls[0].id)
        assertEquals("tc2", result.toolCalls[1].id)
    }
}

private fun LlmToolDefinition.toChatToolDefinitionForTest(): ChatCompletionService.ChatToolDefinition {
    val safeParameters = parametersJson.ifBlank { "{}" }
    return ChatCompletionService.ChatToolDefinition(
        name = name,
        description = description,
        parameters = JSONObject(safeParameters),
    )
}

private fun ChatCompletionService.ChatCompletionResult.toLlmInvocationResultForTest(): LlmInvocationResult {
    return LlmInvocationResult(
        text = text,
        toolCalls = toolCalls.map { tc ->
            LlmToolCall(
                id = tc.id,
                name = tc.name,
                arguments = tc.arguments,
            )
        },
        finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop",
    )
}
