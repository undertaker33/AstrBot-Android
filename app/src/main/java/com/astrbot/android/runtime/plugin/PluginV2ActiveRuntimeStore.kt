package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PluginV2BootstrapSummary(
    val pluginId: String,
    val sessionInstanceId: String,
    val compiledAtEpochMillis: Long,
    val handlerCount: Int,
    val warningCount: Int,
    val errorCount: Int,
)

data class PluginV2ActiveRuntimeEntry(
    val session: PluginV2RuntimeSession,
    val compiledRegistry: PluginV2CompiledRegistry,
    val lastBootstrapSummary: PluginV2BootstrapSummary,
    val diagnostics: List<PluginV2CompilerDiagnostic>,
    val callbackTokens: List<PluginV2CallbackToken>,
) {
    val pluginId: String
        get() = session.pluginId
}

data class PluginV2ActiveRuntimeSnapshot(
    val activeRuntimeEntriesByPluginId: Map<String, PluginV2ActiveRuntimeEntry> = emptyMap(),
    val activeSessionsByPluginId: Map<String, PluginV2RuntimeSession> = emptyMap(),
    val compiledRegistriesByPluginId: Map<String, PluginV2CompiledRegistrySnapshot> = emptyMap(),
    val callbackTokenIndexByPluginId: Map<String, List<String>> = emptyMap(),
    val diagnosticsByPluginId: Map<String, List<PluginV2CompilerDiagnostic>> = emptyMap(),
    val lastBootstrapSummariesByPluginId: Map<String, PluginV2BootstrapSummary> = emptyMap(),
    val toolRegistrySnapshot: PluginV2ToolRegistrySnapshot? = null,
    val toolRegistryDiagnostics: List<PluginV2CompilerDiagnostic> = emptyList(),
    val toolAvailabilityByName: Map<String, PluginV2ToolAvailabilitySnapshot> = emptyMap(),
)

