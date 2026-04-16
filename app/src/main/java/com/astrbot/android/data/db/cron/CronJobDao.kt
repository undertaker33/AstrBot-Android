package com.astrbot.android.data.db.cron

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.astrbot.android.data.db.CronJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CronJobDao {
    @Query("SELECT * FROM cron_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CronJobEntity>>

    @Query("SELECT * FROM cron_jobs ORDER BY createdAt DESC")
    suspend fun listAll(): List<CronJobEntity>

    @Query("SELECT * FROM cron_jobs WHERE jobType = :jobType ORDER BY createdAt DESC")
    suspend fun listByType(jobType: String): List<CronJobEntity>

    @Query("SELECT * FROM cron_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getByJobId(jobId: String): CronJobEntity?

    @Query("SELECT * FROM cron_jobs WHERE enabled = 1 ORDER BY nextRunTime ASC")
    suspend fun listEnabled(): List<CronJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CronJobEntity)

    @Update
    suspend fun update(entity: CronJobEntity)

    @Query("DELETE FROM cron_jobs WHERE jobId = :jobId")
    suspend fun deleteByJobId(jobId: String)

    @Query("DELETE FROM cron_jobs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cron_jobs")
    suspend fun count(): Int
}
