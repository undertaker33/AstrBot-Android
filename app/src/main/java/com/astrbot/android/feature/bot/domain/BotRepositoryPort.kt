package com.astrbot.android.feature.bot.domain

import com.astrbot.android.model.BotProfile
import kotlinx.coroutines.flow.StateFlow

interface BotRepositoryPort {
    val bots: StateFlow<List<BotProfile>>
    val selectedBotId: StateFlow<String>
    fun currentBot(): BotProfile
    fun snapshotProfiles(): List<BotProfile>
    suspend fun save(profile: BotProfile)
    suspend fun create(profile: BotProfile)
    suspend fun delete(id: String)
    suspend fun select(id: String)
}
