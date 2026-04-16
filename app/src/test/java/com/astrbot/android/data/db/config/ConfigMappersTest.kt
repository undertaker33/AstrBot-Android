package com.astrbot.android.data.db.config

import com.astrbot.android.data.db.ConfigAdminUidEntity
import com.astrbot.android.data.db.ConfigAggregate
import com.astrbot.android.data.db.ConfigKeywordPatternEntity
import com.astrbot.android.data.db.ConfigMcpServerEntity
import com.astrbot.android.data.db.ConfigProfileEntity
import com.astrbot.android.data.db.ConfigSkillEntity
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
            config = ConfigProfileEntity(
                id = "default", name = "Default",
                defaultChatProviderId = "", defaultVisionProviderId = "", defaultSttProviderId = "", defaultTtsProviderId = "",
                sttEnabled = false, ttsEnabled = false, alwaysTtsEnabled = false, ttsReadBracketedContent = true,
                textStreamingEnabled = false, voiceStreamingEnabled = false, streamingMessageIntervalMs = 120,
                realWorldTimeAwarenessEnabled = false, imageCaptionTextEnabled = false, webSearchEnabled = false,
                proactiveEnabled = false, ttsVoiceId = "", sessionIsolationEnabled = false,
                wakeWordsAdminOnlyEnabled = false, privateChatRequiresWakeWord = false, replyTextPrefix = "",
                quoteSenderMessageEnabled = false, mentionSenderEnabled = false, replyOnAtOnlyEnabled = true,
                whitelistEnabled = false, logOnWhitelistMiss = false, adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true, ignoreSelfMessageEnabled = true, ignoreAtAllEventEnabled = true,
                replyWhenPermissionDenied = false, rateLimitWindowSeconds = 0, rateLimitMaxCount = 0,
                rateLimitStrategy = "drop", keywordDetectionEnabled = false,
                contextLimitStrategy = "truncate_by_turns", maxContextTurns = -1, dequeueContextTurns = 1,
                llmCompressInstruction = "", llmCompressKeepRecent = 6, llmCompressProviderId = "",
                sortIndex = 0, updatedAt = 1L,
            ),
            adminUids = listOf(ConfigAdminUidEntity("default", "10001", 0)),
            wakeWords = listOf(ConfigWakeWordEntity("default", "wake", 0)),
            whitelistEntries = listOf(ConfigWhitelistEntryEntity("default", "qq:10001", 0)),
            keywordPatterns = listOf(ConfigKeywordPatternEntity("default", "ping", 0)),
            textRules = listOf(ConfigTextRuleEntity("default", "prompt")),
            mcpServers = emptyList(),
            skills = emptyList(),
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
