package com.astrbot.android.data.db.bot

import com.astrbot.android.data.db.BotAggregate
import com.astrbot.android.data.db.BotBoundQqUinEntity
import com.astrbot.android.data.db.BotEntity
import com.astrbot.android.data.db.BotTriggerWordEntity
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.BotProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class BotMappersTest {
    @Test
    fun aggregate_toProfile_restoresListFields() {
        val profile = BotAggregate(
            bot = BotEntity("qq-main", "QQ", "Bot", "tag", "10001", true, true, "bridge", "ws://", "provider", "persona", "config", "Idle", 1L),
            boundQqUins = listOf(BotBoundQqUinEntity("qq-main", "10001", 0)),
            triggerWords = listOf(BotTriggerWordEntity("qq-main", "astrbot", 0)),
        ).toProfile()

        assertEquals(listOf("10001"), profile.boundQqUins)
        assertEquals(listOf("astrbot"), profile.triggerWords)
    }

    @Test
    fun profile_toWriteModel_flattensListFields() {
        val writeModel = BotProfile(id = "qq-main", boundQqUins = listOf("10001"), triggerWords = listOf("astrbot")).toWriteModel()
        assertEquals(1, writeModel.boundQqUins.size)
        assertEquals(1, writeModel.triggerWords.size)
    }
}
