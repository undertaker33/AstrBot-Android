package com.astrbot.android.feature.plugin.runtime

import com.whl.quickjs.wrapper.QuickJSContext
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2QuickJsTestGate {
    @Test
    fun probe_returns_structured_availability_without_throwing() {
        val availability = probe()

        assertTrue(availability.reason.isNotBlank())
        if (availability.available) {
            assertTrue(availability.reason == "available")
        } else {
            assertFalse(availability.reason == "available")
        }
    }

    @Test
    fun probe_reports_available_when_injected_checks_succeed() {
        val tempRoot = Files.createTempDirectory("plugin-v2-quickjs-gate-test").toFile()
        try {
            val availability = probe(
                dependencies = ProbeDependencies(
                    createTempRoot = { tempRoot },
                    runInlineEvaluation = { },
                    runBootstrapProbe = { root ->
                        File(root, "runtime").mkdirs()
                    },
                ),
            )

            assertTrue(availability.available)
            assertEquals("available", availability.reason)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun probe_reports_unavailable_when_injected_check_fails() {
        val tempRoot = Files.createTempDirectory("plugin-v2-quickjs-gate-test").toFile()
        try {
            val availability = probe(
                dependencies = ProbeDependencies(
                    createTempRoot = { tempRoot },
                    runInlineEvaluation = {
                        error("synthetic probe failure")
                    },
                    runBootstrapProbe = { },
                ),
            )

            assertFalse(availability.available)
            assertEquals("synthetic probe failure", availability.reason)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    companion object {
        data class Availability(
            val available: Boolean,
            val reason: String,
        )

        data class ProbeDependencies(
            val createTempRoot: () -> File = {
                Files.createTempDirectory("plugin-v2-quickjs-gate").toFile()
            },
            val runInlineEvaluation: () -> Unit = {
                QuickJSContext.create().use { context ->
                    val inlineResult = context.evaluate("1 + 1;", "__astrbot_quickjs_gate__.js")
                    check(inlineResult?.toString()?.trim() == "2") {
                        "QuickJS inline evaluation returned unexpected result: $inlineResult"
                    }
                }
            },
            val runBootstrapProbe: (File) -> Unit = { tempRoot ->
                val runtimeDir = File(tempRoot, "runtime").apply { mkdirs() }
                val bootstrapFile = File(runtimeDir, "bootstrap.js")
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
                } finally {
                    session.dispose()
                }
            },
        )

        fun assumeAvailable() {
            val availability = probe()
            Assume.assumeTrue(
                "QuickJS gate blocked: ${availability.reason}",
                availability.available,
            )
        }

        fun probe(
            dependencies: ProbeDependencies = ProbeDependencies(),
        ): Availability {
            val tempRoot = dependencies.createTempRoot()
            return runCatching {
                dependencies.runInlineEvaluation()
                dependencies.runBootstrapProbe(tempRoot)
                Availability(
                    available = true,
                    reason = "available",
                )
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
}
