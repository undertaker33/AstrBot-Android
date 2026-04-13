package com.astrbot.android.runtime.plugin.catalog
import com.astrbot.android.data.plugin.catalog.PluginCatalogJson
import com.astrbot.android.data.plugin.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.runtime.RuntimeLogRepository

class PluginCatalogSynchronizer(
    private val store: PluginCatalogSyncStore,
    private val fetcher: PluginCatalogFetcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val decode: (String) -> PluginRepositorySource = PluginCatalogJson::decodeRepositorySource,
) {
    suspend fun sync(sourceId: String): PluginCatalogSyncState {
        val subscribedSource = store.getRepositorySource(sourceId)
            ?: error("Plugin repository source not found for sourceId=$sourceId")
        val syncSource = subscribedSource.withNormalizedCatalogUrl()
        if (syncSource.catalogUrl != subscribedSource.catalogUrl) {
            RuntimeLogRepository.append(
                "Plugin market sync normalized source URL: " +
                    "sourceId=${subscribedSource.sourceId} " +
                    "from=${subscribedSource.catalogUrl} " +
                    "to=${syncSource.catalogUrl}",
            )
            store.upsertRepositorySource(syncSource)
        }

        val attemptAt = now()
        RuntimeLogRepository.append(
            "Plugin market sync start: " +
                "sourceId=${syncSource.sourceId} " +
                "url=${syncSource.catalogUrl} " +
                "cachedPlugins=${syncSource.plugins.size}",
        )
        return runCatching {
            val rawJson = fetcher.fetch(syncSource.catalogUrl)
            RuntimeLogRepository.append(
                "Plugin market sync fetched: sourceId=${syncSource.sourceId} chars=${rawJson.length}",
            )
            val parsedSource = decode(rawJson)
            RuntimeLogRepository.append(
                "Plugin market sync decoded: " +
                    "sourceId=${syncSource.sourceId} " +
                    "upstreamSourceId=${parsedSource.sourceId} " +
                    "plugins=${parsedSource.plugins.size}",
            )
            val normalized = parsedSource.normalizeForSubscription(
                sourceId = syncSource.sourceId,
                catalogUrl = syncSource.catalogUrl,
                lastSyncAtEpochMillis = attemptAt,
            )
            if (normalized.plugins.isEmpty()) {
                val emptySource = normalized.copy(lastSyncStatus = PluginCatalogSyncStatus.EMPTY)
                store.upsertRepositorySource(emptySource)
                RuntimeLogRepository.append(
                    "Plugin market sync empty: sourceId=${emptySource.sourceId} cachedPlugins=${syncSource.plugins.size}",
                )
                emptySource.toSyncState()
            } else {
                val successSource = normalized.copy(lastSyncStatus = PluginCatalogSyncStatus.SUCCESS)
                store.replaceRepositoryCatalog(successSource)
                RuntimeLogRepository.append(
                    "Plugin market sync success: " +
                        "sourceId=${successSource.sourceId} " +
                        "plugins=${successSource.plugins.size} " +
                        "versions=${successSource.plugins.sumOf { it.versions.size }}",
                )
                successSource.toSyncState()
            }
        }.getOrElse { failure ->
            val failedSource = syncSource.copy(
                lastSyncAtEpochMillis = attemptAt,
                lastSyncStatus = PluginCatalogSyncStatus.FAILED,
                lastSyncErrorSummary = failure.toErrorSummary(),
            )
            store.upsertRepositorySource(failedSource)
            RuntimeLogRepository.append(
                "Plugin market sync failed: sourceId=${failedSource.sourceId} error=${failure.toRuntimeLogSummary()}",
            )
            failedSource.toSyncState()
        }
    }
}

private fun PluginRepositorySource.withNormalizedCatalogUrl(): PluginRepositorySource {
    val normalizedCatalogUrl = runCatching {
        PluginInstallIntent.repositoryUrl(catalogUrl).url
    }.getOrDefault(catalogUrl)
    return if (normalizedCatalogUrl == catalogUrl) this else copy(catalogUrl = normalizedCatalogUrl)
}

private fun PluginRepositorySource.normalizeForSubscription(
    sourceId: String,
    catalogUrl: String,
    lastSyncAtEpochMillis: Long,
): PluginRepositorySource {
    return copy(
        sourceId = sourceId,
        catalogUrl = catalogUrl,
        lastSyncAtEpochMillis = lastSyncAtEpochMillis,
        lastSyncStatus = PluginCatalogSyncStatus.NEVER_SYNCED,
        lastSyncErrorSummary = "",
        plugins = plugins.map { entry ->
            entry.copy(
                versions = entry.versions.map { version ->
                    version.normalizeAgainstCatalog(catalogUrl)
                },
            )
        },
    )
}

private fun PluginCatalogVersion.normalizeAgainstCatalog(catalogUrl: String): PluginCatalogVersion {
    return copy(packageUrl = resolvePackageUrl(catalogUrl))
}

private fun PluginRepositorySource.toSyncState(): PluginCatalogSyncState {
    return PluginCatalogSyncState(
        sourceId = sourceId,
        lastSyncAtEpochMillis = lastSyncAtEpochMillis,
        lastSyncStatus = lastSyncStatus,
        lastSyncErrorSummary = lastSyncErrorSummary,
    )
}

private fun Throwable.toErrorSummary(): String {
    val message = message?.trim().orEmpty()
    return when {
        message.isNotBlank() -> message.take(MAX_ERROR_SUMMARY_LENGTH)
        else -> javaClass.simpleName.take(MAX_ERROR_SUMMARY_LENGTH)
    }
}

private fun Throwable.toRuntimeLogSummary(): String {
    return message?.trim().takeUnless { it.isNullOrBlank() } ?: javaClass.simpleName
}

private const val MAX_ERROR_SUMMARY_LENGTH = 240
