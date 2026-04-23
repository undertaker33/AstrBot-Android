package com.astrbot.android.feature.plugin.presentation

import com.astrbot.android.feature.plugin.domain.PluginGovernancePort
import com.astrbot.android.feature.plugin.domain.PluginManagementResult
import com.astrbot.android.feature.plugin.domain.PluginManagementUseCases
import com.astrbot.android.feature.plugin.domain.PluginRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginRuntimePort
import com.astrbot.android.feature.plugin.presentation.bindings.PluginManagementBindings
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
    fun controller_primary_constructor_depends_on_management_bindings() {
        val constructors = PluginPresentationController::class.java.declaredConstructors.toList()

        assertEquals(1, constructors.size)
        assertEquals(
            listOf(PluginManagementBindings::class.java),
            constructors.single().parameterTypes.toList(),
        )
    }

    @Test
    fun refreshCatalog_returnsSuccess_when_runtime_refresh_succeeds() = runTest {
        val controller = PluginPresentationController(
            FakePluginManagementBindings(),
        )

        val result = controller.refreshCatalog()

        assertTrue(result is PluginManagementResult.Success)
    }

    @Test
    fun refreshCatalog_returnsFailed_result_instead_of_throwing_when_runtime_refresh_fails() = runTest {
        val controller = PluginPresentationController(
            FakePluginManagementBindings(refreshError = IllegalStateException("boom")),
        )

        val result = controller.refreshCatalog()

        assertEquals(PluginManagementResult.Failed("boom"), result)
    }

    private class FakePluginManagementBindings(
        private val refreshError: Throwable? = null,
    ) : PluginManagementBindings {
        override fun enablePlugin(pluginId: String): PluginInstallRecord {
            error("Not used in this test")
        }

        override fun disablePlugin(pluginId: String): PluginInstallRecord {
            error("Not used in this test")
        }

        override fun uninstallPlugin(
            pluginId: String,
            policy: com.astrbot.android.model.plugin.PluginUninstallPolicy,
        ): com.astrbot.android.feature.plugin.data.PluginUninstallResult {
            return com.astrbot.android.feature.plugin.data.PluginUninstallResult(
                pluginId = pluginId,
                policy = policy,
                removedData = policy == com.astrbot.android.model.plugin.PluginUninstallPolicy.REMOVE_DATA,
            )
        }

        override suspend fun refreshCatalog(): PluginManagementResult {
            if (refreshError != null) throw refreshError
            return PluginManagementResult.Success
        }

        override fun recoverPlugin(pluginId: String) = Unit

        override fun suspendPlugin(pluginId: String, reason: String) = Unit

        override fun findByPluginId(pluginId: String): PluginInstallRecord? = null
    }
}
