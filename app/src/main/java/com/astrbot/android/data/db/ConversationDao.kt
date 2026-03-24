package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun listConversations(): List<ConversationEntity>

    @Upsert
    suspend fun upsert(entity: ConversationEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE id = :sessionId")
    suspend fun deleteById(sessionId: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()

    @Query("DELETE FROM conversations WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int
}
