package com.astrbot.android.ui.screen

import com.astrbot.android.ui.viewmodel.PluginScreenUiState

enum class PluginDetailSection {
    TopSummary,
    PrimaryActions,
    Overview,
    SafetyCompatibility,
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
        add(PluginDetailSection.TechnicalMetadata)
    }
}
