package com.astrbot.android.feature.plugin.runtime.catalog
import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogJson
import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBusProvider
import com.astrbot.android.feature.plugin.runtime.publishMarketV2ValidationCompleted
import javax.inject.Inject

class PluginCatalogSynchronizer @Inject constructor(
    private val store: PluginCatalogSyncStore,
    private val fetcher: PluginCatalogFetcher,
    private val logBus: PluginRuntimeLogBus,
) {
    private var now: () -> Long = System::currentTimeMillis
    private var decode: (String) -> PluginRepositorySource = PluginCatalogJson::decodeRepositorySource

    constructor(
        store: PluginCatalogSyncStore,
        fetcher: PluginCatalogFetcher,
        now: () -> Long = System::currentTimeMillis,
        decode: (String) -> PluginRepositorySource = PluginCatalogJson::decodeRepositorySource,
        logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    ) : this(store = store, fetcher = fetcher, logBus = logBus) {
        this.now = now
        this.decode = decode
    }

    suspend fun sync(sourceId: String): PluginCatalogSyncState {
        val subscribedSource = store.getRepositorySource(sourceId)
            ?: error("Plugin repository source not found for sourceId=$sourceId")
        val syncSource = subscribedSource.withNormalizedCatalogUrl()
        if (syncSource.catalogUrl != subscribedSource.catalogUrl) {
            AppLogger.append(
                "Plugin market sync normalized source URL: " +
                    "sourceId=${subscribedSource.sourceId} " +
                    "from=${subscribedSource.catalogUrl} " +
                    "to=${syncSource.catalogUrl}",
            )
            store.upsertRepositorySource(syncSource)
        }

        val attemptAt = now()
        AppLogger.append(
            "Plugin market sync start: " +
                "sourceId=${syncSource.sourceId} " +
                "url=${syncSource.catalogUrl} " +
                "cachedPlugins=${syncSource.plugins.size}",
        )
        return runCatching {
            val rawJson = fetcher.fetch(syncSource.catalogUrl)
            AppLogger.append(
                "Plugin market sync fetched: sourceId=${syncSource.sourceId} chars=${rawJson.length}",
            )
            val parsedSource = decode(rawJson)
            AppLogger.append(
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
                store.replaceRepositoryCatalog(emptySource)
                logBus.publishMarketValidationForSource(
                    source = emptySource,
                    outcome = PluginCatalogSyncStatus.EMPTY.name,
                    occurredAtEpochMillis = now(),
                )
                AppLogger.append(
                    "Plugin market sync empty: sourceId=${emptySource.sourceId} cachedPlugins=${syncSource.plugins.size}",
                )
                AppLogger.flush()
                emptySource.toSyncState()
            } else {
                val successSource = normalized.copy(lastSyncStatus = PluginCatalogSyncStatus.SUCCESS)
                store.replaceRepositoryCatalog(successSource)
                logBus.publishMarketValidationForSource(
                    source = successSource,
                    outcome = PluginCatalogSyncStatus.SUCCESS.name,
                    occurredAtEpochMillis = now(),
                )
                AppLogger.append(
                    "Plugin market sync success: " +
                        "sourceId=${successSource.sourceId} " +
                        "plugins=${successSource.plugins.size} " +
                        "versions=${successSource.plugins.sumOf { it.versions.size }}",
                )
                AppLogger.flush()
                successSource.toSyncState()
            }
        }.getOrElse { failure ->
            val failedSource = syncSource.copy(
                lastSyncAtEpochMillis = attemptAt,
                lastSyncStatus = PluginCatalogSyncStatus.FAILED,
                lastSyncErrorSummary = failure.toErrorSummary(),
            )
            store.upsertRepositorySource(failedSource)
            logBus.publishMarketV2ValidationCompleted(
                occurredAtEpochMillis = now(),
                sourceId = failedSource.sourceId,
                outcome = PluginCatalogSyncStatus.FAILED.name,
                pluginCount = 0,
                versionCount = 0,
                v2VersionCount = 0,
                issueCount = 1,
            )
            AppLogger.append(
                "Plugin market sync failed: sourceId=${failedSource.sourceId} error=${failure.toRuntimeLogSummary()}",
            )
            AppLogger.flush()
            failedSource.toSyncState()
        }
    }
}

private fun PluginRuntimeLogBus.publishMarketValidationForSource(
    source: PluginRepositorySource,
    outcome: String,
    occurredAtEpochMillis: Long,
) {
    val versions = source.plugins.flatMap(PluginCatalogEntry::versions)
    publishMarketV2ValidationCompleted(
        occurredAtEpochMillis = occurredAtEpochMillis,
        sourceId = source.sourceId,
        outcome = outcome,
        pluginCount = source.plugins.size,
        versionCount = versions.size,
        v2VersionCount = versions.count { version -> version.protocolVersion == 2 },
        issueCount = versions.count { version -> version.protocolVersion != 2 },
    )
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
