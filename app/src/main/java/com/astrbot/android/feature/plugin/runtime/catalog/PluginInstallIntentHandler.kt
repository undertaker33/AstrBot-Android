package com.astrbot.android.feature.plugin.runtime.catalog

import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.feature.plugin.runtime.PluginInstaller

class PluginInstallIntentHandler(
    private val installer: PluginInstaller,
    private val repositorySubscriptionManager: PluginRepositorySubscriptionManager,
) {
    suspend fun handle(
        intent: PluginInstallIntent,
        onDownloadProgress: (PluginDownloadProgress) -> Unit = {},
    ): PluginInstallIntentResult {
        return when (intent) {
            is PluginInstallIntent.CatalogVersion -> installResult {
                installer.install(
                    intent = intent,
                    onProgress = onDownloadProgress,
                )
            }
            is PluginInstallIntent.DirectPackageUrl -> installResult {
                installer.install(
                    intent = intent,
                    onProgress = onDownloadProgress,
                )
            }
            is PluginInstallIntent.RepositoryUrl -> PluginInstallIntentResult.RepositorySynced(
                syncState = repositorySubscriptionManager.subscribeAndSync(intent.url).syncState,
            )
        }
    }

    private suspend fun installResult(install: suspend () -> PluginInstallRecord): PluginInstallIntentResult.Installed {
        return PluginInstallIntentResult.Installed(record = install())
    }
}
