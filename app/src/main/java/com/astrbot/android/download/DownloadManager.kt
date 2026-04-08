package com.astrbot.android.download

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DownloadManager {
    suspend fun enqueue(request: DownloadRequest): String

    fun observe(taskKey: String): Flow<DownloadTaskRecord?>

    suspend fun awaitCompletion(
        taskKey: String,
        onUpdate: (DownloadTaskRecord) -> Unit = {},
    ): DownloadTaskRecord

    suspend fun resume(taskKey: String)

    suspend fun cancel(taskKey: String)
}

class DefaultDownloadManager(
    private val store: DownloadTaskStore,
    private val downloader: ResumableHttpDownloader,
    private val serviceStarter: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) : DownloadManager {
    private val processMutex = Mutex()
    private val activeTaskKey = AtomicReference<String?>(null)
    private val pausedTaskKeys = mutableSetOf<String>()

    override suspend fun enqueue(request: DownloadRequest): String {
        val next = mergeWithExisting(request, store.get(request.taskKey))
        store.upsert(next)
        serviceStarter()
        return request.taskKey
    }

    override fun observe(taskKey: String): Flow<DownloadTaskRecord?> = store.observe(taskKey)

    override suspend fun awaitCompletion(
        taskKey: String,
        onUpdate: (DownloadTaskRecord) -> Unit,
    ): DownloadTaskRecord {
        serviceStarter()
        val terminal = CompletableDeferred<DownloadTaskRecord>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch {
            observe(taskKey)
                .filterNotNull()
                .collect { task ->
                    onUpdate(task)
                    if (task.status.isTerminal && !terminal.isCompleted) {
                        terminal.complete(task)
                    }
                }
        }
        val result = terminal.await()
        job.cancel()
        if (result.status == DownloadTaskStatus.COMPLETED) {
            return result
        }
        throw IllegalStateException(result.errorMessage.ifBlank { "Download ${result.displayName} failed." })
    }

    override suspend fun resume(taskKey: String) {
        val existing = store.get(taskKey) ?: return
        synchronized(pausedTaskKeys) {
            pausedTaskKeys.remove(taskKey)
        }
        store.upsert(
            existing.copy(
                status = DownloadTaskStatus.QUEUED,
                errorMessage = "",
                bytesPerSecond = 0L,
                updatedAt = clock(),
            ),
        )
        serviceStarter()
    }

    override suspend fun cancel(taskKey: String) {
        synchronized(pausedTaskKeys) {
            pausedTaskKeys += taskKey
        }
        val existing = store.get(taskKey) ?: return
        if (activeTaskKey.get() != taskKey) {
            store.upsert(
                existing.copy(
                    status = DownloadTaskStatus.PAUSED,
                    bytesPerSecond = 0L,
                    errorMessage = "",
                    updatedAt = clock(),
                ),
            )
        }
    }

    suspend fun processQueue(onTaskUpdated: (DownloadTaskRecord?) -> Unit = {}) {
        processMutex.withLock {
            while (true) {
                val next = store.listRecoverable()
                    .asSequence()
                    .map(::normalizeForRecovery)
                    .firstOrNull { task ->
                        task.status == DownloadTaskStatus.QUEUED || task.status == DownloadTaskStatus.RUNNING
                    }
                    ?: break
                processTask(next, onTaskUpdated)
            }
            onTaskUpdated(null)
        }
    }

    suspend fun hasRecoverableTasks(): Boolean {
        return store.listRecoverable().isNotEmpty()
    }

    private suspend fun processTask(
        task: DownloadTaskRecord,
        onTaskUpdated: (DownloadTaskRecord?) -> Unit,
    ) {
        var current = normalizeForRecovery(task)
        if (current.status == DownloadTaskStatus.COMPLETED) {
            store.upsert(current)
            onTaskUpdated(current)
            return
        }
        current = current.copy(
            status = DownloadTaskStatus.RUNNING,
            errorMessage = "",
            bytesPerSecond = 0L,
            updatedAt = clock(),
        )
        store.upsert(current)
        onTaskUpdated(current)
        activeTaskKey.set(current.taskKey)
        try {
            val completion = downloader.download(
                request = current.toRequest(),
                existing = current,
                shouldCancel = {
                    synchronized(pausedTaskKeys) {
                        pausedTaskKeys.contains(current.taskKey)
                    }
                },
                onProgress = { snapshot ->
                    current = current.copy(
                        status = DownloadTaskStatus.RUNNING,
                        downloadedBytes = snapshot.downloadedBytes,
                        totalBytes = snapshot.totalBytes,
                        bytesPerSecond = snapshot.bytesPerSecond,
                        etag = snapshot.etag ?: current.etag,
                        lastModified = snapshot.lastModified ?: current.lastModified,
                        errorMessage = "",
                        updatedAt = clock(),
                    )
                    store.upsert(current)
                    onTaskUpdated(current)
                },
            )
            current = current.copy(
                status = DownloadTaskStatus.COMPLETED,
                downloadedBytes = completion.totalBytes,
                totalBytes = completion.totalBytes,
                bytesPerSecond = 0L,
                etag = completion.etag ?: current.etag,
                lastModified = completion.lastModified ?: current.lastModified,
                errorMessage = "",
                updatedAt = clock(),
                completedAt = clock(),
            )
            store.upsert(current)
            onTaskUpdated(current)
        } catch (_: DownloadInterruptedException) {
            synchronized(pausedTaskKeys) {
                pausedTaskKeys.remove(current.taskKey)
            }
            current = current.copy(
                status = DownloadTaskStatus.PAUSED,
                bytesPerSecond = 0L,
                errorMessage = "",
                updatedAt = clock(),
            )
            store.upsert(current)
            onTaskUpdated(current)
        } catch (error: Throwable) {
            current = current.copy(
                status = DownloadTaskStatus.FAILED,
                bytesPerSecond = 0L,
                errorMessage = error.message ?: error.javaClass.simpleName,
                updatedAt = clock(),
            )
            store.upsert(current)
            onTaskUpdated(current)
        } finally {
            activeTaskKey.set(null)
        }
    }

    private fun mergeWithExisting(
        request: DownloadRequest,
        existing: DownloadTaskRecord?,
    ): DownloadTaskRecord {
        val now = clock()
        val targetFile = File(request.targetFilePath)
        val partialFile = File(request.partialFilePath)
        val downloadedBytes = when {
            targetFile.exists() -> targetFile.length()
            partialFile.exists() -> partialFile.length()
            else -> 0L
        }
        val status = if (targetFile.exists()) {
            DownloadTaskStatus.COMPLETED
        } else {
            DownloadTaskStatus.QUEUED
        }
        val preservedValidators = partialFile.exists()
        return (existing ?: DownloadTaskRecord(
            taskKey = request.taskKey,
            url = request.url,
            targetFilePath = request.targetFilePath,
            partialFilePath = request.partialFilePath,
            displayName = request.displayName,
            ownerType = request.ownerType,
            ownerId = request.ownerId,
            status = status,
            downloadedBytes = downloadedBytes,
            totalBytes = if (status == DownloadTaskStatus.COMPLETED) downloadedBytes else null,
            bytesPerSecond = 0L,
            etag = null,
            lastModified = null,
            errorMessage = "",
            createdAt = now,
            updatedAt = now,
            completedAt = if (status == DownloadTaskStatus.COMPLETED) now else null,
        )).copy(
            url = request.url,
            targetFilePath = request.targetFilePath,
            partialFilePath = request.partialFilePath,
            displayName = request.displayName,
            ownerType = request.ownerType,
            ownerId = request.ownerId,
            status = status,
            downloadedBytes = downloadedBytes,
            totalBytes = when {
                status == DownloadTaskStatus.COMPLETED -> downloadedBytes
                preservedValidators -> existing?.totalBytes
                else -> null
            },
            bytesPerSecond = 0L,
            etag = if (preservedValidators) existing?.etag else null,
            lastModified = if (preservedValidators) existing?.lastModified else null,
            errorMessage = "",
            updatedAt = now,
            completedAt = if (status == DownloadTaskStatus.COMPLETED) now else null,
        )
    }

    private fun normalizeForRecovery(task: DownloadTaskRecord): DownloadTaskRecord {
        val targetFile = task.targetFile()
        val partialFile = task.partialFile()
        return when {
            targetFile.exists() -> task.copy(
                status = DownloadTaskStatus.COMPLETED,
                downloadedBytes = targetFile.length(),
                totalBytes = targetFile.length(),
                bytesPerSecond = 0L,
                errorMessage = "",
                completedAt = task.completedAt ?: clock(),
                updatedAt = clock(),
            )
            !partialFile.exists() && task.downloadedBytes > 0L -> task.copy(
                status = DownloadTaskStatus.QUEUED,
                downloadedBytes = 0L,
                totalBytes = null,
                bytesPerSecond = 0L,
                etag = null,
                lastModified = null,
                errorMessage = "",
                updatedAt = clock(),
            )
            task.status == DownloadTaskStatus.RUNNING -> task.copy(
                status = DownloadTaskStatus.QUEUED,
                bytesPerSecond = 0L,
                errorMessage = "",
                updatedAt = clock(),
            )
            else -> task
        }
    }
}

