package com.astrbot.android.data.db.conversation

import com.astrbot.android.data.db.ConversationAggregate
import com.astrbot.android.data.db.ConversationAttachmentEntity
import com.astrbot.android.data.db.ConversationEntity
import com.astrbot.android.data.db.ConversationMessageAggregate
import com.astrbot.android.data.db.ConversationMessageEntity
import com.astrbot.android.data.db.toConversationSession
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationMappersTest {
    @Test
    fun aggregate_toSession_restoresMessageOrderAndAttachments() {
        val aggregate = ConversationAggregate(
            session = ConversationEntity(
                id = "chat-main",
                title = "Main",
                botId = "qq-main",
                personaId = "",
                providerId = "",
                platformId = "app",
                messageType = "other",
                originSessionId = "chat-main",
                maxContextMessages = 12,
                sessionSttEnabled = true,
                sessionTtsEnabled = true,
                pinned = false,
                titleCustomized = false,
                updatedAt = 200L,
            ),
            messageAggregates = listOf(
                ConversationMessageAggregate(
                    message = ConversationMessageEntity(
                        id = "m2",
                        sessionId = "chat-main",
                        role = "assistant",
                        content = "second",
                        timestamp = 200L,
                        sortIndex = 1,
                    ),
                    attachments = emptyList(),
                ),
                ConversationMessageAggregate(
                    message = ConversationMessageEntity(
                        id = "m1",
                        sessionId = "chat-main",
                        role = "user",
                        content = "first",
                        timestamp = 100L,
                        sortIndex = 0,
                    ),
                    attachments = listOf(
                        ConversationAttachmentEntity(
                            id = "a1",
                            messageId = "m1",
                            type = "image",
                            mimeType = "image/jpeg",
                            fileName = "a.jpg",
                            base64Data = "abc",
                            remoteUrl = "",
                            sortIndex = 0,
                        ),
                    ),
                ),
            ),
        )

        val session = aggregate.toConversationSession()

        assertEquals(listOf("m1", "m2"), session.messages.map { it.id })
        assertEquals(listOf("a1"), session.messages.first().attachments.map { it.id })
    }

    @Test
    fun session_toWriteModel_flattensMessagesAndAttachments() {
        val session = ConversationSession(
            id = "chat-main",
            title = "Main",
            botId = "qq-main",
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            messages = listOf(
                ConversationMessage(
                    id = "m1",
                    role = "user",
                    content = "hello",
                    timestamp = 123L,
                    attachments = listOf(
                        ConversationAttachment(
                            id = "a1",
                            fileName = "a.jpg",
                            base64Data = "abc",
                        ),
                    ),
                ),
            ),
        )

        val writeModel = session.toWriteModel()

        assertEquals(1, writeModel.messages.size)
        assertEquals(1, writeModel.attachments.size)
        assertEquals("chat-main", writeModel.messages.single().sessionId)
        assertEquals("m1", writeModel.attachments.single().messageId)
    }
}
