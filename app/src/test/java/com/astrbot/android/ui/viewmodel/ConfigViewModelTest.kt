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

    private class FakeConfigPort : ConfigRepositoryPort {
        override val profiles: StateFlow<List<ConfigProfile>> =
            MutableStateFlow(listOf(ConfigProfile(id = "config-1"), ConfigProfile(id = "config-2")))
        override val selectedProfileId: StateFlow<String> = MutableStateFlow("config-1")

        val directDeleteIds = mutableListOf<String>()

        override fun snapshotProfiles(): List<ConfigProfile> = profiles.value

        override fun create(name: String): ConfigProfile = ConfigProfile(id = "new-config", name = name)

        override fun resolve(profileId: String): ConfigProfile {
            return profiles.value.firstOrNull { it.id == profileId } ?: ConfigProfile(id = profileId)
        }

        override fun resolveExistingId(id: String?): String = id ?: selectedProfileId.value

        override suspend fun save(profile: ConfigProfile) = Unit

        override suspend fun delete(profileId: String) {
            directDeleteIds += profileId
        }

        override suspend fun select(profileId: String) = Unit
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

    private class FakeBotPort : BotRepositoryPort {
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(emptyList())
        override val selectedBotId: StateFlow<String> = MutableStateFlow("bot-1")

        override fun currentBot(): BotProfile = BotProfile(id = "bot-1", displayName = "Bot")

        override fun snapshotProfiles(): List<BotProfile> = bots.value

        override fun create(name: String): BotProfile = BotProfile(id = "created-bot", displayName = name)

        override suspend fun save(profile: BotProfile) = Unit

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
