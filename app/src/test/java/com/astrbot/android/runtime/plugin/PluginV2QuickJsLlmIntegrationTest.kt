package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gated QuickJS LLM integration assets.
 *
 * Manual run:
 * `.\gradlew.bat :app:testDebugUnitTest --tests "com.astrbot.android.runtime.plugin.PluginV2QuickJsLlmIntegrationTest"`
 */
class PluginV2QuickJsLlmIntegrationTest {

    @Test
    fun request_response_fixture_bootstraps_request_mutation_and_response_observer_hooks() {
        PluginV2QuickJsTestGate.assumeAvailable()

        withBootstrappedFixture(
            name = "request-response-plugin",
            pluginId = "com.astrbot.samples.request-response-plugin",
        ) { handle, compiledRegistry ->
            val rawRegistry = requireNotNull(handle.session.rawRegistry)

            assertEquals(2, rawRegistry.llmHooks.size)
            assertEquals(
                listOf("request-response.request", "request-response.response"),
                rawRegistry.llmHooks.map { it.registrationKey },
            )
            assertEquals(
                listOf("on_llm_request", "on_llm_response"),
                rawRegistry.llmHooks.map { it.descriptor.hook },
            )
            assertEquals(
                "request_mutation",
                rawRegistry.llmHooks.first().metadata.values["behavior"],
            )
            assertEquals(
                "response_observer",
                rawRegistry.llmHooks.last().metadata.values["behavior"],
            )
            assertEquals(
                listOf(PluginV2InternalStage.LlmRequest, PluginV2InternalStage.LlmResponse),
                compiledRegistry.handlerRegistry.llmHookHandlers.map { it.surface.stage },
            )
            assertTrue(handle.bootstrapAbsolutePath.endsWith("runtime${File.separator}bootstrap.js"))
        }
    }

    @Test
    fun decorating_fixture_bootstraps_result_decorating_mutation_hook() {
        PluginV2QuickJsTestGate.assumeAvailable()

        withBootstrappedFixture(
            name = "decorating-plugin",
            pluginId = "com.astrbot.samples.decorating-plugin",
        ) { handle, compiledRegistry ->
            val rawRegistry = requireNotNull(handle.session.rawRegistry)

            assertEquals(1, rawRegistry.llmHooks.size)
            assertEquals("decorating.result", rawRegistry.llmHooks.single().registrationKey)
            assertEquals("on_decorating_result", rawRegistry.llmHooks.single().descriptor.hook)
            assertEquals(
                "append_decorated_text",
                rawRegistry.llmHooks.single().metadata.values["behavior"],
            )
            assertEquals(
                listOf(PluginV2InternalStage.ResultDecorating),
                compiledRegistry.handlerRegistry.llmHookHandlers.map { it.surface.stage },
            )
            assertTrue(handle.bootstrapAbsolutePath.endsWith("runtime${File.separator}bootstrap.js"))
        }
    }

    @Test
    fun after_sent_fixture_bootstraps_read_only_observer_hook() {
        PluginV2QuickJsTestGate.assumeAvailable()

        withBootstrappedFixture(
            name = "after-sent-plugin",
            pluginId = "com.astrbot.samples.after-sent-plugin",
        ) { handle, compiledRegistry ->
            val rawRegistry = requireNotNull(handle.session.rawRegistry)

            assertEquals(1, rawRegistry.llmHooks.size)
            assertEquals("after-sent.observe", rawRegistry.llmHooks.single().registrationKey)
            assertEquals("after_message_sent", rawRegistry.llmHooks.single().descriptor.hook)
            assertEquals(
                "read_only_observer",
                rawRegistry.llmHooks.single().metadata.values["behavior"],
            )
            assertEquals(
                listOf(PluginV2InternalStage.AfterMessageSent),
                compiledRegistry.handlerRegistry.llmHookHandlers.map { it.surface.stage },
            )
            assertTrue(handle.bootstrapAbsolutePath.endsWith("runtime${File.separator}bootstrap.js"))
        }
    }

    private inline fun withBootstrappedFixture(
        name: String,
        pluginId: String,
        block: (PluginV2RuntimeHandle, PluginV2CompiledRegistrySnapshot) -> Unit,
    ) {
        val sourceRoot = fixtureRoot(name)
        val workingRoot = Files.createTempDirectory("plugin-v2-llm-$name").toFile()
        try {
            sourceRoot.copyRecursively(workingRoot, overwrite = true)
            val installRecord = samplePluginV2InstallRecord(pluginId = pluginId)
                .copyForFixture(workingRoot.absolutePath)
            val handle = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ).createSession(installRecord)
            try {
                handle.executeBootstrap()
                val compiledRegistry = requireNotNull(
                    PluginV2RegistryCompiler(logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }), clock = { 1L })
                        .compile(requireNotNull(handle.session.rawRegistry))
                        .compiledRegistry,
                )
                block(handle, compiledRegistry)
            } finally {
                handle.dispose()
            }
        } finally {
            workingRoot.deleteRecursively()
        }
    }

    private fun fixtureRoot(name: String): File {
        val resource = requireNotNull(javaClass.classLoader?.getResource("plugin-v2-llm/$name")) {
            "Missing QuickJS LLM integration fixture: $name"
        }
        return File(resource.toURI())
    }

    private fun PluginInstallRecord.copyForFixture(
        extractedDir: String,
    ): PluginInstallRecord {
        val contractSnapshot = requireNotNull(packageContractSnapshot)
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifestSnapshot,
            source = source,
            packageContractSnapshot = contractSnapshot.copy(
                runtime = contractSnapshot.runtime.copy(
                    bootstrap = "runtime/bootstrap.js",
                ),
            ),
            permissionSnapshot = permissionSnapshot,
            compatibilityState = compatibilityState,
            uninstallPolicy = uninstallPolicy,
            enabled = enabled,
            failureState = failureState,
            catalogSourceId = catalogSourceId,
            installedPackageUrl = installedPackageUrl,
            lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
            installedAt = installedAt,
            lastUpdatedAt = lastUpdatedAt,
            localPackagePath = localPackagePath,
            extractedDir = extractedDir,
        )
    }
}
