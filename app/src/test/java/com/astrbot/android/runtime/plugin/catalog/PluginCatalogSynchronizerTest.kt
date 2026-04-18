package com.astrbot.android.feature.plugin.runtime.catalog

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBusProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogSynchronizerTest {
    @Test
    fun sync_persists_normalized_catalog_and_marks_source_success() = runBlocking {
        RuntimeLogRepository.clear()
        val store = FakePluginCatalogSyncStore(
            source = subscribedSource(),
        )
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = FakePluginCatalogFetcher(
                responseByUrl = mapOf(
                    "https://repo.example.com/catalogs/stable/index.json" to successCatalogJson(),
                ),
            ),
            now = { 9_999L },
        )

        val result = synchronizer.sync("official")

        assertEquals(PluginCatalogSyncStatus.SUCCESS, result.lastSyncStatus)
        assertEquals(9_999L, result.lastSyncAtEpochMillis)
        assertEquals("", result.lastSyncErrorSummary)
        assertEquals(1, store.replacedCatalogs.size)
        assertEquals(
            "https://repo.example.com/catalogs/packages/weather-1.4.0.zip",
            store.replacedCatalogs.single().plugins.single().versions.single().packageUrl,
        )
        assertEquals(
            PluginCatalogSyncStatus.SUCCESS,
            store.sources.single().lastSyncStatus,
        )
        assertTrue(
            RuntimeLogRepository.logs.value.any {
                it.contains("Plugin market sync start") &&
                    it.contains("sourceId=official")
            },
        )
        assertTrue(
            RuntimeLogRepository.logs.value.any {
                it.contains("Plugin market sync success") &&
                    it.contains("sourceId=official") &&
                    it.contains("plugins=1") &&
                    it.contains("versions=1")
            },
        )
    }

    @Test
    fun sync_publishes_structured_market_v2_validation_completed_hook() = runBlocking {
        val bus = InMemoryPluginRuntimeLogBus(capacity = 16)
        PluginRuntimeLogBusProvider.setBusOverrideForTests(bus)
        try {
            val store = FakePluginCatalogSyncStore(
                source = subscribedSource(),
            )
            val synchronizer = PluginCatalogSynchronizer(
                store = store,
                fetcher = FakePluginCatalogFetcher(
                    responseByUrl = mapOf(
                        "https://repo.example.com/catalogs/stable/index.json" to successCatalogJson(),
                    ),
                ),
                now = { 9_999L },
            )

            synchronizer.sync("official")

            val record = bus.snapshot(
                pluginId = "__market__",
                code = "market_v2_validation_completed",
            ).single()
            assertEquals("market_v2_validation_completed", record.metadata["code"])
            assertEquals("PluginMarket", record.stage)
            assertEquals("SUCCESS", record.outcome)
            assertEquals("official", record.metadata["sourceId"])
            assertEquals("1", record.metadata["pluginCount"])
            assertEquals("1", record.metadata["versionCount"])
            assertEquals("1", record.metadata["v2VersionCount"])
            assertEquals("0", record.metadata["issueCount"])
        } finally {
            PluginRuntimeLogBusProvider.setBusOverrideForTests(null)
        }
    }

    @Test
    fun sync_replaces_empty_catalog_and_drops_existing_cache() = runBlocking {
        val cached = subscribedSource(
            plugins = listOf(sampleEntry()),
        )
        val store = FakePluginCatalogSyncStore(source = cached)
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = FakePluginCatalogFetcher(
                responseByUrl = mapOf(
                    cached.catalogUrl to emptyCatalogJson(),
                ),
            ),
            now = { 7_000L },
        )

        val result = synchronizer.sync(cached.sourceId)

        assertEquals(PluginCatalogSyncStatus.EMPTY, result.lastSyncStatus)
        assertEquals(7_000L, result.lastSyncAtEpochMillis)
        assertEquals(1, store.replacedCatalogs.size)
        assertTrue(store.replacedCatalogs.single().plugins.isEmpty())
        assertTrue(store.sources.single().plugins.isEmpty())
        assertEquals(PluginCatalogSyncStatus.EMPTY, store.sources.single().lastSyncStatus)
    }

    @Test
    fun sync_marks_failure_on_bad_json_and_preserves_cached_entries() = runBlocking {
        val cached = subscribedSource(
            plugins = listOf(sampleEntry()),
        )
        val store = FakePluginCatalogSyncStore(source = cached)
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = FakePluginCatalogFetcher(
                responseByUrl = mapOf(
                    cached.catalogUrl to """{"sourceId":"official","title":"Broken","catalogUrl":"https://repo.example.com/catalogs/stable/index.json","plugins":[{"pluginId":""}]}""",
                ),
            ),
            now = { 8_000L },
        )

        val result = synchronizer.sync(cached.sourceId)

        assertEquals(PluginCatalogSyncStatus.FAILED, result.lastSyncStatus)
        assertEquals(8_000L, result.lastSyncAtEpochMillis)
        assertTrue(result.lastSyncErrorSummary.contains("required"))
        assertTrue(store.replacedCatalogs.isEmpty())
        assertEquals(listOf(sampleEntry()), store.sources.single().plugins)
        assertEquals(PluginCatalogSyncStatus.FAILED, store.sources.single().lastSyncStatus)
    }

    @Test
    fun sync_marks_failure_on_network_error_and_preserves_cached_entries() = runBlocking {
        val cached = subscribedSource(
            plugins = listOf(sampleEntry()),
        )
        val store = FakePluginCatalogSyncStore(source = cached)
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = FakePluginCatalogFetcher(
                failureByUrl = mapOf(
                    cached.catalogUrl to IllegalStateException("timeout while fetching catalog"),
                ),
            ),
            now = { 8_100L },
        )

        val result = synchronizer.sync(cached.sourceId)

        assertEquals(PluginCatalogSyncStatus.FAILED, result.lastSyncStatus)
        assertEquals("timeout while fetching catalog", result.lastSyncErrorSummary)
        assertTrue(store.replacedCatalogs.isEmpty())
        assertEquals(listOf(sampleEntry()), store.sources.single().plugins)
    }

    @Test
    fun sync_normalizes_github_blob_catalog_url_before_fetching_existing_source() = runBlocking {
        val blobSource = subscribedSource().copy(
            catalogUrl = "https://github.com/undertaker33/astrbot_android_plugin_memes/blob/main/publish/0.1.0/repository/catalog.json",
        )
        val rawUrl = "https://raw.githubusercontent.com/undertaker33/astrbot_android_plugin_memes/main/publish/0.1.0/repository/catalog.json"
        val store = FakePluginCatalogSyncStore(source = blobSource)
        val fetcher = FakePluginCatalogFetcher(
            responseByUrl = mapOf(rawUrl to successCatalogJson()),
        )
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = fetcher,
            now = { 9_999L },
        )

        val result = synchronizer.sync(blobSource.sourceId)

        assertEquals(PluginCatalogSyncStatus.SUCCESS, result.lastSyncStatus)
        assertEquals(rawUrl, fetcher.requestedUrls.single())
        assertEquals(rawUrl, store.sources.single().catalogUrl)
    }

    @Test
    fun sync_keeps_success_summary_empty_even_when_catalog_contains_legacy_v1_versions() = runBlocking {
        val store = FakePluginCatalogSyncStore(source = subscribedSource())
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = FakePluginCatalogFetcher(
                responseByUrl = mapOf(
                    "https://repo.example.com/catalogs/stable/index.json" to legacyV1CatalogJson(),
                ),
            ),
            now = { 10_500L },
        )

        val result = synchronizer.sync("official")

        assertEquals(PluginCatalogSyncStatus.SUCCESS, result.lastSyncStatus)
        assertEquals("", result.lastSyncErrorSummary)
        assertEquals(result.lastSyncErrorSummary, store.sources.single().lastSyncErrorSummary)
    }

    @Test
    fun market_gate_projects_legacy_v1_catalog_versions_with_upgrade_to_protocol_2_notes() {
        val gate = PluginRepository.evaluateCatalogVersion(
            version = PluginCatalogVersion(
                version = "1.9.0",
                packageUrl = "https://repo.example.com/catalogs/packages/legacy-1.9.0.zip",
                publishedAt = 1_600L,
                protocolVersion = 1,
                minHostVersion = "0.2.0",
                maxHostVersion = "",
                permissions = emptyList(),
                changelog = "Legacy runtime package.",
            ),
            hostVersion = "0.4.6",
        )

        assertEquals(
            "Legacy v1 plugin packages are unsupported. Upgrade the plugin package to protocol version 2.",
            gate.compatibilityState.notes,
        )
        assertEquals(false, gate.installable)
    }

    @Test
    fun market_gate_keeps_generic_unsupported_wording_for_non_v1_non_v2_protocols() {
        val gate = PluginRepository.evaluateCatalogVersion(
            version = PluginCatalogVersion(
                version = "3.0.0",
                packageUrl = "https://repo.example.com/catalogs/packages/future-3.0.0.zip",
                publishedAt = 1_900L,
                protocolVersion = 3,
                minHostVersion = "0.2.0",
                maxHostVersion = "",
                permissions = emptyList(),
                changelog = "Future runtime package.",
            ),
            hostVersion = "0.4.6",
        )

        assertEquals(
            "Protocol version 3 is not supported.",
            gate.compatibilityState.notes,
        )
        assertEquals(false, gate.installable)
    }

    @Test
    fun sync_keeps_success_summary_empty_for_non_v1_non_v2_protocols() = runBlocking {
        val store = FakePluginCatalogSyncStore(source = subscribedSource())
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = FakePluginCatalogFetcher(
                responseByUrl = mapOf(
                    "https://repo.example.com/catalogs/stable/index.json" to unsupportedProtocolCatalogJson(protocolVersion = 3),
                ),
            ),
            now = { 10_600L },
        )

        val result = synchronizer.sync("official")

        assertEquals(PluginCatalogSyncStatus.SUCCESS, result.lastSyncStatus)
        assertEquals("", result.lastSyncErrorSummary)
        assertEquals(result.lastSyncErrorSummary, store.sources.single().lastSyncErrorSummary)
    }
}

