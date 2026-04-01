package com.astrbot.android.data.db.config

import com.astrbot.android.data.db.ConfigAdminUidEntity
import com.astrbot.android.data.db.ConfigAggregate
import com.astrbot.android.data.db.ConfigKeywordPatternEntity
import com.astrbot.android.data.db.ConfigProfileEntity
import com.astrbot.android.data.db.ConfigTextRuleEntity
import com.astrbot.android.data.db.ConfigWakeWordEntity
import com.astrbot.android.data.db.ConfigWhitelistEntryEntity
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.ConfigProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigMappersTest {
    @Test
    fun aggregate_toProfile_restoresAllListFields() {
        val profile = ConfigAggregate(
            config = ConfigProfileEntity("default", "Default", "", "", "", "", false, false, false, true, false, false, 120, false, false, false, false, "", false, false, false, "", false, false, true, false, false, true, true, true, true, false, 0, 0, "drop", false, 0, 1L),
            adminUids = listOf(ConfigAdminUidEntity("default", "10001", 0)),
            wakeWords = listOf(ConfigWakeWordEntity("default", "wake", 0)),
            whitelistEntries = listOf(ConfigWhitelistEntryEntity("default", "qq:10001", 0)),
            keywordPatterns = listOf(ConfigKeywordPatternEntity("default", "ping", 0)),
            textRules = listOf(ConfigTextRuleEntity("default", "prompt")),
        ).toProfile()

        assertEquals(listOf("10001"), profile.adminUids)
        assertEquals(listOf("ping"), profile.keywordPatterns)
        assertEquals("prompt", profile.imageCaptionPrompt)
    }

    @Test
    fun profile_toWriteModel_flattensListsAndTextRule() {
        val writeModel = ConfigProfile(id = "default", name = "Default", adminUids = listOf("10001"), wakeWords = listOf("wake"), whitelistEntries = listOf("qq:10001"), keywordPatterns = listOf("ping"), imageCaptionPrompt = "prompt").toWriteModel(sortIndex = 0)
        assertEquals(1, writeModel.adminUids.size)
        assertEquals("prompt", writeModel.textRule.imageCaptionPrompt)
    }
}
