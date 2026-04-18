package com.astrbot.android.feature.plugin.data.catalog

import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginRepositorySource

interface PluginCatalogSyncStore {
    fun getRepositorySource(sourceId: String): PluginRepositorySource?

    fun getRepositorySourceSyncState(sourceId: String): PluginCatalogSyncState?

    fun listRepositorySources(): List<PluginRepositorySource>

    fun listAllCatalogEntries(): List<PluginCatalogEntryRecord>

    fun listCatalogVersions(sourceId: String, pluginId: String): List<PluginCatalogVersion>

    fun replaceRepositoryCatalog(source: PluginRepositorySource)

    fun upsertRepositorySource(source: PluginRepositorySource)
}
