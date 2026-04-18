package com.astrbot.android.feature.qq.data

import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.chat.MessageType

class LegacyQqPlatformConfigAdapter : QqPlatformConfigPort {

    override fun resolveQqBot(selfId: String): BotProfile? {
        return FeatureBotRepository.resolveBoundBot(selfId)
    }

    override fun qqReplyQuoteEnabled(botId: String): Boolean {
        val config = resolveConfigForBot(botId)
        return config?.quoteSenderMessageEnabled == true
    }

    override fun qqReplyMentionEnabled(botId: String): Boolean {
        val config = resolveConfigForBot(botId)
        return config?.mentionSenderEnabled == true
    }

    override fun qqAutoReplyEnabled(botId: String): Boolean {
        val bot = FeatureBotRepository.botProfiles.value.firstOrNull { it.id == botId }
        return bot?.autoReplyEnabled == true
    }

    override fun qqWakeWords(botId: String): List<String> {
        val bot = FeatureBotRepository.botProfiles.value.firstOrNull { it.id == botId } ?: return emptyList()
        val config = resolveConfigForBot(botId)
        return ((bot.triggerWords) + (config?.wakeWords ?: emptyList())).distinct()
    }

    override fun qqWhitelist(botId: String, messageType: MessageType): List<String> {
        val config = resolveConfigForBot(botId)
        return config?.whitelistEntries ?: emptyList()
    }

    override fun qqWhitelistEnabled(botId: String): Boolean {
        val config = resolveConfigForBot(botId)
        return config?.whitelistEnabled == true
    }

    override fun qqRateLimitWindow(botId: String): Int {
        val config = resolveConfigForBot(botId)
        return config?.rateLimitWindowSeconds ?: 0
    }

    override fun qqRateLimitMaxCount(botId: String): Int {
        val config = resolveConfigForBot(botId)
        return config?.rateLimitMaxCount ?: 0
    }

    override fun qqRateLimitStrategy(botId: String): String {
        val config = resolveConfigForBot(botId)
        return config?.rateLimitStrategy.orEmpty()
    }

    override fun qqIsolateGroupUser(botId: String): Boolean {
        val config = resolveConfigForBot(botId)
        return config?.sessionIsolationEnabled == true
    }

    private fun resolveConfigForBot(botId: String): com.astrbot.android.model.ConfigProfile? {
        val bot = FeatureBotRepository.botProfiles.value.firstOrNull { it.id == botId } ?: return null
        return FeatureConfigRepository.resolve(bot.configProfileId)
    }
}


