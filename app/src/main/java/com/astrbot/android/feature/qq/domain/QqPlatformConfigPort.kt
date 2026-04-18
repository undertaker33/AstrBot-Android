package com.astrbot.android.feature.qq.domain

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.chat.MessageType

interface QqPlatformConfigPort {
    fun resolveQqBot(selfId: String): BotProfile?
    fun qqReplyQuoteEnabled(botId: String): Boolean
    fun qqReplyMentionEnabled(botId: String): Boolean
    fun qqAutoReplyEnabled(botId: String): Boolean
    fun qqWakeWords(botId: String): List<String>
    fun qqWhitelist(botId: String, messageType: MessageType): List<String>
    fun qqWhitelistEnabled(botId: String): Boolean
    fun qqRateLimitWindow(botId: String): Int
    fun qqRateLimitMaxCount(botId: String): Int
    fun qqRateLimitStrategy(botId: String): String
    fun qqIsolateGroupUser(botId: String): Boolean
}
