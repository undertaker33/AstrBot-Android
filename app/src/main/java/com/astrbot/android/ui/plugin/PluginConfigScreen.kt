package com.astrbot.android.ui.plugin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.ui.navigation.AppDestination
import com.astrbot.android.ui.app.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.app.RegisterSecondaryTopBar
import com.astrbot.android.ui.app.SecondaryTopBarSpec
import com.astrbot.android.ui.app.SecondaryTopBarPlaceholder
import com.astrbot.android.ui.common.MonochromeSecondaryActionButton
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.plugin.PluginUiSpec
import com.astrbot.android.ui.plugin.schema.PluginSchemaRenderer
import com.astrbot.android.ui.plugin.schema.PluginStaticConfigRenderer
import com.astrbot.android.ui.plugin.schema.buildPluginStaticConfigRenderModel
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginViewModel

private enum class PluginConfigSection {
    BasicSettings,
    RuntimeSettings,
    DataSettings,
    EmptyState,
}

@Composable
fun PluginConfigScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    onOpenConfigBackup: () -> Unit = {},
    pluginViewModel: PluginViewModel = astrBotViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()
    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForConfig(pluginId)
    }
    RegisterSecondaryTopBar(
        route = AppDestination.PluginConfig.route,
        spec = SecondaryTopBarSpec.SubPage(
            title = stringResource(R.string.plugin_config_title),
            onBack = onBack,
        ),
    )

    BoxWithConfigPageBackground {
        if (uiState.selectedPlugin != null) {
            PluginConfigWorkspace(
                uiState = uiState,
                onOpenConfigBackup = onOpenConfigBackup,
                onSchemaCardActionClick = pluginViewModel::onSchemaCardActionClick,
                onSettingsDraftChange = pluginViewModel::updateSettingsDraft,
                onStaticConfigDraftChange = pluginViewModel::updateStaticConfigDraft,
                onSaveConfig = pluginViewModel::saveSelectedPluginConfig,
            )
        }
    }
}

@Composable
private fun PluginConfigWorkspace(
    uiState: PluginScreenUiState,
    onOpenConfigBackup: () -> Unit,
    onSchemaCardActionClick: (actionId: String, payload: Map<String, String>) -> Unit,
    onSettingsDraftChange: (fieldId: String, draftValue: com.astrbot.android.ui.viewmodel.PluginSettingDraftValue) -> Unit,
    onStaticConfigDraftChange: (fieldKey: String, draftValue: com.astrbot.android.ui.viewmodel.PluginSettingDraftValue) -> Unit,
    onSaveConfig: () -> Unit,
) {
    val record = uiState.selectedPlugin ?: return
    val sections = buildConfigSections(uiState)
    val context = LocalContext.current
    val readOnlyState = buildPluginGovernanceReadOnlyState(record, uiState.detailActionState)

    Box(modifier = Modifier.fillMaxSize()) {
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
                SecondaryTopBarPlaceholder()
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
                    Text(
                        text = stringResource(R.string.plugin_config_backup_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonochromeUi.textSecondary,
                    )
                    if (readOnlyState.isReadOnly) {
                        Text(
                            text = readOnlyState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                    MonochromeSecondaryActionButton(
                        label = stringResource(R.string.plugin_config_backup_action),
                        onClick = onOpenConfigBackup,
                        modifier = Modifier.testTag("plugin-config-open-backup"),
                    )
                }
            }

            items(sections, key = { it.name }) { section ->
                when (section) {
                    PluginConfigSection.BasicSettings -> {
                        val staticConfigState = uiState.staticConfigUiState ?: return@items
                        PluginConfigSectionCard(
                            title = stringResource(R.string.plugin_config_basic_title),
                            tag = PluginUiSpec.ConfigBasicSectionTag,
                        ) {
                            if (readOnlyState.isReadOnly) {
                                Text(
                                    text = readOnlyState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MonochromeUi.textSecondary,
                                )
                            } else {
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
                    }

                    PluginConfigSection.RuntimeSettings -> {
                        PluginConfigSectionCard(
                            title = stringResource(R.string.plugin_config_runtime_title),
                            tag = PluginUiSpec.ConfigRuntimeSectionTag,
                        ) {
                            if (readOnlyState.isReadOnly) {
                                Text(
                                    text = readOnlyState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MonochromeUi.textSecondary,
                                )
                            } else {
                                PluginSchemaRenderer(
                                    schemaUiState = uiState.schemaUiState,
                                    onCardActionClick = onSchemaCardActionClick,
                                    onSettingsDraftChange = onSettingsDraftChange,
                                    modifier = Modifier.fillMaxWidth().testTag(PluginUiSpec.SchemaWorkspaceTag),
                                )
                            }
                        }
                    }

                    PluginConfigSection.DataSettings -> {
                        PluginConfigSectionCard(
                            title = stringResource(R.string.plugin_config_data_title),
                            tag = PluginUiSpec.ConfigDataSectionTag,
                        ) {
                            Text(
                                text = if (readOnlyState.isReadOnly) {
                                    "${stringResource(R.string.plugin_config_data_message)} ${readOnlyState.message}"
                                } else {
                                    stringResource(R.string.plugin_config_data_message)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MonochromeUi.textSecondary,
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

        FloatingActionButton(
            onClick = {
                if (readOnlyState.isReadOnly) {
                    Toast.makeText(context, readOnlyState.message, Toast.LENGTH_SHORT).show()
                } else {
                    onSaveConfig()
                    Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = FloatingBottomNavFabBottomPadding)
                .testTag(PluginUiSpec.ConfigSaveFabTag),
            containerColor = MonochromeUi.fabBackground,
            contentColor = MonochromeUi.fabContent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Done,
                contentDescription = stringResource(R.string.common_save),
            )
        }
    }
}

private fun buildConfigSections(uiState: PluginScreenUiState): List<PluginConfigSection> {
    return buildList {
        if (uiState.staticConfigUiState != null) {
            add(PluginConfigSection.BasicSettings)
        }
        if (uiState.schemaUiState !is PluginSchemaUiState.None) {
            add(PluginConfigSection.RuntimeSettings)
        }
        if (isNotEmpty()) {
            add(PluginConfigSection.DataSettings)
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

internal fun pluginConfigUsesUnifiedActionButtons(): Boolean = true
internal fun pluginConfigUsesExplicitSaveFab(): Boolean = true
