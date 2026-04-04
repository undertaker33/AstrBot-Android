package com.astrbot.android.ui.screen

import com.astrbot.android.R
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginReviewState
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginTrustLevel
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

    @Test
    fun `governance display items map risk trust and review states to stable resources`() {
        val items = buildGovernanceDisplayItems(
            PluginGovernanceSnapshot(
                riskLevel = PluginRiskLevel.HIGH,
                trustLevel = PluginTrustLevel.REPOSITORY_LISTED,
                reviewState = PluginReviewState.LOCAL_CHECKS_PASSED,
            ),
        )

        assertEquals(
            listOf(
                PluginGovernanceDisplayItem(
                    labelRes = R.string.plugin_field_risk_level,
                    valueRes = R.string.plugin_risk_high,
                ),
                PluginGovernanceDisplayItem(
                    labelRes = R.string.plugin_field_trust_level,
                    valueRes = R.string.plugin_trust_repository_listed,
                ),
                PluginGovernanceDisplayItem(
                    labelRes = R.string.plugin_field_review_state,
                    valueRes = R.string.plugin_review_local_checks_passed,
                ),
            ),
            items,
        )
    }
}