class PluginV2ActiveRuntimeStore(
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val writeMutex = Mutex()
    private val snapshotRef = AtomicReference(PluginV2ActiveRuntimeSnapshot().frozen())

    fun snapshot(): PluginV2ActiveRuntimeSnapshot {
        return snapshotRef.get()
    }

    suspend fun commitLoadedRuntime(entry: PluginV2ActiveRuntimeEntry): PluginV2ActiveRuntimeSnapshot {
        var committedEntry: PluginV2ActiveRuntimeEntry? = null
        val committedSnapshot = update { currentSnapshot ->
            canonicalizeEntry(
                entry = entry,
                compiledAtEpochMillis = clock(),
            ).also { canonicalEntry ->
                committedEntry = canonicalEntry
            }.let { canonicalEntry ->
                currentSnapshot.withLoadedRuntimeInternal(canonicalEntry)
            }
        }
        committedEntry?.let { canonicalEntry ->
            publishRuntimeStoreCommitted(canonicalEntry)
            publishDiagnosticFeedback(canonicalEntry)
        }
        return committedSnapshot
    }

    suspend fun unload(pluginId: String): PluginV2ActiveRuntimeSnapshot {
        return update { currentSnapshot ->
            currentSnapshot.withoutPluginInternal(pluginId)
        }
    }

    private suspend fun update(
        transform: (PluginV2ActiveRuntimeSnapshot) -> PluginV2ActiveRuntimeSnapshot,
    ): PluginV2ActiveRuntimeSnapshot {
        return writeMutex.withLock {
            val current = snapshotRef.get()
            val next = transform(current).frozen()
            snapshotRef.set(next)
            next
        }
    }

    private fun canonicalizeEntry(
        entry: PluginV2ActiveRuntimeEntry,
        compiledAtEpochMillis: Long,
    ): PluginV2ActiveRuntimeEntry {
        val pluginId = entry.pluginId
        val session = entry.session
        require(pluginId == session.pluginId) {
            "Entry pluginId must match the owning session pluginId."
        }

        val providedTokens = entry.callbackTokens.map(PluginV2CallbackToken::value)
        val sessionTokens = session.snapshotCallbackTokens().map(PluginV2CallbackToken::value)
        require(providedTokens == sessionTokens) {
            "Entry callbackTokens must match session callback token snapshot."
        }

        val frozenCompiledRegistry = (entry.compiledRegistry as? PluginV2CompiledRegistrySnapshot)
            ?.frozenCopy()
            ?: throw IllegalArgumentException(
                "Only PluginV2CompiledRegistrySnapshot payloads can be published to active runtime store.",
            )

        val normalizedDiagnostics = entry.diagnostics.map { diagnostic ->
            require(diagnostic.pluginId == pluginId) {
                "Diagnostic pluginId must match entry pluginId."
            }
            diagnostic.copy()
        }

        val canonicalSummary = PluginV2BootstrapSummary(
            pluginId = pluginId,
            sessionInstanceId = session.sessionInstanceId,
            compiledAtEpochMillis = compiledAtEpochMillis,
            handlerCount = frozenCompiledRegistry.handlerRegistry.totalHandlerCount,
            warningCount = normalizedDiagnostics.count { it.severity == DiagnosticSeverity.Warning },
            errorCount = normalizedDiagnostics.count { it.severity == DiagnosticSeverity.Error },
        )
        validateSummary(entry.lastBootstrapSummary, canonicalSummary)

        return PluginV2ActiveRuntimeEntry(
            session = session,
            compiledRegistry = frozenCompiledRegistry,
            lastBootstrapSummary = canonicalSummary,
            diagnostics = normalizedDiagnostics.toFrozenList(),
            callbackTokens = session.snapshotCallbackTokens().toFrozenList(),
        )
    }

    private fun validateSummary(
        providedSummary: PluginV2BootstrapSummary,
        canonicalSummary: PluginV2BootstrapSummary,
    ) {
        require(providedSummary.pluginId == canonicalSummary.pluginId) {
            "Entry summary pluginId does not match canonical pluginId."
        }
        require(providedSummary.sessionInstanceId == canonicalSummary.sessionInstanceId) {
            "Entry summary sessionInstanceId does not match canonical session."
        }
        require(providedSummary.handlerCount == canonicalSummary.handlerCount) {
            "Entry summary handlerCount does not match compiled registry."
        }
        require(providedSummary.warningCount == canonicalSummary.warningCount) {
            "Entry summary warningCount does not match diagnostics."
        }
        require(providedSummary.errorCount == canonicalSummary.errorCount) {
            "Entry summary errorCount does not match diagnostics."
        }
    }

    private fun publishRuntimeStoreCommitted(entry: PluginV2ActiveRuntimeEntry) {
        logBus.publishBootstrapRecord(
            pluginId = entry.pluginId,
            pluginVersion = entry.session.installRecord.installedVersion,
            occurredAtEpochMillis = clock(),
            level = PluginRuntimeLogLevel.Info,
            code = "runtime_store_committed",
            message = "Plugin v2 runtime snapshot committed.",
            metadata = linkedMapOf(
                "sessionInstanceId" to entry.session.sessionInstanceId,
                "handlerCount" to entry.lastBootstrapSummary.handlerCount.toString(),
                "warningCount" to entry.lastBootstrapSummary.warningCount.toString(),
                "errorCount" to entry.lastBootstrapSummary.errorCount.toString(),
            ),
        )
    }

    private fun publishDiagnosticFeedback(entry: PluginV2ActiveRuntimeEntry) {
        entry.diagnostics.forEach { diagnostic ->
            logBus.publishBootstrapRecord(
                pluginId = entry.pluginId,
                pluginVersion = entry.session.installRecord.installedVersion,
                occurredAtEpochMillis = clock(),
                level = when (diagnostic.severity) {
                    DiagnosticSeverity.Error -> PluginRuntimeLogLevel.Error
                    DiagnosticSeverity.Warning -> PluginRuntimeLogLevel.Warning
                },
                code = "runtime_diagnostic_feedback",
                message = diagnostic.message,
                metadata = linkedMapOf(
                    "diagnosticCode" to diagnostic.code,
                    "severity" to diagnostic.severity.name.lowercase(),
                ).also { metadata ->
                    diagnostic.registrationKind?.let { metadata["registrationKind"] = it }
                    diagnostic.registrationKey?.let { metadata["registrationKey"] = it }
                },
            )
        }
    }
}

