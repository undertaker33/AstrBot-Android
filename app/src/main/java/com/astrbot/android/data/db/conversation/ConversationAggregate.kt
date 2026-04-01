package com.astrbot.android.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class ConversationMessageAggregate(
    @Embedded val message: ConversationMessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId",
    )
    val attachments: List<ConversationAttachmentEntity>,
)

data class ConversationAggregate(
    @Embedded val session: ConversationEntity,
    @Relation(
        entity = ConversationMessageEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val messageAggregates: List<ConversationMessageAggregate>,
)

data class ConversationAggregateWriteModel(
    val session: ConversationEntity,
    val messages: List<ConversationMessageEntity>,
    val attachments: List<ConversationAttachmentEntity>,
)
