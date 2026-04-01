package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ConfigAggregateDao {
    @Transaction
    @Query("SELECT * FROM config_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    abstract fun observeConfigAggregates(): Flow<List<ConfigAggregate>>

    @Transaction
    @Query("SELECT * FROM config_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    abstract suspend fun listConfigAggregates(): List<ConfigAggregate>

    @Upsert protected abstract suspend fun upsertConfigs(entities: List<ConfigProfileEntity>)
    @Upsert protected abstract suspend fun upsertAdminUids(entities: List<ConfigAdminUidEntity>)
    @Upsert protected abstract suspend fun upsertWakeWords(entities: List<ConfigWakeWordEntity>)
    @Upsert protected abstract suspend fun upsertWhitelistEntries(entities: List<ConfigWhitelistEntryEntity>)
    @Upsert protected abstract suspend fun upsertKeywordPatterns(entities: List<ConfigKeywordPatternEntity>)
    @Upsert protected abstract suspend fun upsertTextRules(entities: List<ConfigTextRuleEntity>)
    @Query("DELETE FROM config_profiles WHERE id NOT IN (:ids)") protected abstract suspend fun deleteMissingConfigs(ids: List<String>)
    @Query("DELETE FROM config_profiles") protected abstract suspend fun clearConfigs()
    @Query("DELETE FROM config_admin_uids WHERE configId IN (:configIds)") protected abstract suspend fun deleteAdminUids(configIds: List<String>)
    @Query("DELETE FROM config_wake_words WHERE configId IN (:configIds)") protected abstract suspend fun deleteWakeWords(configIds: List<String>)
    @Query("DELETE FROM config_whitelist_entries WHERE configId IN (:configIds)") protected abstract suspend fun deleteWhitelistEntries(configIds: List<String>)
    @Query("DELETE FROM config_keyword_patterns WHERE configId IN (:configIds)") protected abstract suspend fun deleteKeywordPatterns(configIds: List<String>)
    @Query("DELETE FROM config_text_rules WHERE configId IN (:configIds)") protected abstract suspend fun deleteTextRules(configIds: List<String>)
    @Query("SELECT COUNT(*) FROM config_profiles") abstract suspend fun count(): Int

    @Transaction
    open suspend fun replaceAll(writeModels: List<ConfigWriteModel>) {
        if (writeModels.isEmpty()) {
            clearConfigs()
            return
        }
        val configIds = writeModels.map { it.config.id }
        upsertConfigs(writeModels.map { it.config })
        deleteMissingConfigs(configIds)
        deleteAdminUids(configIds)
        deleteWakeWords(configIds)
        deleteWhitelistEntries(configIds)
        deleteKeywordPatterns(configIds)
        deleteTextRules(configIds)
        val adminUids = writeModels.flatMap { it.adminUids }
        if (adminUids.isNotEmpty()) upsertAdminUids(adminUids)
        val wakeWords = writeModels.flatMap { it.wakeWords }
        if (wakeWords.isNotEmpty()) upsertWakeWords(wakeWords)
        val whitelistEntries = writeModels.flatMap { it.whitelistEntries }
        if (whitelistEntries.isNotEmpty()) upsertWhitelistEntries(whitelistEntries)
        val keywordPatterns = writeModels.flatMap { it.keywordPatterns }
        if (keywordPatterns.isNotEmpty()) upsertKeywordPatterns(keywordPatterns)
        upsertTextRules(writeModels.map { it.textRule })
    }
}
