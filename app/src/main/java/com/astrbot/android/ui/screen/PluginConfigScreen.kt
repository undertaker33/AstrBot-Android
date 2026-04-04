package com.astrbot.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.screen.plugin.schema.PluginSchemaRenderer
import com.astrbot.android.ui.screen.plugin.schema.PluginStaticConfigRenderer
import com.astrbot.android.ui.screen.plugin.schema.buildPluginStaticConfigRenderModel
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginViewModel

private enum class PluginConfigSection {
    StaticConfig,
    RuntimeConfig,
    EmptyState,
}

@Composable
fun PluginConfigScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    pluginViewModel: PluginViewModel = astrBotViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()
    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForConfig(pluginId)
    }

    BoxWithConfigPageBackground {
        if (uiState.selectedPlugin != null) {
            PluginConfigWorkspace(
                uiState = uiState,
                onBack = onBack,
                onSchemaCardActionClick = pluginViewModel::onSchemaCardActionClick,
                onSettingsDraftChange = pluginViewModel::updateSettingsDraft,
                onStaticConfigDraftChange = pluginViewModel::updateStaticConfigDraft,
            )
        }
    }
}

@Composable
private fun PluginConfigWorkspace(
    uiState: PluginScreenUiState,
    onBack: () -> Unit,
    onSchemaCardActionClick: (actionId: String, payload: Map<String, String>) -> Unit,
    onSettingsDraftChange: (fieldId: String, draftValue: com.astrbot.android.ui.viewmodel.PluginSettingDraftValue) -> Unit,
    onStaticConfigDraftChange: (fieldKey: String, draftValue: com.astrbot.android.ui.viewmodel.PluginSettingDraftValue) -> Unit,
) {
    val record = uiState.selectedPlugin ?: return
    val sections = buildConfigSections(uiState)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
            .testTag(PluginUiSpec.ConfigPageTag),
        contentPadding = PaddingValues(
            top = PluginUiSpec.ScreenVerticalPadding,
            bottom = PluginUiSpec.ListContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
    ) {
        item {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(PluginUiSpec.ConfigBackActionTag),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = MonochromeUi.textSecondary,
                )
                Text(
                    text = stringResource(R.string.common_back),
                    color = MonochromeUi.textSecondary,
                )
            }
        }

        item {
            PluginConfigSectionCard(
                title = stringResource(R.string.plugin_config_title),
                tag = PluginUiSpec.ConfigSummaryTag,
            ) {
                Text(
                    text = record.manifestSnapshot.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MonochromeUi.textPrimary,
                )
                Text(
                    text = record.manifestSnapshot.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonochromeUi.textSecondary,
                )
            }
        }

        items(sections, key = { it.name }) { section ->
            when (section) {
                PluginConfigSection.StaticConfig -> {
                    val staticConfigState = uiState.staticConfigUiState ?: return@items
                    PluginConfigSectionCard(
                        title = stringResource(R.string.plugin_config_core_title),
                        tag = PluginUiSpec.ConfigStaticSectionTag,
                    ) {
                        PluginStaticConfigRenderer(
                            model = buildPluginStaticConfigRenderModel(
                                schema = staticConfigState.schema,
                                draftValues = staticConfigState.draftValues,
                            ),
                            onDraftChange = onStaticConfigDraftChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                PluginConfigSection.RuntimeConfig -> {
                    PluginConfigSectionCard(
                        title = stringResource(R.string.plugin_config_extension_title),
                        tag = PluginUiSpec.ConfigRuntimeSectionTag,
                    ) {
                        PluginSchemaRenderer(
                            schemaUiState = uiState.schemaUiState,
                            onCardActionClick = onSchemaCardActionClick,
                            onSettingsDraftChange = onSettingsDraftChange,
                            modifier = Modifier.fillMaxWidth().testTag(PluginUiSpec.SchemaWorkspaceTag),
                        )
                    }
                }

                PluginConfigSection.EmptyState -> {
                    PluginConfigSectionCard(
                        title = stringResource(R.string.plugin_config_empty_title),
                        tag = PluginUiSpec.ConfigEmptyStateTag,
                    ) {
                        Text(
                            text = stringResource(R.string.plugin_config_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

private fun buildConfigSections(uiState: PluginScreenUiState): List<PluginConfigSection> {
    return buildList {
        if (uiState.staticConfigUiState != null) {
            add(PluginConfigSection.StaticConfig)
        }
        if (uiState.schemaUiState !is PluginSchemaUiState.None) {
            add(PluginConfigSection.RuntimeConfig)
        }
        if (isEmpty()) {
            add(PluginConfigSection.EmptyState)
        }
    }
}

@Composable
private fun PluginConfigSectionCard(
    title: String,
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            content()
        }
    }
}

@Composable
private fun BoxWithConfigPageBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        content()
    }
}
