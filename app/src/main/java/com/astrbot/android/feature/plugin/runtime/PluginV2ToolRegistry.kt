package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.PersonaToolEnablementSnapshot
import java.util.LinkedHashMap

data class PluginV2ToolRegistryEntry(
    val pluginId: String,
    val name: String,
    val toolId: String,
    val description: String,
    val visibility: PluginToolVisibility,
    val sourceKind: PluginToolSourceKind,
    val inputSchema: JsonLikeMap,
    val metadata: JsonLikeMap?,
    val sourceOrder: Int,
)

data class PluginV2ToolRegistrySnapshot(
    val activeEntries: List<PluginV2ToolRegistryEntry> = emptyList(),
    val activeEntriesByName: Map<String, PluginV2ToolRegistryEntry> = emptyMap(),
    val activeEntriesByToolId: Map<String, PluginV2ToolRegistryEntry> = emptyMap(),
)

data class PluginV2ToolRegistryCompileResult(
    val activeRegistry: PluginV2ToolRegistrySnapshot?,
    val diagnostics: List<PluginV2CompilerDiagnostic>,
)

data class PluginV2ToolRegistryRuntimeSnapshot(
    val activeRegistry: PluginV2ToolRegistrySnapshot?,
    val diagnostics: List<PluginV2CompilerDiagnostic>,
    val availabilityByName: Map<String, PluginV2ToolAvailabilitySnapshot> = emptyMap(),
)

enum class PluginV2ToolAvailabilityFailureReason(
    val code: String,
) {
    RegistryInactive("registry_inactive"),
    PersonaDisabled("persona_disabled"),
    CapabilityDenied("capability_denied"),
    SourceUnavailable("source_unavailable"),
}

data class PluginV2ToolAvailabilitySnapshot(
    val toolName: String,
    val toolId: String = "",
    val pluginId: String = "",
    val sourceKind: PluginToolSourceKind? = null,
    val registryActive: Boolean = false,
    val personaEnabled: Boolean = false,
    val capabilityAllowed: Boolean = false,
    val sourceProviderAvailable: Boolean = false,
    val available: Boolean = false,
    val firstFailureReason: PluginV2ToolAvailabilityFailureReason? = null,
)

fun interface PluginV2ToolCapabilityGateway {
    fun isAllowed(entry: PluginV2ToolRegistryEntry): Boolean
}

