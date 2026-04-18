package com.astrbot.android.feature.plugin.presentation

import com.astrbot.android.feature.plugin.domain.PluginGovernancePort
import com.astrbot.android.feature.plugin.domain.PluginManagementResult
import com.astrbot.android.feature.plugin.domain.PluginManagementUseCases
import com.astrbot.android.feature.plugin.domain.PluginRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginRuntimePort
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPresentationControllerTest {

    @Test
    fun refreshCatalog_returnsSuccess_when_runtime_refresh_succeeds() = runTest {
        val controller = PluginPresentationController(
            PluginManagementUseCases(
                repository = FakePluginRepositoryPort(),
                runtime = FakePluginRuntimePort(),
                governance = FakePluginGovernancePort(),
            ),
        )

        val result = controller.refreshCatalog()

        assertTrue(result is PluginManagementResult.Success)
    }

    @Test
    fun refreshCatalog_returnsFailed_result_instead_of_throwing_when_runtime_refresh_fails() = runTest {
        val controller = PluginPresentationController(
            PluginManagementUseCases(
                repository = FakePluginRepositoryPort(),
                runtime = FakePluginRuntimePort(refreshError = IllegalStateException("boom")),
                governance = FakePluginGovernancePort(),
            ),
        )

        val result = controller.refreshCatalog()

        assertEquals(PluginManagementResult.Failed("boom"), result)
    }

    private class FakePluginRuntimePort(
        private val refreshError: Throwable? = null,
    ) : PluginRuntimePort {
        override suspend fun refreshRuntimeRegistry() {
            if (refreshError != null) throw refreshError
        }

        override fun runtimeLogSummary(pluginId: String): String = ""

        override fun isPluginLoaded(pluginId: String): Boolean = false
    }

    private class FakePluginGovernancePort : PluginGovernancePort {
        override fun isSuspended(pluginId: String): Boolean = false

        override fun recoverPlugin(pluginId: String) = Unit

        override fun suspendPlugin(pluginId: String, reason: String) = Unit

        override fun currentFailureState(pluginId: String): PluginFailureState? = null
    }

    private class FakePluginRepositoryPort : PluginRepositoryPort {
        override fun findByPluginId(pluginId: String): PluginInstallRecord? = null

        override fun upsert(record: PluginInstallRecord) = Unit

        override fun delete(pluginId: String) = Unit

        override fun setEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord {
            error("Not used in this test")
        }

        override fun updateFailureState(
            pluginId: String,
            failureState: PluginFailureState,
        ): PluginInstallRecord {
            error("Not used in this test")
        }

        override fun uninstall(
            pluginId: String,
            policy: PluginUninstallPolicy,
        ): com.astrbot.android.feature.plugin.domain.PluginUninstallResult {
            error("Not used in this test")
        }

        override fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? = null

        override fun saveCoreConfig(
            pluginId: String,
            boundary: PluginConfigStorageBoundary,
            coreValues: Map<String, PluginStaticConfigValue>,
        ): PluginConfigStoreSnapshot {
            error("Not used in this test")
        }

        override fun saveExtensionConfig(
            pluginId: String,
            boundary: PluginConfigStorageBoundary,
            extensionValues: Map<String, PluginStaticConfigValue>,
        ): PluginConfigStoreSnapshot {
            error("Not used in this test")
        }
    }
}
