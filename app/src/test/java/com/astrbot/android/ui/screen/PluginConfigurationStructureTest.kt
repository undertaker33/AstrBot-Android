package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginHostWorkspaceUiState
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginStaticConfigUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginConfigurationStructureTest {

    @Test
    fun `config section builder groups basic runtime data when settings are available`() {
        val sections = buildConfigSectionsForTest(
            PluginScreenUiState(
                staticConfigUiState = PluginStaticConfigUiState(
                    schema = PluginStaticConfigSchema(),
                ),
                schemaUiState = PluginSchemaUiState.Settings(
                    schema = PluginSettingsSchema(title = "Runtime settings"),
                ),
            ),
        )

        assertEquals(
            listOf("BasicSettings", "RuntimeSettings", "DataSettings"),
            sections.map { it.name },
        )
    }

    @Test
    fun `config section builder keeps empty state when no settings are exposed`() {
        val sections = buildConfigSectionsForTest(PluginScreenUiState())

        assertEquals(listOf("EmptyState"), sections.map { it.name })
    }

    @Test
    fun `workspace section builder groups resource import export cache and debug areas`() {
        val method = Class.forName("com.astrbot.android.ui.screen.PluginWorkspaceScreenKt")
            .declaredMethods
            .firstOrNull { it.name == "buildWorkspaceSections" }

        assertNotNull("Expected buildWorkspaceSections to exist for workspace grouping.", method)

        method!!.isAccessible = true
        val sections = method.invoke(
            null,
            PluginHostWorkspaceUiState(
                isVisible = true,
                pluginId = "demo",
                title = "Demo",
                description = "Demo workspace",
                privateRootPath = "/private/demo",
                importsPath = "/private/demo/imports",
                runtimePath = "/private/demo/runtime",
                exportsPath = "/private/demo/exports",
                cachePath = "/private/demo/cache",
                lastActionMessage = PluginActionFeedback.Text("Imported file"),
                managementSchemaState = PluginSchemaUiState.Settings(
                    schema = PluginSettingsSchema(title = "Debug actions"),
                ),
            ),
        ) as List<*>

        assertEquals(
            listOf("ResourceArea", "ImportArea", "ExportArea", "CacheArea", "DebugArea"),
            sections.map { (it as Enum<*>).name },
        )
    }

    @Test
    fun `config and workspace tertiary pages use unified action button styling`() {
        assertTrue(pluginConfigUsesUnifiedActionButtons())
        assertTrue(pluginConfigUsesExplicitSaveFab())
        assertTrue(pluginWorkspaceUsesUnifiedActionButtons())
    }

    private fun buildConfigSectionsForTest(uiState: PluginScreenUiState): List<Enum<*>> {
        val method = Class.forName("com.astrbot.android.ui.screen.PluginConfigScreenKt")
            .getDeclaredMethod("buildConfigSections", PluginScreenUiState::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, uiState) as List<Enum<*>>
    }
}
