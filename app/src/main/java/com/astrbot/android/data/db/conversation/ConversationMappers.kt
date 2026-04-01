package com.astrbot.android.data.db

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.chat.defaultSessionRefFor

fun ConversationSession.toWriteModel(): ConversationAggregateWriteModel {
    val messageEntities = messages.mapIndexed { index, message ->
        ConversationMessageEntity(
            id = message.id,
            sessionId = id,
            role = message.role,
            content = message.content,
            timestamp = message.timestamp,
            sortIndex = index,
        )
    }
    val attachmentEntities = messages.flatMap { message ->
        message.attachments.mapIndexed { index, attachment ->
            ConversationAttachmentEntity(
                id = attachment.id,
                messageId = message.id,
                type = attachment.type,
                mimeType = attachment.mimeType,
                fileName = attachment.fileName,
                base64Data = attachment.base64Data,
                remoteUrl = attachment.remoteUrl,
                sortIndex = index,
            )
        }
    }
    return ConversationAggregateWriteModel(
        session = ConversationEntity(
            id = id,
            title = title,
            botId = botId,
            personaId = personaId,
            providerId = providerId,
            platformId = platformId,
            messageType = messageType.wireValue,
            originSessionId = originSessionId,
            maxContextMessages = maxContextMessages,
            sessionSttEnabled = sessionSttEnabled,
            sessionTtsEnabled = sessionTtsEnabled,
            pinned = pinned,
            titleCustomized = titleCustomized,
            updatedAt = messages.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis(),
        ),
        messages = messageEntities,
        attachments = attachmentEntities,
    )
}

fun ConversationAggregate.toConversationSession(): ConversationSession {
    val defaultRef = defaultSessionRefFor(session.id)
    return ConversationSession(
        id = session.id,
        title = session.title,
        botId = session.botId,
        personaId = session.personaId,
        providerId = session.providerId,
        platformId = session.platformId.ifBlank { defaultRef.platformId },
        messageType = MessageType.fromWireValue(session.messageType) ?: defaultRef.messageType,
        originSessionId = session.originSessionId.ifBlank { defaultRef.originSessionId },
        maxContextMessages = session.maxContextMessages,
        sessionSttEnabled = session.sessionSttEnabled,
        sessionTtsEnabled = session.sessionTtsEnabled,
        pinned = session.pinned,
        titleCustomized = session.titleCustomized,
        messages = messageAggregates
            .sortedBy { it.message.sortIndex }
            .map { aggregate ->
                ConversationMessage(
                    id = aggregate.message.id,
                    role = aggregate.message.role,
                    content = aggregate.message.content,
                    timestamp = aggregate.message.timestamp,
                    attachments = aggregate.attachments
                        .sortedBy { it.sortIndex }
                        .map { attachment ->
                            ConversationAttachment(
                                id = attachment.id,
                                type = attachment.type,
                                mimeType = attachment.mimeType,
                                fileName = attachment.fileName,
                                base64Data = attachment.base64Data,
                                remoteUrl = attachment.remoteUrl,
                            )
                        },
                )
            },
    )
}
