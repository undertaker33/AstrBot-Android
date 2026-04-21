package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.di.hilt.TtsVoiceAssets
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.config.domain.Phase3DataTransactionService
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.chat.ConversationAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val configRepository: ConfigRepositoryPort,
    private val providerRepository: ProviderRepositoryPort,
    private val botRepository: BotRepositoryPort,
    @TtsVoiceAssets private val ttsVoiceAssetsFlow: StateFlow<@JvmSuppressWildcards List<TtsVoiceReferenceAsset>>,
    private val phase3DataTransactionService: Phase3DataTransactionService,
    private val llmProviderProbePort: LlmProviderProbePort,
) : ViewModel() {
    val configProfiles: StateFlow<List<ConfigProfile>> = configRepository.profiles
    val selectedConfigProfileId: StateFlow<String> = configRepository.selectedProfileId
    val providers: StateFlow<List<ProviderProfile>> = providerRepository.providers
    val bots: StateFlow<List<BotProfile>> = botRepository.bots
    val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = ttsVoiceAssetsFlow

    fun select(profileId: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            configRepository.select(profileId)
        }
    }

    fun save(profile: ConfigProfile) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            configRepository.save(profile)
        }
    }

    fun create(): ConfigProfile {
        val created = configRepository.create()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            configRepository.select(created.id)
        }
        return created
    }

    fun delete(profileId: String) {
        viewModelScope.launch {
            phase3DataTransactionService.deleteConfigProfile(profileId)
        }
    }

    fun resolve(profileId: String): ConfigProfile {
        return configRepository.resolve(profileId)
    }

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return llmProviderProbePort.synthesizeSpeech(
            provider = provider,
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        )
    }
}
