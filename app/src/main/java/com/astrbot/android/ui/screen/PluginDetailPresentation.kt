package com.astrbot.android.ui.screen

import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState

enum class PluginDetailSection {
    TopSummary,
    PrimaryActions,
    Overview,
    SafetyCompatibility,
    PluginPanel,
    TechnicalMetadata,
}

internal fun buildPluginDetailSections(
    uiState: PluginScreenUiState,
): List<PluginDetailSection> {
    return buildList {
        add(PluginDetailSection.TopSummary)
        add(PluginDetailSection.PrimaryActions)
        add(PluginDetailSection.Overview)
        add(PluginDetailSection.SafetyCompatibility)
        if (uiState.schemaUiState !is PluginSchemaUiState.None) {
            add(PluginDetailSection.PluginPanel)
        }
        add(PluginDetailSection.TechnicalMetadata)
    }
}
