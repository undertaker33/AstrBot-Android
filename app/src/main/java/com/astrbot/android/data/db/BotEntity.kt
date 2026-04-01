package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "bots")
data class BotEntity(
    @PrimaryKey val id: String,
    val platformName: String,
    val displayName: String,
    val tag: String,
    val accountHint: String,
    val autoReplyEnabled: Boolean,
    val persistConversationLocally: Boolean,
    val bridgeMode: String,
    val bridgeEndpoint: String,
    val defaultProviderId: String,
    val defaultPersonaId: String,
    val configProfileId: String,
    val status: String,
    val updatedAt: Long,
)
