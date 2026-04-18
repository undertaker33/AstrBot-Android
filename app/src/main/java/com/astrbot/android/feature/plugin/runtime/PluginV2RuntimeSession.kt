package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import java.util.LinkedHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val MAX_CALLBACKS_PER_PLUGIN: Int = 1_024

enum class PluginV2RuntimeSessionState {
    Unloaded,
    Loading,
    BootstrapRunning,
    Active,
    BootstrapFailed,
    Disposed,
}

data class PluginV2CallbackToken(
    val value: String,
) {
    override fun toString(): String = value
}

interface PluginV2CompiledRegistry

class PluginV2RuntimeSession(
    val installRecord: PluginInstallRecord,
    val sessionInstanceId: String,
) {
    val packageContractSnapshot: PluginPackageContractSnapshot

    private val callbackHandles = LinkedHashMap<PluginV2CallbackToken, PluginV2CallbackHandle>()
    private val callbackExecutionMutex = Mutex()

    val pluginId: String
        get() = installRecord.pluginId

    var state: PluginV2RuntimeSessionState = PluginV2RuntimeSessionState.Unloaded
        private set

    var rawRegistry: PluginV2RawRegistry? = null
        private set

    var compiledRegistry: PluginV2CompiledRegistry? = null
        private set

    private var callbackRuntimeSession: ExternalPluginBootstrapSession? = null

    init {
        require(sessionInstanceId.isNotBlank()) { "sessionInstanceId must not be blank." }
        packageContractSnapshot = requireNotNull(installRecord.packageContractSnapshot) {
            "Plugin v2 install record is missing packageContractSnapshot: ${installRecord.pluginId}"
        }
        require(packageContractSnapshot.protocolVersion == 2) {
            "Plugin v2 session requires protocolVersion=2: ${installRecord.pluginId}"
        }
    }

    fun transitionTo(nextState: PluginV2RuntimeSessionState) {
        check(isValidTransition(from = state, to = nextState)) {
            "Invalid runtime session transition: $state -> $nextState"
        }
        state = nextState
    }

    internal fun attachRawRegistry(rawRegistry: PluginV2RawRegistry) {
        check(state == PluginV2RuntimeSessionState.BootstrapRunning) {
            "Raw registry attachment requires BootstrapRunning state."
        }
        check(rawRegistry.pluginId == pluginId) {
            "Raw registry pluginId must match the owning session pluginId."
        }
        val existingRawRegistry = this.rawRegistry
        check(existingRawRegistry == null || existingRawRegistry === rawRegistry) {
            "Raw registry replacement is not allowed for pluginId=$pluginId."
        }
        this.rawRegistry = rawRegistry
    }

    internal fun requireBootstrapRawRegistry(): PluginV2RawRegistry {
        check(state == PluginV2RuntimeSessionState.BootstrapRunning) {
            "Raw registration collection requires BootstrapRunning state."
        }
        return rawRegistry ?: PluginV2RawRegistry(pluginId).also(::attachRawRegistry)
    }

    fun attachCompiledRegistry(compiledRegistry: PluginV2CompiledRegistry) {
        check(state == PluginV2RuntimeSessionState.BootstrapRunning) {
            "Compiled registry attachment requires BootstrapRunning state."
        }
        this.compiledRegistry = compiledRegistry
    }

    internal fun attachCallbackRuntimeSession(
        callbackRuntimeSession: ExternalPluginBootstrapSession,
    ) {
        check(state == PluginV2RuntimeSessionState.BootstrapRunning) {
            "Callback runtime session attachment requires BootstrapRunning state."
        }
        val existingSession = this.callbackRuntimeSession
        check(existingSession == null || existingSession === callbackRuntimeSession) {
            "Callback runtime session replacement is not allowed for pluginId=$pluginId."
        }
        this.callbackRuntimeSession = callbackRuntimeSession
    }

    internal fun allocateCallbackToken(): PluginV2CallbackToken {
        return allocateCallbackToken(NoOpPluginV2CallbackHandle)
    }

    internal fun allocateCallbackToken(handle: PluginV2CallbackHandle): PluginV2CallbackToken {
        check(state == PluginV2RuntimeSessionState.BootstrapRunning) {
            "Callback token allocation requires BootstrapRunning state."
        }
        val nextOrdinal = callbackHandles.size + 1
        check(nextOrdinal <= MAX_CALLBACKS_PER_PLUGIN) {
            "Plugin v2 callback token store is capped at $MAX_CALLBACKS_PER_PLUGIN entries."
        }
        return PluginV2CallbackToken(
            value = "cb::$sessionInstanceId::$nextOrdinal",
        ).also { token ->
            callbackHandles[token] = handle
        }
    }

    fun hasCallbackToken(token: PluginV2CallbackToken): Boolean {
        return callbackHandles.containsKey(token)
    }

    internal fun snapshotCallbackTokens(): List<PluginV2CallbackToken> {
        return callbackHandles.keys.toList()
    }

    fun requireCallbackHandle(token: PluginV2CallbackToken): PluginV2CallbackHandle {
        return callbackHandles[token]
            ?: throw IllegalArgumentException("Unknown callback token: $token")
    }

    suspend fun <T> runSerializedCallback(
        block: suspend () -> T,
    ): T {
        return callbackExecutionMutex.withLock {
            block()
        }
    }

    fun dispose() {
        val runtimeSessionToDispose = callbackRuntimeSession
        callbackRuntimeSession = null
        runtimeSessionToDispose?.dispose()
        callbackHandles.clear()
        rawRegistry = null
        compiledRegistry = null
        state = PluginV2RuntimeSessionState.Disposed
    }

    private fun isValidTransition(
        from: PluginV2RuntimeSessionState,
        to: PluginV2RuntimeSessionState,
    ): Boolean {
        return when (from) {
            PluginV2RuntimeSessionState.Unloaded -> {
                to == PluginV2RuntimeSessionState.Loading ||
                    to == PluginV2RuntimeSessionState.Disposed
            }

            PluginV2RuntimeSessionState.Loading -> {
                to == PluginV2RuntimeSessionState.BootstrapRunning ||
                    to == PluginV2RuntimeSessionState.BootstrapFailed ||
                    to == PluginV2RuntimeSessionState.Disposed
            }

            PluginV2RuntimeSessionState.BootstrapRunning -> {
                to == PluginV2RuntimeSessionState.Active ||
                    to == PluginV2RuntimeSessionState.BootstrapFailed ||
                    to == PluginV2RuntimeSessionState.Disposed
            }

            PluginV2RuntimeSessionState.Active -> {
                to == PluginV2RuntimeSessionState.Disposed
            }

            PluginV2RuntimeSessionState.BootstrapFailed -> {
                to == PluginV2RuntimeSessionState.Disposed
            }

            PluginV2RuntimeSessionState.Disposed -> false
        }
    }
}

private object NoOpPluginV2CallbackHandle : PluginV2CallbackHandle {
    override fun invoke() = Unit
}
