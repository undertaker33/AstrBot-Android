package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.chat.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QqOneBotOutboundGatewayTest {
    @Test
    fun scheduled_message_without_connected_transport_returns_failure() {
        val bot = BotProfile(id = "bot-1", configProfileId = "config-1")
        val gateway = QqOneBotOutboundGateway(
            transport = OneBotReverseWebSocketTransport(
                port = 6199,
                path = "/ws",
                authToken = "token",
                onPayload = {},
                log = {},
            ),
            botPort = FakeBotPort(bot),
            configPort = FakeConfigPort(ConfigProfile(id = "config-1")),
            platformConfigPort = FakePlatformConfigPort(bot),
            audioMaterializer = QqAudioAttachmentMaterializer(
                filesDirProvider = { null },
                encodeSilkAudio = { input -> input },
                log = {},
            ),
            replyOverrideProvider = { null },
            log = {},
        )

        val result = gateway.sendScheduledMessage(
            conversationId = "group:12345",
            text = "hello",
            attachments = emptyList(),
            botId = bot.id,
        )

        assertFalse(result.success)
        assertEquals("reverse_ws_not_connected", result.errorSummary)
    }

    private class FakeBotPort(private val bot: BotProfile) : BotRepositoryPort {
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(listOf(bot))
        override val selectedBotId: StateFlow<String> = MutableStateFlow(bot.id)
        override fun currentBot(): BotProfile = bot
        override fun snapshotProfiles(): List<BotProfile> = listOf(bot)
        override fun create(name: String): BotProfile = bot.copy(displayName = name)
        override suspend fun save(profile: BotProfile) = Unit
        override suspend fun create(profile: BotProfile) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun select(id: String) = Unit
    }

    private class FakeConfigPort(private val config: ConfigProfile) : ConfigRepositoryPort {
        override val profiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(listOf(config))
        override val selectedProfileId: StateFlow<String> = MutableStateFlow(config.id)
        override fun snapshotProfiles(): List<ConfigProfile> = listOf(config)
        override fun create(name: String): ConfigProfile = config.copy(name = name)
        override fun resolve(id: String): ConfigProfile = config
        override fun resolveExistingId(id: String?): String = config.id
        override suspend fun save(profile: ConfigProfile) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun select(id: String) = Unit
    }

    private class FakePlatformConfigPort(private val bot: BotProfile) : QqPlatformConfigPort {
        override fun resolveQqBot(selfId: String): BotProfile? = bot
        override fun qqReplyQuoteEnabled(botId: String): Boolean = false
        override fun qqReplyMentionEnabled(botId: String): Boolean = false
        override fun qqAutoReplyEnabled(botId: String): Boolean = true
        override fun qqWakeWords(botId: String): List<String> = emptyList()
        override fun qqWhitelist(botId: String, messageType: MessageType): List<String> = emptyList()
        override fun qqWhitelistEnabled(botId: String): Boolean = false
        override fun qqRateLimitWindow(botId: String): Int = 0
        override fun qqRateLimitMaxCount(botId: String): Int = 0
        override fun qqRateLimitStrategy(botId: String): String = ""
        override fun qqIsolateGroupUser(botId: String): Boolean = false
    }
}
