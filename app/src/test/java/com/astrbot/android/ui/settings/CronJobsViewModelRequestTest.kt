package com.astrbot.android.ui.settings

import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.BotProfile
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTargetContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CronJobsViewModelRequestTest {
    @Test
    fun build_create_future_task_request_preserves_bot_derived_target_context_and_payload() {
        val selectedBot = BotProfile(
            id = "bot-1",
            platformName = "QQ",
            displayName = "Primary Bot",
            configProfileId = "cfg-1",
            defaultPersonaId = "persona-1",
            defaultProviderId = "provider-1",
        )
        val draft = CronJobEditorDraft(
            name = "Morning check",
            cronExpression = "0 9 * * *",
            conversationId = ConversationRepository.DEFAULT_SESSION_ID,
            selectedBotId = selectedBot.id,
        )
        val target = draft.toTargetContext(selectedBot)

        val request = buildCronJobCreateRequest(
            name = draft.name,
            cronExpression = draft.cronExpression,
            runAt = "",
            note = "Summarize overnight messages",
            runOnce = false,
            targetContext = target,
        )

        assertEquals(target.platform, request.targetPlatform)
        assertEquals(target.conversationId, request.targetConversationId)
        assertEquals(target.botId, request.targetBotId)
        assertEquals(target.configProfileId, request.targetConfigProfileId)
        assertEquals(target.personaId, request.targetPersonaId)
        assertEquals(target.providerId, request.targetProviderId)
        assertEquals(target.origin, request.targetOrigin)
        assertEquals("Morning check", request.payload["name"])
        assertEquals("0 9 * * *", request.payload["cron_expression"])
        assertEquals("", request.payload["run_at"])
        assertEquals("Summarize overnight messages", request.payload["note"])
        assertEquals(false, request.payload["run_once"])
        assertEquals(true, request.payload["enabled"])
        assertEquals("ui", request.payload["origin"])
        assertTrue((request.payload["timezone"] as String).isNotBlank())
        assertFalse((request.payload["timezone"] as String).isBlank())
    }

    @Test
    fun bot_target_context_helper_uses_default_app_chat_platform_and_conversation_when_not_overridden() {
        val selectedBot = BotProfile(
            id = "bot-2",
            platformName = "QQ",
            displayName = "Secondary Bot",
            configProfileId = "cfg-2",
            defaultPersonaId = "persona-2",
            defaultProviderId = "provider-2",
        )

        val target = selectedBot.toCronJobTargetContext()

        assertEquals(RuntimePlatform.APP_CHAT.wireValue, target.platform)
        assertEquals(ConversationRepository.DEFAULT_SESSION_ID, target.conversationId)
        assertEquals("bot-2", target.botId)
        assertEquals("cfg-2", target.configProfileId)
        assertEquals("persona-2", target.personaId)
        assertEquals("provider-2", target.providerId)
        assertEquals("ui", target.origin)
    }

    @Test
    fun resolve_default_target_context_uses_injected_ports_for_selected_bot_config_and_provider_fallback() {
        val target = resolveDefaultCronJobTargetContext(
            botPort = FakeBotRepositoryPort(
                bots = listOf(
                    BotProfile(
                        id = "bot-1",
                        displayName = "Primary",
                        configProfileId = "cfg-selected",
                        defaultPersonaId = "persona-1",
                    ),
                    BotProfile(
                        id = "bot-2",
                        displayName = "Secondary",
                        configProfileId = "cfg-secondary",
                        defaultPersonaId = "persona-2",
                        defaultProviderId = "provider-bot",
                    ),
                ),
                selectedBotId = "bot-1",
            ),
            configPort = FakeConfigRepositoryPort(
                selectedProfileId = "cfg-fallback",
                configs = listOf(
                    ConfigProfile(
                        id = "cfg-selected",
                        name = "Selected",
                        defaultChatProviderId = "provider-config",
                    ),
                ),
            ),
            providerPort = FakeProviderRepositoryPort(
                providers = listOf(
                    providerProfile(id = "provider-disabled", enabled = false, capabilities = setOf(ProviderCapability.CHAT)),
                    providerProfile(id = "provider-fallback", enabled = true, capabilities = setOf(ProviderCapability.CHAT)),
                ),
            ),
        )

        assertEquals(RuntimePlatform.APP_CHAT.wireValue, target.platform)
        assertEquals(ConversationRepository.DEFAULT_SESSION_ID, target.conversationId)
        assertEquals("bot-1", target.botId)
        assertEquals("cfg-selected", target.configProfileId)
        assertEquals("persona-1", target.personaId)
        assertEquals("provider-config", target.providerId)
        assertEquals("ui", target.origin)
    }
}

private class FakeBotRepositoryPort(
    bots: List<BotProfile>,
    selectedBotId: String,
) : BotRepositoryPort {
    override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(bots)
    override val selectedBotId: StateFlow<String> = MutableStateFlow(selectedBotId)

    override fun currentBot(): BotProfile = bots.value.first()

    override fun snapshotProfiles(): List<BotProfile> = bots.value

    override fun create(name: String): BotProfile = BotProfile(id = name, displayName = name)

    override suspend fun save(profile: BotProfile) = Unit

    override suspend fun create(profile: BotProfile) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun select(id: String) = Unit
}

private class FakeConfigRepositoryPort(
    selectedProfileId: String,
    configs: List<ConfigProfile>,
) : ConfigRepositoryPort {
    override val profiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(configs)
    override val selectedProfileId: StateFlow<String> = MutableStateFlow(selectedProfileId)

    override fun snapshotProfiles(): List<ConfigProfile> = profiles.value

    override fun create(name: String): ConfigProfile = ConfigProfile(id = name, name = name)

    override fun resolve(id: String): ConfigProfile =
        profiles.value.firstOrNull { it.id == id } ?: ConfigProfile(id = id)

    override fun resolveExistingId(id: String?): String = id.orEmpty()

    override suspend fun save(profile: ConfigProfile) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun select(id: String) = Unit
}

private class FakeProviderRepositoryPort(
    providers: List<ProviderProfile>,
) : ProviderRepositoryPort {
    override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(providers)

    override fun snapshotProfiles(): List<ProviderProfile> = providers.value

    override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> =
        providers.value.filter { capability in it.capabilities }

    override fun toggleEnabled(id: String) = Unit

    override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) = Unit

    override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) = Unit

    override fun updateSttProbeSupport(id: String, support: FeatureSupportState) = Unit

    override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) = Unit

    override suspend fun save(profile: ProviderProfile) = Unit

    override suspend fun delete(id: String) = Unit
}

private fun providerProfile(
    id: String,
    enabled: Boolean,
    capabilities: Set<ProviderCapability>,
): ProviderProfile {
    return ProviderProfile(
        id = id,
        name = id,
        baseUrl = "https://example.com",
        model = "model",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        apiKey = "key",
        capabilities = capabilities,
        enabled = enabled,
    )
}
