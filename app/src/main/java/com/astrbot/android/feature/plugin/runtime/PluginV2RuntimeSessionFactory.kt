package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.util.UUID

class PluginV2RuntimeSessionFactory(
    private val scriptExecutor: ExternalPluginScriptExecutor = QuickJsExternalPluginScriptExecutor(),
    private val sessionInstanceIdFactory: (PluginInstallRecord) -> String = { installRecord ->
        "${installRecord.pluginId}::${UUID.randomUUID()}"
    },
) {
    fun createSession(
        installRecord: PluginInstallRecord,
    ): PluginV2RuntimeHandle {
        val contractSnapshot = requireNotNull(installRecord.packageContractSnapshot) {
            "Plugin v2 install record is missing packageContractSnapshot: ${installRecord.pluginId}"
        }
        require(contractSnapshot.protocolVersion == 2) {
            "Plugin v2 session factory requires protocolVersion=2: ${installRecord.pluginId}"
        }
        require(contractSnapshot.runtime.kind == SUPPORTED_RUNTIME_KIND) {
            "Unsupported plugin v2 runtime kind for ${installRecord.pluginId}: ${contractSnapshot.runtime.kind}"
        }

        val pluginRootDirectory = File(installRecord.extractedDir)
        check(pluginRootDirectory.isDirectory) {
            "Plugin v2 extractedDir does not exist for ${installRecord.pluginId}: ${installRecord.extractedDir}"
        }
        val canonicalPluginRoot = pluginRootDirectory.canonicalFile
        val bootstrapPath = contractSnapshot.runtime.bootstrap.trim()
        require(bootstrapPath.isNotBlank()) {
            "Plugin v2 bootstrap path must not be blank: ${installRecord.pluginId}"
        }

        val canonicalBootstrapFile = File(canonicalPluginRoot, bootstrapPath).canonicalFile
        check(canonicalBootstrapFile.toPath().startsWith(canonicalPluginRoot.toPath())) {
            "Plugin v2 bootstrap must stay within the plugin root for ${installRecord.pluginId}: $bootstrapPath"
        }
        check(canonicalBootstrapFile.isFile) {
            "Plugin v2 bootstrap file does not exist for ${installRecord.pluginId}: ${canonicalBootstrapFile.absolutePath}"
        }

        val session = PluginV2RuntimeSession(
            installRecord = installRecord,
            sessionInstanceId = sessionInstanceIdFactory(installRecord),
        ).also { runtimeSession ->
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.Loading)
        }
        val bootstrapSession = try {
            scriptExecutor.openBootstrapSession(
                ExternalPluginBootstrapSessionRequest(
                    pluginId = installRecord.pluginId,
                    bootstrapAbsolutePath = canonicalBootstrapFile.absolutePath,
                    pluginRootDirectory = canonicalPluginRoot.absolutePath,
                    bootstrapTimeoutMs = DEFAULT_BOOTSTRAP_TIMEOUT_MS,
                ),
            )
        } catch (error: Exception) {
            session.dispose()
            throw error
        }
        return PluginV2RuntimeHandle(
            session = session,
            bootstrapAbsolutePath = canonicalBootstrapFile.absolutePath,
            bootstrapTimeoutMs = DEFAULT_BOOTSTRAP_TIMEOUT_MS,
            bootstrapSession = bootstrapSession,
        )
    }

    companion object {
        const val DEFAULT_BOOTSTRAP_TIMEOUT_MS: Long = 10_000L
        private const val SUPPORTED_RUNTIME_KIND: String = "js_quickjs"
    }
}

class PluginV2RuntimeHandle internal constructor(
    val session: PluginV2RuntimeSession,
    val bootstrapAbsolutePath: String,
    val bootstrapTimeoutMs: Long,
    internal val bootstrapSession: ExternalPluginBootstrapSession,
) {
    private val lifecycleMonitor = Object()

    @Volatile
    private var disposed: Boolean = false
    private var bootstrapInFlight: Boolean = false
    private var bootstrapSessionTransferredToRuntime: Boolean = false

    val liveHandleCount: Int
        get() = bootstrapSession.liveHandleCount

    fun installBootstrapGlobal(
        name: String,
        value: Any?,
    ) {
        synchronized(lifecycleMonitor) {
            check(!disposed) {
                "Bootstrap session has already been disposed: ${session.pluginId}"
            }
            bootstrapSession.installGlobal(name, value)
        }
    }

    fun executeBootstrap() {
        synchronized(lifecycleMonitor) {
            check(!disposed) {
                "Bootstrap session has already been disposed: ${session.pluginId}"
            }
            check(!bootstrapInFlight) {
                "Bootstrap is already running: ${session.pluginId}"
            }
            check(session.state == PluginV2RuntimeSessionState.Loading) {
                "Bootstrap can only execute from Loading state: ${session.pluginId}"
            }
            bootstrapInFlight = true
            session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }
        var failure: Exception? = null
        try {
            bootstrapSession.executeBootstrap()
        } catch (error: Exception) {
            failure = error
        } finally {
            synchronized(lifecycleMonitor) {
                bootstrapInFlight = false
                lifecycleMonitor.notifyAll()
                if (!disposed && failure != null) {
                    session.transitionTo(PluginV2RuntimeSessionState.BootstrapFailed)
                }
            }
        }
        failure?.let { throw it }
    }

    fun transferBootstrapSessionOwnershipToRuntime() {
        synchronized(lifecycleMonitor) {
            check(!disposed) {
                "Bootstrap session has already been disposed: ${session.pluginId}"
            }
            check(!bootstrapInFlight) {
                "Bootstrap session cannot transfer ownership while bootstrap is running: ${session.pluginId}"
            }
            check(!bootstrapSessionTransferredToRuntime) {
                "Bootstrap session ownership was already transferred: ${session.pluginId}"
            }
            session.attachCallbackRuntimeSession(bootstrapSession)
            bootstrapSessionTransferredToRuntime = true
        }
    }

    fun dispose() {
        synchronized(lifecycleMonitor) {
            if (disposed) {
                return
            }
            while (bootstrapInFlight) {
                try {
                    lifecycleMonitor.wait()
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException(
                        "Interrupted while waiting to dispose bootstrap session: ${session.pluginId}",
                        interrupted,
                    )
                }
            }
            disposed = true
        }
        try {
            if (!bootstrapSessionTransferredToRuntime) {
                bootstrapSession.dispose()
            }
        } finally {
            session.dispose()
        }
    }
}
