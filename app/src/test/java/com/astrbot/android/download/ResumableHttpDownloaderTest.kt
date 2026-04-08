package com.astrbot.android.download

import java.io.File
import java.net.Socket
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableHttpDownloaderTest {
    @Test
    fun downloader_downloads_full_file_when_no_partial_exists() = runBlocking {
        val payload = "astrbot-download".repeat(1024).encodeToByteArray()
        val server = TestDownloadServer(
            payload = payload,
            etag = "\"v1\"",
            supportRange = true,
        )
        val tempDir = Files.createTempDirectory("download-full").toFile()
        try {
            server.start()
            val target = File(tempDir, "plugin.zip")
            val downloader = UrlConnectionResumableHttpDownloader()

            val completion = downloader.download(
                request = requestFor(server.url("/plugin.zip"), target),
                existing = null,
                onProgress = {},
            )

            assertEquals(payload.size.toLong(), completion.totalBytes)
            assertTrue(target.exists())
            assertArrayEquals(payload, target.readBytes())
        } finally {
            server.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloader_resumes_from_partial_file_when_server_supports_range() = runBlocking {
        val payload = "range-supported".repeat(2048).encodeToByteArray()
        val server = TestDownloadServer(
            payload = payload,
            etag = "\"v1\"",
            supportRange = true,
        )
        val tempDir = Files.createTempDirectory("download-range").toFile()
        try {
            server.start()
            val target = File(tempDir, "asset.tar.bz2")
            val partial = File(tempDir, "asset.tar.bz2.part")
            val midpoint = payload.size / 2
            partial.writeBytes(payload.copyOfRange(0, midpoint))
            val downloader = UrlConnectionResumableHttpDownloader()

            downloader.download(
                request = requestFor(server.url("/asset.tar.bz2"), target),
                existing = recordFor(
                    request = requestFor(server.url("/asset.tar.bz2"), target),
                    downloadedBytes = midpoint.toLong(),
                    totalBytes = payload.size.toLong(),
                    etag = "\"v1\"",
                ),
                onProgress = {},
            )

            assertEquals("bytes=$midpoint-", server.lastRangeHeader)
            assertTrue(target.exists())
            assertArrayEquals(payload, target.readBytes())
        } finally {
            server.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloader_falls_back_to_full_download_when_etag_changes() = runBlocking {
        val payload = "etag-changed".repeat(1536).encodeToByteArray()
        val server = TestDownloadServer(
            payload = payload,
            etag = "\"new\"",
            supportRange = true,
        )
        val tempDir = Files.createTempDirectory("download-etag").toFile()
        try {
            server.start()
            val target = File(tempDir, "plugin.zip")
            val partial = File(tempDir, "plugin.zip.part")
            partial.writeBytes(payload.copyOfRange(0, payload.size / 3))
            val initialPartialLength = partial.length()
            val downloader = UrlConnectionResumableHttpDownloader()

            downloader.download(
                request = requestFor(server.url("/plugin.zip"), target),
                existing = recordFor(
                    request = requestFor(server.url("/plugin.zip"), target),
                    downloadedBytes = partial.length(),
                    totalBytes = payload.size.toLong(),
                    etag = "\"old\"",
                ),
                onProgress = {},
            )

            assertArrayEquals(payload, target.readBytes())
            assertEquals("bytes=$initialPartialLength-", server.lastRangeHeader)
            assertEquals("\"old\"", server.lastIfRangeHeader)
        } finally {
            server.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloader_falls_back_to_full_download_when_server_does_not_support_range() = runBlocking {
        val payload = "range-fallback".repeat(1536).encodeToByteArray()
        val server = TestDownloadServer(
            payload = payload,
            etag = "\"v1\"",
            supportRange = false,
        )
        val tempDir = Files.createTempDirectory("download-no-range").toFile()
        try {
            server.start()
            val target = File(tempDir, "asset.tar.bz2")
            val partial = File(tempDir, "asset.tar.bz2.part")
            partial.writeBytes(payload.copyOfRange(0, payload.size / 4))
            val initialPartialLength = partial.length()
            val downloader = UrlConnectionResumableHttpDownloader()

            downloader.download(
                request = requestFor(server.url("/asset.tar.bz2"), target),
                existing = recordFor(
                    request = requestFor(server.url("/asset.tar.bz2"), target),
                    downloadedBytes = partial.length(),
                    totalBytes = payload.size.toLong(),
                    etag = "\"v1\"",
                ),
                onProgress = {},
            )

            assertArrayEquals(payload, target.readBytes())
            assertEquals("bytes=$initialPartialLength-", server.lastRangeHeader)
        } finally {
            server.close()
            tempDir.deleteRecursively()
        }
    }

    private fun requestFor(url: String, target: File): DownloadRequest {
        return DownloadRequest(
            taskKey = "task:${target.name}",
            url = url,
            targetFilePath = target.absolutePath,
            displayName = target.name,
            ownerType = DownloadOwnerType.RUNTIME_ASSET,
            ownerId = target.nameWithoutExtension,
        )
    }

    private fun recordFor(
        request: DownloadRequest,
        downloadedBytes: Long,
        totalBytes: Long,
        etag: String,
    ): DownloadTaskRecord {
        return DownloadTaskRecord(
            taskKey = request.taskKey,
            url = request.url,
            targetFilePath = request.targetFilePath,
            partialFilePath = request.partialFilePath,
            displayName = request.displayName,
            ownerType = request.ownerType,
            ownerId = request.ownerId,
            status = DownloadTaskStatus.RUNNING,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            bytesPerSecond = 0L,
            etag = etag,
            lastModified = null,
            errorMessage = "",
            createdAt = 1L,
            updatedAt = 1L,
            completedAt = null,
        )
    }
}

private class TestDownloadServer(
    private val payload: ByteArray,
    private val etag: String,
    private val supportRange: Boolean,
) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    private val serverThread = thread(start = false, name = "test-download-server") {
        while (!serverSocket.isClosed) {
            val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
            handle(socket)
        }
    }

    @Volatile
    var lastRangeHeader: String? = null

    @Volatile
    var lastIfRangeHeader: String? = null

    fun start() {
        serverThread.start()
    }

    fun url(path: String): String {
        return "http://127.0.0.1:${serverSocket.localPort}$path"
    }

    override fun close() {
        runCatching { serverSocket.close() }
        serverThread.join(1_000)
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            val input = client.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            if (requestLine.isBlank()) return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) break
                val separatorIndex = line.indexOf(':')
                if (separatorIndex > 0) {
                    val key = line.substring(0, separatorIndex).trim()
                    val value = line.substring(separatorIndex + 1).trim()
                    headers[key] = value
                }
            }
            lastRangeHeader = headers["Range"]
            lastIfRangeHeader = headers["If-Range"]
            val canResume = supportRange &&
                lastRangeHeader != null &&
                (lastIfRangeHeader == null || lastIfRangeHeader == etag)
            val body = if (canResume) {
                val start = lastRangeHeader
                    ?.removePrefix("bytes=")
                    ?.substringBefore('-')
                    ?.toInt()
                    ?: 0
                payload.copyOfRange(start, payload.size)
            } else {
                payload
            }
            val statusLine = if (canResume) {
                "HTTP/1.1 206 Partial Content"
            } else {
                "HTTP/1.1 200 OK"
            }
            val extraHeaders = buildList {
                add("ETag: $etag")
                add("Accept-Ranges: ${if (supportRange) "bytes" else "none"}")
                add("Content-Length: ${body.size}")
                if (canResume) {
                    val start = lastRangeHeader
                        ?.removePrefix("bytes=")
                        ?.substringBefore('-')
                        ?.toInt()
                        ?: 0
                    add("Content-Range: bytes $start-${payload.lastIndex}/${payload.size}")
                }
            }
            val response = buildString {
                append(statusLine).append("\r\n")
                extraHeaders.forEach { header ->
                    append(header).append("\r\n")
                }
                append("Connection: close\r\n")
                append("\r\n")
            }.encodeToByteArray()
            client.getOutputStream().use { output ->
                output.write(response)
                output.write(body)
                output.flush()
            }
        }
    }
}
