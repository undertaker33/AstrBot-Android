package com.astrbot.android.core.runtime.container

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ContainerRuntimeEntryPoint {
    fun bridgeStatePort(): ContainerBridgeStatePort
    fun containerRuntimeInstaller(): ContainerRuntimeInstaller
}

internal fun Context.containerRuntimeEntryPoint(): ContainerRuntimeEntryPoint {
    return EntryPointAccessors.fromApplication(
        applicationContext,
        ContainerRuntimeEntryPoint::class.java,
    )
}
