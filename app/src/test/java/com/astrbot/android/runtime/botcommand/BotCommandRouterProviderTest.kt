package com.astrbot.android.feature.chat.runtime.botcommand

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotCommandRouterProviderTest {
    @Test
    fun `provider command updates config authority and projection bindings for app chat`() {
        var updatedConfig: ConfigProfile? = null
        var updatedBot: BotProfile? = null
        var updatedBinding: Triple<String, String, String>? = null

        val result = BotCommandRouter.handle(
            input = "/provider qwen",
            context = commandContext(
                source = BotCommandSource.APP_CHAT,
                updateConfig = { updatedConfig = it },
                updateBot = { updatedBot = it },
                updateSessionBindings = { providerId, personaId, botId ->
                    updatedBinding = Triple(providerId, personaId, botId)
                },
            ),
        )

        assertTrue(result.handled)
        assertEquals("qwen-chat", updatedConfig?.defaultChatProviderId)
        assertEquals("qwen-chat", updatedBot?.defaultProviderId)
        assertEquals(Triple("qwen-chat", "persona-default", "bot-1"), updatedBinding)
    }

    @Test
    fun `provider command updates config authority and projection bindings for qq`() {
        var updatedConfig: ConfigProfile? = null
        var updatedBot: BotProfile? = null
        var updatedBinding: Triple<String, String, String>? = null

        val result = BotCommandRouter.handle(
            input = "/provider qwen",
            context = commandContext(
                source = BotCommandSource.QQ,
                updateConfig = { updatedConfig = it },
                updateBot = { updatedBot = it },
                updateSessionBindings = { providerId, personaId, botId ->
                    updatedBinding = Triple(providerId, personaId, botId)
                },
            ),
        )

        assertTrue(result.handled)
        assertEquals("qwen-chat", updatedConfig?.defaultChatProviderId)
        assertEquals("qwen-chat", updatedBot?.defaultProviderId)
        assertEquals(Triple("qwen-chat", "persona-default", "bot-1"), updatedBinding)
    }

    private fun commandContext(
        source: BotCommandSource,
        updateConfig: (ConfigProfile) -> Unit,
        updateBot: (BotProfile) -> Unit,
        updateSessionBindings: (String, String, String) -> Unit,
    ): BotCommandContext {
        val bot = BotProfile(
            id = "bot-1",
            displayName = "Bot",
            configProfileId = "config-1",
            defaultProviderId = "deepseek-chat",
        )
        val config = ConfigProfile(
            id = "config-1",
            defaultChatProviderId = "deepseek-chat",
        )
        val session = ConversationSession(
            id = "session-1",
            title = "Session",
            botId = bot.id,
            personaId = "persona-default",
            providerId = "deepseek-chat",
            maxContextMessages = 12,
            messages = emptyList(),
        )
        return BotCommandContext(
            source = source,
            languageTag = "zh-CN",
            sessionId = session.id,
            session = session,
            sessions = listOf(session),
            bot = bot,
            availableBots = listOf(bot),
            config = config,
            activeProviderId = "deepseek-chat",
            availableProviders = listOf(
                provider("deepseek-chat", "deepseek"),
                provider("qwen-chat", "qwen"),
            ),
            currentPersona = null,
            availablePersonas = emptyList(),
            messageType = MessageType.FriendMessage,
            updateConfig = updateConfig,
            updateBot = updateBot,
            updateSessionBindings = updateSessionBindings,
        )
    }

    private fun provider(id: String, name: String): ProviderProfile {
        return ProviderProfile(
            id = id,
            name = name,
            baseUrl = "https://example.com",
            model = "model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "key",
            capabilities = setOf(ProviderCapability.CHAT),
            enabled = true,
        )
    }
}
