package com.astrbot.android.core.runtime.container

import android.content.Context
import kotlinx.coroutines.CoroutineScope

object NapCatContainerRuntime {
    fun warmUpAsync(context: Context, scope: CoroutineScope) {
        ContainerRuntimeInstaller.warmUpAsync(context, scope)
    }

    suspend fun ensureInstalled(context: Context) {
        ContainerRuntimeInstaller.ensureInstalled(context)
    }

    fun startBridge(context: Context) {
        ContainerBridgeController.start(context)
    }

    fun stopBridge(context: Context) {
        ContainerBridgeController.stop(context)
    }
}
