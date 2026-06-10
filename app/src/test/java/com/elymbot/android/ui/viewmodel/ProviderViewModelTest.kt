package com.elymbot.android.ui.viewmodel

import com.elymbot.android.MainDispatcherRule
import com.elymbot.android.core.common.profile.ProviderInUseException
import com.elymbot.android.core.common.profile.ProviderReferenceChecker
import com.elymbot.android.data.ProviderRepository
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.provider.data.FeatureProviderRepository
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.provider.api.runtime.ProviderRuntimePort
import com.elymbot.android.feature.provider.api.runtime.ProviderRuntimeSttProbeResult
import org.junit.Assert.assertFalse
import com.elymbot.android.model.ConfigProfile
import com.elymbot.android.model.FeatureSupportState
import com.elymbot.android.model.ProviderCapability
import com.elymbot.android.model.ProviderProfile
import com.elymbot.android.model.ProviderType
import com.elymbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.elymbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun save_builds_provider_profile_and_delegates() {
        val providerPort = FakeProviderPort()
        val viewModel = ProviderViewModel(
            providerRepository = providerPort,
            configRepository = FakeConfigPort(),
            providerRuntime = FakeProviderRuntimePort(),
        )

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

        val saved = requireNotNull(providerPort.savedProfile)
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
        val runtimePort = FakeProviderRuntimePort(
            sttProbeResult = ProviderRuntimeSttProbeResult(
                state = FeatureSupportState.SUPPORTED,
                transcript = "hello",
            ),
        )
        val viewModel = ProviderViewModel(
            providerRepository = FakeProviderPort(),
            configRepository = FakeConfigPort(),
            providerRuntime = runtimePort,
        )

        val result = viewModel.probeSttSupport(fakeProvider())

        assertEquals(FeatureSupportState.SUPPORTED, result.state)
        assertEquals("hello", result.transcript)
        assertTrue(runtimePort.probedProviders.contains("provider-1"))
    }

    @Test
    fun fetch_models_dispatches_off_calling_thread() = runTest {
        val callingThread = Thread.currentThread()
        val runtimePort = FakeProviderRuntimePort(
            fetchModelsResult = listOf("deepseek-chat"),
            onFetchModels = {
                assertNotSame(callingThread, Thread.currentThread())
            },
        )
        val viewModel = ProviderViewModel(
            providerRepository = FakeProviderPort(),
            configRepository = FakeConfigPort(),
            providerRuntime = runtimePort,
        )

        val result = viewModel.fetchModels(fakeProvider())

        assertEquals(listOf("deepseek-chat"), result)
    }

    /**
     * Task10 Phase3 鈥?Task C: deleting a referenced provider must surface a stable error.
     * The actual enforcement is in FeatureProviderRepository; here we verify the guard
     * contract is exercised via the ProviderReferenceGuard.
     */
    @Test
    fun delete_referenced_provider_is_blocked_by_reference_guard() {
        val inUseId = "provider-in-use"
        installProviderReferenceChecker { providerId -> providerId == inUseId }
        val originalProviders = ProviderRepository.snapshotProfiles()

        try {
            ProviderRepository.restoreProfiles(
                listOf(
                    com.elymbot.android.model.ProviderProfile(id = inUseId, name = "In-Use Provider", baseUrl = "", model = "", providerType = com.elymbot.android.model.ProviderType.OPENAI_COMPATIBLE, apiKey = "", capabilities = emptySet()),
                    com.elymbot.android.model.ProviderProfile(id = "provider-spare", name = "Spare Provider", baseUrl = "", model = "", providerType = com.elymbot.android.model.ProviderType.OPENAI_COMPATIBLE, apiKey = "", capabilities = emptySet()),
                ),
            )

            try {
                ProviderRepository.delete(inUseId)
                org.junit.Assert.fail("Expected ProviderInUseException but delete succeeded")
            } catch (e: ProviderInUseException) {
                assertEquals(inUseId, e.providerId)
            }

            org.junit.Assert.assertTrue(
                "In-use provider must still be present after blocked delete",
                ProviderRepository.snapshotProfiles().any { it.id == inUseId },
            )
        } finally {
            installProviderReferenceChecker { false }
            ProviderRepository.restoreProfiles(originalProviders)
        }
    }

    @Test
    fun delete_unreferenced_provider_succeeds() = runBlocking {
        installProviderReferenceChecker { false }
        val originalProviders = ProviderRepository.snapshotProfiles()
        val providerId = "provider-delete-unreferenced"
        val spareProviderId = "provider-delete-spare"

        try {
            ProviderRepository.restoreProfiles(
                listOf(
                    com.elymbot.android.model.ProviderProfile(id = providerId, name = "Provider A", baseUrl = "", model = "", providerType = com.elymbot.android.model.ProviderType.OPENAI_COMPATIBLE, apiKey = "", capabilities = emptySet()),
                    com.elymbot.android.model.ProviderProfile(id = spareProviderId, name = "Provider B", baseUrl = "", model = "", providerType = com.elymbot.android.model.ProviderType.OPENAI_COMPATIBLE, apiKey = "", capabilities = emptySet()),
                ),
            )

            ProviderRepository.delete(providerId)
            awaitProviderAbsent(providerId)

            org.junit.Assert.assertFalse(
                ProviderRepository.snapshotProfiles().any { it.id == providerId },
            )
        } finally {
            installProviderReferenceChecker { false }
            ProviderRepository.restoreProfiles(originalProviders)
        }
    }

    @Test
    fun probe_stt_support_dispatches_off_calling_thread() = runTest {
        val callingThread = Thread.currentThread()
        val runtimePort = FakeProviderRuntimePort(
            sttProbeResult = ProviderRuntimeSttProbeResult(
                state = FeatureSupportState.SUPPORTED,
                transcript = "hello",
            ),
            onProbeStt = {
                assertNotSame(callingThread, Thread.currentThread())
            },
        )
        val viewModel = ProviderViewModel(
            providerRepository = FakeProviderPort(),
            configRepository = FakeConfigPort(),
            providerRuntime = runtimePort,
        )

        val result = viewModel.probeSttSupport(fakeProvider())

        assertEquals(FeatureSupportState.SUPPORTED, result.state)
        assertEquals("hello", result.transcript)
    }

    @Test
    fun synthesize_speech_dispatches_off_calling_thread() = runTest {
        val callingThread = Thread.currentThread()
        val attachment = ConversationAttachment(
            id = "audio-1",
            type = "audio",
            fileName = "preview.wav",
            mimeType = "audio/wav",
            base64Data = "ZmFrZQ==",
        )
        val runtimePort = FakeProviderRuntimePort(
            synthesizedAttachment = attachment,
            onSynthesizeSpeech = {
                assertNotSame(callingThread, Thread.currentThread())
            },
        )
        val viewModel = ProviderViewModel(
            providerRepository = FakeProviderPort(),
            configRepository = FakeConfigPort(),
            providerRuntime = runtimePort,
        )

        val result = viewModel.synthesizeSpeech(
            provider = fakeProvider(),
            text = "hello",
            voiceId = "voice-1",
            readBracketedContent = true,
        )

        assertEquals("audio-1", result.id)
        assertEquals("preview.wav", result.fileName)
    }

    private class FakeProviderPort : ProviderRepositoryPort {
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(emptyList())

        var savedProfile: ProviderProfile? = null

        override fun snapshotProfiles(): List<ProviderProfile> = providers.value

        override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> =
            providers.value.filter { capability in it.capabilities }

        override fun toggleEnabled(id: String) = Unit

        override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateSttProbeSupport(id: String, support: FeatureSupportState) = Unit

        override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) = Unit

        override suspend fun save(profile: ProviderProfile) {
            savedProfile = profile
        }

        override suspend fun delete(id: String) = Unit
    }

    private class FakeConfigPort : ConfigRepositoryPort {
        override val profiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(emptyList())
        override val selectedProfileId: StateFlow<String> = MutableStateFlow("default")

        override fun snapshotProfiles(): List<ConfigProfile> = profiles.value

        override fun create(name: String): ConfigProfile = ConfigProfile(id = "created-config", name = name)

        override fun resolve(id: String): ConfigProfile = ConfigProfile(id = id)

        override fun resolveExistingId(id: String?): String = id ?: selectedProfileId.value

        override suspend fun save(profile: ConfigProfile) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun select(id: String) = Unit
    }

    private class FakeProviderRuntimePort(
        private val sttProbeResult: ProviderRuntimeSttProbeResult = ProviderRuntimeSttProbeResult(
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
    ) : ProviderRuntimePort {
        val probedProviders = mutableListOf<String>()
        override val voiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = MutableStateFlow(emptyList())

        override fun fetchModels(provider: ProviderProfile): List<String> {
            onFetchModels()
            return fetchModelsResult
        }

        override fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun probeSttSupport(provider: ProviderProfile): ProviderRuntimeSttProbeResult {
            onProbeStt()
            probedProviders += provider.id
            return sttProbeResult
        }

        override fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState = FeatureSupportState.UNKNOWN

        override fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> = emptyList()

        override fun importReferenceAudio(
            context: android.content.Context,
            sourceUri: android.net.Uri,
            name: String,
            assetId: String?,
        ): com.elymbot.android.feature.provider.api.runtime.VoiceAssetImportResult {
            error("Not needed in test")
        }

        override fun saveVoiceBinding(
            assetId: String,
            providerId: String,
            providerType: ProviderType,
            model: String,
            voiceId: String,
            displayName: String,
        ) = Unit

        override fun renameVoiceBinding(assetId: String, bindingId: String, displayName: String) = Unit

        override fun clearReferenceAudio(assetId: String) = Unit

        override fun deleteReferenceClip(assetId: String, clipId: String) = Unit

        override fun deleteVoiceBinding(assetId: String, bindingId: String) = Unit

        override fun ttsAssetState(context: android.content.Context): com.elymbot.android.feature.provider.api.runtime.ProviderRuntimeTtsAssetState {
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

    private fun installProviderReferenceChecker(checker: ProviderReferenceChecker) {
        ProviderRepository.snapshotProfiles()
        val delegateField = FeatureProviderRepository::class.java.getDeclaredField("delegate")
        delegateField.isAccessible = true
        val store = checkNotNull(delegateField.get(FeatureProviderRepository))
        val checkerField = store.javaClass.getDeclaredField("providerReferenceChecker")
        checkerField.isAccessible = true
        checkerField.set(store, checker)
    }

    private suspend fun awaitProviderAbsent(providerId: String) {
        withTimeout(2_000) {
            while (ProviderRepository.snapshotProfiles().any { it.id == providerId }) {
                delay(10)
            }
        }
    }
}
