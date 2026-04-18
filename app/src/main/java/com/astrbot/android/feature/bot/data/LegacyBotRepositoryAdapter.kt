package com.astrbot.android.feature.bot.data

import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.model.BotProfile
import kotlinx.coroutines.flow.StateFlow

class LegacyBotRepositoryAdapter : BotRepositoryPort {

    override val bots: StateFlow<List<BotProfile>>
        get() = FeatureBotRepository.botProfiles

    override val selectedBotId: StateFlow<String>
        get() = FeatureBotRepository.selectedBotId

    override fun currentBot(): BotProfile =
        FeatureBotRepository.botProfile.value

    override fun snapshotProfiles(): List<BotProfile> =
        FeatureBotRepository.snapshotProfiles()

    override suspend fun save(profile: BotProfile) {
        FeatureBotRepository.save(profile)
    }

    override suspend fun create(profile: BotProfile) {
        FeatureBotRepository.save(profile)
    }

    override suspend fun delete(id: String) {
        FeatureBotRepository.delete(id)
    }

    override suspend fun select(id: String) {
        FeatureBotRepository.select(id)
    }
}



