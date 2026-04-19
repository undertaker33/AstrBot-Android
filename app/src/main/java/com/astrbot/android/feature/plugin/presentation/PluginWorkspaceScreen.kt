package com.astrbot.android.ui.plugin
import com.astrbot.android.ui.common.MonochromeSecondaryActionButton

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.astrbot.android.ui.navigation.AppDestination
import com.astrbot.android.ui.app.RegisterSecondaryTopBar
import com.astrbot.android.ui.app.SecondaryTopBarSpec
import com.astrbot.android.ui.app.SecondaryTopBarPlaceholder
import androidx.hilt.navigation.compose.hiltViewModel
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.plugin.PluginUiSpec
import com.astrbot.android.ui.plugin.schema.PluginSchemaRenderer
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginHostWorkspaceUiState
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class PluginWorkspaceSection {
    ResourceArea,
    ImportArea,
    ExportArea,
    CacheArea,
    DebugArea,
}

@Composable
fun PluginWorkspaceScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    pluginViewModel: PluginViewModel = hiltViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pluginViewModel.importWorkspaceFile(uri.toString())
    }
    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForWorkspace(pluginId)
    }
    RegisterSecondaryTopBar(
        route = AppDestination.PluginWorkspace.route,
        spec = SecondaryTopBarSpec.SubPage(
            title = stringResource(R.string.plugin_workspace_title),
            onBack = onBack,
        ),
    )

    BoxWithPluginWorkspaceBackground {
        if (uiState.selectedPlugin != null) {
            PluginWorkspacePage(
                uiState = uiState,
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
    onImport: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onSchemaCardActionClick: (String, Map<String, String>) -> Unit,
    onSettingsDraftChange: (String, com.astrbot.android.ui.viewmodel.PluginSettingDraftValue) -> Unit,
) {
    val workspace = uiState.hostWorkspaceState
    if (!workspace.isVisible) return
    val record = uiState.selectedPlugin
    val readOnlyState = buildPluginGovernanceReadOnlyState(record, uiState.detailActionState)

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
            SecondaryTopBarPlaceholder()
        }
        items(buildWorkspaceSections(workspace), key = { it.name }) { section ->
            when (section) {
                    PluginWorkspaceSection.ResourceArea -> {
                        PluginWorkspaceSectionCard(
                            title = stringResource(R.string.plugin_workspace_resources_title),
                            tag = PluginUiSpec.WorkspaceResourceSectionTag,
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
                            if (readOnlyState.isReadOnly) {
                                Text(
                                    text = readOnlyState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                            WorkspacePathLine(
                                label = stringResource(R.string.plugin_workspace_private_root_label),
                                value = workspace.privateRootPath,
                            )
                            WorkspacePathLine(
                                label = stringResource(R.string.plugin_workspace_runtime_root_label),
                                value = workspace.runtimePath,
                            )
                        }
                    }

                    PluginWorkspaceSection.ImportArea -> {
                        PluginWorkspaceSectionCard(
                            title = stringResource(R.string.plugin_workspace_imports_title),
                            tag = PluginUiSpec.WorkspaceImportSectionTag,
                        ) {
                            Text(
                                text = stringResource(R.string.plugin_workspace_imports_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MonochromeUi.textSecondary,
                            )
                            WorkspacePathLine(
                                label = stringResource(R.string.plugin_workspace_imports_root_label),
                                value = workspace.importsPath,
                            )
                            workspace.lastActionMessage?.let { message ->
                                Text(
                                    text = message.asWorkspaceText(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                            MonochromeSecondaryActionButton(
                                label = stringResource(R.string.plugin_workspace_import_action),
                                onClick = onImport,
                                enabled = !workspace.isImportActionRunning && !readOnlyState.isReadOnly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(PluginUiSpec.WorkspaceImportActionTag),
                            )
                            WorkspaceFileList(
                                files = workspace.files.filter { it.relativePath.startsWith("imports/") },
                                emptyMessage = stringResource(R.string.plugin_workspace_empty_files),
                                onDeleteFile = onDeleteFile,
                                allowDelete = !readOnlyState.isReadOnly,
                            )
                        }
                    }

                    PluginWorkspaceSection.ExportArea -> {
                        PluginWorkspaceSectionCard(
                            title = stringResource(R.string.plugin_workspace_exports_title),
                            tag = PluginUiSpec.WorkspaceExportSectionTag,
                        ) {
                            Text(
                                text = stringResource(R.string.plugin_workspace_exports_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MonochromeUi.textSecondary,
                            )
                            WorkspacePathLine(
                                label = stringResource(R.string.plugin_workspace_exports_root_label),
                                value = workspace.exportsPath,
                            )
                            WorkspaceFileList(
                                files = workspace.files.filter { it.relativePath.startsWith("exports/") },
                                emptyMessage = stringResource(R.string.plugin_workspace_exports_empty),
                                onDeleteFile = onDeleteFile,
                                allowDelete = !readOnlyState.isReadOnly,
                            )
                        }
                    }

                    PluginWorkspaceSection.CacheArea -> {
                        PluginWorkspaceSectionCard(
                            title = stringResource(R.string.plugin_workspace_cache_title),
                            tag = PluginUiSpec.WorkspaceCacheSectionTag,
                        ) {
                            WorkspacePathLine(
                                label = stringResource(R.string.plugin_workspace_cache_root_label),
                                value = workspace.cachePath,
                            )
                            WorkspaceFileList(
                                files = workspace.files.filter { it.relativePath.startsWith("cache/") },
                                emptyMessage = stringResource(R.string.plugin_workspace_cache_empty),
                                onDeleteFile = onDeleteFile,
                                allowDelete = !readOnlyState.isReadOnly,
                            )
                        }
                    }

                    PluginWorkspaceSection.DebugArea -> {
                        PluginWorkspaceSectionCard(
                            title = stringResource(R.string.plugin_workspace_debug_title),
                            tag = PluginUiSpec.WorkspaceDebugSectionTag,
                        ) {
                            if (readOnlyState.isReadOnly) {
                                Text(
                                    text = readOnlyState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MonochromeUi.textSecondary,
                                )
                            } else if (workspace.managementSchemaState is PluginSchemaUiState.None) {
                                Text(
                                    text = stringResource(R.string.plugin_workspace_debug_empty),
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
    }
}

private fun buildWorkspaceSections(workspace: PluginHostWorkspaceUiState): List<PluginWorkspaceSection> {
    if (!workspace.isVisible) return emptyList()
    return listOf(
        PluginWorkspaceSection.ResourceArea,
        PluginWorkspaceSection.ImportArea,
        PluginWorkspaceSection.ExportArea,
        PluginWorkspaceSection.CacheArea,
        PluginWorkspaceSection.DebugArea,
    )
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
private fun WorkspaceFileList(
    files: List<com.astrbot.android.ui.viewmodel.PluginHostWorkspaceFileUiState>,
    emptyMessage: String,
    onDeleteFile: (String) -> Unit,
    allowDelete: Boolean,
) {
    if (files.isEmpty()) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textSecondary,
            modifier = Modifier.testTag(PluginUiSpec.WorkspaceEmptyFilesTag),
        )
        return
    }

    files.forEach { file ->
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
                MonochromeSecondaryActionButton(
                    label = stringResource(R.string.plugin_workspace_delete_action),
                    onClick = { onDeleteFile(file.relativePath) },
                    enabled = allowDelete,
                    modifier = Modifier.testTag(
                        PluginUiSpec.workspaceDeleteActionTag(file.relativePath),
                    ),
                )
            }
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

internal fun pluginWorkspaceUsesUnifiedActionButtons(): Boolean = true
