package com.astrbot.android.runtime.plugin.catalog

import com.astrbot.android.data.plugin.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.runtime.RuntimeLogRepository
import java.net.URI
import java.security.MessageDigest

data class PluginRepositorySubscriptionResult(
    val source: PluginRepositorySource,
    val syncState: PluginCatalogSyncState,
)

class PluginRepositorySubscriptionManager(
    private val store: PluginCatalogSyncStore,
    private val synchronizer: PluginCatalogSynchronizer,
    private val sourceIdFactory: (String) -> String = ::defaultPluginRepositorySourceId,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun subscribeAndSync(rawCatalogUrl: String): PluginRepositorySubscriptionResult {
        RuntimeLogRepository.append("Plugin market subscribe start: rawUrl=$rawCatalogUrl")
        val intent = PluginInstallIntent.repositoryUrl(rawCatalogUrl)
        val catalogUrl = intent.url
        val existing = store.listRepositorySources().firstOrNull { it.catalogUrl == catalogUrl }
        val source = existing ?: PluginRepositorySource(
            sourceId = sourceIdFactory(catalogUrl),
            title = buildRepositoryTitle(catalogUrl),
            catalogUrl = catalogUrl,
            updatedAt = now(),
        )
        RuntimeLogRepository.append(
            "Plugin market subscribe normalized: " +
                "catalogUrl=$catalogUrl " +
                "existing=${existing != null} " +
                "sourceId=${source.sourceId}",
        )
        store.upsertRepositorySource(source)
        val syncState = synchronizer.sync(source.sourceId)
        val refreshed = store.getRepositorySource(source.sourceId) ?: source
        RuntimeLogRepository.append(
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
