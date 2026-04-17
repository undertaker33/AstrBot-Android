package com.astrbot.android.data

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationToolCall
import com.astrbot.android.data.http.AstrBotHttpClient
import com.astrbot.android.data.http.HttpRequestSpec
import com.astrbot.android.data.http.HttpResponsePayload
import com.astrbot.android.data.http.MultipartPartSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionServiceTest {
    @Test
    fun openai_stream_chunk_with_null_content_produces_empty_delta() {
        val chunk = """
            {"choices":[{"delta":{"content":null}}]}
        """.trimIndent()

        assertEquals("", ChatCompletionService.extractOpenAiStyleStreamingContentForTests(chunk))
    }

    @Test
    fun openai_stream_chunk_with_role_only_produces_empty_delta() {
        val chunk = """
            {"choices":[{"delta":{"role":"assistant"}}]}
        """.trimIndent()

        assertEquals("", ChatCompletionService.extractOpenAiStyleStreamingContentForTests(chunk))
    }

    @Test
    fun openai_stream_chunk_with_text_content_keeps_text_delta() {
        val chunk = """
            {"choices":[{"delta":{"content":"hello"}}]}
        """.trimIndent()

        assertEquals("hello", ChatCompletionService.extractOpenAiStyleStreamingContentForTests(chunk))
    }

    @Test
    fun image_route_requires_explicit_caption_provider_selection() {
        val plan = ChatCompletionService.resolveImageHandlingPlanForTests(
            provider = plainChatProvider(),
            messages = imageMessages(),
            config = ConfigProfile(
                imageCaptionTextEnabled = true,
                defaultVisionProviderId = "",
            ),
            availableProviders = listOf(multimodalCaptionProvider()),
        )

        assertEquals(ChatCompletionService.ImageHandlingMode.STRIP_ATTACHMENTS, plan.mode)
        assertEquals(ChatCompletionService.ImageHandlingReason.CAPTION_PROVIDER_NOT_SELECTED, plan.reason)
    }

    @Test
    fun image_route_uses_selected_caption_provider_when_chat_model_cannot_read_images() {
        val plan = ChatCompletionService.resolveImageHandlingPlanForTests(
            provider = plainChatProvider(),
            messages = imageMessages(),
            config = ConfigProfile(
                imageCaptionTextEnabled = true,
                defaultVisionProviderId = "vision-1",
            ),
            availableProviders = listOf(multimodalCaptionProvider()),
        )

        assertEquals(ChatCompletionService.ImageHandlingMode.CAPTION_TEXT, plan.mode)
        assertEquals(ChatCompletionService.ImageHandlingReason.CAPTION_PROVIDER_SELECTED, plan.reason)
        assertEquals("vision-1", plan.captionProvider?.id)
    }

    @Test
    fun image_route_prefers_direct_multimodal_chat_when_chat_model_supports_images() {
        val plan = ChatCompletionService.resolveImageHandlingPlanForTests(
            provider = multimodalChatProvider(),
            messages = imageMessages(),
            config = ConfigProfile(
                imageCaptionTextEnabled = true,
                defaultVisionProviderId = "vision-1",
            ),
            availableProviders = listOf(multimodalCaptionProvider()),
        )

        assertEquals(ChatCompletionService.ImageHandlingMode.DIRECT_MULTIMODAL, plan.mode)
        assertEquals(ChatCompletionService.ImageHandlingReason.CHAT_PROVIDER_SUPPORTS_IMAGES, plan.reason)
        assertEquals(null, plan.captionProvider)
    }

    private fun plainChatProvider(): ProviderProfile {
        return ProviderProfile(
            id = "chat-1",
            name = "Plain Chat",
            baseUrl = "https://example.com",
            model = "text-only",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.UNSUPPORTED,
            multimodalProbeSupport = FeatureSupportState.UNSUPPORTED,
        )
    }

    private fun multimodalChatProvider(): ProviderProfile {
        return ProviderProfile(
            id = "chat-mm",
            name = "Multimodal Chat",
            baseUrl = "https://example.com",
            model = "gpt-4o",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.SUPPORTED,
            multimodalProbeSupport = FeatureSupportState.SUPPORTED,
        )
    }

    private fun multimodalCaptionProvider(): ProviderProfile {
        return ProviderProfile(
            id = "vision-1",
            name = "Vision Model",
            baseUrl = "https://example.com",
            model = "vision-model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.SUPPORTED,
            multimodalProbeSupport = FeatureSupportState.SUPPORTED,
        )
    }

    private fun imageMessages(): List<ConversationMessage> {
        return listOf(
            ConversationMessage(
                id = "m1",
                role = "user",
                content = "look",
                timestamp = 1L,
                attachments = listOf(
                    ConversationAttachment(
                        id = "a1",
                        type = "image",
                        mimeType = "image/jpeg",
                        fileName = "photo.jpg",
                        base64Data = "abc",
                    ),
                ),
            ),
        )
    }

    // --- F1: OpenAI tool call response parsing ---

    @Test
    fun parse_openai_response_with_text_only() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent()
        val result = ChatCompletionService.parseOpenAiChatCompletionResult(body)
        assertEquals("Hello!", result.text)
        assertEquals(0, result.toolCalls.size)
    }

    @Test
    fun parse_openai_response_with_tool_calls_only() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
                {"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"weather\"}"}}
            ]}}]}
        """.trimIndent()
        val result = ChatCompletionService.parseOpenAiChatCompletionResult(body)
        assertEquals("", result.text)
        assertEquals(1, result.toolCalls.size)
        assertEquals("call_1", result.toolCalls[0].id)
        assertEquals("web_search", result.toolCalls[0].name)
        assertEquals("{\"query\":\"weather\"}", result.toolCalls[0].arguments)
    }

    @Test
    fun parse_openai_response_with_text_and_tool_calls() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"Let me search for that.",
            "tool_calls":[
                {"id":"call_a","type":"function","function":{"name":"get_time","arguments":"{}"}},
                {"id":"call_b","type":"function","function":{"name":"web_search","arguments":"{\"q\":\"news\"}"}}
            ]}}]}
        """.trimIndent()
        val result = ChatCompletionService.parseOpenAiChatCompletionResult(body)
        assertEquals("Let me search for that.", result.text)
        assertEquals(2, result.toolCalls.size)
        assertEquals("get_time", result.toolCalls[0].name)
        assertEquals("web_search", result.toolCalls[1].name)
    }

    @Test
    fun build_openai_payload_preserves_assistant_tool_calls_and_tool_call_id() {
        val payload = ChatCompletionService.buildOpenAiPayloadForTests(
            provider = plainChatProvider(),
            messages = listOf(
                ConversationMessage(
                    id = "assistant-1",
                    role = "assistant",
                    content = "Let me search.",
                    timestamp = 1L,
                    assistantToolCalls = listOf(
                        ConversationToolCall(
                            id = "call_weather",
                            name = "web_search",
                            arguments = "{\"query\":\"weather\"}",
                        ),
                    ),
                ),
                ConversationMessage(
                    id = "tool-1",
                    role = "tool",
                    content = "sunny",
                    timestamp = 2L,
                    toolCallId = "call_weather",
                ),
            ),
            tools = listOf(
                ChatCompletionService.ChatToolDefinition(
                    name = "web_search",
                    description = "search the web",
                    parameters = org.json.JSONObject("{\"type\":\"object\"}"),
                ),
            ),
        )

        val messages = payload.getJSONArray("messages")
        val assistant = messages.getJSONObject(0)
        assertEquals("assistant", assistant.getString("role"))
        assertEquals("Let me search.", assistant.getString("content"))
        val toolCalls = assistant.getJSONArray("tool_calls")
        assertEquals("call_weather", toolCalls.getJSONObject(0).getString("id"))
        assertEquals(
            "web_search",
            toolCalls.getJSONObject(0).getJSONObject("function").getString("name"),
        )

        val tool = messages.getJSONObject(1)
        assertEquals("tool", tool.getString("role"))
        assertEquals("call_weather", tool.getString("tool_call_id"))
        assertEquals("sunny", tool.getString("content"))
        assertTrue(payload.getJSONArray("tools").length() == 1)
    }

    @Test(expected = IllegalStateException::class)
    fun parse_openai_response_empty_content_and_no_tool_calls_throws() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":""}}]}
        """.trimIndent()
        ChatCompletionService.parseOpenAiChatCompletionResult(body)
    }

    @Test
    fun build_openai_payload_assistant_tool_calls_empty_content_uses_empty_string_not_null() {
        val payload = ChatCompletionService.buildOpenAiPayloadForTests(
            provider = plainChatProvider(),
            messages = listOf(
                ConversationMessage(
                    id = "assistant-tc",
                    role = "assistant",
                    content = "",
                    timestamp = 1L,
                    assistantToolCalls = listOf(
                        ConversationToolCall(
                            id = "call_abc",
                            name = "get_weather",
                            arguments = "{}",
                        ),
                    ),
                ),
                ConversationMessage(
                    id = "tool-resp",
                    role = "tool",
                    content = "sunny",
                    timestamp = 2L,
                    toolCallId = "call_abc",
                ),
            ),
            tools = listOf(
                ChatCompletionService.ChatToolDefinition(
                    name = "get_weather",
                    description = "get current weather",
                    parameters = org.json.JSONObject("{\"type\":\"object\"}"),
                ),
            ),
        )
        val messages = payload.getJSONArray("messages")
        val assistant = messages.getJSONObject(0)
        // Must be empty string "", not JSONObject.NULL or literal "null"
        assertEquals("", assistant.getString("content"))
        assertTrue(assistant.has("tool_calls"))
    }

    @Test
    fun configured_chat_with_tools_keeps_web_search_assistant_tool_call_round_after_sanitizing() {
        val captureClient = CapturingHttpClient()
        ChatCompletionService.setHttpClientOverrideForTests(captureClient)
        try {
            ChatCompletionService.sendConfiguredChatWithTools(
                provider = plainChatProvider(),
                messages = listOf(
                    ConversationMessage(
                        id = "user-search",
                        role = "user",
                        content = "search the web",
                        timestamp = 1L,
                    ),
                    ConversationMessage(
                        id = "assistant-tool-call",
                        role = "assistant",
                        content = "",
                        timestamp = 2L,
                        assistantToolCalls = listOf(
                            ConversationToolCall(
                                id = "call_web_search",
                                name = "web_search",
                                arguments = "{\"query\":\"AstrBot\"}",
                            ),
                        ),
                    ),
                    ConversationMessage(
                        id = "tool-result",
                        role = "tool",
                        content = "search result",
                        timestamp = 3L,
                        toolCallId = "call_web_search",
                    ),
                ),
                tools = listOf(
                    ChatCompletionService.ChatToolDefinition(
                        name = "web_search",
                        description = "search the web",
                        parameters = org.json.JSONObject("{\"type\":\"object\"}"),
                    ),
                ),
            )

            val payload = org.json.JSONObject(captureClient.requireBody())
            val messages = payload.getJSONArray("messages")
            assertEquals(3, messages.length())
            assertEquals("user", messages.getJSONObject(0).getString("role"))

            val assistant = messages.getJSONObject(1)
            assertEquals("assistant", assistant.getString("role"))
            assertEquals("", assistant.getString("content"))
            val toolCalls = assistant.getJSONArray("tool_calls")
            assertEquals(1, toolCalls.length())
            val toolCall = toolCalls.getJSONObject(0)
            assertEquals("call_web_search", toolCall.getString("id"))
            assertEquals("web_search", toolCall.getJSONObject("function").getString("name"))

            val tool = messages.getJSONObject(2)
            assertEquals("tool", tool.getString("role"))
            assertEquals("call_web_search", tool.getString("tool_call_id"))
            assertEquals("search result", tool.getString("content"))
        } finally {
            ChatCompletionService.setHttpClientOverrideForTests(null)
        }
    }

    @Test
    fun configured_chat_with_tools_still_removes_plain_empty_assistant_after_sanitizing() {
        val captureClient = CapturingHttpClient()
        ChatCompletionService.setHttpClientOverrideForTests(captureClient)
        try {
            ChatCompletionService.sendConfiguredChatWithTools(
                provider = plainChatProvider(),
                messages = listOf(
                    ConversationMessage(
                        id = "empty-assistant",
                        role = "assistant",
                        content = "",
                        timestamp = 1L,
                    ),
                    ConversationMessage(
                        id = "user",
                        role = "user",
                        content = "hello",
                        timestamp = 2L,
                    ),
                ),
                tools = emptyList(),
            )

            val messages = org.json.JSONObject(captureClient.requireBody()).getJSONArray("messages")
            assertEquals(1, messages.length())
            assertEquals("user", messages.getJSONObject(0).getString("role"))
            assertEquals("hello", messages.getJSONObject(0).getString("content"))
        } finally {
            ChatCompletionService.setHttpClientOverrideForTests(null)
        }
    }

    @Test
    fun configured_chat_with_tools_keeps_empty_tool_result_with_tool_call_id_after_sanitizing() {
        val captureClient = CapturingHttpClient()
        ChatCompletionService.setHttpClientOverrideForTests(captureClient)
        try {
            ChatCompletionService.sendConfiguredChatWithTools(
                provider = plainChatProvider(),
                messages = listOf(
                    ConversationMessage(
                        id = "assistant-tool-call",
                        role = "assistant",
                        content = "",
                        timestamp = 1L,
                        assistantToolCalls = listOf(
                            ConversationToolCall(
                                id = "call_web_search_empty",
                                name = "web_search",
                                arguments = "{\"query\":\"empty\"}",
                            ),
                        ),
                    ),
                    ConversationMessage(
                        id = "tool-empty",
                        role = "tool",
                        content = "",
                        timestamp = 2L,
                        toolCallId = "call_web_search_empty",
                    ),
                ),
                tools = listOf(
                    ChatCompletionService.ChatToolDefinition(
                        name = "web_search",
                        description = "search the web",
                        parameters = org.json.JSONObject("{\"type\":\"object\"}"),
                    ),
                ),
            )

            val messages = org.json.JSONObject(captureClient.requireBody()).getJSONArray("messages")
            assertEquals(2, messages.length())
            val tool = messages.getJSONObject(1)
            assertEquals("tool", tool.getString("role"))
            assertEquals("call_web_search_empty", tool.getString("tool_call_id"))
            assertEquals("", tool.getString("content"))
        } finally {
            ChatCompletionService.setHttpClientOverrideForTests(null)
        }
    }

    @Test
    fun configured_chat_stream_ignores_sse_keep_alive_lines() = runBlocking {
        val captureClient = CapturingHttpClient(
            streamedLines = listOf(
                ": keep-alive",
                """data: {"choices":[{"delta":{"content":"hello"}}]}""",
                "data: [DONE]",
            ),
        )
        ChatCompletionService.setHttpClientOverrideForTests(captureClient)
        try {
            val deltas = mutableListOf<String>()
            val text = ChatCompletionService.sendConfiguredChatStream(
                provider = plainChatProvider(),
                messages = listOf(
                    ConversationMessage(
                        id = "user-stream",
                        role = "user",
                        content = "hello",
                        timestamp = 1L,
                    ),
                ),
                onDelta = { deltas += it },
            )

            assertEquals("hello", text)
            assertEquals(listOf("hello"), deltas)
            assertTrue(captureClient.requireBody().contains("\"stream\":true"))
        } finally {
            ChatCompletionService.setHttpClientOverrideForTests(null)
        }
    }

    @Test
    fun configured_chat_stream_with_tools_ignores_sse_keep_alive_lines() = runBlocking {
        val captureClient = CapturingHttpClient(
            streamedLines = listOf(
                ": keep-alive",
                """data: {"choices":[{"delta":{"content":"tool ok"}}]}""",
                "data: [DONE]",
            ),
        )
        ChatCompletionService.setHttpClientOverrideForTests(captureClient)
        try {
            val deltas = mutableListOf<String>()
            val result = ChatCompletionService.sendConfiguredChatStreamWithTools(
                provider = plainChatProvider(),
                messages = listOf(
                    ConversationMessage(
                        id = "user-tool-stream",
                        role = "user",
                        content = "search the web",
                        timestamp = 1L,
                    ),
                ),
                tools = listOf(
                    ChatCompletionService.ChatToolDefinition(
                        name = "web_search",
                        description = "search the web",
                        parameters = org.json.JSONObject("{\"type\":\"object\"}"),
                    ),
                ),
                onDelta = { deltas += it },
            )

            assertEquals("tool ok", result.text)
            assertEquals(listOf("tool ok"), deltas)
            assertEquals(0, result.toolCalls.size)
            assertTrue(captureClient.requireBody().contains("\"stream\":true"))
        } finally {
            ChatCompletionService.setHttpClientOverrideForTests(null)
        }
    }

    private class CapturingHttpClient(
        private val executeResponseBody: String = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""",
        private val streamedLines: List<String> = emptyList(),
    ) : AstrBotHttpClient {
        private var body: String? = null

        override fun execute(requestSpec: HttpRequestSpec): HttpResponsePayload {
            body = requestSpec.body
            return HttpResponsePayload(
                code = 200,
                body = executeResponseBody,
                headers = emptyMap(),
                url = requestSpec.url,
            )
        }

        override fun executeBytes(requestSpec: HttpRequestSpec): ByteArray = ByteArray(0)

        override suspend fun executeStream(
            requestSpec: HttpRequestSpec,
            onLine: suspend (String) -> Unit,
        ) {
            body = requestSpec.body
            streamedLines.forEach { line ->
                onLine(line)
            }
        }

        override fun executeMultipart(
            requestSpec: HttpRequestSpec,
            parts: List<MultipartPartSpec>,
        ): HttpResponsePayload = execute(requestSpec)

        fun requireBody(): String = requireNotNull(body) { "Expected request body to be captured." }
    }
}
