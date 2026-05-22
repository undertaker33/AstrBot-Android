package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginCompatibilityState
import com.elymbot.android.model.plugin.PluginManifest
import com.elymbot.android.model.plugin.PluginPackageContractSnapshot
import com.elymbot.android.model.plugin.PluginPermissionDeclaration
import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.elymbot.android.model.plugin.PluginSource
import com.elymbot.android.model.plugin.PluginSourceType

internal fun agentSession(pluginId: String): PluginV2RuntimeSession {
    val session = PluginV2RuntimeSession(
        installRecord = agentInstallRecord(pluginId),
        sessionInstanceId = "session-$pluginId",
    )
    session.transitionTo(PluginV2RuntimeSessionState.Loading)
    session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
    session.requireBootstrapRawRegistry()
    return session
}

internal fun agentInstallRecord(pluginId: String): PluginInstallRecord {
    val manifest = PluginManifest(
            pluginId = pluginId,
            version = "1.0.0",
            protocolVersion = 2,
            author = "ElymBot",
            title = pluginId,
            description = "Agent test plugin",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = PluginV2HostApiPermissions.AGENT_RUN,
                    title = "Agent run",
                    description = "Allows agent run",
                    riskLevel = PluginRiskLevel.MEDIUM,
                    required = true,
                ),
            ),
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Agent test",
            riskLevel = PluginRiskLevel.LOW,
        )
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifest,
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "/tmp/$pluginId.zip",
            importedAt = 100L,
        ),
        packageContractSnapshot = PluginPackageContractSnapshot(
            protocolVersion = 2,
            runtime = PluginRuntimeDeclarationSnapshot(
                kind = "js_quickjs",
                bootstrap = "runtime/index.js",
                apiVersion = 1,
            ),
        ),
        permissionSnapshot = manifest.permissions,
        compatibilityState = PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        ),
        enabled = true,
        installedAt = 100L,
        lastUpdatedAt = 100L,
        localPackagePath = "/tmp/$pluginId.zip",
        extractedDir = "/tmp/$pluginId",
    )
}

internal fun compiledAgent(
    key: String = "research-agent",
    tools: List<String> = emptyList(),
): PluginV2CompiledAgentHandler {
    return PluginV2CompiledAgentHandler(
        pluginId = "com.example.agent.run",
        registrationKind = "agent",
        registrationKey = key,
        normalizedRegistrationKey = "com.example.agent.run/agent/$key",
        handlerId = "hdl::com.example.agent.run::agent::$key",
        callbackToken = PluginV2CallbackToken("cb::agent::1"),
        priority = 0,
        filterAttachments = emptyList(),
        metadata = BootstrapRegistrationMetadata(),
        sourceOrder = 0,
        agentKey = key,
        systemPrompt = "Use tools carefully.",
        tools = tools,
        model = AgentModelSelection(providerId = "provider-a", modelId = "model-a-1"),
    )
}

internal fun hostContext(): PluginV2HostApiRequestContext {
    return PluginV2HostApiRequestContext(
        pluginId = "com.example.agent.run",
        pluginVersion = "1.0.0",
        requestId = "req-agent",
        conversationId = "conv-agent",
        manifestPermissionIds = setOf(PluginV2HostApiPermissions.AGENT_RUN),
        permissionSnapshot = listOf(
            PluginPermissionGrant(
                permissionId = PluginV2HostApiPermissions.AGENT_RUN,
                title = "Agent run",
                granted = true,
                required = true,
                riskLevel = PluginRiskLevel.MEDIUM,
            ),
        ),
        triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.AGENT_RUN),
    )
}

internal fun agentRunner(
    llmPort: PluginV2HostLlmPort,
    toolExecutor: PluginV2ToolExecutor = PluginV2ToolExecutor { args ->
        PluginToolResult(
            toolCallId = args.toolCallId,
            requestId = args.requestId,
            toolId = args.toolId,
            status = PluginToolResultStatus.ERROR,
            errorCode = "tool_executor_unavailable",
            text = "Tool executor is unavailable.",
        )
    },
): PluginV2AgentRunner {
    return PluginV2AgentRunner(
        llmPort = llmPort,
        toolExecutor = toolExecutor,
        logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
        clock = { 1L },
        toolCallIdFactory = { "call-agent" },
    )
}

internal fun snapshotWithTools(
    vararg entries: PluginV2ToolRegistryEntry,
    availability: Map<String, PluginV2ToolAvailabilitySnapshot> = entries.associate { entry ->
        entry.name to PluginV2ToolAvailabilitySnapshot(
            toolName = entry.name,
            toolId = entry.toolId,
            pluginId = entry.pluginId,
            sourceKind = entry.sourceKind,
            registryActive = true,
            personaEnabled = true,
            capabilityAllowed = true,
            sourceProviderAvailable = true,
            available = true,
        )
    },
): PluginV2ActiveRuntimeSnapshot {
    val ordered = entries.toList()
    return PluginV2ActiveRuntimeSnapshot(
        toolRegistrySnapshot = PluginV2ToolRegistrySnapshot(
            activeEntries = ordered,
            activeEntriesByName = ordered.associateBy { it.name },
            activeEntriesByToolId = ordered.associateBy { it.toolId },
        ),
        toolAvailabilityByName = availability,
    )
}

internal fun pluginTool(pluginId: String, name: String): PluginV2ToolRegistryEntry {
    return tool(pluginId, name, PluginToolSourceKind.PLUGIN_V2)
}

internal fun tool(
    pluginId: String,
    name: String,
    sourceKind: PluginToolSourceKind,
): PluginV2ToolRegistryEntry {
    return PluginV2ToolRegistryEntry(
        pluginId = pluginId,
        name = name,
        toolId = "$pluginId:$name",
        description = "$name tool",
        visibility = PluginToolVisibility.LLM_VISIBLE,
        sourceKind = sourceKind,
        inputSchema = linkedMapOf("type" to "object"),
        metadata = null,
        sourceOrder = 0,
    )
}
