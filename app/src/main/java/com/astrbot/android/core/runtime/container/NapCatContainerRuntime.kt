@file:Suppress("DEPRECATION")

package com.astrbot.android.core.runtime.container

import android.content.Context
import kotlinx.coroutines.CoroutineScope

object NapCatContainerRuntime {
    fun warmUpAsync(context: Context, scope: CoroutineScope) {
        context.containerRuntimeEntryPoint().containerRuntimeInstaller().warmUpAsync(scope)
    }

    suspend fun ensureInstalled(context: Context) {
        context.containerRuntimeEntryPoint().containerRuntimeInstaller().ensureInstalled()
    }

    fun startBridge(context: Context) {
        ContainerBridgeController.start(context)
    }

    fun stopBridge(context: Context) {
        ContainerBridgeController.stop(context)
    }
}
