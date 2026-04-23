package com.astrbot.android.core.runtime.container

import android.content.Context

object NapCatContainerRuntime {
    fun startBridge(context: Context) {
        ContainerBridgeController.start(context)
    }

    fun stopBridge(context: Context) {
        ContainerBridgeController.stop(context)
    }
}
