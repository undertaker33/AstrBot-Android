@file:Suppress("DEPRECATION")

package com.astrbot.android.di

import com.astrbot.android.core.db.backup.AppBackupDataPort
import com.astrbot.android.core.db.backup.AppBackupExternalState
import com.astrbot.android.core.db.backup.ConversationBackupDataPort
import com.astrbot.android.core.db.backup.ConversationImportPreview
import com.astrbot.android.core.db.backup.ConversationImportResult
import com.astrbot.android.feature.bot.data.FeatureBotRepository as BotRepository
import com.astrbot.android.feature.chat.data.FeatureConversationRepository as ConversationRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository as ConfigRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository as PersonaRepository
import com.astrbot.android.feature.provider.data.FeatureProviderRepository as ProviderRepository
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

internal object ProductionAppBackupDataPort : AppBackupDataPort {
    override fun snapshotBots(): List<BotProfile> = BotRepository.snapshotProfiles()

    override fun snapshotProviders(): List<ProviderProfile> = ProviderRepository.snapshotProfiles()

    override fun snapshotPersonas(): List<PersonaProfile> = PersonaRepository.snapshotProfiles()

    override fun snapshotConfigs(): List<ConfigProfile> = ConfigRepository.snapshotProfiles()

    override fun snapshotConversations(): List<ConversationSession> = ConversationRepository.snapshotSessions()

    override fun snapshotExternalState(): AppBackupExternalState {
        val loginState = NapCatLoginRepository.loginState.value
        return AppBackupExternalState(
            selectedBotId = BotRepository.selectedBotId.value,
            selectedConfigId = ConfigRepository.selectedProfileId.value,
            quickLoginUin = loginState.quickLoginUin,
            savedAccounts = loginState.savedAccounts,
        )
    }

    override suspend fun restoreBots(profiles: List<BotProfile>, selectedBotId: String) {
        BotRepository.restoreProfiles(profiles, selectedBotId)
    }

    override fun restoreProviders(profiles: List<ProviderProfile>) {
        ProviderRepository.restoreProfiles(profiles)
    }

    override fun restorePersonas(profiles: List<PersonaProfile>) {
        PersonaRepository.restoreProfiles(profiles)
    }

    override fun restoreConfigs(profiles: List<ConfigProfile>, selectedConfigId: String) {
        ConfigRepository.restoreProfiles(profiles, selectedConfigId)
    }

    override suspend fun restoreConversations(sessions: List<ConversationSession>) {
        ConversationRepository.restoreSessionsDurable(sessions)
    }

    override fun restoreQqLoginState(quickLoginUin: String, savedAccounts: List<SavedQqAccount>) {
        NapCatLoginRepository.restoreSavedLoginState(
            quickLoginUin = quickLoginUin,
            savedAccounts = savedAccounts,
        )
    }
}

internal object ProductionConversationBackupDataPort : ConversationBackupDataPort {
    override val isReady: StateFlow<Boolean> = ConversationRepository.isReady
    override val sessions: StateFlow<List<ConversationSession>> = ConversationRepository.sessions
    override val defaultSessionTitle: String = ConversationRepository.DEFAULT_SESSION_TITLE

    override fun selectedBotId(): String = BotRepository.selectedBotId.value

    override fun snapshotSessions(): List<ConversationSession> = ConversationRepository.snapshotSessions()

    override fun restoreSessions(restoredSessions: List<ConversationSession>) {
        ConversationRepository.restoreSessions(restoredSessions)
    }

    override fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview {
        val preview = ConversationRepository.previewImportedSessions(importedSessions)
        return ConversationImportPreview(
            totalSessions = preview.totalSessions,
            duplicateSessions = preview.duplicateSessions,
            newSessions = preview.newSessions,
        )
    }

    override fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        val result = ConversationRepository.importSessions(
            importedSessions = importedSessions,
            overwriteDuplicates = overwriteDuplicates,
        )
        return ConversationImportResult(
            importedCount = result.importedCount,
            overwrittenCount = result.overwrittenCount,
            skippedCount = result.skippedCount,
        )
    }

    override suspend fun importSessionsDurable(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        val result = ConversationRepository.importSessionsDurable(
            importedSessions = importedSessions,
            overwriteDuplicates = overwriteDuplicates,
        )
        return ConversationImportResult(
            importedCount = result.importedCount,
            overwrittenCount = result.overwrittenCount,
            skippedCount = result.skippedCount,
        )
    }
}
