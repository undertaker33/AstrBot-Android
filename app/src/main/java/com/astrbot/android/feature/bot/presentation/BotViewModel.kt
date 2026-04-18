package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.di.BotViewModelDependencies
import com.astrbot.android.di.DefaultBotViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.flow.StateFlow

class BotViewModel(
    private val dependencies: BotViewModelDependencies = DefaultBotViewModelDependencies,
) : ViewModel() {
    val botProfile: StateFlow<BotProfile> = dependencies.botProfile
    val botProfiles: StateFlow<List<BotProfile>> = dependencies.botProfiles
    val selectedBotId: StateFlow<String> = dependencies.selectedBotId
    val providers: StateFlow<List<ProviderProfile>> = dependencies.providers
    val personas: StateFlow<List<PersonaProfile>> = dependencies.personas
    val configProfiles: StateFlow<List<ConfigProfile>> = dependencies.configProfiles
    val loginState: StateFlow<NapCatLoginState> = dependencies.loginState

    fun select(botId: String) {
        dependencies.select(botId)
    }

    fun save(profile: BotProfile) {
        dependencies.save(profile)
    }

    fun saveWithConfigModel(profile: BotProfile, defaultChatProviderId: String) {
        val resolvedConfig = dependencies.resolveConfig(profile.configProfileId)
        dependencies.saveConfig(
            resolvedConfig.copy(defaultChatProviderId = defaultChatProviderId),
        )
        dependencies.save(profile.copy(defaultProviderId = defaultChatProviderId))
    }

    fun create() {
        dependencies.create()
    }

    fun deleteSelected(): Result<Unit> {
        return runCatching {
            dependencies.delete(botProfile.value.id)
        }
    }
}
