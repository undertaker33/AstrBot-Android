package com.astrbot.android.download

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

fun interface HttpUrlConnectionFactory {
    fun open(url: String): HttpURLConnection
}

interface ResumableHttpDownloader {
    suspend fun download(
        request: DownloadRequest,
        existing: DownloadTaskRecord?,
        shouldCancel: () -> Boolean = { false },
        onProgress: suspend (DownloadTransferSnapshot) -> Unit = {},
    ): DownloadCompletion
}

class UrlConnectionResumableHttpDownloader(
    private val connectionFactory: HttpUrlConnectionFactory = HttpUrlConnectionFactory { url ->
        URL(url).openConnection() as HttpURLConnection
    },
) : ResumableHttpDownloader {
    override suspend fun download(
        request: DownloadRequest,
        existing: DownloadTaskRecord?,
        shouldCancel: () -> Boolean,
        onProgress: suspend (DownloadTransferSnapshot) -> Unit,
    ): DownloadCompletion {
        val targetFile = File(request.targetFilePath)
        val partialFile = File(request.partialFilePath)
        targetFile.parentFile?.mkdirs()
        partialFile.parentFile?.mkdirs()
        val resumeBytes = partialFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
        val attemptResume = resumeBytes > 0L
        val connection = connectionFactory.open(request.url).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            doInput = true
            if (attemptResume) {
                setRequestProperty("Range", "bytes=$resumeBytes-")
                existing?.etag?.takeIf { it.isNotBlank() }?.let { setRequestProperty("If-Range", it) }
                    ?: existing?.lastModified?.takeIf { it.isNotBlank() }?.let { setRequestProperty("If-Range", it) }
            }
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code while downloading ${request.displayName}.")
            }
            val resuming = code == HttpURLConnection.HTTP_PARTIAL && attemptResume
            if (!resuming && partialFile.exists()) {
                partialFile.delete()
            }
            val responseEtag = connection.getHeaderField("ETag")
            val responseLastModified = connection.getHeaderField("Last-Modified")
            val totalBytes = when {
                resuming -> {
                    connection.getHeaderField("Content-Range")
                        ?.substringAfter('/')
                        ?.toLongOrNull()
                        ?: connection.contentLengthLong.takeIf { it >= 0L }?.plus(resumeBytes)
                        ?: existing?.totalBytes
                }
                else -> connection.contentLengthLong.takeIf { it >= 0L }
            }
            var downloadedBytes = if (resuming) resumeBytes else 0L
            val startedAtNanos = System.nanoTime()
            onProgress(
                DownloadTransferSnapshot(
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    bytesPerSecond = 0L,
                    etag = responseEtag,
                    lastModified = responseLastModified,
                ),
            )
            connection.inputStream.use { input ->
                FileOutputStream(partialFile, resuming).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (shouldCancel()) {
                            throw DownloadInterruptedException()
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        onProgress(
                            DownloadTransferSnapshot(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                bytesPerSecond = calculateBytesPerSecond(
                                    bytesDownloaded = downloadedBytes,
                                    startedAtNanos = startedAtNanos,
                                ),
                                etag = responseEtag,
                                lastModified = responseLastModified,
                            ),
                        )
                    }
                }
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!partialFile.renameTo(targetFile)) {
                partialFile.copyTo(targetFile, overwrite = true)
                partialFile.delete()
            }
            return DownloadCompletion(
                totalBytes = targetFile.length(),
                etag = responseEtag,
                lastModified = responseLastModified,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun calculateBytesPerSecond(
        bytesDownloaded: Long,
        startedAtNanos: Long,
    ): Long {
        val elapsedNanos = (System.nanoTime() - startedAtNanos).coerceAtLeast(1L)
        return (bytesDownloaded * 1_000_000_000L) / elapsedNanos
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 20_000
        private const val READ_TIMEOUT_MILLIS = 120_000
    }
}

class DownloadInterruptedException : IllegalStateException("Download paused.")
