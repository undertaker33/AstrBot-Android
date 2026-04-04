package com.astrbot.android.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
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
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginHostWorkspaceUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PluginWorkspaceScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    pluginViewModel: PluginViewModel = astrBotViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pluginViewModel.importWorkspaceFile(uri.toString())
    }
    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForWorkspace(pluginId)
    }

    BoxWithPluginWorkspaceBackground {
        if (uiState.selectedPlugin != null) {
            PluginWorkspacePage(
                uiState = uiState,
                onBack = onBack,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onDeleteFile = pluginViewModel::deleteWorkspaceFile,
                onSchemaCardActionClick = pluginViewModel::onHostWorkspaceCardActionClick,
                onSettingsDraftChange = pluginViewModel::updateHostWorkspaceSettingsDraft,
            )
        }
    }
}

@Composable
private fun PluginWorkspacePage(
    uiState: PluginScreenUiState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onSchemaCardActionClick: (String, Map<String, String>) -> Unit,
    onSettingsDraftChange: (String, com.astrbot.android.ui.viewmodel.PluginSettingDraftValue) -> Unit,
) {
    val workspace = uiState.hostWorkspaceState
    if (!workspace.isVisible) return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
            .testTag(PluginUiSpec.WorkspacePageTag),
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
            PluginWorkspaceSectionCard(
                title = stringResource(R.string.plugin_workspace_title),
                tag = PluginUiSpec.WorkspaceSummaryTag,
            ) {
                Text(
                    text = workspace.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MonochromeUi.textPrimary,
                )
                Text(
                    text = workspace.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonochromeUi.textSecondary,
                )
                WorkspacePathLine(
                    label = stringResource(R.string.plugin_workspace_private_root_label),
                    value = workspace.privateRootPath,
                )
                WorkspacePathLine(
                    label = stringResource(R.string.plugin_workspace_imports_root_label),
                    value = workspace.importsPath,
                )
                WorkspacePathLine(
                    label = stringResource(R.string.plugin_workspace_runtime_root_label),
                    value = workspace.runtimePath,
                )
            }
        }
        item {
            PluginWorkspaceSectionCard(
                title = stringResource(R.string.plugin_workspace_files_title),
                tag = PluginUiSpec.WorkspaceFilesSectionTag,
            ) {
                workspace.lastActionMessage?.let { message ->
                    Text(
                        text = message.asWorkspaceText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonochromeUi.textSecondary,
                    )
                }
                OutlinedButton(
                    onClick = onImport,
                    enabled = !workspace.isImportActionRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PluginUiSpec.WorkspaceImportActionTag),
                ) {
                    Text(stringResource(R.string.plugin_workspace_import_action))
                }
                if (workspace.files.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plugin_workspace_empty_files),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonochromeUi.textSecondary,
                        modifier = Modifier.testTag(PluginUiSpec.WorkspaceEmptyFilesTag),
                    )
                } else {
                    workspace.files.forEach { file ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(PluginUiSpec.workspaceFileTag(file.relativePath)),
                            shape = PluginUiSpec.SectionShape,
                            color = MonochromeUi.cardBackground,
                            border = PluginUiSpec.CardBorder,
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = file.relativePath,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MonochromeUi.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.plugin_workspace_file_meta,
                                        file.sizeBytes,
                                        formatWorkspaceTimestamp(file.lastModifiedAtEpochMillis),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonochromeUi.textSecondary,
                                )
                                OutlinedButton(
                                    onClick = { onDeleteFile(file.relativePath) },
                                    modifier = Modifier.testTag(
                                        PluginUiSpec.workspaceDeleteActionTag(file.relativePath),
                                    ),
                                ) {
                                    Text(stringResource(R.string.plugin_workspace_delete_action))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            PluginWorkspaceSectionCard(
                title = stringResource(R.string.plugin_workspace_management_title),
                tag = PluginUiSpec.WorkspaceManagementSectionTag,
            ) {
                if (workspace.managementSchemaState is com.astrbot.android.ui.viewmodel.PluginSchemaUiState.None) {
                    Text(
                        text = stringResource(R.string.plugin_config_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonochromeUi.textSecondary,
                    )
                } else {
                    PluginSchemaRenderer(
                        schemaUiState = workspace.managementSchemaState,
                        onCardActionClick = onSchemaCardActionClick,
                        onSettingsDraftChange = onSettingsDraftChange,
                        modifier = Modifier.fillMaxWidth().testTag(PluginUiSpec.SchemaWorkspaceTag),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspacePathLine(
    label: String,
    value: String,
) {
    Text(
        text = "$label: ${value.ifBlank { stringResource(R.string.plugin_value_not_available) }}",
        style = MaterialTheme.typography.bodySmall,
        color = MonochromeUi.textSecondary,
    )
}

@Composable
private fun PluginWorkspaceSectionCard(
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
private fun BoxWithPluginWorkspaceBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        content()
    }
}

private fun PluginActionFeedback.asWorkspaceText(): String {
    return when (this) {
        is PluginActionFeedback.Resource -> ""
        is PluginActionFeedback.Text -> value
    }
}

private fun formatWorkspaceTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown"
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault(),
    ).format(Date(timestamp))
}
