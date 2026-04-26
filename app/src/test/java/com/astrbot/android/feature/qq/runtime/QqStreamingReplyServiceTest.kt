package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqReplyPayload
import com.astrbot.android.core.runtime.search.WebSearchPromptStringProvider
import com.astrbot.android.core.runtime.search.WebSearchTriggerIntent
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QqStreamingReplyServiceTest {
    @Test
    fun news_reply_uses_bullet_segments_and_bypasses_pseudo_streaming() = runTest {
        val sentPayloads = mutableListOf<QqReplyPayload>()
        val sentTimes = mutableListOf<Long>()
        val service = QqStreamingReplyService(
            replySender = QqReplySender(
                socketSender = {},
                resolveReplyConfig = { null },
                sendOverride = { payload, _ ->
                    sentPayloads += payload
                    sentTimes += currentTime
                    PluginV2HostSendResult(success = true)
                },
            ),
            synthesizeSpeech = { _, _, _, _ -> error("unused") },
            webSearchPromptStrings = TestWebSearchPromptStrings,
        )
        val prepared = PluginV2HostPreparedReply(
            text = listOf(
                "1. ${"A".repeat(260)}",
                "2. ${"B".repeat(260)}",
                "3. ${"C".repeat(260)}",
            ).joinToString("\n"),
        )

        val result = service.sendPreparedReply(
            message = incomingMessage("today news"),
            prepared = prepared,
            config = ConfigProfile(streamingMessageIntervalMs = 0),
            streamingMode = PluginV2StreamingMode.NATIVE_STREAM,
        )

        assertTrue(result.success)
        assertEquals(3, sentPayloads.size)
        assertEquals(listOf(0L, 2_000L, 4_000L), sentTimes)
        assertTrue(sentPayloads.all { it.text.length <= 450 })
        assertTrue(sentPayloads[0].text.startsWith("1."))
        assertTrue(sentPayloads[1].text.startsWith("2."))
        assertTrue(sentPayloads[2].text.startsWith("3."))
    }

    @Test
    fun news_search_tool_result_is_sent_as_facts_and_rewritten_for_commentary_only_followup() = runTest {
        val sentPayloads = mutableListOf<QqReplyPayload>()
        val service = QqStreamingReplyService(
            replySender = QqReplySender(
                socketSender = {},
                resolveReplyConfig = { null },
                sendOverride = { payload, _ ->
                    sentPayloads += payload
                    PluginV2HostSendResult(success = true)
                },
            ),
            synthesizeSpeech = { _, _, _, _ -> error("unused") },
            webSearchPromptStrings = TestWebSearchPromptStrings,
        )
        val original = PluginToolResult(
            toolCallId = "call-1",
            requestId = "req-1",
            toolId = "__host__:web_search",
            status = PluginToolResultStatus.SUCCESS,
            text = "raw search json",
            structuredContent = linkedMapOf(
                "query" to "today news",
                "provider" to "tavily_search",
                "fallbackUsed" to false,
                "results" to listOf(
                    linkedMapOf(
                        "title" to "Major update",
                        "snippet" to "A confirmed event happened today.",
                        "source" to "Example News",
                        "index" to 1,
                    ),
                ),
            ),
        )

        val rewritten = service.deliverNewsSearchResultIfNeeded(
            message = incomingMessage("today news"),
            toolName = "web_search",
            result = original,
        )

        assertEquals(1, sentPayloads.size)
        assertTrue(sentPayloads.single().text.contains("Major update"))
        assertTrue(sentPayloads.single().text.contains("Example News"))
        assertTrue(rewritten.text.orEmpty().contains("Do not repeat the news items"))
        assertTrue(rewritten.text.orEmpty().contains("Only provide a brief evaluation"))
        assertEquals(original.structuredContent, rewritten.structuredContent)
    }

    @Test
    fun news_commentary_followup_uses_regular_streaming_instead_of_news_fact_segmentation() = runTest {
        val sentPayloads = mutableListOf<QqReplyPayload>()
        val service = QqStreamingReplyService(
            replySender = QqReplySender(
                socketSender = {},
                resolveReplyConfig = { null },
                sendOverride = { payload, _ ->
                    sentPayloads += payload
                    PluginV2HostSendResult(success = true)
                },
            ),
            synthesizeSpeech = { _, _, _, _ -> error("unused") },
        )
        val prepared = PluginV2HostPreparedReply(
            text = "I think this is important. It may affect ordinary people.",
            deliveryTags = setOf(QqStreamingReplyService.NEWS_COMMENTARY_FOLLOWUP_TAG),
        )

        val result = service.sendPreparedReply(
            message = incomingMessage("today news"),
            prepared = prepared,
            config = ConfigProfile(streamingMessageIntervalMs = 0),
            streamingMode = PluginV2StreamingMode.NATIVE_STREAM,
        )

        assertTrue(result.success)
        assertTrue(sentPayloads.size >= 2)
        assertTrue(sentPayloads.none { it.text.startsWith("1.") })
    }

    private fun incomingMessage(text: String): IncomingQqMessage {
        return IncomingQqMessage(
            selfId = "bot",
            messageId = "msg-1",
            conversationId = "user-1",
            senderId = "user-1",
            senderName = "User",
            text = text,
            messageType = MessageType.FriendMessage,
            rawPayload = "{}",
        )
    }

    private object TestWebSearchPromptStrings : WebSearchPromptStringProvider {
        override fun guidanceFor(intent: WebSearchTriggerIntent): String? = null

        override fun newsDirectDeliveryCommentary(
            factText: String,
            sent: Boolean,
        ): String {
            return "Do not repeat the news items. Only provide a brief evaluation. " +
                "Sent summary:\n$factText\nsent=$sent"
        }
    }
}
