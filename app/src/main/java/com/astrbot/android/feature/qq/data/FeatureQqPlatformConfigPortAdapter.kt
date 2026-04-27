
package com.astrbot.android.feature.qq.data

import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.chat.MessageType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class FeatureQqPlatformConfigPortAdapter private constructor(
    private val resolveQqBotReader: (String) -> BotProfile?,
    private val botReader: (String) -> BotProfile?,
    private val configReader: (String) -> ConfigProfile,
) : QqPlatformConfigPort {

    @Inject
    constructor(
        botRepositoryPort: BotRepositoryPort,
        configRepositoryPort: ConfigRepositoryPort,
    ) : this(
        resolveQqBotReader = { selfId ->
            resolveQqBot(
                bots = botRepositoryPort.bots.value,
                selectedBotId = botRepositoryPort.selectedBotId.value,
                selfId = selfId,
            )
        },
        botReader = { botId -> botRepositoryPort.bots.value.firstOrNull { it.id == botId } },
        configReader = configRepositoryPort::resolve,
    )

    constructor() : this(
        resolveQqBotReader = FeatureBotRepository::resolveBoundBot,
        botReader = { botId -> FeatureBotRepository.botProfiles.value.firstOrNull { it.id == botId } },
        configReader = FeatureConfigRepository::resolve,
    )

    override fun resolveQqBot(selfId: String): BotProfile? {
        return resolveQqBotReader(selfId)
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
        val bot = botReader(botId)
        return bot?.autoReplyEnabled == true
    }

    override fun qqWakeWords(botId: String): List<String> {
        val bot = botReader(botId) ?: return emptyList()
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

    private companion object {
        fun resolveQqBot(
            bots: List<BotProfile>,
            selectedBotId: String,
            selfId: String,
        ): BotProfile? {
            val cleanedSelfId = selfId.trim()
            val enabledBots = bots.filter {
                it.platformName.equals("QQ", ignoreCase = true) && it.autoReplyEnabled
            }
            if (cleanedSelfId.isBlank()) {
                return enabledBots.firstOrNull()
            }
            return enabledBots.firstOrNull { bot ->
                bot.id == selectedBotId && bot.boundQqUins.contains(cleanedSelfId)
            } ?: enabledBots.firstOrNull { bot ->
                bot.boundQqUins.contains(cleanedSelfId)
            } ?: enabledBots.firstOrNull()
        }
    }

    private fun resolveConfigForBot(botId: String): ConfigProfile? {
        val bot = botReader(botId) ?: return null
        return configReader(bot.configProfileId)
    }
}