class PluginV2ToolRegistry(
    private val sourceGateway: PluginV2ToolSourceGateway = PluginV2ToolSourceGateway(),
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun compile(rawRegistries: Collection<PluginV2RawRegistry>): PluginV2ToolRegistryCompileResult {
        return compileCandidates(rawRegistries.toToolDescriptors())
    }

    fun compileRuntimeSnapshot(
        rawRegistries: Collection<PluginV2RawRegistry>,
        additionalToolDescriptors: Collection<PluginToolDescriptor> = emptyList(),
        personaSnapshot: PersonaToolEnablementSnapshot? = null,
        capabilityGateway: PluginV2ToolCapabilityGateway = PluginV2ToolCapabilityGateway { true },
    ): PluginV2ToolRegistryRuntimeSnapshot {
        val compileResult = compileCandidates(rawRegistries.toToolDescriptors() + additionalToolDescriptors.toList())
        val activeRegistry = compileResult.activeRegistry
        val availabilityByName = activeRegistry?.let { registrySnapshot ->
            val effectivePersona = personaSnapshot ?: registrySnapshot.defaultEnabledPersonaSnapshot()
            registrySnapshot.activeEntries.associate { entry ->
                entry.name to evaluateAvailability(
                    toolName = entry.name,
                    activeRegistry = registrySnapshot,
                    personaSnapshot = effectivePersona,
                    capabilityGateway = capabilityGateway,
                )
            }
        }.orEmpty()
        publishToolAvailabilitySnapshotBuilt(availabilityByName)

        return PluginV2ToolRegistryRuntimeSnapshot(
            activeRegistry = activeRegistry,
            diagnostics = compileResult.diagnostics,
            availabilityByName = availabilityByName,
        )
    }

    fun compileCandidates(toolDescriptors: Collection<PluginToolDescriptor>): PluginV2ToolRegistryCompileResult {
        val descriptorList = toolDescriptors.toList()
        val diagnostics = mutableListOf<PluginV2CompilerDiagnostic>()
        val activeEntriesByName = LinkedHashMap<String, PluginV2ToolRegistryEntry>()
        val activeEntriesInOrder = mutableListOf<PluginV2ToolRegistryEntry>()

        descriptorList.forEachIndexed { sourceOrder, descriptor ->
            val normalizedName = descriptor.name.trim()
            val activeEntry = PluginV2ToolRegistryEntry(
                pluginId = descriptor.pluginId.trim(),
                name = normalizedName,
                toolId = descriptor.toolId,
                description = descriptor.description,
                visibility = descriptor.visibility,
                sourceKind = descriptor.sourceKind,
                inputSchema = descriptor.inputSchema,
                metadata = descriptor.metadata,
                sourceOrder = sourceOrder,
            )
            val sourceResolution = sourceGateway.resolve(activeEntry)
            if (sourceResolution is PluginV2ToolSourceGatewayResult.ReservedSourceUnavailable) {
                diagnostics += PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    code = "tool_source_kind_reserved",
                    message = "Reserved source kind ${descriptor.sourceKind} cannot enter the active tool registry. Reserved for future source integration in Phase 6+.",
                    pluginId = descriptor.pluginId,
                    registrationKind = "tool",
                    registrationKey = normalizedName,
                )
                return@forEachIndexed
            }
            if (activeEntriesByName.containsKey(normalizedName)) {
                diagnostics += PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    code = "duplicate_public_tool_name",
                    message = "Duplicate public tool name detected: $normalizedName",
                    pluginId = descriptor.pluginId,
                    registrationKind = "tool",
                    registrationKey = normalizedName,
                )
                return@forEachIndexed
            }

            activeEntriesByName[normalizedName] = activeEntry
            activeEntriesInOrder += activeEntry
        }

        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            publishToolRegistryCompiled(
                activeToolCount = 0,
                diagnostics = diagnostics,
                sourceKinds = descriptorList.map(PluginToolDescriptor::sourceKind),
            )
            return PluginV2ToolRegistryCompileResult(
                activeRegistry = null,
                diagnostics = diagnostics.toList(),
            )
        }

        val activeEntriesByToolId = activeEntriesInOrder.associateBy { entry -> entry.toolId }
        publishToolRegistryCompiled(
            activeToolCount = activeEntriesInOrder.size,
            diagnostics = diagnostics,
            sourceKinds = activeEntriesInOrder.map(PluginV2ToolRegistryEntry::sourceKind),
        )
        return PluginV2ToolRegistryCompileResult(
            activeRegistry = PluginV2ToolRegistrySnapshot(
                activeEntries = activeEntriesInOrder.toList(),
                activeEntriesByName = LinkedHashMap(activeEntriesByName).toMap(),
                activeEntriesByToolId = LinkedHashMap(activeEntriesByToolId).toMap(),
            ),
            diagnostics = diagnostics.toList(),
        )
    }

    fun evaluateAvailability(
        toolName: String,
        activeRegistry: PluginV2ToolRegistrySnapshot?,
        personaSnapshot: PersonaToolEnablementSnapshot,
        capabilityGateway: PluginV2ToolCapabilityGateway = PluginV2ToolCapabilityGateway { true },
    ): PluginV2ToolAvailabilitySnapshot {
        val normalizedToolName = toolName.trim()
        val registryEntry = activeRegistry?.activeEntriesByName?.get(normalizedToolName)
            ?: return PluginV2ToolAvailabilitySnapshot(
                toolName = normalizedToolName,
                available = false,
                firstFailureReason = PluginV2ToolAvailabilityFailureReason.RegistryInactive,
            )

        val normalizedEnabledTools = personaSnapshot.enabledTools
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        val personaEnabled = personaSnapshot.enabled && normalizedEnabledTools.contains(normalizedToolName)
        if (!personaEnabled) {
            return PluginV2ToolAvailabilitySnapshot(
                toolName = normalizedToolName,
                toolId = registryEntry.toolId,
                pluginId = registryEntry.pluginId,
                sourceKind = registryEntry.sourceKind,
                registryActive = true,
                personaEnabled = false,
                available = false,
                firstFailureReason = PluginV2ToolAvailabilityFailureReason.PersonaDisabled,
            )
        }

        val capabilityAllowed = capabilityGateway.isAllowed(registryEntry)
        if (!capabilityAllowed) {
            return PluginV2ToolAvailabilitySnapshot(
                toolName = normalizedToolName,
                toolId = registryEntry.toolId,
                pluginId = registryEntry.pluginId,
                sourceKind = registryEntry.sourceKind,
                registryActive = true,
                personaEnabled = true,
                capabilityAllowed = false,
                available = false,
                firstFailureReason = PluginV2ToolAvailabilityFailureReason.CapabilityDenied,
            )
        }

        return when (sourceGateway.resolve(registryEntry)) {
            is PluginV2ToolSourceGatewayResult.ActiveEntry -> PluginV2ToolAvailabilitySnapshot(
                toolName = normalizedToolName,
                toolId = registryEntry.toolId,
                pluginId = registryEntry.pluginId,
                sourceKind = registryEntry.sourceKind,
                registryActive = true,
                personaEnabled = true,
                capabilityAllowed = true,
                sourceProviderAvailable = true,
                available = true,
            )

            PluginV2ToolSourceGatewayResult.SourceUnavailable,
            PluginV2ToolSourceGatewayResult.ReservedSourceUnavailable,
            -> PluginV2ToolAvailabilitySnapshot(
                toolName = normalizedToolName,
                toolId = registryEntry.toolId,
                pluginId = registryEntry.pluginId,
                sourceKind = registryEntry.sourceKind,
                registryActive = true,
                personaEnabled = true,
                capabilityAllowed = true,
                sourceProviderAvailable = false,
                available = false,
                firstFailureReason = PluginV2ToolAvailabilityFailureReason.SourceUnavailable,
            )
        }
    }

    private fun Collection<PluginV2RawRegistry>.toToolDescriptors(): List<PluginToolDescriptor> {
        return flatMap { rawRegistry ->
            rawRegistry.tools
                .sortedBy(ToolRawRegistration::sourceOrder)
                .map(ToolRawRegistration::descriptor)
        }
    }

    private fun PluginV2ToolRegistrySnapshot.defaultEnabledPersonaSnapshot(): PersonaToolEnablementSnapshot {
        return PersonaToolEnablementSnapshot(
            personaId = "__host_llm_default__",
            enabled = true,
            enabledTools = activeEntries.map(PluginV2ToolRegistryEntry::name).toSet(),
        )
    }

    private fun publishToolRegistryCompiled(
        activeToolCount: Int,
        diagnostics: List<PluginV2CompilerDiagnostic>,
        sourceKinds: Collection<PluginToolSourceKind>,
    ) {
        logBus.publishToolRegistryCompiled(
            occurredAtEpochMillis = clock(),
            activeToolCount = activeToolCount,
            warningCount = diagnostics.count { diagnostic -> diagnostic.severity == DiagnosticSeverity.Warning },
            errorCount = diagnostics.count { diagnostic -> diagnostic.severity == DiagnosticSeverity.Error },
            sourceKinds = sourceKinds,
        )
    }

    private fun publishToolAvailabilitySnapshotBuilt(
        availabilityByName: Map<String, PluginV2ToolAvailabilitySnapshot>,
    ) {
        logBus.publishToolAvailabilitySnapshotBuilt(
            occurredAtEpochMillis = clock(),
            availabilityByName = availabilityByName,
        )
    }
}
