package com.astrbot.android.download

import com.astrbot.android.data.db.DownloadTaskDao
import com.astrbot.android.data.db.toEntity
import com.astrbot.android.data.db.toRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DownloadTaskStore {
    suspend fun get(taskKey: String): DownloadTaskRecord?

    fun observe(taskKey: String): Flow<DownloadTaskRecord?>

    suspend fun upsert(task: DownloadTaskRecord)

    suspend fun listRecoverable(): List<DownloadTaskRecord>

    suspend fun listPending(): List<DownloadTaskRecord>
}

class RoomDownloadTaskStore(
    private val dao: DownloadTaskDao,
) : DownloadTaskStore {
    override suspend fun get(taskKey: String): DownloadTaskRecord? {
        return dao.getTask(taskKey)?.toRecord()
    }

    override fun observe(taskKey: String): Flow<DownloadTaskRecord?> {
        return dao.observeTask(taskKey).map { entity -> entity?.toRecord() }
    }

    override suspend fun upsert(task: DownloadTaskRecord) {
        dao.upsertTask(task.toEntity())
    }

    override suspend fun listRecoverable(): List<DownloadTaskRecord> {
        return dao.listRecoverableTasks().map { entity -> entity.toRecord() }
    }

    override suspend fun listPending(): List<DownloadTaskRecord> {
        return dao.listPendingTasks().map { entity -> entity.toRecord() }
    }
}
