package com.astrbot.android.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class BotAggregate(
    @Embedded val bot: BotEntity,
    @Relation(parentColumn = "id", entityColumn = "botId")
    val boundQqUins: List<BotBoundQqUinEntity>,
    @Relation(parentColumn = "id", entityColumn = "botId")
    val triggerWords: List<BotTriggerWordEntity>,
)

data class BotWriteModel(
    val bot: BotEntity,
    val boundQqUins: List<BotBoundQqUinEntity>,
    val triggerWords: List<BotTriggerWordEntity>,
)
