package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginExecutionHostContextInjectionTest {
    @Test
    fun inject_adds_workspace_and_config_host_api_without_overwriting_existing_extras() {
        val plugin = runtimePlugin(pluginId = "context-plugin")
        val base = executionContextFor(plugin).copy(
            config = executionContextFor(plugin).config.copy(
                extras = mapOf("source" to "test"),
            ),
        )

        val injected = PluginExecutionHostApi.inject(
            context = base,
            hostSnapshot = PluginExecutionHostSnapshot(
                runtimeKind = ExternalPluginRuntimeKind.JsQuickJs,
                bridgeMode = "compatibility_only",
                workspaceSnapshot = PluginHostWorkspaceSnapshot(
                    privateRootPath = "/workspace/private",
                    importsPath = "/workspace/private/imports",
                    runtimePath = "/workspace/private/runtime",
                    exportsPath = "/workspace/private/exports",
                    cachePath = "/workspace/private/cache",
                ),
                configBoundary = PluginConfigStorageBoundary(
                    coreFieldKeys = setOf("token"),
                    extensionFieldKeys = setOf("mode"),
                ),
                configSnapshot = PluginConfigStoreSnapshot(
                    coreValues = mapOf("token" to PluginStaticConfigValue.StringValue("secret")),
                    extensionValues = mapOf("mode" to PluginStaticConfigValue.StringValue("safe")),
                ),
            ),
        )

        assertEquals("test", injected.config.extras["source"])
        assertTrue(
            injected.config.extras[PluginExecutionHostApi.WorkspaceApiKey]
                ?.contains("\"importsPath\":\"/workspace/private/imports\"") == true,
        )
        assertTrue(
            injected.config.extras[PluginExecutionHostApi.ConfigSnapshotKey]
                ?.contains("\"token\":\"secret\"") == true,
        )
        assertEquals(
            "js_quickjs",
            injected.triggerMetadata.extras[PluginExecutionHostApi.RuntimeKindKey],
        )
        assertEquals(
            "compatibility_only",
            injected.triggerMetadata.extras[PluginExecutionHostApi.BridgeModeKey],
        )
    }
}
