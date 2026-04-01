package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TtsVoiceAssetAggregateDao {
    @Transaction
    @Query("SELECT * FROM tts_voice_assets ORDER BY createdAt DESC")
    abstract fun observeAssetAggregates(): Flow<List<TtsVoiceAssetAggregate>>

    @Transaction
    @Query("SELECT * FROM tts_voice_assets ORDER BY createdAt DESC")
    abstract suspend fun listAssetAggregates(): List<TtsVoiceAssetAggregate>

    @Upsert
    protected abstract suspend fun upsertAssets(entities: List<TtsVoiceAssetEntity>)

    @Upsert
    protected abstract suspend fun upsertClips(entities: List<TtsVoiceClipEntity>)

    @Upsert
    protected abstract suspend fun upsertProviderBindings(entities: List<TtsVoiceProviderBindingEntity>)

    @Query("DELETE FROM tts_voice_assets WHERE id NOT IN (:ids)")
    protected abstract suspend fun deleteMissingAssets(ids: List<String>)

    @Query("DELETE FROM tts_voice_assets")
    protected abstract suspend fun clearAssets()

    @Query("DELETE FROM tts_voice_clips WHERE assetId IN (:assetIds)")
    protected abstract suspend fun deleteClipsForAssets(assetIds: List<String>)

    @Query("DELETE FROM tts_voice_provider_bindings WHERE assetId IN (:assetIds)")
    protected abstract suspend fun deleteBindingsForAssets(assetIds: List<String>)

    @Query("SELECT COUNT(*) FROM tts_voice_assets")
    abstract suspend fun count(): Int

    @Transaction
    open suspend fun replaceAll(writeModels: List<TtsVoiceAssetWriteModel>) {
        if (writeModels.isEmpty()) {
            clearAssets()
            return
        }
        val assetIds = writeModels.map { it.asset.id }
        upsertAssets(writeModels.map { it.asset })
        deleteMissingAssets(assetIds)
        deleteClipsForAssets(assetIds)
        deleteBindingsForAssets(assetIds)
        val clips = writeModels.flatMap { it.clips }
        if (clips.isNotEmpty()) {
            upsertClips(clips)
        }
        val bindings = writeModels.flatMap { it.providerBindings }
        if (bindings.isNotEmpty()) {
            upsertProviderBindings(bindings)
        }
    }
}
