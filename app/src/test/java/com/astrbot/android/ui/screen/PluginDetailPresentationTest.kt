package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginDetailPresentationTest {

    @Test
    fun `detail section builder keeps top summary primary actions overview safety metadata order`() {
        val sectionsWithRuntimeSchema = buildPluginDetailSections(
            uiState = PluginScreenUiState(
                schemaUiState = PluginSchemaUiState.Settings(
                    schema = PluginSettingsSchema(
                        title = "Runtime Settings",
                    ),
                ),
            ),
        )
        val sectionsWithoutPanel = buildPluginDetailSections(uiState = PluginScreenUiState())

        assertEquals(
            listOf(
                PluginDetailSection.TopSummary,
                PluginDetailSection.PrimaryActions,
                PluginDetailSection.Overview,
                PluginDetailSection.SafetyCompatibility,
                PluginDetailSection.TechnicalMetadata,
            ),
            sectionsWithRuntimeSchema,
        )
        assertEquals(
            listOf(
                PluginDetailSection.TopSummary,
                PluginDetailSection.PrimaryActions,
                PluginDetailSection.Overview,
                PluginDetailSection.SafetyCompatibility,
                PluginDetailSection.TechnicalMetadata,
            ),
            sectionsWithoutPanel,
        )
    }
}
