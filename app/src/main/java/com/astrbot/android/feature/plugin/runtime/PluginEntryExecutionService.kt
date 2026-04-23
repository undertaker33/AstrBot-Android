package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import javax.inject.Inject

open class PluginEntryExecutionService @Inject constructor(
    private val engine: PluginExecutionEngine,
    private val pluginCatalog: ExternalPluginRuntimeCatalog,
) {
    open fun execute(
        record: PluginInstallRecord,
        context: PluginExecutionContext,
    ): PluginExecutionResult? {
        val runtime = pluginCatalog.plugins()
            .firstOrNull { plugin ->
                plugin.pluginId == record.pluginId &&
                    PluginTriggerSource.OnPluginEntryClick in plugin.supportedTriggers
            }
            ?: return null
        return engine.execute(
            plugin = runtime,
            context = context,
        ).result
    }
}
