package com.astrbot.android.feature.plugin.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2EventResultCoordinatorTest {
    @Test
    fun runDecorating_applies_batches_atomically_and_sorts_by_priority_then_handler_id() = runBlocking {
        val coordinator = PluginV2EventResultCoordinator()
        val executionOrder = mutableListOf<String>()

        val result = coordinator.runDecorating(
            initialResult = PluginMessageEventResult(
                requestId = "req-1",
                conversationId = "conv-1",
            ),
            handlers = listOf(
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "b-handler",
                    priority = 10,
                ),
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "a-handler",
                    priority = 20,
                ),
            ),
        ) { handler, eventResult ->
            executionOrder += handler.handlerId
            when (handler.handlerId) {
                "a-handler" -> {
                    eventResult.replaceText("alpha")
                    eventResult.appendText("-beta")
                    eventResult.clearText()
                    eventResult.appendText("final text")

                    eventResult.replaceAttachments(
                        listOf(
                            PluginMessageEventResult.Attachment(
                                uri = "content://image/1",
                                mimeType = "image/png",
                            ),
                        ),
                    )
                    eventResult.appendAttachment(
                        PluginMessageEventResult.Attachment(
                            uri = "content://audio/2",
                            mimeType = "audio/mpeg",
                        ),
                    )
                    eventResult.clearAttachments()
                    eventResult.setShouldSend(false)
                }

                "b-handler" -> {
                    assertEquals("final text", eventResult.text)
                    assertTrue(eventResult.attachments.isEmpty())
                    assertEquals(PluginMessageEventResult.AttachmentMutationIntent.CLEARED, eventResult.attachmentMutationIntent)
                    assertFalse(eventResult.shouldSend)
                    assertFalse(eventResult.isStopped)

                    eventResult.replaceAttachments(emptyList())
                }
            }
        }

        assertEquals(listOf("a-handler", "b-handler"), executionOrder)
        assertEquals("final text", result.finalResult.text)
        assertTrue(result.finalResult.attachments.isEmpty())
        assertEquals(
            PluginMessageEventResult.AttachmentMutationIntent.REPLACED_EMPTY,
            result.finalResult.attachmentMutationIntent,
        )
        assertFalse(result.finalResult.shouldSend)
        assertFalse(result.finalResult.isStopped)
        assertEquals(listOf("a-handler", "b-handler"), result.appliedHandlerIds)
        assertNull(result.stoppedByHandlerId)
    }

    @Test
    fun runDecorating_breaks_ties_by_handler_id_ascending_when_priorities_match() = runBlocking {
        val coordinator = PluginV2EventResultCoordinator()
        val executionOrder = mutableListOf<String>()

        val result = coordinator.runDecorating(
            initialResult = PluginMessageEventResult(
                requestId = "req-tie",
                conversationId = "conv-tie",
            ),
            handlers = listOf(
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "z-handler",
                    priority = 10,
                ),
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "a-handler",
                    priority = 10,
                ),
            ),
        ) { handler, eventResult ->
            executionOrder += handler.handlerId
            when (handler.handlerId) {
                "a-handler" -> eventResult.appendText("a")
                "z-handler" -> eventResult.appendText("z")
            }
        }

        assertEquals(listOf("a-handler", "z-handler"), executionOrder)
        assertEquals(listOf("a-handler", "z-handler"), result.appliedHandlerIds)
        assertEquals("az", result.finalResult.text)
    }

    @Test
    fun stop_waits_until_current_batch_commits_before_blocking_later_handlers() = runBlocking {
        val coordinator = PluginV2EventResultCoordinator()
        val executionOrder = mutableListOf<String>()

        val result = coordinator.runDecorating(
            initialResult = PluginMessageEventResult(
                requestId = "req-stop",
                conversationId = "conv-stop",
            ),
            handlers = listOf(
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "b-handler",
                    priority = 10,
                ),
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "a-handler",
                    priority = 20,
                ),
            ),
        ) { handler, eventResult ->
            executionOrder += handler.handlerId
            when (handler.handlerId) {
                "a-handler" -> {
                    eventResult.replaceText("before-stop")
                    eventResult.stop()
                    eventResult.appendText(" after-stop")
                }

                "b-handler" -> error("later handler must not run after stop")
            }
        }

        assertEquals(listOf("a-handler"), executionOrder)
        assertEquals(listOf("a-handler"), result.appliedHandlerIds)
        assertEquals("before-stop after-stop", result.finalResult.text)
        assertTrue(result.finalResult.isStopped)
        assertTrue(result.finalResult.shouldSend)
        assertEquals("a-handler", result.stoppedByHandlerId)
    }

    @Test
    fun setShouldSend_false_does_not_imply_stop_or_skip_later_handlers() = runBlocking {
        val coordinator = PluginV2EventResultCoordinator()
        val executionOrder = mutableListOf<String>()

        val result = coordinator.runDecorating(
            initialResult = PluginMessageEventResult(
                requestId = "req-send",
                conversationId = "conv-send",
            ),
            handlers = listOf(
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "b-handler",
                    priority = 10,
                ),
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "a-handler",
                    priority = 20,
                ),
            ),
        ) { handler, eventResult ->
            executionOrder += handler.handlerId
            when (handler.handlerId) {
                "a-handler" -> {
                    eventResult.setShouldSend(false)
                    eventResult.appendText("suppress-send")
                }

                "b-handler" -> {
                    assertFalse(eventResult.shouldSend)
                    assertFalse(eventResult.isStopped)
                    eventResult.appendText("still-runs")
                }
            }
        }

        assertEquals(listOf("a-handler", "b-handler"), executionOrder)
        assertEquals(listOf("a-handler", "b-handler"), result.appliedHandlerIds)
        assertFalse(result.finalResult.shouldSend)
        assertFalse(result.finalResult.isStopped)
        assertEquals("suppress-sendstill-runs", result.finalResult.text)
        assertNull(result.stoppedByHandlerId)
    }

    @Test
    fun shouldSend_uses_last_write_wins_across_handler_batches() = runBlocking {
        val coordinator = PluginV2EventResultCoordinator()
        val executionOrder = mutableListOf<String>()

        val result = coordinator.runDecorating(
            initialResult = PluginMessageEventResult(
                requestId = "req-should-send",
                conversationId = "conv-should-send",
            ),
            handlers = listOf(
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "b-handler",
                    priority = 10,
                ),
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = "a-handler",
                    priority = 20,
                ),
            ),
        ) { handler, eventResult ->
            executionOrder += handler.handlerId
            when (handler.handlerId) {
                "a-handler" -> eventResult.setShouldSend(false)
                "b-handler" -> {
                    assertFalse(eventResult.shouldSend)
                    eventResult.setShouldSend(true)
                }
            }
        }

        assertEquals(listOf("a-handler", "b-handler"), executionOrder)
        assertEquals(listOf("a-handler", "b-handler"), result.appliedHandlerIds)
        assertTrue(result.finalResult.shouldSend)
        assertFalse(result.finalResult.isStopped)
        assertNull(result.stoppedByHandlerId)
    }

    @Test
    fun buildAfterSentView_copies_finalize_snapshot_and_detaches_from_input_lists() {
        val coordinator = PluginV2EventResultCoordinator()
        val receiptIds = mutableListOf("receipt-1")
        val deliveredEntries = mutableListOf(
            PluginV2AfterSentView.DeliveredEntry(
                entryId = "entry-1",
                entryType = "assistant_message",
                textPreview = "hello",
                attachmentCount = 1,
            ),
        )

        val view = coordinator.buildAfterSentView(
            requestId = "req-after",
            conversationId = "conv-after",
            sendAttemptId = "send-1",
            platformAdapterType = "onebot",
            platformInstanceKey = "bot-a",
            sentAtEpochMs = 1710000000000L,
            deliveryStatus = PluginV2AfterSentView.DeliveryStatus.SUCCESS,
            receiptIds = receiptIds,
            deliveredEntries = deliveredEntries,
            usage = PluginLlmUsageSnapshot(totalTokens = 42),
        )

        receiptIds += "receipt-2"
        deliveredEntries += PluginV2AfterSentView.DeliveredEntry(
            entryId = "entry-2",
            entryType = "assistant_message",
            textPreview = "world",
            attachmentCount = 0,
        )

        assertEquals("req-after", view.requestId)
        assertEquals("conv-after", view.conversationId)
        assertEquals("send-1", view.sendAttemptId)
        assertEquals("onebot", view.platformAdapterType)
        assertEquals("bot-a", view.platformInstanceKey)
        assertEquals(1710000000000L, view.sentAtEpochMs)
        assertEquals(PluginV2AfterSentView.DeliveryStatus.SUCCESS, view.deliveryStatus)
        assertEquals(listOf("receipt-1"), view.receiptIds)
        assertEquals(1, view.deliveredEntries.size)
        assertEquals(1, view.deliveredEntryCount)
        assertEquals("entry-1", view.deliveredEntries.single().entryId)
        assertEquals(42, view.usage?.totalTokens)
    }
}
