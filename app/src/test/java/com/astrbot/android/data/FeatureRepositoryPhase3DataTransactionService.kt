package com.astrbot.android.data

import kotlinx.coroutines.delay

internal object FeatureRepositoryPhase3DataTransactionService {
    suspend fun deleteConfigProfile(profileId: String) {
        waitForConfigRestore(profileId)
        val profiles = ConfigRepository.profiles.value
        if (profiles.size <= 1) return
        val fallbackId = if (ConfigRepository.selectedProfileId.value == profileId) {
            profiles.firstOrNull { it.id != profileId }?.id ?: return
        } else {
            ConfigRepository.selectedProfileId.value
        }
        BotRepository.replaceConfigBinding(profileId, fallbackId)
        ConfigRepository.delete(profileId)
    }

    suspend fun deleteBotProfile(botId: String) {
        val sessionSnapshot = ConversationRepository.snapshotSessions()
        ConversationRepository.deleteSessionsForBot(botId)
        try {
            BotRepository.delete(botId)
        } catch (error: Throwable) {
            ConversationRepository.restoreSessions(sessionSnapshot)
            throw error
        }
    }

    private suspend fun waitForConfigRestore(profileId: String) {
        repeat(50) {
            if (ConfigRepository.profiles.value.any { it.id == profileId }) return
            delay(10)
        }
    }
}
