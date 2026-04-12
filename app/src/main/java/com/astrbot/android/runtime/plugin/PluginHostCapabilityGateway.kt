package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginExecutionContext

interface PluginHostCapabilityGateway {
    fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult

    fun injectContext(context: PluginExecutionContext): PluginExecutionContext

    fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        personaSnapshot: PersonaToolEnablementSnapshot? = null,
    ): PluginV2ActiveRuntimeSnapshot

    fun executeHostBuiltinTool(
        args: PluginToolArgs,
    ): PluginToolResult?

    fun isToolAllowed(entry: PluginV2ToolRegistryEntry): Boolean
}

class DefaultPluginHostCapabilityGateway(
    private val hostActionExecutor: ExternalPluginHostActionExecutor = ExternalPluginHostActionExecutor(),
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
        )
    }

    override fun injectContext(context: PluginExecutionContext): PluginExecutionContext {
        return PluginExecutionHostApi.inject(
            context = context,
            hostSnapshot = PluginExecutionHostApi.resolve(context.pluginId),
        )
    }

    override fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        personaSnapshot: PersonaToolEnablementSnapshot?,
    ): PluginV2ActiveRuntimeSnapshot {
        return PluginExecutionHostApi.registerHostBuiltinTools(
            snapshot = snapshot,
            handlers = hostToolHandlers,
            personaSnapshot = personaSnapshot,
            capabilityGateway = PluginV2ToolCapabilityGateway(::isToolAllowed),
        )
    }

    override fun executeHostBuiltinTool(
        args: PluginToolArgs,
    ): PluginToolResult? {
        return PluginExecutionHostApi.executeHostBuiltinTool(
            args = args,
            handlers = hostToolHandlers,
        )
    }

    override fun isToolAllowed(entry: PluginV2ToolRegistryEntry): Boolean {
        return when (entry.sourceKind) {
            PluginToolSourceKind.PLUGIN_V2 -> true
            PluginToolSourceKind.HOST_BUILTIN -> entry.name in PluginExecutionHostApi
                .registeredHostToolDescriptors(hostToolHandlers)
                .map(PluginToolDescriptor::name)
                .toSet()
            else -> false
        }
    }
}
