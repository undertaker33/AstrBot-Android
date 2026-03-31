package com.astrbot.android.runtime.botcommand

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotCommandRouterTest {

    @Test
    fun `stt command toggles session flag and returns localized reply`() {
        var sttEnabled = true

        val result = BotCommandRouter.handle(
            input = "/stt",
            context = testContext(
                session = testSession(sessionSttEnabled = sttEnabled),
                onUpdateSessionServiceFlags = { stt, _ -> sttEnabled = stt ?: sttEnabled },
            ),
        )

        assertTrue(result.handled)
        assertTrue(result.stopModelDispatch)
        assertEquals(false, sttEnabled)
        assertEquals("STT 已关闭（当前会话）", result.replyText)
    }

    @Test
    fun `reset command clears messages and persona switch updates bindings`() {
        var clearedMessages: List<ConversationMessage>? = null
        var updatedPersonaId = ""

        val context = testContext(
            onReplaceMessages = { clearedMessages = it },
            onUpdateSessionBindings = { _, personaId, _ -> updatedPersonaId = personaId },
        )

        val resetResult = BotCommandRouter.handle("/reset", context)
        val personaResult = BotCommandRouter.handle("/persona Narrator", context)

        assertTrue(resetResult.handled)
        assertEquals(emptyList<ConversationMessage>(), clearedMessages)
        assertTrue(personaResult.handled)
        assertEquals("persona-2", updatedPersonaId)
    }

    @Test
    fun `help sid agent and ls commands return formatted replies`() {
        val result = BotCommandRouter.handle(
            input = "/help",
            context = testContext(languageTag = "en"),
        )
        val sidResult = BotCommandRouter.handle(
            input = "/sid",
            context = testContext(
                source = BotCommandSource.QQ,
                session = testSession(
                    id = "qq-bot-private-10001",
                    platformId = "qq",
                    messageType = MessageType.FriendMessage,
                    originSessionId = "friend:10001",
                ),
                sourceUid = "10001",
            ),
        )
        val agentResult = BotCommandRouter.handle("/agent", testContext())
        val lsResult = BotCommandRouter.handle("/ls 1", testContext())

        assertTrue(result.replyText.orEmpty().contains("AstrBot V0.3.5"))
        assertTrue(sidResult.replyText.orEmpty().contains("UMO: `qqbot:FriendMessage:10001`"))
        assertTrue(agentResult.replyText.orEmpty().contains("当前 agent 列表为空"))
        assertTrue(lsResult.replyText.orEmpty().contains("会话列表"))
    }

    @Test
    fun `admin and whitelist commands return fixed replies`() {
        var grantedConfig: ConfigProfile? = null
        var revokedConfig: ConfigProfile? = null
        var whitelistAddedConfig: ConfigProfile? = null
        var whitelistRemovedConfig: ConfigProfile? = null

        val opResult = BotCommandRouter.handle(
            "/op 10001",
            testContext(onUpdateConfig = { grantedConfig = it }),
        )
        val deopResult = BotCommandRouter.handle(
            "/deop 10001",
            testContext(
                config = ConfigProfile(id = "config-1", adminUids = listOf("10001", "10002")),
                onUpdateConfig = { revokedConfig = it },
            ),
        )
        val wlResult = BotCommandRouter.handle(
            "/wl qqbot:GroupMessage:123456",
            testContext(onUpdateConfig = { whitelistAddedConfig = it }),
        )
        val dwlResult = BotCommandRouter.handle(
            "/dwl qqbot:GroupMessage:123456",
            testContext(
                config = ConfigProfile(id = "config-1", whitelistEntries = listOf("qqbot:GroupMessage:123456", "qqbot:GroupMessage:999999")),
                onUpdateConfig = { whitelistRemovedConfig = it },
            ),
        )

        assertEquals("已授权管理员10001", opResult.replyText)
        assertEquals(listOf("10001"), grantedConfig?.adminUids)
        assertEquals("已取消管理员10001 的授权", deopResult.replyText)
        assertEquals(listOf("10002"), revokedConfig?.adminUids)
        assertEquals("已将 qqbot:GroupMessage:123456 添加至白名单", wlResult.replyText)
        assertEquals(listOf("qqbot:GroupMessage:123456"), whitelistAddedConfig?.whitelistEntries)
        assertEquals("已将 qqbot:GroupMessage:123456 移出白名单", dwlResult.replyText)
        assertEquals(listOf("qqbot:GroupMessage:999999"), whitelistRemovedConfig?.whitelistEntries)
    }

    @Test
    fun `app session commands create switch rename and delete conversations`() {
        var createdSession: ConversationSession? = null
        var selectedSessionId = ""
        var deletedSessionId = ""
        var renamedSessionId = ""
        var renamedTitle = ""
        val sessions = listOf(
            testSession(id = "session-1", originSessionId = "session-1"),
            testSession(id = "session-2", originSessionId = "session-2"),
        )

        val context = testContext(
            session = sessions.first(),
            sessions = sessions,
            onCreateSession = {
                testSession(id = "session-3", title = "新对话", originSessionId = "session-3").also {
                    createdSession = it
                }
            },
            onDeleteSession = { deletedSessionId = it },
            onRenameSession = { sessionId, title ->
                renamedSessionId = sessionId
                renamedTitle = title
            },
            onSelectSession = { selectedSessionId = it },
        )

        val newResult = BotCommandRouter.handle("/new", context)
        val switchResult = BotCommandRouter.handle("/switch 2", context)
        val renameResult = BotCommandRouter.handle("/rename 测试会话", context)
        val deleteResult = BotCommandRouter.handle("/del", context)
        val groupResult = BotCommandRouter.handle("/groupnew", context)

        assertEquals("session-3", createdSession?.id)
        assertEquals("新会话已创建，会话ID：session-3", newResult.replyText)
        assertEquals("session-2", selectedSessionId)
        assertEquals("已切换至 session-2", switchResult.replyText)
        assertEquals("session-1", renamedSessionId)
        assertEquals("测试会话", renamedTitle)
        assertEquals("当前会话已重命名为 测试会话", renameResult.replyText)
        assertEquals("session-1", deletedSessionId)
        assertEquals("当前会话已删除", deleteResult.replyText)
        assertEquals("指令 /groupnew 暂未实现", groupResult.replyText)
    }

    @Test
    fun `provider commands list switch model and key through shared router`() {
        var selectedProviderId = ""
        val savedProviders = mutableListOf<ProviderProfile>()
        val providers = listOf(
            testProvider(id = "provider-1", name = "Primary", model = "gpt-4.1-mini", apiKey = "key-1"),
            testProvider(id = "provider-2", name = "Backup", model = "deepseek-chat", apiKey = "key-2"),
        )

        val context = testContext(
            languageTag = "en",
            availableProviders = providers,
            activeProviderId = "provider-1",
            onUpdateSessionBindings = { providerId, _, _ ->
                selectedProviderId = providerId
            },
            onUpdateProvider = { provider ->
                savedProviders += provider
            },
        )

        val listResult = BotCommandRouter.handle("/provider", context)
        val switchResult = BotCommandRouter.handle("/provider Backup", context)
        val modelResult = BotCommandRouter.handle("/model gpt-5", context)
        val keyResult = BotCommandRouter.handle("/key sk-test-123", context)

        assertTrue(listResult.replyText.orEmpty().contains("Current provider: Primary"))
        assertTrue(listResult.replyText.orEmpty().contains("Backup"))
        assertEquals("provider-2", selectedProviderId)
        assertEquals("Switched provider to Backup.", switchResult.replyText)
        assertEquals("gpt-5", savedProviders.firstOrNull()?.model)
        assertEquals("Model updated to gpt-5 for provider Primary.", modelResult.replyText)
        assertEquals("sk-test-123", savedProviders.lastOrNull()?.apiKey)
        assertEquals("API key updated for provider Primary.", keyResult.replyText)
    }

    private fun testContext(
        source: BotCommandSource = BotCommandSource.APP_CHAT,
        languageTag: String = "zh",
        session: ConversationSession = testSession(),
        sessions: List<ConversationSession> = listOf(session),
        availableProviders: List<ProviderProfile> = listOf(testProvider()),
        activeProviderId: String = "provider-1",
        config: ConfigProfile? = null,
        sourceUid: String = if (source == BotCommandSource.QQ) "10001" else "",
        onUpdateConfig: (ConfigProfile) -> Unit = {},
        onUpdateSessionServiceFlags: (Boolean?, Boolean?) -> Unit = { _, _ -> },
        onReplaceMessages: (List<ConversationMessage>) -> Unit = {},
        onUpdateSessionBindings: (String, String, String) -> Unit = { _, _, _ -> },
        onUpdateProvider: (ProviderProfile) -> Unit = {},
        onCreateSession: (() -> ConversationSession)? = null,
        onDeleteSession: ((String) -> Unit)? = null,
        onRenameSession: ((String, String) -> Unit)? = null,
        onSelectSession: ((String) -> Unit)? = null,
    ): BotCommandContext {
        val bot = BotProfile(
            id = "qqbot",
            displayName = "QQ Bot",
            defaultPersonaId = "default",
            configProfileId = "config-1",
        )
        val resolvedConfig = config ?: ConfigProfile(
            id = "config-1",
            defaultChatProviderId = "provider-1",
            sessionIsolationEnabled = session.originSessionId.contains(":user:"),
        )
        val personas = listOf(
            PersonaProfile(id = "default", name = "Default Assistant", systemPrompt = "default", enabledTools = emptySet()),
            PersonaProfile(id = "persona-2", name = "Narrator", systemPrompt = "narrator", enabledTools = emptySet()),
        )
        return BotCommandContext(
            source = source,
            languageTag = languageTag,
            sessionId = session.id,
            session = session,
            sessions = sessions,
            bot = bot,
            config = resolvedConfig,
            activeProviderId = activeProviderId,
            availableProviders = availableProviders,
            currentPersona = personas.first(),
            availablePersonas = personas,
            messageType = session.messageType,
            sourceUid = sourceUid,
            sourceGroupId = "",
            createSession = onCreateSession,
            deleteSession = onDeleteSession,
            renameSession = onRenameSession,
            selectSession = onSelectSession,
            updateConfig = onUpdateConfig,
            updateSessionServiceFlags = onUpdateSessionServiceFlags,
            replaceMessages = onReplaceMessages,
            updateSessionBindings = onUpdateSessionBindings,
            updateProvider = onUpdateProvider,
        )
    }

    private fun testSession(
        id: String = "session-1",
        title: String = "默认会话",
        platformId: String = "app",
        messageType: MessageType = MessageType.OtherMessage,
        originSessionId: String = "session-1",
        sessionSttEnabled: Boolean = true,
    ): ConversationSession {
        return ConversationSession(
            id = id,
            title = title,
            botId = "qqbot",
            personaId = "default",
            providerId = "provider-1",
            platformId = platformId,
            messageType = messageType,
            originSessionId = originSessionId,
            maxContextMessages = 12,
            sessionSttEnabled = sessionSttEnabled,
            messages = listOf(
                ConversationMessage(
                    id = "message-1",
                    role = "user",
                    content = "hello",
                    timestamp = 1L,
                ),
            ),
        )
    }
    private fun testProvider(
        id: String = "provider-1",
        name: String = "Primary",
        model: String = "gpt-4.1-mini",
        apiKey: String = "key-1",
    ): ProviderProfile {
        return ProviderProfile(
            id = id,
            name = name,
            baseUrl = "https://example.com/v1",
            model = model,
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = apiKey,
            capabilities = setOf(ProviderCapability.CHAT),
            enabled = true,
            multimodalRuleSupport = FeatureSupportState.UNKNOWN,
            multimodalProbeSupport = FeatureSupportState.UNKNOWN,
        )
    }
}
