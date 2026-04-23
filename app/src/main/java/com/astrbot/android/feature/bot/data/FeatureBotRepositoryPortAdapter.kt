package com.astrbot.android.feature.bot.data

import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.model.BotProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeatureBotRepositoryPortAdapter @Inject constructor(
    private val repository: FeatureBotRepositoryStore,
) : BotRepositoryPort {

    override val bots: StateFlow<List<BotProfile>>
        get() = repository.botProfiles

    override val selectedBotId: StateFlow<String>
        get() = repository.selectedBotId

    override fun currentBot(): BotProfile =
        repository.botProfile.value

    override fun snapshotProfiles(): List<BotProfile> =
        repository.snapshotProfiles()

    override fun create(name: String): BotProfile =
        repository.create(name)

    override suspend fun save(profile: BotProfile) {
        repository.save(profile)
    }

    override suspend fun create(profile: BotProfile) {
        repository.save(profile)
    }

    override suspend fun delete(id: String) {
        repository.delete(id)
    }

    override suspend fun select(id: String) {
        repository.select(id)
    }
}
