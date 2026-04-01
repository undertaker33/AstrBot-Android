package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ConversationAggregateDao {
    @Transaction
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    abstract fun observeConversationAggregates(): Flow<List<ConversationAggregate>>

    @Transaction
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    abstract suspend fun listConversationAggregates(): List<ConversationAggregate>

    @Upsert
    protected abstract suspend fun upsertSessions(entities: List<ConversationEntity>)

    @Upsert
    protected abstract suspend fun upsertMessages(entities: List<ConversationMessageEntity>)

    @Upsert
    protected abstract suspend fun upsertAttachments(entities: List<ConversationAttachmentEntity>)

    @Query("DELETE FROM conversations WHERE id NOT IN (:ids)")
    protected abstract suspend fun deleteMissingSessions(ids: List<String>)

    @Query("DELETE FROM conversations")
    protected abstract suspend fun clearSessions()

    @Query("DELETE FROM conversation_messages WHERE sessionId IN (:sessionIds)")
    protected abstract suspend fun deleteMessagesForSessions(sessionIds: List<String>)

    @Query("SELECT COUNT(*) FROM conversations")
    abstract suspend fun count(): Int

    @Transaction
    open suspend fun replaceAll(writeModels: List<ConversationAggregateWriteModel>) {
        if (writeModels.isEmpty()) {
            clearSessions()
            return
        }
        val sessionIds = writeModels.map { it.session.id }
        upsertSessions(writeModels.map { it.session })
        deleteMissingSessions(sessionIds)
        deleteMessagesForSessions(sessionIds)
        val messages = writeModels.flatMap { it.messages }
        if (messages.isNotEmpty()) {
            upsertMessages(messages)
        }
        val attachments = writeModels.flatMap { it.attachments }
        if (attachments.isNotEmpty()) {
            upsertAttachments(attachments)
        }
    }
}
