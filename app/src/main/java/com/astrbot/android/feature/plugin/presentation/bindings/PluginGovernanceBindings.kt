package com.astrbot.android.feature.plugin.presentation.bindings

import com.astrbot.android.feature.plugin.runtime.PluginGovernanceReadModel
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import kotlinx.coroutines.flow.Flow

interface PluginGovernanceBindings {
    val governanceReadModels: Flow<Map<String, PluginGovernanceReadModel>>
    val logBus: PluginRuntimeLogBus

    fun getPluginGovernance(pluginId: String): PluginGovernanceReadModel?

    fun getPluginGovernanceSilently(pluginId: String): PluginGovernanceReadModel?
}
