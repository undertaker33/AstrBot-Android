package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ConversationMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["messageId", "sortIndex"])],
)
data class ConversationAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val type: String,
    val mimeType: String,
    val fileName: String,
    val base64Data: String,
    val remoteUrl: String,
    val sortIndex: Int,
)
