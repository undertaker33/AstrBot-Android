package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostPreparedReply
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostSendResult
import com.elymbot.android.feature.qq.domain.IncomingQqMessage
import com.elymbot.android.feature.qq.domain.QqReplyPayload
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqStreamingReplyServiceAttachmentStreamingTest {
    @Test
    fun text_streaming_sends_text_segments_before_image_attachments() = runBlocking {
        val sentPayloads = mutableListOf<QqReplyPayload>()
        val service = QqStreamingReplyService(
            replySender = QqReplySender(
                socketSender = {},
                resolveReplyConfig = { null },
                sendOverride = { payload, _ ->
                    sentPayloads += payload
                    PluginV2HostSendResult(
                        success = true,
                        receiptIds = listOf("receipt-${sentPayloads.size}"),
                    )
                },
            ),
            synthesizeSpeech = { _, _, _, _ -> error("unused") },
        )
        val prepared = PluginV2HostPreparedReply(
            text = "第一句来了，这里稍微长一点。第二句继续补充，方便观察分段。第三句收尾。",
            attachments = listOf(
                ConversationAttachment(
                    id = "image-1",
                    type = "image",
                    mimeType = "image/jpeg",
                    remoteUrl = "https://example.invalid/meme.jpg",
                ),
            ),
        )

        val result = service.sendPreparedReply(
            message = incomingMessage("hello stream with image"),
            prepared = prepared,
            config = ConfigProfile(streamingMessageIntervalMs = 0),
            streamingMode = PluginV2StreamingMode.NATIVE_STREAM,
        )

        assertTrue(result.success)
        assertTrue(sentPayloads.size > 1)
        assertTrue(
            sentPayloads.dropLast(1).all { payload ->
                payload.attachments.isEmpty() && payload.text.isNotBlank()
            },
        )
        assertEquals("", sentPayloads.last().text)
        assertEquals(1, sentPayloads.last().attachments.size)
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
}
