package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.plugin.PluginExecutionContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seam interface that encapsulates all calls to [PluginExecutionHostApi].
 * Injected into the capability gateway so the gateway no longer
 * calls the static API object directly.
 */
interface PluginExecutionHostResolver {

    fun resolve(pluginId: String): PluginExecutionHostSnapshot

    fun inject(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext

    fun registeredHostToolDescriptors(
        handlers: PluginExecutionHostToolHandlers,
    ): List<PluginToolDescriptor>

    fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        handlers: PluginExecutionHostToolHandlers,
        personaSnapshot: PersonaToolEnablementSnapshot?,
        capabilityGateway: PluginV2ToolCapabilityGateway,
        futureSourceDescriptors: Collection<PluginToolDescriptor>,
        activeFutureSourceKinds: Set<PluginToolSourceKind>,
    ): PluginV2ActiveRuntimeSnapshot

    fun executeHostBuiltinTool(
        args: PluginToolArgs,
        handlers: PluginExecutionHostToolHandlers,
    ): PluginToolResult?
}

/**
 * Production implementation: delegates directly to the static [PluginExecutionHostApi].
 * Registered as a [Singleton] in [com.astrbot.android.di.hilt.PluginHostCapabilityModule].
 */
@Singleton
class DefaultPluginExecutionHostResolver @Inject constructor() : PluginExecutionHostResolver {

    override fun resolve(pluginId: String): PluginExecutionHostSnapshot =
        PluginExecutionHostApi.resolve(pluginId)

    override fun inject(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext =
        PluginExecutionHostApi.inject(context, hostSnapshot)

    override fun registeredHostToolDescriptors(
        handlers: PluginExecutionHostToolHandlers,
    ): List<PluginToolDescriptor> =
        PluginExecutionHostApi.registeredHostToolDescriptors(handlers)

    override fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        handlers: PluginExecutionHostToolHandlers,
        personaSnapshot: PersonaToolEnablementSnapshot?,
        capabilityGateway: PluginV2ToolCapabilityGateway,
        futureSourceDescriptors: Collection<PluginToolDescriptor>,
        activeFutureSourceKinds: Set<PluginToolSourceKind>,
    ): PluginV2ActiveRuntimeSnapshot =
        PluginExecutionHostApi.registerHostBuiltinTools(
            snapshot = snapshot,
            handlers = handlers,
            personaSnapshot = personaSnapshot,
            capabilityGateway = capabilityGateway,
            futureSourceDescriptors = futureSourceDescriptors,
            activeFutureSourceKinds = activeFutureSourceKinds,
        )

    override fun executeHostBuiltinTool(
        args: PluginToolArgs,
        handlers: PluginExecutionHostToolHandlers,
    ): PluginToolResult? =
        PluginExecutionHostApi.executeHostBuiltinTool(args, handlers)
}
