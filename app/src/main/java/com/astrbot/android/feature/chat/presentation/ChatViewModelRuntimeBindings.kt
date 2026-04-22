package com.astrbot.android.ui.viewmodel

import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.chat.domain.SendAppMessageUseCase
import com.astrbot.android.feature.chat.presentation.AppChatSendHandler
import com.astrbot.android.feature.chat.runtime.AppChatPluginCommandService
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.StateFlow

interface ChatViewModelRuntimeBindings {
    val defaultSessionId: String
    val defaultSessionTitle: String
    val defaultAppChatPluginRuntime: AppChatPluginRuntime
    val bots: StateFlow<List<BotProfile>>
    val selectedBotId: StateFlow<String>
    val providers: StateFlow<List<ProviderProfile>>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val sessions: StateFlow<List<ConversationSession>>
    val personas: StateFlow<List<PersonaProfile>>

    fun session(sessionId: String): ConversationSession

    fun createSession(botId: String): ConversationSession

    fun deleteSession(sessionId: String)

    fun renameSession(sessionId: String, title: String)

    fun toggleSessionPinned(sessionId: String)

    fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean? = null, sessionTtsEnabled: Boolean? = null)

    fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String)

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>)

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    )

    fun syncSystemSessionTitle(sessionId: String, title: String)

    fun resolveConfig(profileId: String): ConfigProfile

    fun saveConfig(profile: ConfigProfile)

    fun saveBot(profile: BotProfile)

    fun saveProvider(profile: ProviderProfile)

    suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String

    suspend fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): String

    suspend fun sendConfiguredChatStream(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
        onDelta: suspend (String) -> Unit,
    ): String

    suspend fun sendConfiguredChatWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
    ): LlmInvocationResult

    suspend fun sendConfiguredChatStreamWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
        onDelta: suspend (String) -> Unit,
        onToolCallDelta: suspend (index: Int, name: String, argumentsFragment: String) -> Unit,
    ): LlmInvocationResult

    suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment

    suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T

    fun log(message: String)

    val runtimeContextResolverPort: RuntimeContextResolverPort
    val conversationRepositoryPort: ConversationRepositoryPort
    val appChatRuntimePort: AppChatRuntimePort
    val chatSessionController: ChatSessionController
    val sendAppMessageUseCase: SendAppMessageUseCase

    fun createChatSendHandler(
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext,
    ): AppChatSendHandler

    fun createAppChatPluginCommandService(
        appChatPluginRuntime: AppChatPluginRuntime,
    ): AppChatPluginCommandService
}
