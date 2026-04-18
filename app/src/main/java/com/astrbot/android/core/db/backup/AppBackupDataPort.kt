package com.astrbot.android.core.db.backup

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.chat.ConversationSession

data class AppBackupExternalState(
    val selectedBotId: String = "",
    val selectedConfigId: String = "",
    val quickLoginUin: String = "",
    val savedAccounts: List<SavedQqAccount> = emptyList(),
)

interface AppBackupDataPort {
    fun snapshotBots(): List<BotProfile>
    fun snapshotProviders(): List<ProviderProfile>
    fun snapshotPersonas(): List<PersonaProfile>
    fun snapshotConfigs(): List<ConfigProfile>
    fun snapshotConversations(): List<ConversationSession>
    fun snapshotExternalState(): AppBackupExternalState
    suspend fun restoreBots(profiles: List<BotProfile>, selectedBotId: String)
    fun restoreProviders(profiles: List<ProviderProfile>)
    fun restorePersonas(profiles: List<PersonaProfile>)
    fun restoreConfigs(profiles: List<ConfigProfile>, selectedConfigId: String)
    fun restoreConversations(sessions: List<ConversationSession>)
    fun restoreQqLoginState(quickLoginUin: String, savedAccounts: List<SavedQqAccount>)
}

object AppBackupDataRegistry {
    @Volatile
    var port: AppBackupDataPort = MissingAppBackupDataPort
}

private object MissingAppBackupDataPort : AppBackupDataPort {
    override fun snapshotBots(): List<BotProfile> = emptyList()

    override fun snapshotProviders(): List<ProviderProfile> = emptyList()

    override fun snapshotPersonas(): List<PersonaProfile> = emptyList()

    override fun snapshotConfigs(): List<ConfigProfile> = emptyList()

    override fun snapshotConversations(): List<ConversationSession> = emptyList()

    override fun snapshotExternalState(): AppBackupExternalState = AppBackupExternalState()

    override suspend fun restoreBots(profiles: List<BotProfile>, selectedBotId: String) = Unit

    override fun restoreProviders(profiles: List<ProviderProfile>) = Unit

    override fun restorePersonas(profiles: List<PersonaProfile>) = Unit

    override fun restoreConfigs(profiles: List<ConfigProfile>, selectedConfigId: String) = Unit

    override fun restoreConversations(sessions: List<ConversationSession>) = Unit

    override fun restoreQqLoginState(quickLoginUin: String, savedAccounts: List<SavedQqAccount>) = Unit
}
