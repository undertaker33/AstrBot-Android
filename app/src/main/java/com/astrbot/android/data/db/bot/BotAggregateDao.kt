package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BotAggregateDao {
    @Transaction
    @Query("SELECT * FROM bots ORDER BY updatedAt DESC")
    abstract fun observeBotAggregates(): Flow<List<BotAggregate>>

    @Transaction
    @Query("SELECT * FROM bots ORDER BY updatedAt DESC")
    abstract suspend fun listBotAggregates(): List<BotAggregate>

    @Upsert protected abstract suspend fun upsertBots(entities: List<BotEntity>)
    @Upsert protected abstract suspend fun upsertBoundQqUins(entities: List<BotBoundQqUinEntity>)
    @Upsert protected abstract suspend fun upsertTriggerWords(entities: List<BotTriggerWordEntity>)
    @Query("DELETE FROM bots WHERE id NOT IN (:ids)") protected abstract suspend fun deleteMissingBots(ids: List<String>)
    @Query("DELETE FROM bots") protected abstract suspend fun clearBots()
    @Query("DELETE FROM bot_bound_qq_uins WHERE botId IN (:botIds)") protected abstract suspend fun deleteBoundQqUins(botIds: List<String>)
    @Query("DELETE FROM bot_trigger_words WHERE botId IN (:botIds)") protected abstract suspend fun deleteTriggerWords(botIds: List<String>)
    @Query("SELECT COUNT(*) FROM bots") abstract suspend fun count(): Int

    @Transaction
    open suspend fun replaceAll(writeModels: List<BotWriteModel>) {
        if (writeModels.isEmpty()) {
            clearBots()
            return
        }
        val botIds = writeModels.map { it.bot.id }
        upsertBots(writeModels.map { it.bot })
        deleteMissingBots(botIds)
        deleteBoundQqUins(botIds)
        deleteTriggerWords(botIds)
        val qqUins = writeModels.flatMap { it.boundQqUins }
        if (qqUins.isNotEmpty()) upsertBoundQqUins(qqUins)
        val triggerWords = writeModels.flatMap { it.triggerWords }
        if (triggerWords.isNotEmpty()) upsertTriggerWords(triggerWords)
    }
}
