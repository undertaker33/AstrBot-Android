package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "bot_bound_qq_uins",
    primaryKeys = ["botId", "uin"],
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
data class BotBoundQqUinEntity(
    val botId: String,
    val uin: String,
    val sortIndex: Int,
)
