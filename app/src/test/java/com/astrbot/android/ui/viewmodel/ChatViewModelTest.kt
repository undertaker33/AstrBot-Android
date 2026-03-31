package com.astrbot.android.ui.viewmodel

import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun init_prefers_non_default_restored_session() = runTest(dispatcher) {
        val deps = FakeChatDependencies(
            sessions = listOf(
                defaultSession(),
                ConversationSession(
                    id = "session-restored",
                    title = "Restored",
                    botId = "bot-2",
                    providerId = "provider-2",
                    personaId = "",
                    maxContextMessages = 12,
                    messages = emptyList(),
                ),
            ),
            bots = listOf(defaultBot(), defaultBot(id = "bot-2", defaultProviderId = "provider-2")),
            providers = listOf(defaultProvider("provider-2")),
        )

        val viewModel = ChatViewModel(deps)
        advanceUntilIdle()

        assertEquals("session-restored", viewModel.uiState.value.selectedSessionId)
        assertTrue(deps.bindingUpdates.any { it.sessionId == "session-restored" && it.providerId == "provider-2" })
    }

    @Test
    fun send_message_without_available_provider_does_not_append_messages() = runTest(dispatcher) {
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot()),
            providers = emptyList(),
        )
        val viewModel = ChatViewModel(deps)
        advanceUntilIdle()

        viewModel.sendMessage("hello")
        advanceUntilIdle()

        assertEquals(0, deps.appendedMessages.size)
        assertTrue(deps.loggedMessages.any { it.contains("Chat send blocked") })
        assertEquals(false, viewModel.uiState.value.isSending)
    }

    private class FakeChatDependencies(
        sessions: List<ConversationSession>,
        bots: List<BotProfile>,
        providers: List<ProviderProfile>,
    ) : ChatViewModelDependencies {
        override val defaultSessionId: String = "chat-main"
        override val defaultSessionTitle: String = "Default session"
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(bots)
        override val selectedBotId: StateFlow<String> = MutableStateFlow(bots.firstOrNull()?.id ?: "qq-main")
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(providers)
        override val configProfiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(listOf(ConfigProfile()))
        override val sessions: StateFlow<List<ConversationSession>> = MutableStateFlow(sessions)
        override val personas: StateFlow<List<PersonaProfile>> = MutableStateFlow(emptyList())

        data class BindingUpdate(val sessionId: String, val providerId: String, val personaId: String, val botId: String)

        val bindingUpdates = mutableListOf<BindingUpdate>()
        val appendedMessages = mutableListOf<ConversationMessage>()
        val loggedMessages = mutableListOf<String>()

        override fun session(sessionId: String): ConversationSession {
            return sessions.value.first { it.id == sessionId }
        }

        override fun createSession(botId: String): ConversationSession {
            error("Not needed in test")
        }

        override fun deleteSession(sessionId: String) = Unit

        override fun renameSession(sessionId: String, title: String) = Unit

        override fun toggleSessionPinned(sessionId: String) = Unit

        override fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean?, sessionTtsEnabled: Boolean?) = Unit

        override fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) {
            bindingUpdates += BindingUpdate(sessionId, providerId, personaId, botId)
        }

        override fun appendMessage(
            sessionId: String,
            role: String,
            content: String,
            attachments: List<ConversationAttachment>,
        ): String {
            appendedMessages += ConversationMessage(
                id = "msg-${appendedMessages.size}",
                role = role,
                content = content,
                timestamp = 1L,
                attachments = attachments,
            )
            return appendedMessages.last().id
        }

        override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) = Unit

        override fun updateMessage(
            sessionId: String,
            messageId: String,
            content: String?,
            attachments: List<ConversationAttachment>?,
        ) = Unit

        override fun syncSystemSessionTitle(sessionId: String, title: String) = Unit

        override fun resolveConfig(profileId: String): ConfigProfile = ConfigProfile(defaultChatProviderId = "provider-2")

        override fun saveConfig(profile: ConfigProfile) = Unit

        override fun saveProvider(profile: ProviderProfile) = Unit

        override suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String {
            error("Not needed in test")
        }

        override suspend fun sendConfiguredChat(
            provider: ProviderProfile,
            messages: List<ConversationMessage>,
            systemPrompt: String?,
            config: ConfigProfile?,
            availableProviders: List<ProviderProfile>,
        ): String {
            error("Not needed in test")
        }

        override suspend fun sendConfiguredChatStream(
            provider: ProviderProfile,
            messages: List<ConversationMessage>,
            systemPrompt: String?,
            config: ConfigProfile,
            availableProviders: List<ProviderProfile>,
            onDelta: suspend (String) -> Unit,
        ): String {
            error("Not needed in test")
        }

        override suspend fun synthesizeSpeech(
            provider: ProviderProfile,
            text: String,
            voiceId: String,
            readBracketedContent: Boolean,
        ): ConversationAttachment {
            error("Not needed in test")
        }

        override suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T {
            return block()
        }

        override fun log(message: String) {
            loggedMessages += message
        }
    }

    private fun defaultSession(): ConversationSession {
        return ConversationSession(
            id = "chat-main",
            title = "Default session",
            botId = "qq-main",
            providerId = "",
            personaId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )
    }

    private fun defaultBot(
        id: String = "qq-main",
        defaultProviderId: String = "",
    ): BotProfile {
        return BotProfile(
            id = id,
            displayName = id,
            configProfileId = ConfigProfile().id,
            defaultProviderId = defaultProviderId,
        )
    }

    private fun defaultProvider(id: String): ProviderProfile {
        return ProviderProfile(
            id = id,
            name = id,
            baseUrl = "https://example.com",
            model = "gpt",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "key",
            capabilities = setOf(ProviderCapability.CHAT),
            enabled = true,
            multimodalRuleSupport = FeatureSupportState.UNKNOWN,
            multimodalProbeSupport = FeatureSupportState.UNKNOWN,
        )
    }
}
