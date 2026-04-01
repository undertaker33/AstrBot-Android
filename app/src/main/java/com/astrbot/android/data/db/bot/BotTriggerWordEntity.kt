package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "bot_trigger_words",
    primaryKeys = ["botId", "word"],
    foreignKeys = [
        ForeignKey(
            entity = BotEntity::class,
            parentColumns = ["id"],
            childColumns = ["botId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["botId", "sortIndex"])],
)
data class BotTriggerWordEntity(
    val botId: String,
    val word: String,
    val sortIndex: Int,
)
