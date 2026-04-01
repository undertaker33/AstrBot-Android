package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PersonaAggregateDao {
    @Transaction
    @Query("SELECT * FROM persona_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    abstract fun observePersonaAggregates(): Flow<List<PersonaAggregate>>

    @Transaction
    @Query("SELECT * FROM persona_profiles ORDER BY sortIndex ASC, updatedAt ASC")
    abstract suspend fun listPersonaAggregates(): List<PersonaAggregate>

    @Upsert protected abstract suspend fun upsertPersonas(entities: List<PersonaEntity>)
    @Upsert protected abstract suspend fun upsertPrompts(entities: List<PersonaPromptEntity>)
    @Upsert protected abstract suspend fun upsertEnabledTools(entities: List<PersonaEnabledToolEntity>)
    @Query("DELETE FROM persona_profiles WHERE id NOT IN (:ids)") protected abstract suspend fun deleteMissingPersonas(ids: List<String>)
    @Query("DELETE FROM persona_profiles") protected abstract suspend fun clearPersonas()
    @Query("DELETE FROM persona_prompts WHERE personaId IN (:personaIds)") protected abstract suspend fun deletePrompts(personaIds: List<String>)
    @Query("DELETE FROM persona_enabled_tools WHERE personaId IN (:personaIds)") protected abstract suspend fun deleteEnabledTools(personaIds: List<String>)
    @Query("SELECT COUNT(*) FROM persona_profiles") abstract suspend fun count(): Int

    @Transaction
    open suspend fun replaceAll(writeModels: List<PersonaWriteModel>) {
        if (writeModels.isEmpty()) {
            clearPersonas()
            return
        }
        val personaIds = writeModels.map { it.persona.id }
        upsertPersonas(writeModels.map { it.persona })
        deleteMissingPersonas(personaIds)
        deletePrompts(personaIds)
        deleteEnabledTools(personaIds)
        upsertPrompts(writeModels.map { it.prompt })
        val enabledTools = writeModels.flatMap { it.enabledTools }
        if (enabledTools.isNotEmpty()) upsertEnabledTools(enabledTools)
    }
}
