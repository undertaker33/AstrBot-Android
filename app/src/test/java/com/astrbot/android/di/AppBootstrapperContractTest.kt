package com.astrbot.android.di

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.di.startup.observePluginRuntimeRecords
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSyncResult
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppBootstrapperContractTest {

    @Test
    fun bootstrap_delegates_to_runner_without_hand_assembled_startup_calls() {
        val source = appBootstrapperSource()
        val bootstrapBody = functionBody(source, "bootstrap")

        assertTrue(
            "AppBootstrapper must delegate startup execution to AppStartupRunner",
            source.contains("private val appStartupRunner: com.astrbot.android.di.startup.AppStartupRunner") ||
                source.contains("private val appStartupRunner: AppStartupRunner"),
        )
        assertTrue(
            "AppBootstrapper.bootstrap must call AppStartupRunner.run()",
            bootstrapBody.contains("appStartupRunner.run()"),
        )

        val forbiddenTokens = listOf(
            "InitializationCoordinator(",
            "providerRepositoryWarmup.warmUp()",
            "AppDownloadManager.initialize(application)",
            "NapCatBridgeRepository.initialize(application)",
            "NapCatLoginRepository.initialize(application)",
            "RuntimeAssetRepository.initialize(application)",
            "ActiveCapabilityToolSourceProvider.initialize(application)",
            "ResourceCenterRepository.initialize(application)",
            "CronJobScheduler.initialize(application)",
            "PersonaReferenceGuard.register",
            "ProviderReferenceGuard.register",
            "observePluginRuntimeRecords(",
            "qqBridgeRuntime.start()",
            "containerRuntimeInstaller.warmUpAsync(appScope)",
        )
        val violations = forbiddenTokens.filter(bootstrapBody::contains)
        assertTrue(
            "AppBootstrapper must stay a thin shell and must not hand-assemble startup dependencies: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_repository_updates_trigger_repeated_loader_syncs() = runTest {
        RuntimeLogRepository.clear()
        val records = MutableStateFlow<List<PluginInstallRecord>>(emptyList())
        val dispatcher = StandardTestDispatcher(testScheduler)
        val syncCount = AtomicInteger(0)
        val observedSnapshots = mutableListOf<List<String>>()

        val job: Job = observePluginRuntimeRecords(
            records = records,
            sync = { currentRecords ->
                syncCount.incrementAndGet()
                observedSnapshots += currentRecords.map { it.pluginId }
                PluginV2RuntimeSyncResult(loads = emptyList(), unloads = emptyList())
            },
            dispatcher = dispatcher,
        )

        try {
            advanceUntilIdle()
            records.value = listOf(pluginV2Record("com.astrbot.samples.container_sync_a"))
            advanceUntilIdle()
            records.value = listOf(
                pluginV2Record("com.astrbot.samples.container_sync_a"),
                pluginV2Record("com.astrbot.samples.container_sync_b"),
            )
            advanceUntilIdle()

            assertTrue(syncCount.get() >= 3)
            assertEquals(emptyList<String>(), observedSnapshots.first())
            assertEquals(
                listOf("com.astrbot.samples.container_sync_a", "com.astrbot.samples.container_sync_b"),
                observedSnapshots.last(),
            )
        } finally {
            job.cancel()
            RuntimeLogRepository.clear()
        }
    }

    @Test
    fun superseded_collect_latest_sync_is_not_reported_as_failure() = runTest {
        RuntimeLogRepository.clear()
        val records = MutableStateFlow<List<PluginInstallRecord>>(emptyList())
        val dispatcher = StandardTestDispatcher(testScheduler)
        val startedSnapshots = mutableListOf<List<String>>()

        val job: Job = observePluginRuntimeRecords(
            records = records,
            sync = { currentRecords ->
                startedSnapshots += currentRecords.map { it.pluginId }
                awaitCancellation()
            },
            dispatcher = dispatcher,
        )

        try {
            advanceUntilIdle()
            records.value = listOf(pluginV2Record("com.astrbot.samples.container_cancel_a"))
            advanceUntilIdle()
            records.value = listOf(
                pluginV2Record("com.astrbot.samples.container_cancel_a"),
                pluginV2Record("com.astrbot.samples.container_cancel_b"),
            )
            advanceUntilIdle()

            assertTrue(startedSnapshots.size >= 3)
            assertEquals(emptyList<String>(), startedSnapshots.first())
            assertEquals(
                listOf("com.astrbot.samples.container_cancel_a", "com.astrbot.samples.container_cancel_b"),
                startedSnapshots.last(),
            )
            assertTrue(
                RuntimeLogRepository.logs.value.none { log ->
                    log.contains("Plugin v2 runtime loader sync failed")
                },
            )
        } finally {
            job.cancel()
            RuntimeLogRepository.clear()
        }
    }

    private fun appBootstrapperSource(): String {
        val sourceFile = listOf(
            File("src/main/java/com/astrbot/android/di/AppBootstrapper.kt"),
            File("app/src/main/java/com/astrbot/android/di/AppBootstrapper.kt"),
        ).first { it.exists() }
        return sourceFile.readText()
    }

    private fun functionBody(source: String, functionName: String): String {
        val signatureIndex = source.indexOf("fun $functionName(")
        require(signatureIndex >= 0) { "Missing function: $functionName" }
        val bodyStart = source.indexOf('{', signatureIndex)
        require(bodyStart >= 0) { "Missing body for function: $functionName" }
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun matchingBraceIndex(source: String, openIndex: Int): Int {
        var depth = 0
        for (index in openIndex until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        error("No matching closing brace found")
    }

    private fun pluginV2Record(pluginId: String): PluginInstallRecord {
        val manifest = PluginManifest(
            pluginId = pluginId,
            version = "1.0.0",
            protocolVersion = 2,
            author = "AstrBot",
            title = "Plugin V2",
            description = "Container sync test plugin",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = "net.access",
                    title = "Network access",
                    description = "Allows outgoing requests",
                    riskLevel = PluginRiskLevel.LOW,
                    required = true,
                ),
            ),
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Bootstrap",
            riskLevel = PluginRiskLevel.LOW,
        )
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/tmp/$pluginId.zip",
                importedAt = 0L,
            ),
            packageContractSnapshot = PluginPackageContractSnapshot(
                protocolVersion = 2,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 1,
                ),
            ),
            permissionSnapshot = manifest.permissions,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            enabled = true,
            installedAt = 0L,
            lastUpdatedAt = 0L,
            localPackagePath = "/tmp/$pluginId.zip",
            extractedDir = "/tmp/$pluginId",
        )
    }
}