object AppDownloadManager : DownloadManager {
    private val initializationMutex = Mutex()
    @Volatile
    private var delegate: DefaultDownloadManager? = null

    suspend fun initialize(context: Context) {
        if (delegate != null) return
        initializationMutex.withLock {
            if (delegate != null) return
            val appContext = context.applicationContext
            val store = RoomDownloadTaskStore(AstrBotDatabase.get(appContext).downloadTaskDao())
            val manager = DefaultDownloadManager(
                store = store,
                downloader = UrlConnectionResumableHttpDownloader(),
                serviceStarter = {
                    DownloadForegroundService.start(appContext)
                },
            )
            delegate = manager
            if (manager.hasRecoverableTasks()) {
                DownloadForegroundService.start(appContext)
            }
        }
    }

    override suspend fun enqueue(request: DownloadRequest): String {
        return requireDelegate().enqueue(request)
    }

    override fun observe(taskKey: String): Flow<DownloadTaskRecord?> {
        return requireDelegate().observe(taskKey)
    }

    override suspend fun awaitCompletion(
        taskKey: String,
        onUpdate: (DownloadTaskRecord) -> Unit,
    ): DownloadTaskRecord {
        return requireDelegate().awaitCompletion(taskKey, onUpdate)
    }

    override suspend fun resume(taskKey: String) {
        requireDelegate().resume(taskKey)
    }

    override suspend fun cancel(taskKey: String) {
        requireDelegate().cancel(taskKey)
    }

    suspend fun processQueue(onTaskUpdated: (DownloadTaskRecord?) -> Unit = {}) {
        requireDelegate().processQueue(onTaskUpdated)
    }

    private fun requireDelegate(): DefaultDownloadManager {
        return delegate ?: error("AppDownloadManager.initialize(context) must be called before use.")
    }
}
