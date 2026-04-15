package com.astrbot.android.ui.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginManagerPresentationTest {

    @Test
    fun `manager presentation maps open and update actions`() {
        val uiState = PluginScreenUiState(
            records = listOf(
                installedRecord(
                    pluginId = "camera-toolkit",
                    title = "Camera Toolkit",
                    author = "Alice",
                    version = "1.0.0",
                ),
                installedRecord(
                    pluginId = "weather-kit",
                    title = "Weather Kit",
                    author = "Bob",
                    version = "1.1.0",
                ),
            ),
            updateAvailabilitiesByPluginId = mapOf(
                "weather-kit" to PluginUpdateAvailability(
                    pluginId = "weather-kit",
                    installedVersion = "1.1.0",
                    latestVersion = "1.2.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    changelogSummary = "improve cache",
                    catalogSourceId = "market",
                    packageUrl = "https://example.com/weather-kit-1.2.0.zip",
                ),
            ),
        )

        val presentation = buildPluginManagerPresentation(uiState)

        assertEquals(2, presentation.cards.size)
        assertEquals("camera-toolkit", presentation.cards[0].pluginId)
        assertEquals(PluginManagerPrimaryAction.Open, presentation.cards[0].primaryAction)
        assertEquals("weather-kit", presentation.cards[1].pluginId)
        assertEquals(PluginManagerPrimaryAction.Update, presentation.cards[1].primaryAction)
        assertEquals(1, presentation.updatableCount)
    }

    @Test
    fun `manager presentation keeps latest version empty when no update exists`() {
        val uiState = PluginScreenUiState(
            records = listOf(
                installedRecord(
                    pluginId = "solo",
                    title = "Solo Plugin",
                    author = "AstrBot",
                    version = "2.0.0",
                ),
            ),
        )

        val presentation = buildPluginManagerPresentation(uiState)
        val card = presentation.cards.single()

        assertEquals("", card.latestVersion)
        assertEquals(PluginManagerPrimaryAction.Open, card.primaryAction)
    }

    private fun installedRecord(
        pluginId: String,
        title: String,
        author: String,
        version: String,
    ): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = pluginId,
                version = version,
                protocolVersion = 1,
                author = author,
                title = title,
                description = "$title description",
                permissions = listOf(
                    PluginPermissionDeclaration(
                        permissionId = "$pluginId.permission",
                        title = "Permission",
                        description = "Permission",
                        riskLevel = PluginRiskLevel.MEDIUM,
                    ),
                ),
                minHostVersion = "0.0.0",
                maxHostVersion = "",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "entry",
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/tmp/$pluginId.zip",
                importedAt = 1L,
            ),
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            enabled = true,
            installedAt = 1L,
            lastUpdatedAt = 1L,
        )
    }
}
