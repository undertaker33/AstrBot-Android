package com.astrbot.android.ui.viewmodel

import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.core.runtime.llm.SttProbeResult
import com.astrbot.android.feature.config.domain.Phase3DataTransactionService
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    /**
     * Task10 Phase3 – Task A contract:
     * ConfigViewModel.delete() must delegate to a single atomic entry point,
     * NOT chain delete() + replaceConfigBinding() from the presentation layer.
     */
    @Test
    fun delete_delegates_to_single_entry_point_not_separate_steps() = runTest(dispatcher) {
        val configPort = FakeConfigPort()
        val deleteService = FakePhase3DataTransactionService()
        val viewModel = ConfigViewModel(
            configRepository = configPort,
            providerRepository = FakeProviderPort(),
            botRepository = FakeBotPort(),
            ttsVoiceAssetsFlow = MutableStateFlow(emptyList()),
            phase3DataTransactionService = deleteService,
            llmProviderProbePort = FakeLlmProviderProbePort(),
        )

        viewModel.delete("config-1")

        assertTrue("delete() must defer work to viewModelScope", deleteService.deleteConfigProfileIds.isEmpty())
        advanceUntilIdle()
        assertEquals("delete() must call deleteConfigProfile() once", listOf("config-1"), deleteService.deleteConfigProfileIds)
        assertTrue("delete() must NOT call delete() directly", configPort.directDeleteIds.isEmpty())
    }

    @Test
    fun deleteConfigProfile_receives_the_correct_profile_id() = runTest(dispatcher) {
        val deleteService = FakePhase3DataTransactionService()
        val viewModel = ConfigViewModel(
            configRepository = FakeConfigPort(),
            providerRepository = FakeProviderPort(),
            botRepository = FakeBotPort(),
            ttsVoiceAssetsFlow = MutableStateFlow(emptyList()),
            phase3DataTransactionService = deleteService,
            llmProviderProbePort = FakeLlmProviderProbePort(),
        )

        viewModel.delete("my-special-config")

        assertTrue(deleteService.deleteConfigProfileIds.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("my-special-config"), deleteService.deleteConfigProfileIds)
    }

    @Test
    fun save_selected_config_rebinds_selected_bot_after_saved_config_is_visible() = runTest(dispatcher) {
        val configPort = FakeConfigPort(
            selectedProfileId = "config-qwen",
            profiles = listOf(
                ConfigProfile(id = "config-qwen", defaultChatProviderId = "deepseek-chat"),
                ConfigProfile(id = "config-deepseek", defaultChatProviderId = "deepseek-chat"),
            ),
            exposeSavesOnlyAfterEmit = true,
        )
        val botPort = FakeBotPort(
            configPort = configPort,
            bots = listOf(
                BotProfile(
                    id = "bot-1",
                    displayName = "QQ Bot",
                    configProfileId = "config-deepseek",
                    defaultProviderId = "deepseek-chat",
                ),
            ),
            selectedBotId = "bot-1",
        )
        val viewModel = ConfigViewModel(
            configRepository = configPort,
            providerRepository = FakeProviderPort(),
            botRepository = botPort,
            ttsVoiceAssetsFlow = MutableStateFlow(emptyList()),
            phase3DataTransactionService = FakePhase3DataTransactionService(),
            llmProviderProbePort = FakeLlmProviderProbePort(),
        )

        viewModel.save(ConfigProfile(id = "config-qwen", defaultChatProviderId = "qwen-chat"))

        runCurrent()
        assertTrue("bot must wait until saved config is visible before rebinding", botPort.savedProfiles.isEmpty())
        assertEquals(listOf(ConfigProfile(id = "config-qwen", defaultChatProviderId = "qwen-chat")), configPort.savedProfiles)

        configPort.emitSavedProfiles()
        advanceUntilIdle()
        assertEquals("config-qwen", botPort.savedProfiles.single().configProfileId)
        assertEquals("qwen-chat", botPort.savedProfiles.single().defaultProviderId)
    }

    @Test
    fun save_rebinds_selected_bot_even_when_selected_config_flow_lags_behind_save() = runTest(dispatcher) {
        val configPort = FakeConfigPort(
            selectedProfileId = "config-deepseek",
            profiles = listOf(
                ConfigProfile(id = "config-qwen", defaultChatProviderId = "deepseek-chat"),
                ConfigProfile(id = "config-deepseek", defaultChatProviderId = "deepseek-chat"),
            ),
            exposeSavesOnlyAfterEmit = true,
        )
        val botPort = FakeBotPort(
            configPort = configPort,
            bots = listOf(
                BotProfile(
                    id = "bot-1",
                    displayName = "QQ Bot",
                    configProfileId = "config-deepseek",
                    defaultProviderId = "deepseek-chat",
                ),
            ),
            selectedBotId = "bot-1",
        )
        val viewModel = ConfigViewModel(
            configRepository = configPort,
            providerRepository = FakeProviderPort(),
            botRepository = botPort,
            ttsVoiceAssetsFlow = MutableStateFlow(emptyList()),
            phase3DataTransactionService = FakePhase3DataTransactionService(),
            llmProviderProbePort = FakeLlmProviderProbePort(),
        )

        viewModel.save(ConfigProfile(id = "config-qwen", defaultChatProviderId = "qwen-chat"))
        runCurrent()
        configPort.emitSavedProfiles()
        advanceUntilIdle()

        assertEquals("config-qwen", botPort.savedProfiles.single().configProfileId)
        assertEquals("qwen-chat", botPort.savedProfiles.single().defaultProviderId)
    }

    private class FakeConfigPort(
        selectedProfileId: String = "config-1",
        profiles: List<ConfigProfile> = listOf(ConfigProfile(id = "config-1"), ConfigProfile(id = "config-2")),
        private val exposeSavesOnlyAfterEmit: Boolean = false,
    ) : ConfigRepositoryPort {
        private val mutableProfiles = MutableStateFlow(profiles)
        override val profiles: StateFlow<List<ConfigProfile>> = mutableProfiles
        override val selectedProfileId: StateFlow<String> = MutableStateFlow(selectedProfileId)

        val directDeleteIds = mutableListOf<String>()
        val savedProfiles = mutableListOf<ConfigProfile>()

        override fun snapshotProfiles(): List<ConfigProfile> = profiles.value

        override fun create(name: String): ConfigProfile = ConfigProfile(id = "new-config", name = name)

        override fun resolve(id: String): ConfigProfile {
            return profiles.value.firstOrNull { it.id == id } ?: ConfigProfile(id = id)
        }

        override fun resolveExistingId(id: String?): String = id ?: selectedProfileId.value

        override suspend fun save(profile: ConfigProfile) {
            savedProfiles += profile
            if (!exposeSavesOnlyAfterEmit) {
                emitSavedProfiles()
            }
        }

        override suspend fun delete(id: String) {
            directDeleteIds += id
        }

        override suspend fun select(id: String) = Unit

        fun emitSavedProfiles() {
            val savedById = savedProfiles.associateBy { it.id }
            mutableProfiles.value = mutableProfiles.value.map { profile ->
                savedById[profile.id] ?: profile
            }
        }
    }

    private class FakeProviderPort : ProviderRepositoryPort {
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(emptyList())

        override fun snapshotProfiles(): List<ProviderProfile> = providers.value

        override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> = emptyList()

        override fun toggleEnabled(id: String) = Unit

        override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateSttProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) = Unit

        override suspend fun save(profile: ProviderProfile) = Unit

        override suspend fun delete(id: String) = Unit
    }

    private class FakeBotPort(
        bots: List<BotProfile> = emptyList(),
        selectedBotId: String = "bot-1",
        private val configPort: ConfigRepositoryPort? = null,
    ) : BotRepositoryPort {
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(bots)
        override val selectedBotId: StateFlow<String> = MutableStateFlow(selectedBotId)
        val savedProfiles = mutableListOf<BotProfile>()

        override fun currentBot(): BotProfile = BotProfile(id = "bot-1", displayName = "Bot")

        override fun snapshotProfiles(): List<BotProfile> = bots.value

        override fun create(name: String): BotProfile = BotProfile(id = "created-bot", displayName = name)

        override suspend fun save(profile: BotProfile) {
            val configDefaultProviderId = configPort
                ?.resolve(profile.configProfileId)
                ?.defaultChatProviderId
                .orEmpty()
            savedProfiles += if (configDefaultProviderId.isBlank()) {
                profile
            } else {
                profile.copy(defaultProviderId = configDefaultProviderId)
            }
        }

        override suspend fun create(profile: BotProfile) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun select(id: String) = Unit
    }

    private class FakePhase3DataTransactionService : Phase3DataTransactionService {
        val deleteConfigProfileIds = mutableListOf<String>()

        override suspend fun deleteConfigProfile(profileId: String) {
            deleteConfigProfileIds += profileId
        }

        override suspend fun deleteBotProfile(botId: String) = Unit
    }

    private class FakeLlmProviderProbePort : LlmProviderProbePort {
        override fun fetchModels(
            baseUrl: String,
            apiKey: String,
            providerType: com.astrbot.android.model.ProviderType,
        ): List<String> = emptyList()

        override fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeSttSupport(provider: ProviderProfile): SttProbeResult {
            return SttProbeResult(state = FeatureSupportState.UNKNOWN, transcript = "")
        }

        override fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun transcribeAudio(
            provider: ProviderProfile,
            attachment: ConversationAttachment,
        ): String = ""

        override fun synthesizeSpeech(
            provider: ProviderProfile,
            text: String,
            voiceId: String,
            readBracketedContent: Boolean,
        ): ConversationAttachment {
            return ConversationAttachment(id = "preview", type = "audio", mimeType = "audio/wav")
        }
    }
}