private class FakePluginCatalogFetcher(
    private val responseByUrl: Map<String, String> = emptyMap(),
    private val failureByUrl: Map<String, Throwable> = emptyMap(),
) : PluginCatalogFetcher {
    val requestedUrls = mutableListOf<String>()

    override suspend fun fetch(catalogUrl: String): String {
        requestedUrls += catalogUrl
        failureByUrl[catalogUrl]?.let { throw it }
        return responseByUrl[catalogUrl] ?: error("No fake response for $catalogUrl")
    }
}

private class FakePluginCatalogSyncStore(
    source: PluginRepositorySource,
) : PluginCatalogSyncStore {
    val replacedCatalogs = mutableListOf<PluginRepositorySource>()
    val sources = mutableListOf(source)

    override fun getRepositorySource(sourceId: String): PluginRepositorySource? {
        return sources.firstOrNull { it.sourceId == sourceId }
    }

    override fun getRepositorySourceSyncState(sourceId: String): PluginCatalogSyncState? {
        return getRepositorySource(sourceId)?.let { source ->
            PluginCatalogSyncState(
                sourceId = source.sourceId,
                lastSyncAtEpochMillis = source.lastSyncAtEpochMillis,
                lastSyncStatus = source.lastSyncStatus,
                lastSyncErrorSummary = source.lastSyncErrorSummary,
            )
        }
    }

    override fun listRepositorySources(): List<PluginRepositorySource> = sources.toList()

    override fun listAllCatalogEntries(): List<PluginCatalogEntryRecord> {
        return sources.flatMap { source ->
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
        return getRepositorySource(sourceId)
            ?.plugins
            ?.firstOrNull { it.pluginId == pluginId }
            ?.versions
            .orEmpty()
    }

    override fun replaceRepositoryCatalog(source: PluginRepositorySource) {
        replacedCatalogs += source
        val index = sources.indexOfFirst { it.sourceId == source.sourceId }
        if (index >= 0) {
            sources[index] = source
        } else {
            sources += source
        }
    }

    override fun upsertRepositorySource(source: PluginRepositorySource) {
        val index = sources.indexOfFirst { it.sourceId == source.sourceId }
        if (index >= 0) {
            val cachedPlugins = sources[index].plugins
            sources[index] = source.copy(
                plugins = if (source.plugins.isEmpty()) cachedPlugins else source.plugins,
            )
        } else {
            sources += source
        }
    }
}

private fun subscribedSource(
    plugins: List<PluginCatalogEntry> = emptyList(),
): PluginRepositorySource {
    return PluginRepositorySource(
        sourceId = "official",
        title = "Official Repository",
        catalogUrl = "https://repo.example.com/catalogs/stable/index.json",
        updatedAt = 100L,
        plugins = plugins,
    )
}

private fun sampleEntry(): PluginCatalogEntry {
    return PluginCatalogEntry(
        pluginId = "com.example.weather",
        title = "Weather",
        author = "AstrBot",
        description = "Shows current weather.",
        entrySummary = "Weather commands",
        scenarios = listOf("forecast", "alerts"),
        versions = listOf(
            PluginCatalogVersion(
                version = "1.4.0",
                packageUrl = "https://repo.example.com/catalogs/packages/weather-1.4.0.zip",
                publishedAt = 1_700L,
                protocolVersion = 2,
                minHostVersion = "0.3.0",
                maxHostVersion = "0.4.0",
                permissions = listOf(
                    PluginPermissionDeclaration(
                        permissionId = "network.http",
                        title = "Network",
                        description = "Fetches weather APIs",
                        riskLevel = PluginRiskLevel.MEDIUM,
                        required = true,
                    ),
                ),
                changelog = "Adds severe weather alerts.",
            ),
        ),
    )
}

private fun successCatalogJson(): String {
    return """
        {
          "sourceId": "remote-official",
          "title": "Official Repository",
          "catalogUrl": "https://unexpected.example.com/ignored.json",
          "updatedAt": 1800,
          "plugins": [
            {
              "pluginId": "com.example.weather",
              "title": "Weather",
              "author": "AstrBot",
              "description": "Shows current weather.",
              "entrySummary": "Weather commands",
              "scenarios": ["forecast", "alerts"],
              "versions": [
                {
                  "version": "1.4.0",
                  "packageUrl": "../packages/weather-1.4.0.zip",
                  "publishedAt": 1700,
                  "protocolVersion": 2,
                  "minHostVersion": "0.3.0",
                  "maxHostVersion": "0.4.0",
                  "permissions": [
                    {
                      "permissionId": "network.http",
                      "title": "Network",
                      "description": "Fetches weather APIs",
                      "riskLevel": "MEDIUM",
                      "required": true
                    }
                  ],
                  "changelog": "Adds severe weather alerts."
                }
              ]
            }
          ]
        }
    """.trimIndent()
}

private fun emptyCatalogJson(): String {
    return """
        {
          "sourceId": "official",
          "title": "Official Repository",
          "catalogUrl": "https://repo.example.com/catalogs/stable/index.json",
          "updatedAt": 1800,
          "plugins": []
        }
    """.trimIndent()
}

private fun legacyV1CatalogJson(): String {
    return unsupportedProtocolCatalogJson(protocolVersion = 1)
}

private fun unsupportedProtocolCatalogJson(protocolVersion: Int): String {
    return """
        {
          "sourceId": "remote-official",
          "title": "Official Repository",
          "catalogUrl": "https://unexpected.example.com/ignored.json",
          "updatedAt": 1800,
          "plugins": [
            {
              "pluginId": "com.example.legacy",
              "title": "Legacy",
              "author": "AstrBot",
              "description": "Legacy plugin.",
              "entrySummary": "Legacy commands",
              "scenarios": ["legacy"],
              "versions": [
                {
                  "version": "1.9.0",
                  "packageUrl": "../packages/legacy-1.9.0.zip",
                  "publishedAt": 1600,
                  "protocolVersion": $protocolVersion,
                  "minHostVersion": "0.2.0",
                  "maxHostVersion": "",
                  "permissions": [],
                  "changelog": "Legacy runtime package."
                }
              ]
            }
          ]
        }
    """.trimIndent()
}
