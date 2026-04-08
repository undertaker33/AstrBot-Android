package com.astrbot.android.download

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadManagerTest {
    @Test
    fun enqueue_and_process_queue_completes_task_and_persists_status() = runBlocking {
        val tempDir = Files.createTempDirectory("download-manager-complete").toFile()
        val store = InMemoryDownloadTaskStore()
        val manager = DefaultDownloadManager(
            store = store,
            downloader = FakeResumableHttpDownloader { request, _, onProgress ->
                val target = File(request.targetFilePath)
                target.parentFile?.mkdirs()
                onProgress(
                    DownloadTransferSnapshot(
                        downloadedBytes = 4L,
                        totalBytes = 8L,
                        bytesPerSecond = 16L,
                        etag = "\"v1\"",
                        lastModified = null,
                    ),
                )
                target.writeBytes("complete".encodeToByteArray())
                DownloadCompletion(
                    totalBytes = target.length(),
                    etag = null,
                    lastModified = null,
                )
            },
            serviceStarter = {},
        )
        try {
            val request = requestFor(File(tempDir, "plugin.zip"))

            manager.enqueue(request)
            manager.processQueue()

            val task = checkNotNull(store.get(request.taskKey))
            assertEquals(DownloadTaskStatus.COMPLETED, task.status)
            assertEquals(File(request.targetFilePath).length(), task.totalBytes)
            assertTrue(File(request.targetFilePath).exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun running_task_is_requeued_and_resumed_after_restart() = runBlocking {
        val tempDir = Files.createTempDirectory("download-manager-resume").toFile()
        val target = File(tempDir, "asset.tar.bz2")
        val partial = File(tempDir, "asset.tar.bz2.part")
        partial.writeBytes("part".encodeToByteArray())
        val store = InMemoryDownloadTaskStore()
        val request = requestFor(target)
        store.upsert(
            DownloadTaskRecord(
                taskKey = request.taskKey,
                url = request.url,
                targetFilePath = request.targetFilePath,
                partialFilePath = request.partialFilePath,
                displayName = request.displayName,
                ownerType = request.ownerType,
                ownerId = request.ownerId,
                status = DownloadTaskStatus.RUNNING,
                downloadedBytes = partial.length(),
                totalBytes = 8L,
                bytesPerSecond = 0L,
                etag = "\"v1\"",
                lastModified = null,
                errorMessage = "",
                createdAt = 1L,
                updatedAt = 1L,
                completedAt = null,
            ),
        )
        val manager = DefaultDownloadManager(
            store = store,
            downloader = FakeResumableHttpDownloader { incomingRequest, existing, onProgress ->
                assertEquals(partial.length(), existing?.downloadedBytes)
                onProgress(
                    DownloadTransferSnapshot(
                        downloadedBytes = 8L,
                        totalBytes = 8L,
                        bytesPerSecond = 32L,
                        etag = "\"v1\"",
                        lastModified = null,
                    ),
                )
                File(incomingRequest.targetFilePath).writeBytes("resumed!".encodeToByteArray())
                DownloadCompletion(
                    totalBytes = 8L,
                    etag = null,
                    lastModified = null,
                )
            },
            serviceStarter = {},
        )
        try {
            manager.processQueue()

            val task = checkNotNull(store.get(request.taskKey))
            assertEquals(DownloadTaskStatus.COMPLETED, task.status)
            assertEquals(8L, task.downloadedBytes)
            assertTrue(target.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun enqueue_marks_existing_file_as_completed_without_redownloading() = runBlocking {
        val tempDir = Files.createTempDirectory("download-manager-existing").toFile()
        val target = File(tempDir, "plugin.zip").apply {
            parentFile?.mkdirs()
            writeBytes("ready".encodeToByteArray())
        }
        val store = InMemoryDownloadTaskStore()
        var downloadInvocations = 0
        val manager = DefaultDownloadManager(
            store = store,
            downloader = FakeResumableHttpDownloader { _, _, _ ->
                downloadInvocations += 1
                DownloadCompletion(
                    totalBytes = 0L,
                    etag = null,
                    lastModified = null,
                )
            },
            serviceStarter = {},
        )
        try {
            val request = requestFor(target)

            manager.enqueue(request)
            val result = manager.awaitCompletion(request.taskKey)

            assertEquals(0, downloadInvocations)
            assertEquals(DownloadTaskStatus.COMPLETED, result.status)
            assertEquals(target.length(), result.downloadedBytes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun requestFor(target: File): DownloadRequest {
        return DownloadRequest(
            taskKey = "task:${target.name}",
            url = "https://example.com/${target.name}",
            targetFilePath = target.absolutePath,
            displayName = target.name,
            ownerType = DownloadOwnerType.PLUGIN_PACKAGE,
            ownerId = target.nameWithoutExtension,
        )
    }
}

private class InMemoryDownloadTaskStore : DownloadTaskStore {
    private val tasks = LinkedHashMap<String, MutableStateFlow<DownloadTaskRecord?>>()

    override suspend fun get(taskKey: String): DownloadTaskRecord? = tasks[taskKey]?.value

    override fun observe(taskKey: String): Flow<DownloadTaskRecord?> {
        return tasks.getOrPut(taskKey) { MutableStateFlow(null) }
    }

    override suspend fun upsert(task: DownloadTaskRecord) {
        tasks.getOrPut(task.taskKey) { MutableStateFlow(null) }.value = task
    }

    override suspend fun listRecoverable(): List<DownloadTaskRecord> {
        return tasks.values.mapNotNull { it.value }
            .filter { it.status == DownloadTaskStatus.QUEUED || it.status == DownloadTaskStatus.RUNNING }
            .sortedBy { it.createdAt }
    }

    override suspend fun listPending(): List<DownloadTaskRecord> {
        return tasks.values.mapNotNull { it.value }
            .filter { it.status == DownloadTaskStatus.QUEUED }
            .sortedBy { it.createdAt }
    }
}

private class FakeResumableHttpDownloader(
    private val block: suspend (
        DownloadRequest,
        DownloadTaskRecord?,
        suspend (DownloadTransferSnapshot) -> Unit,
    ) -> DownloadCompletion,
) : ResumableHttpDownloader {
    override suspend fun download(
        request: DownloadRequest,
        existing: DownloadTaskRecord?,
        shouldCancel: () -> Boolean,
        onProgress: suspend (DownloadTransferSnapshot) -> Unit,
    ): DownloadCompletion {
        return block(request, existing, onProgress)
    }
}
