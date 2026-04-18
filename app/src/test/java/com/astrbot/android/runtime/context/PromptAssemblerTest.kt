package com.astrbot.android.core.runtime.context

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
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
        withRuntimeState { ctx ->
            val prompt = PromptAssembler.assemble(ctx)

            assertTrue(prompt!!.contains("scheduled task"))
            assertTrue(prompt.contains("not a normal chat turn"))
            assertTrue(prompt.contains("Do not greet"))
            assertTrue(prompt.contains("must send the reminder now"))
        }
    }

    @Test
    fun runtime_context_resolver_merges_host_capability_tools_into_persona_snapshot() {
        withRuntimeState { ctx ->
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
                ),
            )
        } finally {
            PersonaRepository.restoreProfiles(personaSnapshot)
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigIdSnapshot)
            ProviderRepository.restoreProfiles(providerSnapshot)
        }
    }
}
