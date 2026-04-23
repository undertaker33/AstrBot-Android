package com.astrbot.android.feature.plugin.domain.cleanup

import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.data.config.PluginConfigStorage
import com.astrbot.android.feature.plugin.data.state.PluginStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

interface PluginRuntimeArtifactCleaner {
    fun cleanup(pluginId: String)
}

@Singleton
class DefaultPluginRuntimeArtifactCleaner @Inject constructor(
    private val runtimeLoaderProvider: Provider<PluginV2RuntimeLoader>,
) : PluginRuntimeArtifactCleaner {
    override fun cleanup(pluginId: String) {
        runCatching {
            runBlocking {
                runtimeLoaderProvider.get().unload(pluginId)
            }
        }
    }
}

interface PluginDataCleanupService {
    fun cleanupForUninstall(
        record: PluginInstallRecord,
        policy: PluginUninstallPolicy,
    )
}

@Singleton
class DefaultPluginDataCleanupService @Inject constructor(
    private val storagePaths: PluginStoragePaths,
    private val configStorage: PluginConfigStorage,
    private val stateStore: PluginStateStore,
    private val runtimeArtifactCleaner: PluginRuntimeArtifactCleaner,
) : PluginDataCleanupService {
    override fun cleanupForUninstall(
        record: PluginInstallRecord,
        policy: PluginUninstallPolicy,
    ) {
        runtimeArtifactCleaner.cleanup(record.pluginId)
        deletePath(record.localPackagePath)
        deletePath(record.extractedDir)
        if (policy == PluginUninstallPolicy.REMOVE_DATA) {
            configStorage.deleteSnapshot(record.pluginId)
            stateStore.deleteByPluginId(record.pluginId)
            deletePath(storagePaths.privateDir(record.pluginId).absolutePath)
        }
    }

    private fun deletePath(path: String) {
        if (path.isBlank()) return
        kotlin.runCatching {
            java.io.File(path).takeIf(java.io.File::exists)?.deleteRecursively()
        }
    }
}
