package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProviderAggregateDao {
    @Transaction
    @Query("SELECT * FROM provider_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    abstract fun observeProviderAggregates(): Flow<List<ProviderAggregate>>

    @Transaction
    @Query("SELECT * FROM provider_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    abstract suspend fun listProviderAggregates(): List<ProviderAggregate>

    @Upsert protected abstract suspend fun upsertProviders(entities: List<ProviderEntity>)
    @Upsert protected abstract suspend fun upsertCapabilities(entities: List<ProviderCapabilityEntity>)
    @Upsert protected abstract suspend fun upsertVoiceOptions(entities: List<ProviderTtsVoiceOptionEntity>)
    @Query("DELETE FROM provider_profiles WHERE id NOT IN (:ids)") protected abstract suspend fun deleteMissingProviders(ids: List<String>)
    @Query("DELETE FROM provider_profiles") protected abstract suspend fun clearProviders()
    @Query("DELETE FROM provider_capabilities WHERE providerId IN (:providerIds)") protected abstract suspend fun deleteCapabilities(providerIds: List<String>)
    @Query("DELETE FROM provider_tts_voice_options WHERE providerId IN (:providerIds)") protected abstract suspend fun deleteVoiceOptions(providerIds: List<String>)
    @Query("SELECT COUNT(*) FROM provider_profiles") abstract suspend fun count(): Int

    @Transaction
    open suspend fun replaceAll(writeModels: List<ProviderWriteModel>) {
        if (writeModels.isEmpty()) {
            clearProviders()
            return
        }
        val providerIds = writeModels.map { it.provider.id }
        upsertProviders(writeModels.map { it.provider })
        deleteMissingProviders(providerIds)
        deleteCapabilities(providerIds)
        deleteVoiceOptions(providerIds)
        val capabilities = writeModels.flatMap { it.capabilities }
        if (capabilities.isNotEmpty()) upsertCapabilities(capabilities)
        val voiceOptions = writeModels.flatMap { it.ttsVoiceOptions }
        if (voiceOptions.isNotEmpty()) upsertVoiceOptions(voiceOptions)
    }
}
