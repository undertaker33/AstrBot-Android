package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.runtime.tool.ToolDescriptor
import com.astrbot.android.core.runtime.tool.ToolDescriptorVisibility
import com.astrbot.android.core.runtime.tool.ToolSourceKind
import com.astrbot.android.core.runtime.tool.ToolSourceProviderPort
import com.astrbot.android.core.runtime.tool.ToolSourceRequestContext
import com.astrbot.android.feature.plugin.runtime.JsonLikeMap
import com.astrbot.android.feature.plugin.runtime.PluginToolArgs
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified contract for future tool source providers (MCP / Skill / Active Capability / Context Strategy).
 *
 * Each provider produces [ToolSourceDescriptorBinding] entries that the gateway feeds into
 * the centralized tool registry compiler. The compiler handles uniqueness, schema validation,
 * and availability evaluation as before.
 */
interface FutureToolSourceProvider : ToolSourceProviderPort {
    val sourceKind: PluginToolSourceKind

    override val kind: ToolSourceKind
        get() = sourceKind.toContractKind()

    override suspend fun descriptors(context: ToolSourceRequestContext): List<ToolDescriptor> {
        val toolSourceContext = FutureToolSourceRegistry.contextForContractRequest(context)
        return listBindings(
            context = ToolSourceRegistryIngestContext(toolSourceContext = toolSourceContext),
        ).map(ToolSourceDescriptorBinding::toContractDescriptor)
    }

    suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding>

    suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability

    suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult
}

// ── Identity ──

data class ToolSourceIdentity(
    val sourceKind: PluginToolSourceKind,
    val ownerId: String,
    val sourceRef: String,
    val displayName: String,
    val versionTag: String? = null,
)

// ── Binding (provider → registry input) ──

data class ToolSourceDescriptorBinding(
    val identity: ToolSourceIdentity,
    val descriptor: PluginToolDescriptor,
    val sourceMetadata: JsonLikeMap = emptyMap(),
)

// ── Availability ──

data class ToolSourceAvailability(
    val providerReachable: Boolean,
    val permissionGranted: Boolean,
    val capabilityAllowed: Boolean,
    val detailCode: String? = null,
    val detailMessage: String? = null,
    val metadata: JsonLikeMap = emptyMap(),
)

// ── Invoke ──

data class ToolSourceInvokeRequest(
    val identity: ToolSourceIdentity,
    val args: PluginToolArgs,
    val timeoutMs: Long,
    val cancellationToken: String? = null,
    val configProfileId: String? = null,
    val toolSourceContext: ToolSourceContext? = null,
)

data class ToolSourceInvokeResult(
    val result: PluginToolResult,
    val diagnosticsMetadata: JsonLikeMap = emptyMap(),
)

// ── Context carriers ──

data class ToolSourceRegistryIngestContext(
    val toolSourceContext: ToolSourceContext,
) {
    val configProfileId: String get() = toolSourceContext.configProfileId
}

data class ToolSourceAvailabilityContext(
    val toolSourceContext: ToolSourceContext,
) {
    val configProfileId: String get() = toolSourceContext.configProfileId
}

internal fun ToolSourceDescriptorBinding.toContractDescriptor(): ToolDescriptor {
    return ToolDescriptor(
        id = descriptor.toolId,
        ownerId = identity.ownerId.ifBlank { descriptor.pluginId },
        name = descriptor.name,
        description = descriptor.description,
        inputSchemaJson = JSONObject(descriptor.inputSchema).toString(),
        source = descriptor.sourceKind.toContractKind(),
        visibility = descriptor.visibility.toContractVisibility(),
    )
}

internal fun ToolDescriptor.toPluginToolDescriptor(): PluginToolDescriptor {
    return PluginToolDescriptor(
        pluginId = ownerId,
        name = name,
        description = description,
        visibility = visibility.toPluginVisibility(),
        sourceKind = source.toPluginToolSourceKind(),
        inputSchema = inputSchemaJson.toJsonLikeMap(),
    )
}

internal fun PluginToolSourceKind.toContractKind(): ToolSourceKind {
    return when (this) {
        PluginToolSourceKind.PLUGIN_V2,
        PluginToolSourceKind.HOST_BUILTIN,
        -> ToolSourceKind.PLUGIN
        PluginToolSourceKind.MCP -> ToolSourceKind.MCP
        PluginToolSourceKind.SKILL -> ToolSourceKind.SKILL
        PluginToolSourceKind.ACTIVE_CAPABILITY -> ToolSourceKind.ACTIVE_CAPABILITY
        PluginToolSourceKind.CONTEXT_STRATEGY -> ToolSourceKind.CONTEXT_STRATEGY
        PluginToolSourceKind.WEB_SEARCH -> ToolSourceKind.WEB_SEARCH
    }
}

private fun ToolSourceKind.toPluginToolSourceKind(): PluginToolSourceKind {
    return when (this) {
        ToolSourceKind.PLUGIN -> PluginToolSourceKind.PLUGIN_V2
        ToolSourceKind.MCP -> PluginToolSourceKind.MCP
        ToolSourceKind.SKILL -> PluginToolSourceKind.SKILL
        ToolSourceKind.WEB_SEARCH -> PluginToolSourceKind.WEB_SEARCH
        ToolSourceKind.ACTIVE_CAPABILITY -> PluginToolSourceKind.ACTIVE_CAPABILITY
        ToolSourceKind.CONTEXT_STRATEGY -> PluginToolSourceKind.CONTEXT_STRATEGY
    }
}

private fun PluginToolVisibility.toContractVisibility(): ToolDescriptorVisibility {
    return when (this) {
        PluginToolVisibility.LLM_VISIBLE -> ToolDescriptorVisibility.LLM_VISIBLE
        PluginToolVisibility.HOST_INTERNAL -> ToolDescriptorVisibility.HOST_INTERNAL
    }
}

private fun ToolDescriptorVisibility.toPluginVisibility(): PluginToolVisibility {
    return when (this) {
        ToolDescriptorVisibility.LLM_VISIBLE -> PluginToolVisibility.LLM_VISIBLE
        ToolDescriptorVisibility.HOST_INTERNAL -> PluginToolVisibility.HOST_INTERNAL
    }
}

private fun String.toJsonLikeMap(): JsonLikeMap {
    if (isBlank()) return emptyMap()
    return JSONObject(this).toJsonLikeMap()
}

private fun JSONObject.toJsonLikeMap(): JsonLikeMap {
    return keys().asSequence().associateWith { key ->
        get(key).toJsonLikeValue()
    }
}

private fun JSONArray.toJsonLikeList(): List<Any?> {
    return (0 until length()).map { index -> get(index).toJsonLikeValue() }
}

private fun Any?.toJsonLikeValue(): Any? {
    return when (this) {
        null,
        JSONObject.NULL,
        -> null
        is JSONObject -> toJsonLikeMap()
        is JSONArray -> toJsonLikeList()
        else -> this
    }
}
