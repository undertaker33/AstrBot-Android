package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import kotlinx.coroutines.flow.StateFlow

class ConfigViewModel : ViewModel() {
    val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    val selectedConfigProfileId: StateFlow<String> = ConfigRepository.selectedProfileId
    val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    val bots: StateFlow<List<BotProfile>> = BotRepository.botProfiles

    fun select(profileId: String) {
        ConfigRepository.select(profileId)
    }

    fun save(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    fun create() {
        val created = ConfigRepository.create()
        ConfigRepository.select(created.id)
    }

    fun delete(profileId: String) {
        val fallbackId = ConfigRepository.delete(profileId)
        BotRepository.replaceConfigBinding(profileId, fallbackId)
    }
}