private fun PluginV2ActiveRuntimeSnapshot.withLoadedRuntimeInternal(
    entry: PluginV2ActiveRuntimeEntry,
): PluginV2ActiveRuntimeSnapshot {
    val pluginId = entry.pluginId

    val nextEntries = LinkedHashMap(activeRuntimeEntriesByPluginId)
    nextEntries[pluginId] = entry

    val nextSessions = LinkedHashMap(activeSessionsByPluginId)
    nextSessions[pluginId] = entry.session

    val nextCompiledRegistries = LinkedHashMap(compiledRegistriesByPluginId)
    nextCompiledRegistries[pluginId] = entry.compiledRegistry as PluginV2CompiledRegistrySnapshot

    val nextCallbackTokenIndex = LinkedHashMap(callbackTokenIndexByPluginId)
    nextCallbackTokenIndex[pluginId] = entry.session.snapshotCallbackTokens()
        .map(PluginV2CallbackToken::value)
        .toFrozenList()

    val nextDiagnostics = LinkedHashMap(diagnosticsByPluginId)
    nextDiagnostics[pluginId] = entry.diagnostics.map { it.copy() }.toFrozenList()

    val nextSummaries = LinkedHashMap(lastBootstrapSummariesByPluginId)
    nextSummaries[pluginId] = entry.lastBootstrapSummary.copy()

    val toolState = compileCentralizedToolState(nextSessions)

    return copy(
        activeRuntimeEntriesByPluginId = nextEntries,
        activeSessionsByPluginId = nextSessions,
        compiledRegistriesByPluginId = nextCompiledRegistries,
        callbackTokenIndexByPluginId = nextCallbackTokenIndex,
        diagnosticsByPluginId = nextDiagnostics,
        lastBootstrapSummariesByPluginId = nextSummaries,
        toolRegistrySnapshot = toolState.activeRegistry,
        toolRegistryDiagnostics = toolState.diagnostics,
        toolAvailabilityByName = toolState.availabilityByName,
    )
}

private fun PluginV2ActiveRuntimeSnapshot.withoutPluginInternal(
    pluginId: String,
): PluginV2ActiveRuntimeSnapshot {
    if (pluginId.isBlank()) {
        return this
    }

    val nextEntries = LinkedHashMap(activeRuntimeEntriesByPluginId).also { entries ->
        entries.remove(pluginId)
    }
    val nextSessions = LinkedHashMap(activeSessionsByPluginId).also { sessions ->
        sessions.remove(pluginId)
    }
    val nextCompiledRegistries = LinkedHashMap(compiledRegistriesByPluginId).also { registries ->
        registries.remove(pluginId)
    }
    val nextCallbackTokenIndex = LinkedHashMap(callbackTokenIndexByPluginId).also { tokenIndex ->
        tokenIndex.remove(pluginId)
    }
    val nextDiagnostics = LinkedHashMap(diagnosticsByPluginId).also { diagnostics ->
        diagnostics.remove(pluginId)
    }
    val nextSummaries = LinkedHashMap(lastBootstrapSummariesByPluginId).also { summaries ->
        summaries.remove(pluginId)
    }

    val toolState = compileCentralizedToolState(nextSessions)

    return copy(
        activeRuntimeEntriesByPluginId = nextEntries,
        activeSessionsByPluginId = nextSessions,
        compiledRegistriesByPluginId = nextCompiledRegistries,
        callbackTokenIndexByPluginId = nextCallbackTokenIndex,
        diagnosticsByPluginId = nextDiagnostics,
        lastBootstrapSummariesByPluginId = nextSummaries,
        toolRegistrySnapshot = toolState.activeRegistry,
        toolRegistryDiagnostics = toolState.diagnostics,
        toolAvailabilityByName = toolState.availabilityByName,
    )
}

