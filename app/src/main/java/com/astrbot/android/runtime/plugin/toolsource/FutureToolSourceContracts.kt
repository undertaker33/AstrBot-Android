package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.runtime.plugin.JsonLikeMap
import com.astrbot.android.runtime.plugin.PluginToolArgs
import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolResult
import com.astrbot.android.runtime.plugin.PluginToolSourceKind

/**
 * Unified contract for future tool source providers (MCP / Skill / Active Capability / Context Strategy).
 *
 * Each provider produces [ToolSourceDescriptorBinding] entries that the gateway feeds into
 * the centralized tool registry compiler. The compiler handles uniqueness, schema validation,
 * and availability evaluation as before.
 */
interface FutureToolSourceProvider {
    val sourceKind: PluginToolSourceKind

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
)

data class ToolSourceInvokeResult(
    val result: PluginToolResult,
    val diagnosticsMetadata: JsonLikeMap = emptyMap(),
)

// ── Context carriers ──

data class ToolSourceRegistryIngestContext(
    val configProfileId: String,
)

data class ToolSourceAvailabilityContext(
    val configProfileId: String,
)
