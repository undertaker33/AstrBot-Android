package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginLifecycleDiagnostic
import com.astrbot.android.model.plugin.PluginLifecycleDiagnosticsStore
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class PluginV2LifecycleManager(
    private val store: PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStoreProvider.store(),
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val astrbotReady = AtomicBoolean(false)
    private val readyPlatformInstanceKeys = ConcurrentHashMap.newKeySet<String>()
    private val activeSessionIdsByPluginId = ConcurrentHashMap<String, String>()

    suspend fun onAstrbotLoaded() {
        if (astrbotReady.compareAndSet(false, true).not()) {
            return
        }
        broadcastLifecycle(
            hook = PluginLifecycleHookSurface.OnAstrbotLoaded,
            pluginId = HOST_PLUGIN_ID,
            pluginVersion = "",
            extraMetadata = emptyMap(),
        )
    }

    suspend fun onPlatformLoaded(platformInstanceKey: String) {
        val normalizedPlatformInstanceKey = platformInstanceKey.trim()
        require(normalizedPlatformInstanceKey.isNotBlank()) {
            "platformInstanceKey must not be blank."
        }
        if (readyPlatformInstanceKeys.add(normalizedPlatformInstanceKey).not()) {
            return
        }
        broadcastLifecycle(
            hook = PluginLifecycleHookSurface.OnPlatformLoaded,
            pluginId = HOST_PLUGIN_ID,
            pluginVersion = "",
            extraMetadata = mapOf("platformInstanceKey" to normalizedPlatformInstanceKey),
        )
    }

    suspend fun onPluginLoaded(pluginId: String) {
        val snapshot = store.snapshot()
        val entry = snapshot.activeRuntimeEntriesByPluginId[pluginId] ?: return
        val session = snapshot.activeSessionsByPluginId[pluginId] ?: return
        if (session.state != PluginV2RuntimeSessionState.Active) {
            return
        }

        val sessionInstanceId = session.sessionInstanceId
        val previousSessionId = activeSessionIdsByPluginId.put(pluginId, sessionInstanceId)
        if (previousSessionId == sessionInstanceId) {
            return
        }

        broadcastLifecycle(
            hook = PluginLifecycleHookSurface.OnPluginLoaded,
            pluginId = pluginId,
            pluginVersion = entry.session.installRecord.installedVersion,
            eventPayload = lifecycleMetadata(pluginId, entry.session.installRecord.installedVersion),
            extraMetadata = mapOf(
                "sessionInstanceId" to sessionInstanceId,
            ),
        )
    }

    suspend fun onPluginUnloaded(
        pluginId: String,
        pluginVersion: String,
        sessionInstanceId: String? = null,
    ) {
        if (activeSessionIdsByPluginId.remove(pluginId) == null) {
            return
        }
        broadcastLifecycle(
            hook = PluginLifecycleHookSurface.OnPluginUnloaded,
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            eventPayload = lifecycleMetadata(pluginId, pluginVersion),
            extraMetadata = buildMap {
                sessionInstanceId?.let { put("sessionInstanceId", it) }
            },
        )
    }

    suspend fun emitPluginError(
        event: PluginErrorEventPayload,
        pluginName: String,
        handlerName: String,
        error: Throwable,
        tracebackText: String,
    ) {
        val pluginVersion = resolvePluginVersion(pluginName)
        val metadata = mapOf(
            "eventType" to event.javaClass.simpleName,
            "pluginName" to pluginName,
            "handlerName" to handlerName,
        )

        broadcastPluginError(
            event = event,
            pluginName = pluginName,
            pluginVersion = pluginVersion,
            handlerName = handlerName,
            error = error,
            tracebackText = tracebackText,
            metadata = metadata,
        )
    }

    private suspend fun broadcastLifecycle(
        hook: PluginLifecycleHookSurface,
        pluginId: String,
        pluginVersion: String,
        eventPayload: PluginErrorEventPayload? = null,
        extraMetadata: Map<String, String> = emptyMap(),
    ) {
        val candidates = resolveCandidates(hook)
        val logMetadata = buildLifecycleLogMetadata(
            hook = hook,
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            candidateCount = candidates.size,
            extraMetadata = extraMetadata,
        )
        publishLifecycleLog(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            code = "lifecycle_broadcast_started",
            message = "Lifecycle broadcast started for ${hook.wireValue}.",
            metadata = logMetadata,
        )

        for (candidate in candidates) {
            try {
                invokeCandidate(candidate, eventPayload)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                emitPluginError(
                    event = lifecycleMetadata(pluginId, pluginVersion),
                    pluginName = candidate.pluginId,
                    handlerName = candidate.descriptor.handlerId,
                    error = error,
                    tracebackText = error.stackTraceToString(),
                )
            }
        }

        publishLifecycleLog(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            code = "lifecycle_broadcast_completed",
            message = "Lifecycle broadcast completed for ${hook.wireValue}.",
            metadata = logMetadata,
        )
    }

    private suspend fun broadcastPluginError(
        event: PluginErrorEventPayload,
        pluginName: String,
        pluginVersion: String,
        handlerName: String,
        error: Throwable,
        tracebackText: String,
        metadata: Map<String, String>,
    ) {
        val candidates = resolveCandidates(PluginLifecycleHookSurface.OnPluginError)
        val logMetadata = buildLifecycleLogMetadata(
            hook = PluginLifecycleHookSurface.OnPluginError,
            pluginId = pluginName,
            pluginVersion = pluginVersion,
            candidateCount = candidates.size,
            extraMetadata = metadata + mapOf(
                "tracebackText" to tracebackText,
                "errorType" to error.javaClass.simpleName,
                "errorMessage" to (error.message ?: ""),
            ),
        )

        publishLifecycleLog(
            pluginId = pluginName,
            pluginVersion = pluginVersion,
            code = "plugin_error_hook_emitted",
            message = "on_plugin_error broadcast started.",
            metadata = logMetadata,
        )

        val errorArgs = PluginErrorHookArgs(
            event = event,
            plugin_name = pluginName,
            handler_name = handlerName,
            error = error,
            traceback_text = tracebackText,
        )

        for (candidate in candidates) {
            try {
                invokeCandidate(candidate, errorArgs)
            } catch (candidateError: Throwable) {
                candidateError.rethrowIfCancellation()
                recordPluginErrorHookFailure(
                    pluginId = candidate.pluginId,
                    pluginVersion = candidate.session.installRecord.installedVersion,
                    message = candidateError.message ?: candidateError.javaClass.simpleName,
                    tracebackText = candidateError.stackTraceToString(),
                    metadata = buildMap {
                        put("hook", PluginLifecycleHookSurface.OnPluginError.wireValue)
                        put("handlerName", candidate.descriptor.handlerId)
                        put("originPluginName", pluginName)
                        put("originHandlerName", handlerName)
                        put("originEventType", event.javaClass.simpleName)
                    },
                )
            }
        }

        publishLifecycleLog(
            pluginId = pluginName,
            pluginVersion = pluginVersion,
            code = "lifecycle_broadcast_completed",
            message = "on_plugin_error broadcast completed.",
            metadata = logMetadata,
        )
    }

    private suspend fun invokeCandidate(
        candidate: LifecycleCandidate,
        eventPayload: PluginErrorEventPayload? = null,
    ) {
        val handle = candidate.session.requireCallbackHandle(candidate.descriptor.callbackToken)
        candidate.session.runSerializedCallback {
            if (eventPayload != null && handle is PluginV2EventAwareCallbackHandle) {
                handle.handleEvent(eventPayload)
            } else {
                handle.invoke()
            }
        }
    }

    private fun resolveCandidates(hook: PluginLifecycleHookSurface): List<LifecycleCandidate> {
        val snapshot = store.snapshot()
        return snapshot.compiledRegistriesByPluginId.entries
            .mapNotNull { (pluginId, registry) ->
                val session = snapshot.activeSessionsByPluginId[pluginId] ?: return@mapNotNull null
                if (session.state != PluginV2RuntimeSessionState.Active) {
                    return@mapNotNull null
                }
                registry.handlerRegistry.lifecycleHandlers
                    .asSequence()
                    .filter { it.hook.equals(hook.wireValue, ignoreCase = true) }
                    .map { descriptor ->
                        LifecycleCandidate(
                            session = session,
                            descriptor = descriptor,
                        )
                    }
                    .toList()
            }
            .flatten()
            .sortedWith(
                compareByDescending<LifecycleCandidate> { it.descriptor.priority }
                    .thenBy { it.descriptor.handlerId },
            )
    }

    private fun publishLifecycleLog(
        pluginId: String,
        pluginVersion: String,
        code: String,
        message: String,
        metadata: Map<String, String>,
    ) {
        logBus.publishLifecycleRecord(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            occurredAtEpochMillis = clock(),
            level = if (code.endsWith("_failed")) PluginRuntimeLogLevel.Error else PluginRuntimeLogLevel.Info,
            code = code,
            message = message,
            metadata = metadata,
        )
    }

    private fun recordPluginErrorHookFailure(
        pluginId: String,
        pluginVersion: String,
        message: String,
        tracebackText: String,
        metadata: Map<String, String>,
    ) {
        val failureMessage = message.ifBlank { "on_plugin_error failed." }
        publishLifecycleLog(
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            code = "plugin_error_hook_failed",
            message = failureMessage,
            metadata = metadata + mapOf("tracebackText" to tracebackText),
        )
        PluginLifecycleDiagnosticsStore.record(
            PluginLifecycleDiagnostic(
                pluginId = pluginId,
                hook = PluginLifecycleHookSurface.OnPluginError.wireValue,
                code = "plugin_error_hook_failed",
                message = failureMessage,
                tracebackText = tracebackText,
                occurredAtEpochMillis = clock(),
            ),
        )
    }

    private fun buildLifecycleLogMetadata(
        hook: PluginLifecycleHookSurface,
        pluginId: String,
        pluginVersion: String,
        candidateCount: Int,
        extraMetadata: Map<String, String>,
    ): Map<String, String> {
        return linkedMapOf(
            "hook" to hook.wireValue,
            "traceId" to "lifecycle::${hook.wireValue}::$pluginId",
            "candidateCount" to candidateCount.toString(),
            "pluginName" to pluginId,
            "pluginVersion" to pluginVersion,
        ).also { metadata ->
            metadata.putAll(extraMetadata)
        }
    }

    private fun resolvePluginVersion(pluginName: String): String {
        return store.snapshot().activeRuntimeEntriesByPluginId[pluginName]
            ?.session
            ?.installRecord
            ?.installedVersion
            .orEmpty()
    }

    private fun lifecycleMetadata(
        pluginName: String,
        pluginVersion: String,
    ): PluginLifecycleMetadata {
        return PluginLifecycleMetadata(
            pluginName = pluginName,
            pluginVersion = pluginVersion,
        )
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private data class LifecycleCandidate(
        val session: PluginV2RuntimeSession,
        val descriptor: PluginV2CompiledLifecycleHandler,
    ) {
        val pluginId: String
            get() = session.pluginId
    }

    private companion object {
        private const val HOST_PLUGIN_ID = "__host__"
    }
}

object PluginV2LifecycleManagerProvider {
    @Volatile
    private var managerOverrideForTests: PluginV2LifecycleManager? = null

    private val sharedManager: PluginV2LifecycleManager by lazy {
        PluginV2LifecycleManager()
    }

    fun manager(): PluginV2LifecycleManager = managerOverrideForTests ?: sharedManager

    internal fun setManagerOverrideForTests(manager: PluginV2LifecycleManager?) {
        managerOverrideForTests = manager
    }
}
