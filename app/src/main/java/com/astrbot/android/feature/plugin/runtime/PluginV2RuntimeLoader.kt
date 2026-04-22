@file:Suppress("DEPRECATION")

package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.data.PluginRepositoryStatePort
import com.astrbot.android.feature.plugin.data.EmptyPluginRepositoryStatePort
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PluginV2RuntimeLoadStatus {
    Loaded,
    Skipped,
    Failed,
}

data class PluginV2RuntimeLoadResult(
    val pluginId: String,
    val status: PluginV2RuntimeLoadStatus,
    val sessionInstanceId: String? = null,
    val previousSessionInstanceId: String? = null,
    val diagnostics: List<PluginV2CompilerDiagnostic> = emptyList(),
    val reason: String = "",
)

data class PluginV2RuntimeUnloadResult(
    val pluginId: String,
    val removed: Boolean,
    val sessionInstanceId: String? = null,
)

data class PluginV2RuntimeSyncResult(
    val loads: List<PluginV2RuntimeLoadResult>,
    val unloads: List<PluginV2RuntimeUnloadResult>,
)

object PluginV2RuntimeLoaderProvider {
    @Volatile
    private var loaderOverrideForTests: PluginV2RuntimeLoader? = null

    @Volatile
    private var installedLoader: PluginV2RuntimeLoader? = null

    private val sharedLoader: PluginV2RuntimeLoader by lazy {
        PluginV2RuntimeLoader(
            logBus = PluginRuntimeLogBusProvider.bus(),
            store = PluginV2ActiveRuntimeStoreProvider.store(),
            lifecycleManager = PluginV2LifecycleManagerProvider.manager(),
        )
    }

    fun loader(): PluginV2RuntimeLoader = loaderOverrideForTests ?: installedLoader ?: sharedLoader

    internal fun installFromHilt(loader: PluginV2RuntimeLoader) {
        installedLoader = loader
    }

    internal fun setLoaderOverrideForTests(loader: PluginV2RuntimeLoader?) {
        loaderOverrideForTests = loader
        if (loader == null) {
            installedLoader = null
        }
    }
}

