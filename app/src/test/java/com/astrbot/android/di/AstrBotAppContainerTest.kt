package com.astrbot.android.di

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.plugin.PluginV2RuntimeSyncResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstrBotAppContainerTest {
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
