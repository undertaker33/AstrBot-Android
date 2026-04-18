package com.astrbot.android.feature.bot.data

import com.astrbot.android.data.BotRepository
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.model.BotProfile
import kotlinx.coroutines.flow.StateFlow

class LegacyBotRepositoryAdapter : BotRepositoryPort {

    override val bots: StateFlow<List<BotProfile>>
        get() = BotRepository.botProfiles

    override val selectedBotId: StateFlow<String>
        get() = BotRepository.selectedBotId

    override fun currentBot(): BotProfile =
        BotRepository.botProfile.value

    override fun snapshotProfiles(): List<BotProfile> =
        BotRepository.snapshotProfiles()

    override suspend fun save(profile: BotProfile) {
        BotRepository.save(profile)
    }

    override suspend fun create(profile: BotProfile) {
        BotRepository.save(profile)
    }

    override suspend fun delete(id: String) {
        BotRepository.delete(id)
    }

    override suspend fun select(id: String) {
        BotRepository.select(id)
    }
}
