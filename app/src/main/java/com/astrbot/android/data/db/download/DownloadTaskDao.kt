package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks WHERE taskKey = :taskKey")
    fun observeTask(taskKey: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE taskKey = :taskKey")
    suspend fun getTask(taskKey: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED') ORDER BY createdAt ASC")
    suspend fun listRecoverableTasks(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' ORDER BY createdAt ASC")
    suspend fun listPendingTasks(): List<DownloadTaskEntity>

    @Upsert
    suspend fun upsertTask(entity: DownloadTaskEntity)
}
