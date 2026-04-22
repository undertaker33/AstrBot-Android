package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginExecutionContext

interface PluginHostCapabilityGateway {
    fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult

    /** Resolve and inject host snapshot for [context.pluginId]. */
    fun injectContext(context: PluginExecutionContext): PluginExecutionContext

    /**
     * Inject a caller-supplied [hostSnapshot] instead of resolving it internally.
     * Useful when the caller has already built a custom snapshot (e.g. PluginViewModel).
     */
    fun injectContext(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext

    fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        personaSnapshot: PersonaToolEnablementSnapshot? = null,
        futureSourceDescriptors: Collection<PluginToolDescriptor> = emptyList(),
        activeFutureSourceKinds: Set<PluginToolSourceKind> = emptySet(),
    ): PluginV2ActiveRuntimeSnapshot

    fun executeHostBuiltinTool(
        args: PluginToolArgs,
    ): PluginToolResult?

    fun isToolAllowed(entry: PluginV2ToolRegistryEntry): Boolean
}

class DefaultPluginHostCapabilityGateway(
    private val resolver: PluginExecutionHostResolver,
    private val hostActionExecutor: ExternalPluginHostActionExecutor,
    private val hostActionHandlers: ExternalPluginHostActionHandlers = ExternalPluginHostActionHandlers(),
    private val hostToolHandlers: PluginExecutionHostToolHandlers = PluginExecutionHostToolHandlers(),
) : PluginHostCapabilityGateway {

    override fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult {
        return hostActionExecutor.execute(
            pluginId = pluginId,
            request = request,
            context = context,
            handlers = hostActionHandlers,
        )
    }

    override fun injectContext(context: PluginExecutionContext): PluginExecutionContext {
        return resolver.inject(
            context = context,
            hostSnapshot = resolver.resolve(context.pluginId),
        )
    }

    override fun injectContext(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext {
        return resolver.inject(context = context, hostSnapshot = hostSnapshot)
    }

    override fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        personaSnapshot: PersonaToolEnablementSnapshot?,
        futureSourceDescriptors: Collection<PluginToolDescriptor>,
        activeFutureSourceKinds: Set<PluginToolSourceKind>,
    ): PluginV2ActiveRuntimeSnapshot {
        return resolver.registerHostBuiltinTools(
            snapshot = snapshot,
            handlers = hostToolHandlers,
            personaSnapshot = personaSnapshot,
            capabilityGateway = PluginV2ToolCapabilityGateway(::isToolAllowed),
            futureSourceDescriptors = futureSourceDescriptors,
            activeFutureSourceKinds = activeFutureSourceKinds,
        )
    }

    override fun executeHostBuiltinTool(
        args: PluginToolArgs,
    ): PluginToolResult? {
        return resolver.executeHostBuiltinTool(args = args, handlers = hostToolHandlers)
    }

    override fun isToolAllowed(entry: PluginV2ToolRegistryEntry): Boolean {
        return when (entry.sourceKind) {
            PluginToolSourceKind.PLUGIN_V2 -> true
            PluginToolSourceKind.HOST_BUILTIN -> entry.name in resolver
                .registeredHostToolDescriptors(hostToolHandlers)
                .map(PluginToolDescriptor::name)
                .toSet()
            PluginToolSourceKind.MCP,
            PluginToolSourceKind.SKILL,
            PluginToolSourceKind.ACTIVE_CAPABILITY,
            PluginToolSourceKind.CONTEXT_STRATEGY,
            PluginToolSourceKind.WEB_SEARCH,
            -> true
        }
    }
}