class PluginV2RuntimeLoader(
    private val sessionFactory: PluginV2RuntimeSessionFactory = PluginV2RuntimeSessionFactory(),
    private val compiler: PluginV2RegistryCompiler = PluginV2RegistryCompiler(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
    private val store: PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStore(
        logBus = logBus,
        clock = clock,
    ),
    private val lifecycleManager: PluginV2LifecycleManager = PluginV2LifecycleManager(
        clock = clock,
        logBus = logBus,
        store = store,
    ),
    private val repositoryStatePort: PluginRepositoryStatePort = EmptyPluginRepositoryStatePort,
) {
    private val pluginLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun sync(records: List<PluginInstallRecord> = repositoryStatePort.records.value): PluginV2RuntimeSyncResult {
        val eligibleRecords = records.filter(::isEligibleForLoad)
        val activeSnapshot = store.snapshot()
        val eligiblePluginIds = eligibleRecords.mapTo(linkedSetOf()) { record -> record.pluginId }
        val unloads = buildList {
            activeSnapshot.activeRuntimeEntriesByPluginId.keys
                .filterNot(eligiblePluginIds::contains)
                .sorted()
                .forEach { pluginId ->
                    add(unload(pluginId))
                }
        }
        val loads = eligibleRecords.map { record ->
            val activeEntry = activeSnapshot.activeRuntimeEntriesByPluginId[record.pluginId]
            when {
                activeEntry == null -> load(record)
                activeEntry.session.installRecord == record -> PluginV2RuntimeLoadResult(
                    pluginId = record.pluginId,
                    status = PluginV2RuntimeLoadStatus.Skipped,
                    sessionInstanceId = activeEntry.session.sessionInstanceId,
                    reason = "already_loaded",
                )

                else -> reload(record)
            }
        }
        return PluginV2RuntimeSyncResult(
            loads = loads,
            unloads = unloads,
        )
    }

    suspend fun load(record: PluginInstallRecord): PluginV2RuntimeLoadResult {
        return withPluginLock(record.pluginId) {
            loadInternal(
                record = record,
                operation = "load",
                replaceExisting = false,
            )
        }
    }

    suspend fun reload(pluginId: String): PluginV2RuntimeLoadResult {
        return withPluginLock(pluginId) {
            val record = resolveReloadRecord(pluginId)
                ?: return@withPluginLock PluginV2RuntimeLoadResult(
                    pluginId = pluginId,
                    status = PluginV2RuntimeLoadStatus.Skipped,
                    reason = "missing_install_record",
                )
            loadInternal(
                record = record,
                operation = "reload",
                replaceExisting = true,
            )
        }
    }

    suspend fun reload(record: PluginInstallRecord): PluginV2RuntimeLoadResult {
        return withPluginLock(record.pluginId) {
            loadInternal(
                record = record,
                operation = "reload",
                replaceExisting = true,
            )
        }
    }

    suspend fun unload(pluginId: String): PluginV2RuntimeUnloadResult {
        return withPluginLock(pluginId) {
            val currentEntry = store.snapshot().activeRuntimeEntriesByPluginId[pluginId]
            if (currentEntry == null) {
                return@withPluginLock PluginV2RuntimeUnloadResult(
                    pluginId = pluginId,
                    removed = false,
                )
            }
            store.unload(pluginId)
            currentEntry.session.dispose()
            lifecycleManager.onPluginUnloaded(
                pluginId = pluginId,
                pluginVersion = currentEntry.session.installRecord.installedVersion,
                sessionInstanceId = currentEntry.session.sessionInstanceId,
            )
            publishLifecycleLog(
                pluginId = pluginId,
                pluginVersion = currentEntry.session.installRecord.installedVersion,
                code = "runtime_unloaded",
                message = "Plugin v2 runtime unloaded.",
                metadata = mapOf(
                    "sessionInstanceId" to currentEntry.session.sessionInstanceId,
                ),
            )
            PluginV2RuntimeUnloadResult(
                pluginId = pluginId,
                removed = true,
                sessionInstanceId = currentEntry.session.sessionInstanceId,
            )
        }
    }

    private suspend fun loadInternal(
        record: PluginInstallRecord,
        operation: String,
        replaceExisting: Boolean,
    ): PluginV2RuntimeLoadResult {
        val pluginId = record.pluginId
        val currentEntry = store.snapshot().activeRuntimeEntriesByPluginId[pluginId]
        if (currentEntry != null && !replaceExisting) {
            return PluginV2RuntimeLoadResult(
                pluginId = pluginId,
                status = PluginV2RuntimeLoadStatus.Skipped,
                sessionInstanceId = currentEntry.session.sessionInstanceId,
                reason = "already_loaded",
            )
        }
        if (!isEligibleForLoad(record)) {
            return PluginV2RuntimeLoadResult(
                pluginId = pluginId,
                status = PluginV2RuntimeLoadStatus.Skipped,
                sessionInstanceId = currentEntry?.session?.sessionInstanceId,
                reason = describeSkipReason(record),
            )
        }

        publishLifecycleLog(
            pluginId = pluginId,
            pluginVersion = record.installedVersion,
            code = lifecycleCode(operation, "started"),
            message = "Plugin v2 runtime $operation started.",
            metadata = buildMap {
                put("enabled", record.enabled.toString())
                put("compatibilityStatus", record.compatibilityState.status.name)
                put("protocolVersion", record.packageContractSnapshot?.protocolVersion?.toString().orEmpty())
                put("sessionInstanceId", currentEntry?.session?.sessionInstanceId.orEmpty())
            },
        )

        val handle = try {
            sessionFactory.createSession(record)
        } catch (error: Exception) {
            if (replaceExisting) {
                deactivatePreviousRuntime(currentEntry)
            }
            publishLifecycleFailure(
                pluginId = pluginId,
                pluginVersion = record.installedVersion,
                operation = operation,
                reason = error.message ?: error.javaClass.simpleName,
            )
            return PluginV2RuntimeLoadResult(
                pluginId = pluginId,
                status = PluginV2RuntimeLoadStatus.Failed,
                previousSessionInstanceId = currentEntry?.session?.sessionInstanceId,
                reason = error.message ?: "Failed to create plugin v2 session.",
            )
        }

        val previousSession = currentEntry?.session
        var loadDiagnostics: List<PluginV2CompilerDiagnostic> = emptyList()
        return try {
            handle.installBootstrapGlobal(
                BOOTSTRAP_HOST_API_GLOBAL_NAME,
                PluginV2BootstrapHostApi(
                    session = handle.session,
                    logBus = logBus,
                    clock = clock,
                ),
            )
            handle.executeBootstrap()

            val rawRegistry = handle.session.rawRegistry ?: PluginV2RawRegistry(handle.session.pluginId)
                .also(handle.session::attachRawRegistry)
            val compileResult = compiler.compile(rawRegistry)
            val compiledRegistry = compileResult.compiledRegistry
                ?: throw IllegalStateException(
                    compileResult.diagnostics.firstOrNull()?.message
                        ?: "Plugin v2 registry compilation failed.",
                )
            val activationDiagnostics = validateActivation(
                pluginId = pluginId,
                compiledRegistry = compiledRegistry,
            )
            loadDiagnostics = compileResult.diagnostics + activationDiagnostics
            if (activationDiagnostics.any { diagnostic -> diagnostic.severity == DiagnosticSeverity.Error }) {
                publishActivationDiagnostics(
                    pluginId = pluginId,
                    pluginVersion = record.installedVersion,
                    diagnostics = activationDiagnostics,
                )
                throw IllegalStateException(
                    activationDiagnostics.firstOrNull { diagnostic -> diagnostic.severity == DiagnosticSeverity.Error }
                        ?.message
                        ?: "Plugin v2 runtime activation validation failed.",
                )
            }
            handle.session.attachCompiledRegistry(compiledRegistry)
            handle.transferBootstrapSessionOwnershipToRuntime()
            handle.session.transitionTo(PluginV2RuntimeSessionState.Active)

            val canonicalDiagnostics = loadDiagnostics
            val entry = PluginV2ActiveRuntimeEntry(
                session = handle.session,
                compiledRegistry = compiledRegistry,
                lastBootstrapSummary = PluginV2BootstrapSummary(
                    pluginId = pluginId,
                    sessionInstanceId = handle.session.sessionInstanceId,
                    compiledAtEpochMillis = clock(),
                    handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                    warningCount = canonicalDiagnostics.count { diagnostic -> diagnostic.severity == DiagnosticSeverity.Warning },
                    errorCount = canonicalDiagnostics.count { diagnostic -> diagnostic.severity == DiagnosticSeverity.Error },
                ),
                diagnostics = canonicalDiagnostics,
                callbackTokens = handle.session.snapshotCallbackTokens(),
            )
            store.commitLoadedRuntime(entry)
            if (replaceExisting) {
                previousSession?.dispose()
            }
            lifecycleManager.onPluginLoaded(pluginId)
            publishLifecycleLog(
                pluginId = pluginId,
                pluginVersion = record.installedVersion,
                code = lifecycleCode(operation, "succeeded"),
                message = "Plugin v2 runtime $operation succeeded.",
                metadata = buildMap {
                    put("sessionInstanceId", handle.session.sessionInstanceId)
                    put("handlerCount", compiledRegistry.handlerRegistry.totalHandlerCount.toString())
                    put("diagnosticCount", canonicalDiagnostics.size.toString())
                    previousSession?.let { put("previousSessionInstanceId", it.sessionInstanceId) }
                },
            )
            PluginV2RuntimeLoadResult(
                pluginId = pluginId,
                status = PluginV2RuntimeLoadStatus.Loaded,
                sessionInstanceId = handle.session.sessionInstanceId,
                previousSessionInstanceId = previousSession?.sessionInstanceId,
                diagnostics = canonicalDiagnostics,
            )
        } catch (error: Exception) {
            if (replaceExisting) {
                deactivatePreviousRuntime(currentEntry)
            }
            publishLifecycleFailure(
                pluginId = pluginId,
                pluginVersion = record.installedVersion,
                operation = operation,
                reason = error.message ?: error.javaClass.simpleName,
                previousSessionInstanceId = currentEntry?.session?.sessionInstanceId,
            )
            handle.dispose()
            PluginV2RuntimeLoadResult(
                pluginId = pluginId,
                status = PluginV2RuntimeLoadStatus.Failed,
                previousSessionInstanceId = previousSession?.sessionInstanceId,
                diagnostics = loadDiagnostics,
                reason = error.message ?: "Plugin v2 runtime $operation failed.",
            )
        }
    }

    private suspend fun deactivatePreviousRuntime(
        currentEntry: PluginV2ActiveRuntimeEntry?,
    ) {
        if (currentEntry == null) {
            return
        }
        store.unload(currentEntry.pluginId)
        currentEntry.session.dispose()
        lifecycleManager.onPluginUnloaded(
            pluginId = currentEntry.pluginId,
            pluginVersion = currentEntry.session.installRecord.installedVersion,
            sessionInstanceId = currentEntry.session.sessionInstanceId,
        )
        publishLifecycleLog(
            pluginId = currentEntry.pluginId,
            pluginVersion = currentEntry.session.installRecord.installedVersion,
            code = "runtime_unloaded",
            message = "Plugin v2 runtime unloaded after failed reload.",
            metadata = mapOf(
                "sessionInstanceId" to currentEntry.session.sessionInstanceId,
                "reason" to "reload_failed",
            ),
        )
    }

    private fun isEligibleForLoad(record: PluginInstallRecord): Boolean {
        return record.packageContractSnapshot?.protocolVersion == 2 &&
            record.enabled &&
            record.compatibilityState.status == PluginCompatibilityStatus.COMPATIBLE
    }

    private fun describeSkipReason(record: PluginInstallRecord): String {
        return when {
            record.packageContractSnapshot?.protocolVersion != 2 -> "not_v2"
            !record.enabled -> "disabled"
            record.compatibilityState.status != PluginCompatibilityStatus.COMPATIBLE -> "incompatible"
            else -> "unsupported"
        }
    }

    private suspend fun <T> withPluginLock(
        pluginId: String,
        block: suspend () -> T,
    ): T {
        val mutex = pluginLocks.getOrPut(pluginId) { Mutex() }
        return mutex.withLock { block() }
    }

    private fun resolveReloadRecord(pluginId: String): PluginInstallRecord? {
        store.snapshot().activeRuntimeEntriesByPluginId[pluginId]?.session?.installRecord?.let { return it }
        return runCatching {
            repositoryStatePort.findByPluginId(pluginId)
        }.getOrNull()
    }

    private fun publishLifecycleFailure(
        pluginId: String,
        pluginVersion: String,
        operation: String,
        reason: String,
        previousSessionInstanceId: String? = null,
    ) {
        publishLifecycleLog(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            code = lifecycleCode(operation, "failed"),
            message = "Plugin v2 runtime $operation failed: $reason",
            metadata = buildMap {
                put("reason", reason)
                previousSessionInstanceId?.let { put("previousSessionInstanceId", it) }
            },
        )
    }

    private fun publishLifecycleLog(
        pluginId: String,
        pluginVersion: String,
        code: String,
        message: String,
        metadata: Map<String, String>,
    ) {
        logBus.publishBootstrapRecord(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            occurredAtEpochMillis = clock(),
            level = if (code.endsWith("_failed")) PluginRuntimeLogLevel.Error else PluginRuntimeLogLevel.Info,
            code = code,
            message = message,
            metadata = metadata,
        )
    }

    private fun lifecycleCode(
        operation: String,
        suffix: String,
    ): String {
        return when (operation) {
            "load" -> "runtime_load_$suffix"
            "reload" -> "runtime_reload_$suffix"
            else -> "runtime_${operation}_$suffix"
        }
    }

    private fun validateActivation(
        pluginId: String,
        compiledRegistry: PluginV2CompiledRegistrySnapshot?,
    ): List<PluginV2CompilerDiagnostic> {
        val registry = compiledRegistry ?: return emptyList()
        return mergeCommandRegistries(
            store.snapshot().compiledRegistriesByPluginId
                .filterKeys { activePluginId -> activePluginId != pluginId }
                .values
                .map(PluginV2CompiledRegistrySnapshot::handlerRegistry) +
                registry.handlerRegistry,
        ).diagnostics
    }

    private fun publishActivationDiagnostics(
        pluginId: String,
        pluginVersion: String,
        diagnostics: List<PluginV2CompilerDiagnostic>,
    ) {
        diagnostics.forEach { diagnostic ->
            logBus.publishBootstrapRecord(
                pluginId = pluginId,
                pluginVersion = pluginVersion,
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

    private companion object {
        private const val BOOTSTRAP_HOST_API_GLOBAL_NAME = "__astrbotBootstrapHostApi"
    }
}

