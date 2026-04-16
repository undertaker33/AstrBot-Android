package com.astrbot.android.data

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import org.junit.Assert.assertEquals
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

    @Test(expected = IllegalStateException::class)
    fun parse_openai_response_empty_content_and_no_tool_calls_throws() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":""}}]}
        """.trimIndent()
        ChatCompletionService.parseOpenAiChatCompletionResult(body)
    }
}
