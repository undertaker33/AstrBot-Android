package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginExecutionProtocolJson
import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginV2LlmStage
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2LlmContractsTest {
    @Test
    fun protocol_enums_keep_the_four_phase_and_streaming_boundaries() {
        assertEquals(
            listOf("LlmWaiting", "LlmRequest", "LlmResponse", "UsingLlmTool", "ToolExecution", "LlmToolRespond", "ResultDecorating", "AfterMessageSent"),
            PluginV2LlmStage.entries.map { it.name },
        )
        assertEquals(
            listOf("NON_STREAM", "PSEUDO_STREAM", "NATIVE_STREAM"),
            PluginV2StreamingMode.entries.map { it.name },
        )
        assertEquals(
            listOf("LlmWaiting", "LlmRequest", "LlmResponse", "UsingLlmTool", "ToolExecution", "LlmToolRespond", "ResultDecorating", "AfterMessageSent"),
            PluginExecutionStage.entries.map { it.name },
        )
        assertEquals(
            listOf("SYSTEM", "USER", "ASSISTANT", "TOOL"),
            PluginProviderMessageRole.entries.map { it.name },
        )
    }

    @Test
    fun provider_request_exposes_read_only_fields_and_writable_allowlists() {
        val providerIds = mutableListOf("openai", "anthropic")
        val modelIdsByProvider = linkedMapOf(
            "openai" to mutableListOf("gpt-4.1", "gpt-5"),
            "anthropic" to mutableListOf("claude-4"),
        )
        val systemPart = PluginProviderMessagePartDto.TextPart(text = "system prompt")
        val userPart = PluginProviderMessagePartDto.MediaRefPart(
            uri = "content://media/1",
            mimeType = "image/png",
        )

        val request = PluginProviderRequest(
            requestId = "req-1",
            availableProviderIds = providerIds,
            availableModelIdsByProvider = modelIdsByProvider,
            conversationId = "conv-1",
            messageIds = listOf("msg-1", "msg-2"),
            llmInputSnapshot = "hello world",
            selectedProviderId = "openai",
            selectedModelId = "gpt-4.1",
            systemPrompt = "base prompt",
            messages = listOf(
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.SYSTEM,
                    parts = listOf(systemPart),
                    name = "system",
                    metadata = mapOf("nested" to listOf("x", 1, mapOf("flag" to true))),
                ),
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.USER,
                    parts = listOf(userPart),
                    name = "user",
                    metadata = mapOf("source" to "app"),
                ),
            ),
            temperature = 0.7,
            topP = 0.9,
            maxTokens = 1024,
            streamingEnabled = true,
            metadata = mapOf("context" to mapOf("session" to "s-1")),
        )

        assertEquals(
            setOf(
                "requestId",
                "availableProviderIds",
                "availableModelIdsByProvider",
                "conversationId",
                "messageIds",
                "llmInputSnapshot",
                "selectedProviderId",
                "selectedModelId",
                "systemPrompt",
                "messages",
                "temperature",
                "topP",
                "maxTokens",
                "streamingEnabled",
                "metadata",
                "tools",
                "allowHostToolMessages",
                "preservedHostToolMessages",
            ),
            declaredFieldNames(PluginProviderRequest::class.java).toSet(),
        )
        assertNull(PluginProviderRequest::class.java.methods.firstOrNull { it.name == "setRequestId" })
        assertNull(PluginProviderRequest::class.java.methods.firstOrNull { it.name == "setConversationId" })
        assertNull(PluginProviderRequest::class.java.methods.firstOrNull { it.name == "setMessageIds" })
        assertFalse(PluginProviderRequest::class.java.methods.none { it.name == "setSelectedProviderId" })
        assertFalse(PluginProviderRequest::class.java.methods.none { it.name == "setSelectedModelId" })

        providerIds += "new-provider"
        modelIdsByProvider["openai"]?.add("mutated")

        assertEquals(listOf("openai", "anthropic"), request.availableProviderIds)
        assertEquals(listOf("gpt-4.1", "gpt-5"), request.availableModelIdsByProvider.getValue("openai"))
        assertEquals("conv-1", request.conversationId)
        assertEquals(listOf("msg-1", "msg-2"), request.messageIds)
        assertEquals("hello world", request.llmInputSnapshot)
        assertEquals("openai", request.selectedProviderId)
        assertEquals("gpt-4.1", request.selectedModelId)
        assertEquals("base prompt", request.systemPrompt)
        assertEquals(true, request.streamingEnabled)
        assertEquals(mapOf("context" to mapOf("session" to "s-1")), request.metadata)
        assertEquals(listOf(systemPart, userPart), request.messages.flatMap { it.parts })
        assertEquals(listOf("SYSTEM", "USER", "ASSISTANT", "TOOL"), PluginProviderMessageRole.entries.map { it.name })
        assertEquals(listOf("text"), declaredFieldNames(systemPart::class.java))
        assertEquals(listOf("uri", "mimeType"), declaredFieldNames(userPart::class.java))

        request.selectedProviderId = "anthropic"
        request.selectedModelId = "claude-4"
        request.systemPrompt = "updated prompt"
        request.streamingEnabled = false
        request.messages = listOf(
            PluginProviderMessageDto(
                role = PluginProviderMessageRole.ASSISTANT,
                parts = listOf(PluginProviderMessagePartDto.TextPart("assistant")),
                metadata = mapOf("score" to 1),
            ),
        )
        request.temperature = 0.25
        request.topP = 0.6
        request.maxTokens = 2048
        request.metadata = mapOf("retry" to 1, "nested" to listOf("a", mapOf("b" to true)))

        assertEquals("anthropic", request.selectedProviderId)
        assertEquals("claude-4", request.selectedModelId)
        assertEquals("updated prompt", request.systemPrompt)
        assertFalse(request.streamingEnabled)
        assertEquals(0.25, request.temperature ?: Double.NaN, 0.0001)
        assertEquals(0.6, request.topP ?: Double.NaN, 0.0001)
        assertEquals(2048, request.maxTokens)
        assertEquals(mapOf("retry" to 1, "nested" to listOf("a", mapOf("b" to true))), request.metadata)

        val providerFailure = runCatching {
            request.selectedProviderId = "missing-provider"
        }.exceptionOrNull()
        assertTrue(providerFailure is IllegalArgumentException)

        val modelFailure = runCatching {
            request.selectedModelId = "gpt-4.1"
        }.exceptionOrNull()
        assertTrue(modelFailure is IllegalArgumentException)

        val toolMessage = PluginProviderMessageDto(
            role = PluginProviderMessageRole.TOOL,
            parts = listOf(PluginProviderMessagePartDto.TextPart("tool message")),
            name = "web_search",
            metadata = mapOf("__host" to mapOf("toolCallId" to "call-1")),
        )
        // Setter silently strips TOOL messages authored by plugins (only host-preserved ones survive).
        request.messages = listOf(toolMessage)
        assertTrue(request.messages.none { it.role == PluginProviderMessageRole.TOOL })

        val metadataFailure = runCatching {
            request.metadata = mapOf("bad" to File("blocked"))
        }.exceptionOrNull()
        assertTrue(metadataFailure is IllegalArgumentException)
    }

    @Test
    fun usage_snapshot_and_response_share_json_like_whitelist_and_boundaries() {
        val usage = PluginLlmUsageSnapshot(
            promptTokens = 12,
            completionTokens = 7,
            totalTokens = 19,
            inputCostMicros = 1250L,
            outputCostMicros = 2600L,
            currencyCode = "USD",
        )
        assertEquals(12, usage.promptTokens)
        assertEquals(7, usage.completionTokens)
        assertEquals(19, usage.totalTokens)
        assertEquals(1250L, usage.inputCostMicros)
        assertEquals(2600L, usage.outputCostMicros)
        assertEquals("USD", usage.currencyCode)

        val usageFailure = runCatching {
            PluginLlmUsageSnapshot(promptTokens = -1)
        }.exceptionOrNull()
        assertTrue(usageFailure is IllegalArgumentException)

        val response = PluginLlmResponse(
            requestId = "req-1",
            providerId = "openai",
            modelId = "gpt-4.1",
            usage = usage,
            finishReason = "stop",
            text = "final answer",
            markdown = true,
            metadata = mapOf("details" to listOf("a", mapOf("b" to 2))),
        )
        assertEquals("req-1", response.requestId)
        assertEquals("openai", response.providerId)
        assertEquals("gpt-4.1", response.modelId)
        assertEquals(usage, response.usage)
        assertEquals("stop", response.finishReason)
        assertEquals("final answer", response.text)
        assertTrue(response.markdown)
        assertEquals(mapOf("details" to listOf("a", mapOf("b" to 2))), response.metadata)

        response.text = "updated"
        response.markdown = false
        response.metadata = mapOf("retry" to true, "nested" to listOf(1, "two"))

        assertEquals("updated", response.text)
        assertFalse(response.markdown)
        assertEquals(mapOf("retry" to true, "nested" to listOf(1, "two")), response.metadata)

        val responseMetadataFailure = runCatching {
            response.metadata = mapOf("bad" to File("blocked"))
        }.exceptionOrNull()
        assertTrue(responseMetadataFailure is IllegalArgumentException)

        val requestJson = PluginExecutionProtocolJson.encodePluginProviderRequest(
            PluginProviderRequest(
                requestId = "req-json",
                availableProviderIds = listOf("openai"),
                availableModelIdsByProvider = mapOf("openai" to listOf("gpt-4.1")),
                conversationId = "conv-json",
                messageIds = listOf("msg-json"),
                llmInputSnapshot = "snapshot",
                selectedProviderId = "openai",
                selectedModelId = "gpt-4.1",
                messages = listOf(
                    PluginProviderMessageDto(
                        role = PluginProviderMessageRole.SYSTEM,
                        parts = listOf(PluginProviderMessagePartDto.TextPart("hello")),
                    ),
                ),
                metadata = mapOf("allowed" to listOf("yes")),
            ),
        )
        val decodedRequest = PluginExecutionProtocolJson.decodePluginProviderRequest(requestJson)
        assertEquals("req-json", decodedRequest.requestId)
        assertEquals("openai", decodedRequest.selectedProviderId)
        assertEquals("gpt-4.1", decodedRequest.selectedModelId)
        assertEquals("snapshot", decodedRequest.llmInputSnapshot)
        assertEquals("hello", (decodedRequest.messages.single().parts.single() as PluginProviderMessagePartDto.TextPart).text)
    }

    @Test
    fun message_event_result_tracks_stop_send_and_attachment_clear_intent() {
        val result = PluginMessageEventResult(
            requestId = "req-1",
            conversationId = "conv-1",
            text = "base",
            markdown = true,
            attachments = listOf(
                PluginMessageEventResult.Attachment(
                    uri = "content://image/1",
                    mimeType = "image/png",
                ),
            ),
        )

        assertEquals("base", result.text)
        assertTrue(result.markdown)
        assertEquals(1, result.attachments.size)
        assertEquals(PluginMessageEventResult.AttachmentMutationIntent.REPLACED, result.attachmentMutationIntent)
        assertFalse(result.isStopped)
        assertTrue(result.shouldSend)

        result.appendText(" + tail")
        result.replaceText("override")
        result.clearText()
        result.appendAttachment(
            PluginMessageEventResult.Attachment(
                uri = "content://audio/2",
                mimeType = "audio/mpeg",
            ),
        )
        result.clearAttachments()

        assertEquals("", result.text)
        assertTrue(result.attachments.isEmpty())
        assertEquals(PluginMessageEventResult.AttachmentMutationIntent.CLEARED, result.attachmentMutationIntent)

        result.replaceAttachments(emptyList())
        assertTrue(result.attachments.isEmpty())
        assertEquals(PluginMessageEventResult.AttachmentMutationIntent.REPLACED_EMPTY, result.attachmentMutationIntent)

        result.setShouldSend(false)
        assertFalse(result.shouldSend)
        assertFalse(result.isStopped)

        result.stop()
        assertTrue(result.isStopped)
        assertFalse(result.shouldSend)

        val roundTripped = PluginExecutionProtocolJson.decodePluginMessageEventResult(
            PluginExecutionProtocolJson.encodePluginMessageEventResult(result),
        )
        assertEquals(result.markdown, roundTripped.markdown)
        assertEquals(result.attachmentMutationIntent, roundTripped.attachmentMutationIntent)
    }

    @Test
    fun message_event_result_roundtrip_preserves_attachment_mutation_intent_states() {
        val untouchedEmpty = PluginMessageEventResult(
            requestId = "req-empty-1",
            conversationId = "conv-empty-1",
        )
        val replacedEmpty = PluginMessageEventResult(
            requestId = "req-empty-2",
            conversationId = "conv-empty-2",
        ).apply {
            replaceAttachments(emptyList())
        }
        val cleared = PluginMessageEventResult(
            requestId = "req-empty-3",
            conversationId = "conv-empty-3",
            attachments = listOf(
                PluginMessageEventResult.Attachment(
                    uri = "content://image/2",
                    mimeType = "image/png",
                ),
            ),
        ).apply {
            clearAttachments()
        }

        listOf(untouchedEmpty, replacedEmpty, cleared).forEach { original ->
            val roundTripped = PluginExecutionProtocolJson.decodePluginMessageEventResult(
                PluginExecutionProtocolJson.encodePluginMessageEventResult(original),
            )
            assertEquals(original.attachmentMutationIntent, roundTripped.attachmentMutationIntent)
            assertTrue(roundTripped.attachments.isEmpty())
        }
    }

    @Test
    fun after_sent_view_is_read_only_and_copies_snapshot_inputs() {
        val deliveredEntries = mutableListOf(
            PluginV2AfterSentView.DeliveredEntry(
                entryId = "entry-1",
                entryType = "assistant_message",
                textPreview = "hello",
                attachmentCount = 1,
            ),
        )
        val receiptIds = mutableListOf("receipt-1")

        val view = PluginV2AfterSentView(
            requestId = "req-1",
            conversationId = "conv-1",
            sendAttemptId = "send-1",
            platformAdapterType = "onebot",
            platformInstanceKey = "bot-a",
            sentAtEpochMs = 1710000000000L,
            deliveryStatus = PluginV2AfterSentView.DeliveryStatus.SUCCESS,
            receiptIds = receiptIds,
            deliveredEntries = deliveredEntries,
            usage = PluginLlmUsageSnapshot(totalTokens = 42),
        )

        assertEquals(
            setOf(
                "requestId",
                "conversationId",
                "sendAttemptId",
                "platformAdapterType",
                "platformInstanceKey",
                "sentAtEpochMs",
                "deliveryStatus",
                "deliveredEntryCount",
                "receiptIds",
                "deliveredEntries",
                "usage",
            ),
            declaredFieldNames(PluginV2AfterSentView::class.java).toSet(),
        )
        assertNull(PluginV2AfterSentView::class.java.methods.firstOrNull { it.name == "setRequestId" })
        assertNull(PluginV2AfterSentView::class.java.methods.firstOrNull { it.name == "setReceiptIds" })
        assertEquals("req-1", view.requestId)
        assertEquals("conv-1", view.conversationId)
        assertEquals("send-1", view.sendAttemptId)
        assertEquals("onebot", view.platformAdapterType)
        assertEquals("bot-a", view.platformInstanceKey)
        assertEquals(1710000000000L, view.sentAtEpochMs)
        assertEquals(PluginV2AfterSentView.DeliveryStatus.SUCCESS, view.deliveryStatus)
        assertEquals(1, view.deliveredEntryCount)
        assertEquals(listOf("receipt-1"), view.receiptIds)
        assertEquals("entry-1", view.deliveredEntries.single().entryId)
        assertEquals(42, view.usage?.totalTokens)

        receiptIds += "receipt-2"
        deliveredEntries += PluginV2AfterSentView.DeliveredEntry(
            entryId = "entry-2",
            entryType = "assistant_message",
            textPreview = "world",
            attachmentCount = 0,
        )

        assertEquals(listOf("receipt-1"), view.receiptIds)
        assertEquals(1, view.deliveredEntries.size)

        val json = PluginExecutionProtocolJson.encodePluginV2AfterSentView(view)
        val decoded = PluginExecutionProtocolJson.decodePluginV2AfterSentView(json)
        assertEquals(view.requestId, decoded.requestId)
        assertEquals(view.conversationId, decoded.conversationId)
        assertEquals(view.sendAttemptId, decoded.sendAttemptId)
        assertEquals(view.platformAdapterType, decoded.platformAdapterType)
        assertEquals(view.platformInstanceKey, decoded.platformInstanceKey)
        assertEquals(view.sentAtEpochMs, decoded.sentAtEpochMs)
        assertEquals(view.deliveryStatus, decoded.deliveryStatus)
        assertEquals(view.receiptIds, decoded.receiptIds)
        assertEquals(view.deliveredEntries.single().entryId, decoded.deliveredEntries.single().entryId)
        assertEquals(view.usage, decoded.usage)
    }

    @Test
    fun provider_request_carries_tool_definitions_as_read_only_copy() {
        val mutableTools = mutableListOf(
            PluginProviderToolDefinition(
                name = "web_search",
                description = "Search the web",
                inputSchema = mapOf("type" to "object", "properties" to mapOf("query" to mapOf("type" to "string"))),
            ),
        )
        val request = PluginProviderRequest(
            requestId = "req-tools-1",
            availableProviderIds = listOf("openai"),
            availableModelIdsByProvider = mapOf("openai" to listOf("gpt-4.1")),
            conversationId = "conv-1",
            messageIds = listOf("m1"),
            llmInputSnapshot = "search for news",
            selectedProviderId = "openai",
            selectedModelId = "gpt-4.1",
            tools = mutableTools,
        )

        assertEquals(1, request.tools.size)
        assertEquals("web_search", request.tools[0].name)
        assertEquals("Search the web", request.tools[0].description)

        // Mutating the original list must not affect the request.
        mutableTools.add(
            PluginProviderToolDefinition(name = "calc", description = "Calculator", inputSchema = emptyMap()),
        )
        assertEquals(1, request.tools.size)
    }

    @Test
    fun provider_request_defaults_to_empty_tools() {
        val request = PluginProviderRequest(
            requestId = "req-tools-2",
            availableProviderIds = listOf("openai"),
            availableModelIdsByProvider = mapOf("openai" to listOf("gpt-4.1")),
            conversationId = "conv-1",
            messageIds = listOf("m1"),
            llmInputSnapshot = "hello",
            selectedProviderId = "openai",
            selectedModelId = "gpt-4.1",
        )
        assertTrue(request.tools.isEmpty())
    }

    @Test
    fun provider_request_preserves_host_tool_round_order_across_construction_and_reset() {
        val user = PluginProviderMessageDto(
            role = PluginProviderMessageRole.USER,
            parts = listOf(PluginProviderMessagePartDto.TextPart("search")),
        )
        val firstAssistant = PluginProviderMessageDto(
            role = PluginProviderMessageRole.ASSISTANT,
            parts = emptyList(),
            toolCalls = listOf(
                PluginProviderAssistantToolCall(
                    id = "call-1",
                    toolName = "web_search",
                    arguments = mapOf("query" to "异环 开服时间"),
                ),
            ),
        )
        val firstTool = PluginProviderMessageDto(
            role = PluginProviderMessageRole.TOOL,
            parts = listOf(PluginProviderMessagePartDto.TextPart("first result")),
            name = "web_search",
            metadata = mapOf("__host" to mapOf("toolCallId" to "call-1")),
        )
        val secondAssistant = PluginProviderMessageDto(
            role = PluginProviderMessageRole.ASSISTANT,
            parts = emptyList(),
            toolCalls = listOf(
                PluginProviderAssistantToolCall(
                    id = "call-2",
                    toolName = "web_search",
                    arguments = mapOf("query" to "异环游戏 上线时间"),
                ),
            ),
        )
        val secondTool = PluginProviderMessageDto(
            role = PluginProviderMessageRole.TOOL,
            parts = listOf(PluginProviderMessagePartDto.TextPart("second result")),
            name = "web_search",
            metadata = mapOf("__host" to mapOf("toolCallId" to "call-2")),
        )
        val orderedMessages = listOf(user, firstAssistant, firstTool, secondAssistant, secondTool)

        val request = PluginProviderRequest(
            requestId = "req-tool-order",
            availableProviderIds = listOf("openai"),
            availableModelIdsByProvider = mapOf("openai" to listOf("gpt-4.1")),
            conversationId = "conv-tool-order",
            messageIds = listOf("msg-tool-order"),
            llmInputSnapshot = "search",
            selectedProviderId = "openai",
            selectedModelId = "gpt-4.1",
            messages = orderedMessages,
            allowHostToolMessages = true,
        )

        assertEquals(
            listOf(
                PluginProviderMessageRole.USER,
                PluginProviderMessageRole.ASSISTANT,
                PluginProviderMessageRole.TOOL,
                PluginProviderMessageRole.ASSISTANT,
                PluginProviderMessageRole.TOOL,
            ),
            request.messages.map { it.role },
        )
        assertEquals(
            listOf(null, null, "call-1", null, "call-2"),
            request.messages.map { message ->
                ((message.metadata?.get("__host") as? Map<*, *>)?.get("toolCallId") as? String)
            },
        )

        request.messages = orderedMessages

        assertEquals(
            listOf(
                PluginProviderMessageRole.USER,
                PluginProviderMessageRole.ASSISTANT,
                PluginProviderMessageRole.TOOL,
                PluginProviderMessageRole.ASSISTANT,
                PluginProviderMessageRole.TOOL,
            ),
            request.messages.map { it.role },
        )
        assertEquals(
            listOf(null, null, "call-1", null, "call-2"),
            request.messages.map { message ->
                ((message.metadata?.get("__host") as? Map<*, *>)?.get("toolCallId") as? String)
            },
        )
    }

    @Test
    fun llm_response_carries_tool_calls() {
        val response = PluginLlmResponse(
            requestId = "req-tc-1",
            providerId = "openai",
            modelId = "gpt-4.1",
            text = "",
            toolCalls = listOf(
                PluginLlmToolCall(
                    toolName = "web_search",
                    arguments = mapOf("query" to "latest news"),
                ),
            ),
        )
        assertEquals(1, response.toolCalls.size)
        assertEquals("web_search", response.toolCalls[0].toolName)
        assertEquals(mapOf("query" to "latest news"), response.toolCalls[0].arguments)
    }

    private fun declaredFieldNames(type: Class<*>): List<String> {
        return type.declaredFields.map { it.name }.filterNot { it.startsWith("$") }
    }
}
