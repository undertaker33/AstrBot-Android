package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.core.common.logging.AppLogger
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
            val visibleProfile = waitForVisibleSavedProfile(profile)
            if (visibleProfile != null) {
                syncSelectedBotBinding(visibleProfile)
            } else {
                AppLogger.append("Config bot binding sync skipped: saved config not visible id=${profile.id}")
            }
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

    private suspend fun syncSelectedBotBinding(profile: ConfigProfile) {
        if (profile.id.isBlank()) {
            return
        }
        val selectedBot = botRepository.bots.value.firstOrNull { bot ->
            bot.id == botRepository.selectedBotId.value
        } ?: run {
            AppLogger.append("Config bot binding sync skipped: selected bot not found config=${profile.id}")
            return
        }
        val updated = selectedBot.copy(
            configProfileId = profile.id,
            defaultProviderId = profile.defaultChatProviderId,
        )
        if (updated != selectedBot) {
            botRepository.save(updated)
            AppLogger.append(
                "Config bot binding sync requested: bot=${updated.id} config=${updated.configProfileId} " +
                    "provider=${updated.defaultProviderId.ifBlank { "none" }}",
            )
        } else {
            AppLogger.append(
                "Config bot binding already current: bot=${selectedBot.id} config=${selectedBot.configProfileId} " +
                    "provider=${selectedBot.defaultProviderId.ifBlank { "none" }}",
            )
        }
    }

    private suspend fun waitForVisibleSavedProfile(profile: ConfigProfile): ConfigProfile? {
        if (profile.id.isBlank()) {
            return null
        }
        return withTimeoutOrNull(CONFIG_SAVE_VISIBILITY_TIMEOUT_MS) {
            configRepository.profiles.first { profiles ->
                profiles.any { candidate ->
                    candidate.id == profile.id &&
                        candidate.defaultChatProviderId == profile.defaultChatProviderId
                }
            }.first { candidate -> candidate.id == profile.id }
        }
    }

    private companion object {
        private const val CONFIG_SAVE_VISIBILITY_TIMEOUT_MS = 5_000L
    }
}
