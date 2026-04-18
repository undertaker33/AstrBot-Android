package com.astrbot.android.feature.plugin.runtime.catalog

import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginRiskLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRepositorySubscriptionManagerTest {
    @Test
    fun subscribe_and_sync_persists_source_then_populates_catalog() = runBlocking {
        val store = InMemoryPluginCatalogSyncStore()
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = FakeCatalogFetcher(
                responseByUrl = mapOf(
                    "https://repo.example.com/catalog.json" to successCatalogJson(),
                ),
            ),
            now = { 200L },
        )
        val manager = PluginRepositorySubscriptionManager(
            store = store,
            synchronizer = synchronizer,
            sourceIdFactory = { "repo-source" },
            now = { 100L },
        )

        val result = manager.subscribeAndSync(" https://repo.example.com/catalog.json ")

        assertEquals("repo-source", result.source.sourceId)
        assertEquals("https://repo.example.com/catalog.json", result.source.catalogUrl)
        assertEquals(PluginCatalogSyncStatus.SUCCESS, result.syncState.lastSyncStatus)
        assertEquals(1, store.listRepositorySources().size)
        assertTrue(store.listAllCatalogEntries().isNotEmpty())
    }
}

private class InMemoryPluginCatalogSyncStore : PluginCatalogSyncStore {
    private val sources = linkedMapOf<String, PluginRepositorySource>()

    override fun getRepositorySource(sourceId: String): PluginRepositorySource? = sources[sourceId]

    override fun getRepositorySourceSyncState(sourceId: String): PluginCatalogSyncState? {
        return sources[sourceId]?.let { source ->
            PluginCatalogSyncState(
                sourceId = source.sourceId,
                lastSyncAtEpochMillis = source.lastSyncAtEpochMillis,
                lastSyncStatus = source.lastSyncStatus,
                lastSyncErrorSummary = source.lastSyncErrorSummary,
            )
        }
    }

    override fun listRepositorySources(): List<PluginRepositorySource> = sources.values.toList()

    override fun listAllCatalogEntries(): List<PluginCatalogEntryRecord> {
        return sources.values.flatMap { source ->
            source.plugins.map { entry ->
                PluginCatalogEntryRecord(
                    sourceId = source.sourceId,
                    sourceTitle = source.title,
                    catalogUrl = source.catalogUrl,
                    entry = entry,
                )
            }
        }
    }

    override fun listCatalogVersions(sourceId: String, pluginId: String): List<PluginCatalogVersion> {
        return sources[sourceId]
            ?.plugins
            ?.firstOrNull { it.pluginId == pluginId }
            ?.versions
            .orEmpty()
    }

    override fun replaceRepositoryCatalog(source: PluginRepositorySource) {
        sources[source.sourceId] = source
    }

    override fun upsertRepositorySource(source: PluginRepositorySource) {
        val existing = sources[source.sourceId]
        sources[source.sourceId] = if (existing == null) {
            source
        } else {
            source.copy(
                plugins = if (source.plugins.isEmpty()) existing.plugins else source.plugins,
            )
        }
    }
}

private class FakeCatalogFetcher(
    private val responseByUrl: Map<String, String> = emptyMap(),
) : PluginCatalogFetcher {
    override suspend fun fetch(catalogUrl: String): String {
        return responseByUrl[catalogUrl] ?: error("No fake response for $catalogUrl")
    }
}

private fun successCatalogJson(): String {
    return """
        {
          "sourceId": "upstream-source",
          "title": "Official Repository",
          "catalogUrl": "https://ignored.example.com/catalog.json",
          "updatedAt": 1800,
          "plugins": [
            {
              "pluginId": "com.example.weather",
              "title": "Weather",
              "author": "AstrBot",
              "description": "Shows current weather.",
              "entrySummary": "Weather commands",
              "versions": [
                {
                  "version": "1.4.0",
                  "packageUrl": "https://repo.example.com/packages/weather-1.4.0.zip",
                  "publishedAt": 1700,
                  "protocolVersion": 2,
                  "minHostVersion": "0.3.0",
                  "permissions": [
                    {
                      "permissionId": "network.http",
                      "title": "Network",
                      "description": "Fetches weather APIs",
                      "riskLevel": "MEDIUM",
                      "required": true
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()
}
