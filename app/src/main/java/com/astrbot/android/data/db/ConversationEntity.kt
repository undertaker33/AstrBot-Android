package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val botId: String,
    val personaId: String,
    val providerId: String,
    val maxContextMessages: Int,
    val sessionSttEnabled: Boolean,
    val sessionTtsEnabled: Boolean,
    val messagesJson: String,
    val updatedAt: Long,
)
