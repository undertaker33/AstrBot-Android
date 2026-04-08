package com.astrbot.android.ui.screen

import com.astrbot.android.R
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginReviewState
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginTrustLevel
import com.astrbot.android.ui.viewmodel.PluginDetailActionState
import com.astrbot.android.ui.viewmodel.PluginDetailManageActionAvailability
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDetailPresentationTest {

    @Test
    fun `detail section builder keeps top summary manage understand metadata order`() {
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
                "TopSummary",
                "ManagePlugin",
                "UnderstandPlugin",
                "TechnicalMetadata",
            ),
            sectionsWithRuntimeSchema.map { it.name },
        )
        assertEquals(
            listOf(
                "TopSummary",
                "ManagePlugin",
                "UnderstandPlugin",
                "TechnicalMetadata",
            ),
            sectionsWithoutPanel.map { it.name },
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

    @Test
    fun `detail action state keeps manage availability centralized`() {
        val state = PluginDetailActionState(
            manageAvailability = PluginDetailManageActionAvailability(
                canEnable = true,
                canDisable = false,
                canUpgrade = true,
            ),
        )

        assertTrue(state.isEnableActionEnabled)
        assertFalse(state.isDisableActionEnabled)
        assertTrue(state.isUpgradeActionEnabled)
    }

    @Test
    fun `plugin detail uses unified manage button styling helper`() {
        assertTrue(pluginDetailUsesUnifiedManageButtonStyle())
    }

    @Test
    fun `detail confirmation spec maps manage actions to stable resources`() {
        val disable = buildPluginDetailConfirmationDialogSpec(
            action = PluginDetailConfirmAction.Disable,
            pluginTitle = "Weather Toolkit",
        )
        val uninstall = buildPluginDetailConfirmationDialogSpec(
            action = PluginDetailConfirmAction.Uninstall,
            pluginTitle = "Weather Toolkit",
        )
        val clearCache = buildPluginDetailConfirmationDialogSpec(
            action = PluginDetailConfirmAction.ClearCache,
            pluginTitle = "Weather Toolkit",
        )
        val restoreDefaults = buildPluginDetailConfirmationDialogSpec(
            action = PluginDetailConfirmAction.RestoreDefaults,
            pluginTitle = "Weather Toolkit",
        )

        assertEquals(R.string.plugin_action_confirm_disable_title, disable.titleRes)
        assertEquals(R.string.plugin_action_confirm_disable_message, disable.messageRes)
        assertEquals("Weather Toolkit", disable.pluginTitle)

        assertEquals(R.string.plugin_action_confirm_uninstall_title, uninstall.titleRes)
        assertEquals(R.string.plugin_action_confirm_uninstall_message, uninstall.messageRes)
        assertEquals("Weather Toolkit", uninstall.pluginTitle)

        assertEquals(R.string.plugin_action_confirm_clear_cache_title, clearCache.titleRes)
        assertEquals(R.string.plugin_action_confirm_clear_cache_message, clearCache.messageRes)
        assertEquals("Weather Toolkit", clearCache.pluginTitle)

        assertEquals(R.string.plugin_action_confirm_restore_defaults_title, restoreDefaults.titleRes)
        assertEquals(R.string.plugin_action_confirm_restore_defaults_message, restoreDefaults.messageRes)
        assertEquals("Weather Toolkit", restoreDefaults.pluginTitle)
    }

    @Test
    fun `plugin detail manage section no longer renders uninstall policy chips`() {
        val source = listOf(
            File("app/src/main/java/com/astrbot/android/ui/screen/PluginDetailScreen.kt"),
            File("src/main/java/com/astrbot/android/ui/screen/PluginDetailScreen.kt"),
        ).first(File::exists).readText()

        assertFalse(source.contains("DetailKeepDataPolicyTag"))
        assertFalse(source.contains("DetailRemoveDataPolicyTag"))
        assertFalse(source.contains("plugin_action_uninstall_policy_keep_data"))
        assertFalse(source.contains("plugin_action_uninstall_policy_remove_data"))
    }

    @Test
    fun `plugin detail removes recovery card and unlisted manage entries`() {
        val source = listOf(
            File("app/src/main/java/com/astrbot/android/ui/screen/PluginDetailScreen.kt"),
            File("src/main/java/com/astrbot/android/ui/screen/PluginDetailScreen.kt"),
        ).first(File::exists).readText()

        assertFalse(source.contains("PluginDetailRecoveryAndUpgradeSection"))
        assertFalse(source.contains("RecoveryAndUpgrade"))
        assertFalse(source.contains("DetailRecoveryTag"))
        assertFalse(source.contains("DetailOpenWorkspaceActionTag"))
        assertFalse(source.contains("DetailOpenTriggersActionTag"))
        assertFalse(source.contains("DetailRetryActionTag"))
        assertFalse(source.contains("DetailCopyDiagnosticsActionTag"))
        assertTrue(source.contains("PluginDetailConfirmAction.ClearCache"))
        assertTrue(source.contains("PluginDetailConfirmAction.RestoreDefaults"))
    }

    @Test
    fun `plugin detail uses confirmation dialogs for destructive actions`() {
        assertTrue(pluginDetailUsesConfirmationDialogs())
    }
}