internal fun compileCentralizedToolState(
    sessionsByPluginId: Map<String, PluginV2RuntimeSession>,
    additionalToolDescriptors: Collection<PluginToolDescriptor> = emptyList(),
    personaSnapshot: PersonaToolEnablementSnapshot? = null,
    capabilityGateway: PluginV2ToolCapabilityGateway = PluginV2ToolCapabilityGateway { true },
): PluginV2ToolRegistryRuntimeSnapshot {
    val rawRegistries = sessionsByPluginId.values
        .filter { session -> session.pluginId != PluginExecutionHostApi.HostBuiltinPluginId }
        .mapNotNull(PluginV2RuntimeSession::rawRegistry)
    return PluginV2ToolRegistry().compileRuntimeSnapshot(
        rawRegistries = rawRegistries,
        additionalToolDescriptors = additionalToolDescriptors,
        personaSnapshot = personaSnapshot,
        capabilityGateway = capabilityGateway,
    )
}

private fun PluginV2ActiveRuntimeSnapshot.frozen(): PluginV2ActiveRuntimeSnapshot {
    return copy(
        activeRuntimeEntriesByPluginId = activeRuntimeEntriesByPluginId
            .mapValues { (_, entry) -> entry.frozen() }
            .toFrozenMap(),
        activeSessionsByPluginId = activeSessionsByPluginId.toFrozenMap(),
        compiledRegistriesByPluginId = compiledRegistriesByPluginId
            .mapValues { (_, registry) -> registry.frozenCopy() }
            .toFrozenMap(),
        callbackTokenIndexByPluginId = callbackTokenIndexByPluginId
            .mapValues { (_, tokens) -> tokens.toFrozenList() }
            .toFrozenMap(),
        diagnosticsByPluginId = diagnosticsByPluginId
            .mapValues { (_, diagnostics) -> diagnostics.map { it.copy() }.toFrozenList() }
            .toFrozenMap(),
        lastBootstrapSummariesByPluginId = lastBootstrapSummariesByPluginId
            .mapValues { (_, summary) -> summary.copy() }
            .toFrozenMap(),
        toolRegistrySnapshot = toolRegistrySnapshot?.frozenCopy(),
        toolRegistryDiagnostics = toolRegistryDiagnostics.map { it.copy() }.toFrozenList(),
        toolAvailabilityByName = toolAvailabilityByName
            .mapValues { (_, availability) -> availability.copy() }
            .toFrozenMap(),
    )
}

private fun PluginV2ActiveRuntimeEntry.frozen(): PluginV2ActiveRuntimeEntry {
    return copy(
        compiledRegistry = (compiledRegistry as PluginV2CompiledRegistrySnapshot).frozenCopy(),
        diagnostics = diagnostics.map { it.copy() }.toFrozenList(),
        callbackTokens = callbackTokens.map { PluginV2CallbackToken(it.value) }.toFrozenList(),
        lastBootstrapSummary = lastBootstrapSummary.copy(),
    )
}

private fun <T> List<T>.toFrozenList(): List<T> {
    return Collections.unmodifiableList(ArrayList(this))
}

private fun <K, V> Map<K, V>.toFrozenMap(): Map<K, V> {
    return Collections.unmodifiableMap(LinkedHashMap(this))
}

private fun PluginV2ToolRegistrySnapshot.frozenCopy(): PluginV2ToolRegistrySnapshot {
    val entries = activeEntries.map { entry -> entry.frozen() }
    return PluginV2ToolRegistrySnapshot(
        activeEntries = entries.toFrozenList(),
        activeEntriesByName = entries.associateBy(PluginV2ToolRegistryEntry::name).toFrozenMap(),
        activeEntriesByToolId = entries.associateBy(PluginV2ToolRegistryEntry::toolId).toFrozenMap(),
    )
}

private fun PluginV2ToolRegistryEntry.frozen(): PluginV2ToolRegistryEntry {
    return copy(
        inputSchema = inputSchema.toFrozenMap(),
        metadata = metadata?.toFrozenMap(),
    )
}

object PluginV2ActiveRuntimeStoreProvider {
    @Volatile
    private var storeOverrideForTests: PluginV2ActiveRuntimeStore? = null

    private val sharedStore: PluginV2ActiveRuntimeStore by lazy {
        PluginV2ActiveRuntimeStore()
    }

    fun store(): PluginV2ActiveRuntimeStore = storeOverrideForTests ?: sharedStore

    internal fun setStoreOverrideForTests(store: PluginV2ActiveRuntimeStore?) {
        storeOverrideForTests = store
    }
}
