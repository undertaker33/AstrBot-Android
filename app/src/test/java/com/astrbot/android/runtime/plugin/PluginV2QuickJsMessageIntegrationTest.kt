package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gated QuickJS message integration asset.
 *
 * Manual run:
 * `.\gradlew.bat :app:testDebugUnitTest --tests "com.astrbot.android.runtime.plugin.PluginV2QuickJsMessageIntegrationTest"`
 */
class PluginV2QuickJsMessageIntegrationTest {

    @Test
    fun basic_message_fixture_bootstraps_message_command_and_regex_registrations() {
        PluginV2QuickJsTestGate.assumeAvailable()

        withBootstrappedFixture("basic-message-plugin") { handle ->
            val rawRegistry = requireNotNull(handle.session.rawRegistry)

            assertEquals(1, rawRegistry.messageHandlers.size)
            assertEquals(1, rawRegistry.commandHandlers.size)
            assertEquals(1, rawRegistry.regexHandlers.size)
            assertEquals(
                "basic-message.message",
                rawRegistry.messageHandlers.single().registrationKey,
            )
            assertEquals(
                listOf(BootstrapFilterKind.Message),
                rawRegistry.messageHandlers.single().declaredFilters.map { it.kind },
            )
            assertEquals(
                "basic-message.command",
                rawRegistry.commandHandlers.single().registrationKey,
            )
            assertEquals(
                "echo",
                rawRegistry.commandHandlers.single().descriptor.command,
            )
            assertEquals(
                "basic-message.regex",
                rawRegistry.regexHandlers.single().registrationKey,
            )
            assertTrue(
                rawRegistry.regexHandlers.single().descriptor.flags.contains("IGNORE_CASE"),
            )
            assertTrue(handle.bootstrapAbsolutePath.endsWith("runtime${File.separator}bootstrap.js"))
        }
    }

    @Test
    fun lifecycle_fixture_bootstraps_lifecycle_hooks_for_loaded_unloaded_and_error() {
        PluginV2QuickJsTestGate.assumeAvailable()

        withBootstrappedFixture("lifecycle-plugin") { handle ->
            val rawRegistry = requireNotNull(handle.session.rawRegistry)

            assertEquals(3, rawRegistry.lifecycleHandlers.size)
            assertEquals(
                listOf(
                    "lifecycle.loaded",
                    "lifecycle.unloaded",
                    "lifecycle.error",
                ),
                rawRegistry.lifecycleHandlers.map { it.registrationKey },
            )
        }
    }

    private inline fun withBootstrappedFixture(
        name: String,
        block: (PluginV2RuntimeHandle) -> Unit,
    ) {
        val sourceRoot = fixtureRoot(name)
        val workingRoot = Files.createTempDirectory("plugin-v2-message-$name").toFile()
        try {
            sourceRoot.copyRecursively(workingRoot, overwrite = true)
            val installRecord = samplePluginV2InstallRecord(
                pluginId = "com.astrbot.samples.$name",
            ).copyForFixture(workingRoot.absolutePath)
            val handle = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ).createSession(installRecord)
            try {
                handle.executeBootstrap()
                block(handle)
            } finally {
                handle.dispose()
            }
        } finally {
            workingRoot.deleteRecursively()
        }
    }

    private fun fixtureRoot(name: String): File {
        val resource = requireNotNull(javaClass.classLoader?.getResource("plugin-v2-message/$name")) {
            "Missing QuickJS message integration fixture: $name"
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
