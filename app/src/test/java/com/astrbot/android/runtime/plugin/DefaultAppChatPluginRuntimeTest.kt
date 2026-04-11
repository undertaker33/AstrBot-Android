package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.model.chat.ConversationAttachment
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppChatPluginRuntimeTest {
    @Test
    fun default_app_chat_runtime_writes_failures_into_shared_store() {
        val attempts = AtomicInteger(0)
        val sharedStore = InMemoryPluginFailureStateStore()
        val scopedStore = InMemoryPluginScopedFailureStateStore()
        PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(sharedStore)
        PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(scopedStore)
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "shared-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    attempts.incrementAndGet()
                    error("app chat boom")
                },
            )
        }

        try {
            repeat(3) {
                DefaultAppChatPluginRuntime.execute(
                    trigger = PluginTriggerSource.BeforeSendMessage,
                    contextFactory = ::executionContextFor,
                )
            }

            val snapshot = sharedStore.get("shared-plugin")
            assertEquals(3, attempts.get())
            assertEquals(3, snapshot?.consecutiveFailureCount)
            assertEquals("app chat boom", snapshot?.lastErrorSummary)
            assertTrue(snapshot?.isSuspended == true)
        } finally {
            PluginRuntimeRegistry.reset()
            PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(null)
            PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(null)
        }
    }

    @Test
    fun default_app_chat_runtime_does_not_call_context_factory_when_no_legacy_plugins_are_registered() {
        val contextFactoryCalls = AtomicInteger(0)

        val batch = DefaultAppChatPluginRuntime.execute(
            trigger = PluginTriggerSource.BeforeSendMessage,
            contextFactory = { plugin ->
                contextFactoryCalls.incrementAndGet()
                executionContextFor(plugin, PluginTriggerSource.BeforeSendMessage)
            },
        )

        assertEquals(0, contextFactoryCalls.get())
        assertTrue(batch.outcomes.isEmpty())
        assertTrue(batch.skipped.isEmpty())
    }

    @Test
    fun default_app_chat_runtime_delegates_llm_pipeline_to_coordinator_path() = runBlocking {
        val event = PluginMessageEvent(
            eventId = "evt-runtime-llm",
            platformAdapterType = "app_chat",
            messageType = com.astrbot.android.model.chat.MessageType.FriendMessage,
            conversationId = "conv-runtime",
            senderId = "sender-runtime",
            timestampEpochMillis = 123L,
            rawText = "hello runtime",
            initialWorkingText = "hello runtime",
            extras = mapOf("source" to "unit-test"),
        )

        val result = DefaultAppChatPluginRuntime.runLlmPipeline(
            input = PluginV2LlmPipelineInput(
                event = event,
                messageIds = listOf("msg-runtime"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
                availableProviderIds = listOf("provider-a"),
                availableModelIdsByProvider = mapOf("provider-a" to listOf("model-a")),
                selectedProviderId = "provider-a",
                selectedModelId = "model-a",
                messages = listOf(
                    PluginProviderMessageDto(
                        role = PluginProviderMessageRole.USER,
                        parts = listOf(PluginProviderMessagePartDto.TextPart("hello runtime")),
                    ),
                ),
                invokeProvider = { request, _ ->
                    PluginV2ProviderInvocationResult.NonStreaming(
                        response = PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "runtime-response",
                        ),
                    )
                },
            ),
        )

        assertEquals("runtime-response", result.sendableResult.text)
        assertTrue(result.admission.requestId.isNotBlank())
    }

    @Test
    fun default_app_chat_runtime_delivers_admitted_pipeline_with_single_runtime_entrypoint() = runBlocking {
        val event = PluginMessageEvent(
            eventId = "evt-runtime-delivery",
            platformAdapterType = "app_chat",
            messageType = com.astrbot.android.model.chat.MessageType.FriendMessage,
            conversationId = "conv-delivery",
            senderId = "sender-delivery",
            timestampEpochMillis = 456L,
            rawText = "hello delivery",
            initialWorkingText = "hello delivery",
            extras = mapOf("source" to "unit-test"),
        )
        val sendCalls = AtomicInteger(0)
        val persistCalls = AtomicInteger(0)

        val result = DefaultAppChatPluginRuntime.deliverLlmPipeline(
            request = PluginV2HostLlmDeliveryRequest(
                pipelineInput = PluginV2LlmPipelineInput(
                    event = event,
                    messageIds = listOf("msg-delivery"),
                    streamingMode = PluginV2StreamingMode.NON_STREAM,
                    availableProviderIds = listOf("provider-a"),
                    availableModelIdsByProvider = mapOf("provider-a" to listOf("model-a")),
                    selectedProviderId = "provider-a",
                    selectedModelId = "model-a",
                    messages = listOf(
                        PluginProviderMessageDto(
                            role = PluginProviderMessageRole.USER,
                            parts = listOf(PluginProviderMessagePartDto.TextPart("hello delivery")),
                        ),
                    ),
                    invokeProvider = { requestPayload, _ ->
                        PluginV2ProviderInvocationResult.NonStreaming(
                            response = PluginLlmResponse(
                                requestId = requestPayload.requestId,
                                providerId = requestPayload.selectedProviderId,
                                modelId = requestPayload.selectedModelId,
                                text = "delivery-response",
                            ),
                        )
                    },
                ),
                conversationId = "internal-host-session",
                platformAdapterType = "app_chat",
                platformInstanceKey = "android",
                prepareReply = { pipelineResult ->
                    PluginV2HostPreparedReply(
                        text = pipelineResult.sendableResult.text,
                        attachments = listOf(
                            ConversationAttachment(
                                id = "attachment-1",
                                type = "image",
                                mimeType = "image/png",
                                remoteUrl = "https://example.com/image.png",
                            ),
                        ),
                        deliveredEntries = listOf(
                            PluginV2AfterSentView.DeliveredEntry(
                                entryId = "assistant-1",
                                entryType = "assistant",
                                textPreview = pipelineResult.sendableResult.text,
                                attachmentCount = 1,
                            ),
                        ),
                    )
                },
                sendReply = { preparedReply ->
                    sendCalls.incrementAndGet()
                    assertEquals("delivery-response", preparedReply.text)
                    PluginV2HostSendResult(
                        success = true,
                        receiptIds = listOf("receipt-1"),
                    )
                },
                persistDeliveredReply = { preparedReply, _, _ ->
                    persistCalls.incrementAndGet()
                    assertEquals(1, preparedReply.attachments.size)
                },
            ),
        )

        assertEquals(1, sendCalls.get())
        assertEquals(1, persistCalls.get())
        assertEquals("delivery-response", result.pipelineResult.sendableResult.text)
        assertTrue(result is PluginV2HostLlmDeliveryResult.Sent)
        result as PluginV2HostLlmDeliveryResult.Sent
        assertEquals("conv-delivery", result.afterSentView.conversationId)
        assertEquals(listOf("receipt-1"), result.afterSentView.receiptIds)
    }
}
