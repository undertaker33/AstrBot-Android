package com.astrbot.android.runtime.plugin.samples

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.plugin.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.runtime.plugin.catalog.PluginCatalogFetcher
import com.astrbot.android.runtime.plugin.catalog.PluginCatalogSynchronizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemeManagerSampleCatalogTest {

    @Test
    fun sample_catalog_fixture_can_be_synced_and_discovered_with_resolved_package_urls() = runBlocking {
        val fixtureFile = SampleAssetPaths.catalogFixture
        assertTrue("Missing fixture: ${fixtureFile.absolutePath}", fixtureFile.exists())
        val fixtureJson = fixtureFile.readText(Charsets.UTF_8)

        val store = InMemoryPluginCatalogSyncStore(
            source = PluginRepositorySource(
                sourceId = "sample",
                title = "Meme Manager Sample Repository",
                catalogUrl = "https://samples.astrbot.local/catalog/meme-manager.json",
                updatedAt = 1_800L,
            ),
        )
        val synchronizer = PluginCatalogSynchronizer(
            store = store,
            fetcher = FakeCatalogFetcher(
                responseByUrl = mapOf(
                    store.listRepositorySources().single().catalogUrl to fixtureJson,
                ),
            ),
            now = { 2_000L },
        )

        val result = synchronizer.sync("sample")

        assertEquals(PluginCatalogSyncStatus.SUCCESS, result.lastSyncStatus)
        val entries = store.listAllCatalogEntries()
        val record = entries.firstOrNull { it.entry.pluginId == SAMPLE_PLUGIN_ID }
        assertNotNull("Catalog should include $SAMPLE_PLUGIN_ID", record)
        val versions = record!!.entry.versions
        assertTrue(versions.any { it.version == "1.0.0" })
        assertTrue(versions.any { it.version == "1.1.0" })
        // Fixture uses relative packageUrl, normalizer should resolve against catalogUrl.
        assertTrue(
            versions.all { version ->
                version.packageUrl.startsWith("https://samples.astrbot.local/catalog/packages/")
            },
        )
    }

    @Test
    fun sample_catalog_protocol_gate_accepts_v2_versions_and_rejects_legacy_v1_versions() = runBlocking {
        val fixtureFile = SampleAssetPaths.catalogFixture
        assertTrue("Missing fixture: ${fixtureFile.absolutePath}", fixtureFile.exists())
        val fixtureJson = fixtureFile.readText(Charsets.UTF_8)

        val decoded = com.astrbot.android.data.plugin.catalog.PluginCatalogJson.decodeRepositorySource(fixtureJson)
        val sampleEntry = decoded.plugins.first { it.pluginId == SAMPLE_PLUGIN_ID }
        val v2Gate = sampleEntry.versions.associateBy({ it.version }) { version ->
            PluginRepository.evaluateCatalogVersion(
                version = version,
                hostVersion = "0.3.6",
                supportedProtocolVersion = 2,
            )
        }

        assertTrue(v2Gate.values.all { gate -> gate.compatibilityState.protocolSupported == true })
        assertTrue(v2Gate.values.all { gate -> gate.installable })

        val legacyVersion = sampleEntry.versions.first().copy(
            version = "0.9.0-legacy",
            protocolVersion = 1,
        )
        val legacyGate = PluginRepository.evaluateCatalogVersion(
            version = legacyVersion,
            hostVersion = "0.3.6",
            supportedProtocolVersion = 2,
        )

        assertEquals(false, legacyGate.compatibilityState.protocolSupported)
        assertFalse(legacyGate.installable)
        assertTrue(legacyGate.compatibilityState.notes.contains("Protocol version 1 is not supported."))
    }
}

private const val SAMPLE_PLUGIN_ID = "com.astrbot.samples.meme_manager"

private class FakeCatalogFetcher(
    private val responseByUrl: Map<String, String> = emptyMap(),
) : PluginCatalogFetcher {
    override suspend fun fetch(catalogUrl: String): String {
        return responseByUrl[catalogUrl] ?: error("No fake response for $catalogUrl")
    }
}

private class InMemoryPluginCatalogSyncStore(
    source: PluginRepositorySource,
) : PluginCatalogSyncStore {
    private val sources = linkedMapOf<String, PluginRepositorySource>(source.sourceId to source)

    override fun getRepositorySource(sourceId: String): PluginRepositorySource? = sources[sourceId]

    override fun getRepositorySourceSyncState(sourceId: String): PluginCatalogSyncState? {
        return sources[sourceId]?.let { src ->
            PluginCatalogSyncState(
                sourceId = src.sourceId,
                lastSyncAtEpochMillis = src.lastSyncAtEpochMillis,
                lastSyncStatus = src.lastSyncStatus,
                lastSyncErrorSummary = src.lastSyncErrorSummary,
            )
        }
    }

    override fun listRepositorySources(): List<PluginRepositorySource> = sources.values.toList()

    override fun listAllCatalogEntries(): List<PluginCatalogEntryRecord> {
        return sources.values.flatMap { src ->
            src.plugins.map { entry ->
                PluginCatalogEntryRecord(
                    sourceId = src.sourceId,
                    sourceTitle = src.title,
                    catalogUrl = src.catalogUrl,
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
            source.copy(plugins = if (source.plugins.isEmpty()) existing.plugins else source.plugins)
        }
    }
}

