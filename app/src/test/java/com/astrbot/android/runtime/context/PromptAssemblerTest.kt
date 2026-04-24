package com.astrbot.android.core.runtime.context

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.di.ProductionRuntimeContextDataPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {
    @Test
    fun assemble_for_app_chat_guides_reminders_to_future_tasks_before_web_search() {
        val prompt = PromptAssembler.assembleForAppChat(
            personaPrompt = "You are helpful.",
            realWorldTimeAwarenessEnabled = false,
        )

        assertTrue(prompt!!.contains("create_future_task"))
        assertTrue(prompt.contains("web_search"))
        assertTrue(prompt.contains("remind"))
    }

    @Test
    fun assemble_for_scheduled_task_adds_host_style_wakeup_guidance() {
        val ctx = resolveWithFakeDataPort(
            event = scheduledEvent(),
            messages = emptyList(),
        )
        val prompt = PromptAssembler.assemble(ctx)

        assertTrue(prompt!!.contains("scheduled task"))
        assertTrue(prompt.contains("not a normal chat turn"))
        assertTrue(prompt.contains("Do not greet"))
        assertTrue(prompt.contains("must send the reminder now"))
    }

    @Test
    fun assemble_for_scheduled_task_includes_task_note_as_metadata_not_user_text() {
        val ctx = resolveWithFakeDataPort(
            event = scheduledEvent(
                rawPlatformPayload = mapOf(
                    "scheduledTask" to mapOf(
                        "jobId" to "job-1",
                        "name" to "喝水提醒",
                        "note" to "提醒用户该喝水了",
                    ),
                ),
            ),
            messages = listOf(
                ConversationMessage(
                    id = "old-user",
                    role = "user",
                    content = "半小时后提醒我喝水",
                    timestamp = 1L,
                ),
            ),
        )

        val prompt = PromptAssembler.assemble(ctx)

        assertTrue(prompt!!.contains("提醒用户该喝水了"))
        assertTrue(prompt.contains("scheduler metadata"))
        assertTrue(prompt.contains("not as a new user message"))
        assertTrue(prompt.contains("must not create another scheduled task"))
    }

    @Test
    fun runtime_context_resolver_clears_message_window_for_scheduled_task() {
        val ctx = resolveWithFakeDataPort(
            event = scheduledEvent(),
            messages = listOf(
                ConversationMessage(
                    id = "create-task",
                    role = "user",
                    content = "半小时后提醒我喝水",
                    timestamp = 1L,
                ),
                ConversationMessage(
                    id = "ack",
                    role = "assistant",
                    content = "设置好了",
                    timestamp = 2L,
                ),
            ),
        )

        assertTrue(ctx.messageWindow.isEmpty())
    }

    @Test
    fun runtime_context_resolver_keeps_message_window_for_user_message() {
        val ctx = resolveWithFakeDataPort(
            event = scheduledEvent(trigger = IngressTrigger.USER_MESSAGE),
            messages = listOf(
                ConversationMessage(
                    id = "user",
                    role = "user",
                    content = "你好",
                    timestamp = 1L,
                ),
            ),
        )

        assertEquals(1, ctx.messageWindow.size)
        assertEquals("你好", ctx.messageWindow.single().content)
    }

    @Test
    fun runtime_context_resolver_merges_host_capability_tools_into_persona_snapshot() {
        val ctx = resolveWithFakeDataPort(
            event = scheduledEvent(),
            messages = emptyList(),
        )
        val enabledTools = ctx.personaToolSnapshot?.enabledTools.orEmpty()

        assertEquals(
            setOf(
                "web_search",
                "create_future_task",
                "delete_future_task",
                "list_future_tasks",
            ),
            enabledTools,
        )
    }

    private inline fun <T> withRuntimeState(block: (ResolvedRuntimeContext) -> T): T {
        val providerSnapshot = ProviderRepository.snapshotProfiles()
        val configSnapshot = ConfigRepository.snapshotProfiles()
        val selectedConfigIdSnapshot = ConfigRepository.selectedProfileId.value
        val personaSnapshot = PersonaRepository.snapshotProfiles()

        val provider = ProviderProfile(
            id = "prompt-provider",
            name = "Prompt Provider",
            baseUrl = "https://example.invalid/v1",
            model = "prompt-model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            capabilities = setOf(ProviderCapability.CHAT),
        )
        val config = ConfigProfile(
            id = "prompt-config",
            name = "Prompt Config",
            defaultChatProviderId = provider.id,
            webSearchEnabled = true,
            proactiveEnabled = true,
        )
        val persona = PersonaProfile(
            id = "prompt-persona",
            name = "Prompt Persona",
            systemPrompt = "You are concise and reliable.",
            enabledTools = setOf("web_search"),
            defaultProviderId = provider.id,
        )

        try {
            ProviderRepository.restoreProfiles(listOf(provider))
            ConfigRepository.restoreProfiles(listOf(config), config.id)
            PersonaRepository.restoreProfiles(listOf(persona))

            return block(
                RuntimeContextResolver.resolve(
                    event = RuntimeIngressEvent(
                        platform = RuntimePlatform.APP_CHAT,
                        conversationId = "conversation-1",
                        messageId = "message-1",
                        sender = SenderInfo(userId = "user-1"),
                        messageType = MessageType.OtherMessage,
                        text = "remind me tomorrow morning",
                        trigger = IngressTrigger.SCHEDULED_TASK,
                    ),
                    bot = BotProfile(
                        id = "bot-1",
                        displayName = "Prompt Bot",
                        defaultProviderId = provider.id,
                        defaultPersonaId = persona.id,
                        configProfileId = config.id,
                    ),
                    dataPort = ProductionRuntimeContextDataPort,
                ),
            )
        } finally {
            PersonaRepository.restoreProfiles(personaSnapshot)
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigIdSnapshot)
            ProviderRepository.restoreProfiles(providerSnapshot)
        }
    }

    private fun resolveWithFakeDataPort(
        event: RuntimeIngressEvent,
        messages: List<ConversationMessage>,
    ): ResolvedRuntimeContext {
        val provider = ProviderProfile(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.invalid/v1",
            model = "model-1",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            capabilities = setOf(ProviderCapability.CHAT),
        )
        val config = ConfigProfile(
            id = "config-1",
            name = "Config",
            defaultChatProviderId = provider.id,
            webSearchEnabled = true,
            proactiveEnabled = true,
        )
        val persona = PersonaProfile(
            id = "persona-1",
            name = "Persona",
            systemPrompt = "You are helpful.",
            enabledTools = emptySet(),
            defaultProviderId = provider.id,
        )
        val session = ConversationSession(
            id = event.conversationId,
            title = "Session",
            botId = "bot-1",
            personaId = persona.id,
            providerId = provider.id,
            maxContextMessages = 10,
            messages = messages,
        )
        val dataPort = object : RuntimeContextDataPort {
            override fun resolveConfig(configProfileId: String): ConfigProfile = config

            override fun listProviders(): List<ProviderProfile> = listOf(provider)

            override fun findEnabledPersona(personaId: String): PersonaProfile? = persona

            override fun session(sessionId: String): ConversationSession = session

            override fun compatibilitySnapshotForConfig(config: ConfigProfile): ResourceCenterCompatibilitySnapshot =
                ResourceCenterCompatibilitySnapshot(resources = emptyList(), projections = emptyList())
        }
        return RuntimeContextResolver.resolve(
            event = event,
            bot = BotProfile(
                id = "bot-1",
                displayName = "Bot",
                defaultProviderId = provider.id,
                defaultPersonaId = persona.id,
                configProfileId = config.id,
            ),
            dataPort = dataPort,
        )
    }

    private fun scheduledEvent(
        trigger: IngressTrigger = IngressTrigger.SCHEDULED_TASK,
        rawPlatformPayload: Any? = null,
    ): RuntimeIngressEvent {
        return RuntimeIngressEvent(
            platform = RuntimePlatform.APP_CHAT,
            conversationId = "conversation-1",
            messageId = "cron:job-1",
            sender = SenderInfo(userId = "cron:job-1", nickname = "scheduled-task"),
            messageType = MessageType.OtherMessage,
            text = "提醒用户该喝水了",
            trigger = trigger,
            rawPlatformPayload = rawPlatformPayload,
        )
    }
}
