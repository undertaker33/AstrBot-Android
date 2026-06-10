package com.elymbot.android.data

import com.elymbot.android.core.common.profile.LastProfileDeletionBlockedException
import com.elymbot.android.model.BotProfile
import com.elymbot.android.model.ConfigProfile
import com.elymbot.android.model.chat.ConversationSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task10 Phase3 – Transaction ordering & rollback contracts for composition-root delete functions.
 *
 * These tests verify two key properties established in the Phase 3 code review:
 *
 * 1. Config delete: bots are rebound to a fallback config BEFORE the config is removed.
 *    Crash between steps → bots point to a valid config, deleted config still in DB → recoverable.
 *
 * 2. Bot delete: conversation sessions are removed first (crash-safe ordering), and any in-memory
 *    deletion is rolled back if the bot itself cannot be deleted (e.g. last-bot guard).
 */
class HiltViewModelDependenciesTransactionTest {
    private lateinit var botSnapshot: List<BotProfile>
    private lateinit var configSnapshot: List<ConfigProfile>
    private lateinit var conversationSnapshot: List<ConversationSession>
    private lateinit var selectedBotId: String
    private lateinit var selectedConfigId: String

    @Before
    fun captureSnapshot() {
        botSnapshot = BotRepository.snapshotProfiles()
        configSnapshot = ConfigRepository.snapshotProfiles()
        conversationSnapshot = ConversationRepository.snapshotSessions()
        selectedBotId = BotRepository.selectedBotId.value
        selectedConfigId = ConfigRepository.selectedProfileId.value
    }

    @After
    fun restoreSnapshot() {
        runBlocking {
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigId)
            FeatureRepositoryPhase3DataTransactionService.waitForConfigRestore(
                expectedProfiles = configSnapshot,
                selectedProfileId = selectedConfigId,
            )
            BotRepository.restoreProfiles(botSnapshot, selectedBotId)
            FeatureRepositoryPhase3DataTransactionService.waitForBotRestore(
                expectedProfiles = botSnapshot,
                selectedBotId = selectedBotId,
            )
            ConversationRepository.restoreSessions(conversationSnapshot)
        }
    }

    // ── Config delete ordering ────────────────────────────────────────────────────────────────

    /**
     * When [HiltConfigViewModelDependencies.deleteConfigProfile] is called, bots must be rebound
     * to the fallback config BEFORE the config deletion DB write is scheduled.
     *
     * The observable invariant in unit tests (no Room DB): after the call returns,
     * [BotRepository.botProfiles].value must not reference the deleted config any more.
     */
    @Test
    fun deleteConfigProfile_rebinds_bots_before_config_is_removed() = runBlocking {
        val deletedConfigId = "hilt-tx-config-to-delete"
        val fallbackConfigId = "hilt-tx-config-fallback"
        val targetBotId = "hilt-tx-bot-config-delete"

        // Set up: two configs and a bot bound to the one we will delete
        ConfigRepository.restoreProfiles(
            profiles = listOf(
                ConfigProfile(id = deletedConfigId),
                ConfigProfile(id = fallbackConfigId),
            ),
            selectedProfileId = fallbackConfigId,
        )
        FeatureRepositoryPhase3DataTransactionService.waitForConfigRestore(
            expectedProfiles = listOf(
                ConfigProfile(id = deletedConfigId),
                ConfigProfile(id = fallbackConfigId),
            ),
            selectedProfileId = fallbackConfigId,
        )
        BotRepository.restoreProfiles(
            profiles = listOf(
                BotProfile(id = targetBotId, displayName = "Transaction Bot", configProfileId = deletedConfigId),
            ),
            selectedBotId = targetBotId,
        )
        FeatureRepositoryPhase3DataTransactionService.waitForBotRestore(
            expectedProfiles = listOf(
                BotProfile(id = targetBotId, displayName = "Transaction Bot", configProfileId = deletedConfigId),
            ),
            selectedBotId = targetBotId,
        )

        FeatureRepositoryPhase3DataTransactionService.deleteConfigProfile(deletedConfigId)

        // Bot must now reference the fallback, not the deleted config
        val targetBot = BotRepository.botProfiles.value.firstOrNull { it.id == targetBotId }
        assertFalse(
            "Bot must not reference the deleted config after deleteConfigProfile()",
            targetBot?.configProfileId == deletedConfigId,
        )
        assertTrue(
            "Bot must reference the fallback config after deleteConfigProfile()",
            targetBot?.configProfileId == fallbackConfigId,
        )
    }

    /**
     * [HiltConfigViewModelDependencies.deleteConfigProfile] must be a no-op when there is only
     * one config profile — the last-config guard must be respected.
     */
    @Test
    fun deleteConfigProfile_does_nothing_when_only_one_config_exists() = runBlocking {
        val onlyConfigId = "hilt-tx-only-config"
        ConfigRepository.restoreProfiles(
            profiles = listOf(ConfigProfile(id = onlyConfigId)),
            selectedProfileId = onlyConfigId,
        )
        FeatureRepositoryPhase3DataTransactionService.waitForConfigRestore(
            expectedProfiles = listOf(ConfigProfile(id = onlyConfigId)),
            selectedProfileId = onlyConfigId,
        )

        // Should not throw; should be a silent no-op
        FeatureRepositoryPhase3DataTransactionService.deleteConfigProfile(onlyConfigId)

        val remaining = ConfigRepository.profiles.value
        assertTrue(
            "The only config must still exist after a no-op delete attempt",
            remaining.any { it.id == onlyConfigId },
        )
    }

    // ── Bot delete rollback ───────────────────────────────────────────────────────────────────

    /**
     * When [HiltBotViewModelDependencies.delete] is blocked by [LastProfileDeletionBlockedException]
     * (last-bot guard), the conversation sessions that were removed in Step 1 must be rolled back
     * so the caller's data is not silently discarded.
     */
    @Test
    fun delete_bot_rolls_back_conversation_sessions_when_deletion_is_rejected() = runBlocking {
        val onlyBotId = "hilt-tx-only-bot"
        val sessionId = "hilt-tx-session-for-only-bot"

        // Ensure exactly one bot exists (default)
        BotRepository.restoreProfiles(
            profiles = listOf(BotProfile(id = onlyBotId, displayName = "Main Bot")),
            selectedBotId = onlyBotId,
        )
        FeatureRepositoryPhase3DataTransactionService.waitForBotRestore(
            expectedProfiles = listOf(BotProfile(id = onlyBotId, displayName = "Main Bot")),
            selectedBotId = onlyBotId,
        )
        // Plant a conversation for that bot
        ConversationRepository.restoreSessions(
            listOf(
                ConversationSession(
                    id = sessionId,
                    title = "Test session",
                    botId = onlyBotId,
                    personaId = "",
                    providerId = "",
                    maxContextMessages = 12,
                    sessionSttEnabled = true,
                    sessionTtsEnabled = true,
                    pinned = false,
                    titleCustomized = false,
                    messages = emptyList(),
                ),
            ),
        )

        var exceptionThrown = false
        try {
            FeatureRepositoryPhase3DataTransactionService.deleteBotProfile(onlyBotId)
        } catch (e: LastProfileDeletionBlockedException) {
            exceptionThrown = true
        }

        assertTrue("Last-bot guard must throw", exceptionThrown)
        assertTrue(
            "Conversation sessions must be restored after failed bot deletion",
            ConversationRepository.snapshotSessions().any { it.id == sessionId },
        )
    }
}
