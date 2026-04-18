package com.astrbot.android.feature.plugin.data

import com.astrbot.android.data.PluginRepository

class LegacyPluginCatalogStoreAdapter {
    fun getUpdateAvailability(pluginId: String, hostVersion: String) =
        PluginRepository.getUpdateAvailability(pluginId, hostVersion)
}
