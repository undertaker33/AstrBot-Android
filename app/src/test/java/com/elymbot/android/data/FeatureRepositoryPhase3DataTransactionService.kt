package com.elymbot.android.data

import com.elymbot.android.model.ConfigProfile
import com.elymbot.android.model.BotProfile
import kotlinx.coroutines.delay

internal object FeatureRepositoryPhase3DataTransactionService {
    suspend fun deleteConfigProfile(profileId: String) {
        waitForConfigRestore(expectedProfileIds = listOf(profileId))
        val profiles = ConfigRepository.profiles.value
        if (profiles.size <= 1) return
        val remainingProfiles = profiles.filterNot { it.id == profileId }
        if (remainingProfiles.size == profiles.size) return
        val selectedId = ConfigRepository.selectedProfileId.value
        val fallbackId = remainingProfiles.firstOrNull { it.id == selectedId }?.id
            ?: remainingProfiles.firstOrNull()?.id
            ?: return
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

    suspend fun waitForConfigRestore(
        expectedProfiles: List<ConfigProfile>,
        selectedProfileId: String,
    ) {
        val expectedIds = expectedProfiles.map { profile -> profile.id }
        check(
            waitForConfigRestore(
                expectedProfileIds = expectedIds,
                selectedProfileId = selectedProfileId,
                requireExactIds = true,
            ),
        ) {
            "Config restore did not settle: expectedIds=$expectedIds selected=$selectedProfileId"
        }
    }

    suspend fun waitForBotRestore(
        expectedProfiles: List<BotProfile>,
        selectedBotId: String,
    ) {
        val expectedIds = expectedProfiles.map { profile -> profile.id }
        check(
            waitForBotRestore(
                expectedProfileIds = expectedIds,
                selectedBotId = selectedBotId,
                requireExactIds = true,
            ),
        ) {
            "Bot restore did not settle: expectedIds=$expectedIds selected=$selectedBotId"
        }
    }

    private suspend fun waitForConfigRestore(
        expectedProfileIds: Collection<String>,
        selectedProfileId: String? = null,
        requireExactIds: Boolean = false,
    ): Boolean {
        val expectedIds = expectedProfileIds.toList()
        repeat(50) {
            val currentIds = ConfigRepository.profiles.value.map { profile -> profile.id }
            val profilesRestored = if (requireExactIds) {
                currentIds == expectedIds
            } else {
                currentIds.containsAll(expectedIds)
            }
            val selectedRestored = selectedProfileId == null ||
                ConfigRepository.selectedProfileId.value == selectedProfileId
            if (profilesRestored && selectedRestored) return true
            delay(10)
        }
        return false
    }

    private suspend fun waitForBotRestore(
        expectedProfileIds: Collection<String>,
        selectedBotId: String? = null,
        requireExactIds: Boolean = false,
    ): Boolean {
        val expectedIds = expectedProfileIds.toList()
        repeat(50) {
            val currentIds = BotRepository.botProfiles.value.map { profile -> profile.id }
            val profilesRestored = if (requireExactIds) {
                currentIds == expectedIds
            } else {
                currentIds.containsAll(expectedIds)
            }
            val selectedRestored = selectedBotId == null ||
                BotRepository.selectedBotId.value == selectedBotId
            if (profilesRestored && selectedRestored) return true
            delay(10)
        }
        return false
    }
}
