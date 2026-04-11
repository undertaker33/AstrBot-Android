package com.astrbot.android.runtime.plugin

import java.io.File
import org.junit.Assume

object PluginV2QuickJsTestGate {
    data class Availability(
        val available: Boolean,
        val reason: String,
    )

    fun assumeAvailable() {
        val availability = probe()
        Assume.assumeTrue(
            "QuickJS gate blocked: ${availability.reason}",
            availability.available,
        )
    }

    fun probe(): Availability {
        val tempRoot = createTempDir(prefix = "plugin-v2-quickjs-gate")
        val runtimeDir = File(tempRoot, "runtime").apply { mkdirs() }
        val bootstrapFile = File(runtimeDir, "bootstrap.js")
        return runCatching {
            bootstrapFile.writeText(
                """
                export default async function bootstrap(hostApi) {
                  await Promise.resolve();
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )
            val executor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {})
            val session = executor.openBootstrapSession(
                ExternalPluginBootstrapSessionRequest(
                    pluginId = "__quickjs_gate__",
                    bootstrapAbsolutePath = bootstrapFile.absolutePath,
                    pluginRootDirectory = tempRoot.absolutePath,
                    bootstrapTimeoutMs = 1_000L,
                ),
            )
            try {
                session.executeBootstrap()
                Availability(
                    available = true,
                    reason = "available",
                )
            } finally {
                session.dispose()
            }
        }.getOrElse { error ->
            Availability(
                available = false,
                reason = error.message ?: error.javaClass.simpleName,
            )
        }.also {
            tempRoot.deleteRecursively()
        }
    }
}
