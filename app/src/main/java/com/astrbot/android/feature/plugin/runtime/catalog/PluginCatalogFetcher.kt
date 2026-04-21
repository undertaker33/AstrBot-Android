package com.astrbot.android.feature.plugin.runtime.catalog

import com.astrbot.android.core.common.logging.AppLogger
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

fun interface PluginCatalogFetcher {
    suspend fun fetch(catalogUrl: String): String
}

class UrlConnectionPluginCatalogFetcher @Inject constructor() : PluginCatalogFetcher {
    override suspend fun fetch(catalogUrl: String): String {
        AppLogger.append("Plugin market fetch start: url=$catalogUrl")
        val connection = (URL(catalogUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
        }
        return try {
            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            check(responseCode in 200..299) {
                val preview = responseBody
                    .lineSequence()
                    .map(String::trim)
                    .firstOrNull { it.isNotBlank() }
                    ?.take(MAX_ERROR_PREVIEW_LENGTH)
                    .orEmpty()
                "Plugin catalog fetch failed with HTTP $responseCode${if (preview.isNotBlank()) " body=$preview" else ""}"
            }
            AppLogger.append(
                "Plugin market fetch completed: url=$catalogUrl code=$responseCode chars=${responseBody.length}",
            )
            responseBody
        } catch (error: Throwable) {
            AppLogger.append("Plugin market fetch failed: url=$catalogUrl error=${error.toRuntimeLogSummary()}")
            throw error
        } finally {
            connection.disconnect()
        }
    }
}

private fun Throwable.toRuntimeLogSummary(): String {
    return message?.trim().takeUnless { it.isNullOrBlank() } ?: javaClass.simpleName
}

private const val MAX_ERROR_PREVIEW_LENGTH = 160
