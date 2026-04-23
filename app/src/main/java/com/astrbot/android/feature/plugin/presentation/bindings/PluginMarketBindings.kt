package com.astrbot.android.feature.plugin.presentation.bindings

import com.astrbot.android.feature.plugin.data.PluginCatalogVersionGateResult
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import kotlinx.coroutines.flow.StateFlow

interface PluginMarketBindings {
    val records: StateFlow<List<PluginInstallRecord>>
    val repositorySources: StateFlow<List<PluginRepositorySource>>
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>>

    suspend fun handleInstallIntent(
        intent: PluginInstallIntent,
        onDownloadProgress: (PluginDownloadProgress) -> Unit,
    ): PluginInstallIntentResult

    suspend fun installFromLocalPackageUri(uri: String): PluginInstallIntentResult

    suspend fun ensureOfficialMarketCatalogSubscribed(): PluginCatalogSyncState

    suspend fun refreshMarketCatalog(): List<PluginCatalogSyncState>

    fun getHostVersion(): String

    fun evaluateCatalogVersion(version: PluginCatalogVersion): PluginCatalogVersionGateResult

    fun getUpdateAvailability(pluginId: String): PluginUpdateAvailability?

    suspend fun upgradePlugin(update: PluginUpdateAvailability): PluginInstallRecord
}
