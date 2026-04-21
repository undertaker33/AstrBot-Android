package com.astrbot.android.di.startup

import javax.inject.Inject

internal class AppStartupRunner @Inject constructor(
    private val bootstrapPrerequisitesStartupChain: BootstrapPrerequisitesStartupChain,
    private val repositoryInitializationStartupChain: RepositoryInitializationStartupChain,
    private val referenceGuardStartupChain: ReferenceGuardStartupChain,
    private val pluginRuntimeObservationStartupChain: PluginRuntimeObservationStartupChain,
    private val runtimeLaunchStartupChain: RuntimeLaunchStartupChain,
) {
    fun run() {
        bootstrapPrerequisitesStartupChain.run()
        repositoryInitializationStartupChain.run()
        referenceGuardStartupChain.run()
        pluginRuntimeObservationStartupChain.run()
        runtimeLaunchStartupChain.run()
    }
}
