package com.astrbot.android.core.db.backup

import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ConversationImportPreview(
    val totalSessions: Int,
    val duplicateSessions: List<ConversationSession>,
    val newSessions: List<ConversationSession>,
)

data class ConversationImportResult(
    val importedCount: Int,
    val overwrittenCount: Int,
    val skippedCount: Int,
)

interface ConversationBackupDataPort {
    val isReady: StateFlow<Boolean>
    val sessions: StateFlow<List<ConversationSession>>
    val defaultSessionTitle: String

    fun selectedBotId(): String
    fun snapshotSessions(): List<ConversationSession>
    fun restoreSessions(restoredSessions: List<ConversationSession>)
    fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview
    fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult
}

object ConversationBackupDataRegistry {
    @Volatile
    var port: ConversationBackupDataPort = MissingConversationBackupDataPort
}

private object MissingConversationBackupDataPort : ConversationBackupDataPort {
    private val readyState = MutableStateFlow(false)
    private val sessionState = MutableStateFlow<List<ConversationSession>>(emptyList())

    override val isReady: StateFlow<Boolean> = readyState
    override val sessions: StateFlow<List<ConversationSession>> = sessionState
    override val defaultSessionTitle: String = "新对话"

    override fun selectedBotId(): String = "qq-main"

    override fun snapshotSessions(): List<ConversationSession> = sessions.value

    override fun restoreSessions(restoredSessions: List<ConversationSession>) = Unit

    override fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview {
        return ConversationImportPreview(
            totalSessions = importedSessions.size,
            duplicateSessions = emptyList(),
            newSessions = importedSessions,
        )
    }

    override fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        return ConversationImportResult(
            importedCount = importedSessions.size,
            overwrittenCount = 0,
            skippedCount = 0,
        )
    }
}
