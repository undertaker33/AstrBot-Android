package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

@Ignore(
    "Gated QuickJS integration asset. Run manually with .\\gradlew.bat :app:testDebugUnitTest --tests \"com.astrbot.android.runtime.plugin.PluginV2QuickJsBootstrapIntegrationTest\" in a JVM-native QuickJS environment.",
)
class PluginV2QuickJsBootstrapIntegrationTest {

    @Test
    fun basic_plugin_async_bootstrap_fixture_loads_and_waits_for_completion() {
        val fixtureRoot = pluginFixtureRoot("basic-plugin")
        val installRecord = samplePluginV2InstallRecord().copyForFixture(
            extractedDir = fixtureRoot.absolutePath,
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
        )

        val handle = factory.createSession(installRecord)

        assertTrue(handle.bootstrapAbsolutePath.endsWith("runtime/bootstrap.js"))
        handle.executeBootstrap()
        assertEquals(PluginV2RuntimeSessionState.BootstrapRunning, handle.session.state)
        handle.dispose()
    }

    @Test
    fun duplicate_key_plugin_bootstrap_fixture_is_available_for_future_registry_conflict_assertions() {
        val fixtureRoot = pluginFixtureRoot("duplicate-key-plugin")
        val installRecord = samplePluginV2InstallRecord(pluginId = "com.example.v2.duplicate").copyForFixture(
            extractedDir = fixtureRoot.absolutePath,
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
        )

        val handle = factory.createSession(installRecord)

        assertTrue(handle.bootstrapAbsolutePath.endsWith("runtime/bootstrap.js"))
        handle.dispose()
    }

    private fun pluginFixtureRoot(name: String): File {
        val resource = requireNotNull(javaClass.classLoader?.getResource("plugin-v2-bootstrap/$name")) {
            "Missing QuickJS bootstrap fixture: $name"
        }
        return File(resource.toURI())
    }
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
