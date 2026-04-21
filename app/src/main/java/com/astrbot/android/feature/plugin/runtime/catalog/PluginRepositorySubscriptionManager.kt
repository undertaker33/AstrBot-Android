package com.astrbot.android.feature.plugin.runtime.catalog

import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.core.common.logging.AppLogger
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

data class PluginRepositorySubscriptionResult(
    val source: PluginRepositorySource,
    val syncState: PluginCatalogSyncState,
)

class PluginRepositorySubscriptionManager @Inject constructor(
    private val store: PluginCatalogSyncStore,
    private val synchronizer: PluginCatalogSynchronizer,
) {
    private var sourceIdFactory: (String) -> String = ::defaultPluginRepositorySourceId
    private var now: () -> Long = System::currentTimeMillis

    constructor(
        store: PluginCatalogSyncStore,
        synchronizer: PluginCatalogSynchronizer,
        sourceIdFactory: (String) -> String = ::defaultPluginRepositorySourceId,
        now: () -> Long = System::currentTimeMillis,
    ) : this(store = store, synchronizer = synchronizer) {
        this.sourceIdFactory = sourceIdFactory
        this.now = now
    }

    suspend fun subscribeAndSync(rawCatalogUrl: String): PluginRepositorySubscriptionResult {
        AppLogger.append("Plugin market subscribe start: rawUrl=$rawCatalogUrl")
        val intent = PluginInstallIntent.repositoryUrl(rawCatalogUrl)
        val catalogUrl = intent.url
        val existing = store.listRepositorySources().firstOrNull { it.catalogUrl == catalogUrl }
        val source = existing ?: PluginRepositorySource(
            sourceId = sourceIdFactory(catalogUrl),
            title = buildRepositoryTitle(catalogUrl),
            catalogUrl = catalogUrl,
            updatedAt = now(),
        )
        AppLogger.append(
            "Plugin market subscribe normalized: " +
                "catalogUrl=$catalogUrl " +
                "existing=${existing != null} " +
                "sourceId=${source.sourceId}",
        )
        store.upsertRepositorySource(source)
        val syncState = synchronizer.sync(source.sourceId)
        val refreshed = store.getRepositorySource(source.sourceId) ?: source
        AppLogger.append(
            "Plugin market subscribe finished: " +
                "sourceId=${refreshed.sourceId} " +
                "status=${syncState.lastSyncStatus.name} " +
                "plugins=${refreshed.plugins.size}",
        )
        return PluginRepositorySubscriptionResult(
            source = refreshed,
            syncState = syncState,
        )
    }
}

private fun buildRepositoryTitle(catalogUrl: String): String {
    val uri = URI(catalogUrl)
    val host = uri.host?.takeIf { it.isNotBlank() } ?: catalogUrl
    return host.removePrefix("www.")
}

private fun defaultPluginRepositorySourceId(catalogUrl: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(catalogUrl.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(12)
    return "repo-$digest"
}
