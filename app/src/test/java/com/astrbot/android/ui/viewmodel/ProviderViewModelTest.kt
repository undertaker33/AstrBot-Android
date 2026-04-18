package com.astrbot.android.ui.viewmodel

import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.di.ProviderViewModelDependencies
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderViewModelTest {
    @Test
    fun save_builds_provider_profile_and_delegates() {
        val deps = FakeProviderDependencies()
        val viewModel = ProviderViewModel(deps)

        viewModel.save(
            id = null,
            name = " Vision ",
            baseUrl = " https://example.com/v1 ",
            model = " gpt-4.1 ",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = " secret ",
            capabilities = setOf(ProviderCapability.CHAT, ProviderCapability.TTS),
            enabled = false,
            multimodalRuleSupport = FeatureSupportState.SUPPORTED,
            ttsVoiceOptions = listOf("alloy"),
        )

        val saved = requireNotNull(deps.savedProfile)
        assertEquals("", saved.id)
        assertEquals(" Vision ", saved.name)
        assertEquals(" https://example.com/v1 ", saved.baseUrl)
        assertEquals(" gpt-4.1 ", saved.model)
        assertEquals(FeatureSupportState.SUPPORTED, saved.multimodalRuleSupport)
        assertEquals(setOf(ProviderCapability.CHAT, ProviderCapability.TTS), saved.capabilities)
        assertEquals(listOf("alloy"), saved.ttsVoiceOptions)
        assertEquals(false, saved.enabled)
    }

    @Test
    fun probe_stt_support_wraps_dependency_result() = runTest {
        val deps = FakeProviderDependencies(
            sttProbeResult = ChatCompletionService.SttProbeResult(
                state = FeatureSupportState.SUPPORTED,
                transcript = "hello",
            ),
        )
        val viewModel = ProviderViewModel(deps)

        val result = viewModel.probeSttSupport(fakeProvider())

        assertEquals(FeatureSupportState.SUPPORTED, result.state)
        assertEquals("hello", result.transcript)
        assertTrue(deps.probedProviders.contains("provider-1"))
    }

    @Test
    fun fetch_models_dispatches_off_calling_thread() = runTest {
        val callingThreadId = Thread.currentThread().id
        val deps = FakeProviderDependencies(
            fetchModelsResult = listOf("deepseek-chat"),
            onFetchModels = {
                assertNotEquals(callingThreadId, Thread.currentThread().id)
            },
        )
        val viewModel = ProviderViewModel(deps)

        val result = viewModel.fetchModels(fakeProvider())

        assertEquals(listOf("deepseek-chat"), result)
    }

    @Test
    fun probe_stt_support_dispatches_off_calling_thread() = runTest {
        val callingThreadId = Thread.currentThread().id
        val deps = FakeProviderDependencies(
            sttProbeResult = ChatCompletionService.SttProbeResult(
                state = FeatureSupportState.SUPPORTED,
                transcript = "hello",
            ),
            onProbeStt = {
                assertNotEquals(callingThreadId, Thread.currentThread().id)
            },
        )
        val viewModel = ProviderViewModel(deps)

        val result = viewModel.probeSttSupport(fakeProvider())

        assertEquals(FeatureSupportState.SUPPORTED, result.state)
        assertEquals("hello", result.transcript)
    }

    @Test
    fun synthesize_speech_dispatches_off_calling_thread() = runTest {
        val callingThreadId = Thread.currentThread().id
        val attachment = ConversationAttachment(
            id = "audio-1",
            type = "audio",
            fileName = "preview.wav",
            mimeType = "audio/wav",
            base64Data = "ZmFrZQ==",
        )
        val deps = FakeProviderDependencies(
            synthesizedAttachment = attachment,
            onSynthesizeSpeech = {
                assertNotEquals(callingThreadId, Thread.currentThread().id)
            },
        )
        val viewModel = ProviderViewModel(deps)

        val result = viewModel.synthesizeSpeech(
            provider = fakeProvider(),
            text = "hello",
            voiceId = "voice-1",
            readBracketedContent = true,
        )

        assertEquals("audio-1", result.id)
        assertEquals("preview.wav", result.fileName)
    }

    private class FakeProviderDependencies(
        private val sttProbeResult: ChatCompletionService.SttProbeResult = ChatCompletionService.SttProbeResult(
            state = FeatureSupportState.UNKNOWN,
            transcript = "",
        ),
        private val fetchModelsResult: List<String> = emptyList(),
        private val synthesizedAttachment: ConversationAttachment = ConversationAttachment(
            id = "audio-default",
            type = "audio",
            fileName = "default.wav",
            mimeType = "audio/wav",
            base64Data = "",
        ),
        private val onFetchModels: () -> Unit = {},
        private val onProbeStt: () -> Unit = {},
        private val onSynthesizeSpeech: () -> Unit = {},
    ) : ProviderViewModelDependencies {
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(emptyList())
        override val configProfiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(emptyList())
        override val selectedConfigProfileId: StateFlow<String> = MutableStateFlow("default")

        var savedProfile: ProviderProfile? = null
        val probedProviders = mutableListOf<String>()

        override fun save(profile: ProviderProfile) {
            savedProfile = profile
        }

        override fun saveConfig(profile: ConfigProfile) = Unit

        override fun toggleEnabled(id: String) = Unit

        override fun delete(id: String) = Unit

        override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateSttProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun fetchModels(provider: ProviderProfile): List<String> {
            onFetchModels()
            return fetchModelsResult
        }

        override fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeSttSupport(provider: ProviderProfile): ChatCompletionService.SttProbeResult {
            onProbeStt()
            probedProviders += provider.id
            return sttProbeResult
        }

        override fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> = emptyList()

        override fun ttsAssetState(context: android.content.Context): com.astrbot.android.core.runtime.audio.SherpaOnnxAssetManager.TtsAssetState {
            error("Not needed in test")
        }

        override fun isSherpaFrameworkReady(): Boolean = false

        override fun isSherpaSttReady(): Boolean = false

        override fun synthesizeSpeech(
            provider: ProviderProfile,
            text: String,
            voiceId: String,
            readBracketedContent: Boolean,
        ): ConversationAttachment {
            onSynthesizeSpeech()
            return synthesizedAttachment
        }
    }

    private fun fakeProvider(): ProviderProfile {
        return ProviderProfile(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1",
            model = "model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "secret",
            capabilities = setOf(ProviderCapability.CHAT),
        )
    }
}
