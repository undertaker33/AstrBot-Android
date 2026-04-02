package com.astrbot.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.astrbot.android.R
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.ui.screen.PluginScreen
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

class PluginScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pluginScreenRendersSummaryAndOpensDetail() {
        val dependencies = FakePluginViewModelDependencies()

        composeRule.setContent {
            MaterialTheme {
                PluginScreen(
                    pluginViewModel = PluginViewModel(dependencies),
                )
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.SummaryCardTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.PluginListTag).assertIsDisplayed()
        composeRule.onNodeWithText("Weather Toolkit", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.pluginCardTag("weather-toolkit")).performClick()

        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag).assertIsDisplayed()
        composeRule.onNodeWithText("AstrBot Labs", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun pluginScreenSupportsPolicyToggleAndDisableFeedback() {
        val dependencies = FakePluginViewModelDependencies()

        composeRule.setContent {
            MaterialTheme {
                PluginScreen(
                    pluginViewModel = PluginViewModel(dependencies),
                )
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.pluginCardTag("weather-toolkit")).performClick()
        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.DetailRemoveDataPolicyTag).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            check(dependencies.records.value.single().uninstallPolicy == PluginUninstallPolicy.REMOVE_DATA)
        }

        composeRule.onNodeWithTag(PluginUiSpec.DetailActionMessageTag)
            .assertIsDisplayed()
            .assertTextContains(
                composeRule.activity.getString(R.string.plugin_action_feedback_uninstall_policy_remove_data),
            )

        composeRule.onNodeWithTag(PluginUiSpec.DetailDisableActionTag).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            check(!dependencies.records.value.single().enabled)
        }

        composeRule.onNodeWithTag(PluginUiSpec.DetailActionMessageTag)
            .assertIsDisplayed()
            .assertTextContains(
                composeRule.activity.getString(R.string.plugin_action_feedback_disabled),
            )
    }
}

private class FakePluginViewModelDependencies : PluginViewModelDependencies {
    private val recordsState = MutableStateFlow(
        listOf(
            PluginInstallRecord.restoreFromPersistedState(
                manifestSnapshot = PluginManifest(
                    pluginId = "weather-toolkit",
                    version = "1.2.0",
                    protocolVersion = 1,
                    author = "AstrBot Labs",
                    title = "Weather Toolkit",
                    description = "Shows local forecasts and quick climate summaries.",
                    permissions = listOf(
                        PluginPermissionDeclaration(
                            permissionId = "network",
                            title = "Network access",
                            description = "Fetches weather data from the configured provider.",
                            riskLevel = PluginRiskLevel.MEDIUM,
                        ),
                    ),
                    minHostVersion = "0.3.6",
                    maxHostVersion = "0.4.0",
                    sourceType = PluginSourceType.LOCAL_FILE,
                    entrySummary = "Adds a forecast helper card to the plugin workspace.",
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
                source = PluginSource(
                    sourceType = PluginSourceType.LOCAL_FILE,
                    location = "/storage/emulated/0/Download/weather-toolkit.zip",
                    importedAt = 42L,
                ),
                compatibilityState = PluginCompatibilityState.evaluated(
                    protocolSupported = true,
                    minHostVersionSatisfied = true,
                    maxHostVersionSatisfied = true,
                    notes = "Validated against Phase 1 protocol.",
                ),
                enabled = true,
                installedAt = 100L,
                lastUpdatedAt = 200L,
            ),
        ),
    )

    override val records: StateFlow<List<PluginInstallRecord>> = recordsState

    override fun setPluginEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord {
        val updated = requireNotNull(recordsState.value.firstOrNull { it.pluginId == pluginId }).copyWith(
            enabled = enabled,
        )
        recordsState.value = recordsState.value.map { record ->
            if (record.pluginId == pluginId) updated else record
        }
        return updated
    }

    override fun updatePluginUninstallPolicy(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): PluginInstallRecord {
        val updated = requireNotNull(recordsState.value.firstOrNull { it.pluginId == pluginId }).copyWith(
            uninstallPolicy = policy,
        )
        recordsState.value = recordsState.value.map { record ->
            if (record.pluginId == pluginId) updated else record
        }
        return updated
    }

    override fun uninstallPlugin(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): com.astrbot.android.data.PluginUninstallResult {
        recordsState.value = recordsState.value.filterNot { record -> record.pluginId == pluginId }
        return com.astrbot.android.data.PluginUninstallResult(
            pluginId = pluginId,
            policy = policy,
            removedData = policy == PluginUninstallPolicy.REMOVE_DATA,
        )
    }
}

private fun PluginInstallRecord.copyWith(
    enabled: Boolean = this.enabled,
    uninstallPolicy: PluginUninstallPolicy = this.uninstallPolicy,
): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifestSnapshot,
        source = source,
        permissionSnapshot = permissionSnapshot,
        compatibilityState = compatibilityState,
        uninstallPolicy = uninstallPolicy,
        enabled = enabled,
        installedAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = localPackagePath,
        extractedDir = extractedDir,
    )
}
