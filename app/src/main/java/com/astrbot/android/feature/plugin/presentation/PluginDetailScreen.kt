package com.astrbot.android.ui.plugin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.common.MonochromeSecondaryActionButton
import com.astrbot.android.ui.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginDetailActionState
import com.astrbot.android.ui.viewmodel.PluginDetailMetadataState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginViewModel

@Composable
fun PluginDetailScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    onOpenLogs: (String) -> Unit,
    onOpenConfig: (String) -> Unit,
    onReturnToInstalledPlugins: () -> Unit,
    pluginViewModel: PluginViewModel = hiltViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val refreshFeedbackText = uiState.marketRefreshFeedback?.asText()
    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForDetail(pluginId)
    }
    LaunchedEffect(refreshFeedbackText) {
        if (!refreshFeedbackText.isNullOrBlank()) {
            Toast.makeText(context, refreshFeedbackText, Toast.LENGTH_SHORT).show()
            pluginViewModel.clearMarketRefreshFeedback()
        }
    }
    RegisterSecondaryTopBar(
        route = AppDestination.PluginDetail.route,
        spec = SecondaryTopBarSpec.SubPage(
            title = stringResource(R.string.plugin_detail_title),
            onBack = onBack,
        ),
    )

    BoxWithPageBackground {
        if (uiState.selectedPlugin != null) {
            PluginDetailRouteWorkspace(
                uiState = uiState,
                onOpenLogs = onOpenLogs,
                onOpenConfig = onOpenConfig,
                onEnable = pluginViewModel::enableSelectedPlugin,
                onDisable = pluginViewModel::disableSelectedPlugin,
                onRequestUpgrade = pluginViewModel::requestUpgradeForSelectedPlugin,
                onUninstall = pluginViewModel::uninstallSelectedPlugin,
                onReturnToInstalledPlugins = onReturnToInstalledPlugins,
                onRestoreDefaults = pluginViewModel::restoreSelectedPluginDefaultConfig,
                onClearCache = pluginViewModel::clearSelectedPluginCache,
            )
        }
        uiState.upgradeDialogState?.let { dialogState ->
            PluginUpgradeDialog(
                state = dialogState,
                onConfirm = pluginViewModel::confirmUpgrade,
                onDismiss = pluginViewModel::dismissUpgradeDialog,
            )
        }
    }
}

@Composable
private fun PluginDetailRouteWorkspace(
    uiState: PluginScreenUiState,
    onOpenLogs: (String) -> Unit,
    onOpenConfig: (String) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRequestUpgrade: () -> Unit,
    onUninstall: () -> Boolean,
    onReturnToInstalledPlugins: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onClearCache: () -> Unit,
) {
    val record = uiState.selectedPlugin ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
            .testTag(PluginUiSpec.DetailPanelTag),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = PluginUiSpec.ScreenVerticalPadding,
            bottom = PluginUiSpec.ListContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
    ) {
        item {
            SecondaryTopBarPlaceholder()
        }

        items(buildPluginDetailSections(uiState), key = { it.name }) { section ->
            when (section) {
                PluginDetailSection.TopSummary -> PluginDetailTopSummarySection(
                    record = record,
                    metadata = uiState.detailMetadataState,
                )
                PluginDetailSection.ManagePlugin -> PluginDetailManageSection(
                    pluginTitle = record.manifestSnapshot.title,
                    actionState = uiState.detailActionState,
                    onOpenLogs = { onOpenLogs(record.pluginId) },
                    onOpenConfig = { onOpenConfig(record.pluginId) },
                    onEnable = onEnable,
                    onDisable = onDisable,
                    onRequestUpgrade = onRequestUpgrade,
                    onUninstall = onUninstall,
                    onReturnToInstalledPlugins = onReturnToInstalledPlugins,
                    onRestoreDefaults = onRestoreDefaults,
                    onClearCache = onClearCache,
                )
                PluginDetailSection.UnderstandPlugin -> PluginDetailUnderstandSection(
                    record = record,
                    metadata = uiState.detailMetadataState,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginDetailTopSummarySection(
    record: PluginInstallRecord,
    metadata: PluginDetailMetadataState,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.DetailTopSummaryTag),
        shape = PluginUiSpec.SummaryShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.InnerSpacing),
        ) {
            Text(
                text = record.manifestSnapshot.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = stringResource(
                    R.string.plugin_detail_subtitle,
                    record.installedVersion,
                    sourceTypeLabel(record.source.sourceType),
                    installStatusLabel(record),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                assistBadge(stringResource(R.string.plugin_field_author), record.manifestSnapshot.author)
                assistBadge(stringResource(R.string.plugin_field_source_label), sourceTypeLabel(record.source.sourceType))
                metadata.governanceState?.let { governance ->
                    assistBadge(stringResource(R.string.plugin_field_protocol), "v${governance.protocolVersion}")
                    assistBadge("Runtime health", runtimeHealthLabel(governance.runtimeHealth.status))
                }
            }
        }
    }
}

@Composable
private fun PluginDetailUnderstandSection(
    record: PluginInstallRecord,
    metadata: PluginDetailMetadataState,
) {
    PluginSectionCard(
        title = stringResource(R.string.plugin_detail_understand_title),
        tag = PluginUiSpec.DetailUnderstandTag,
    ) {
        Text(
            text = record.manifestSnapshot.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textPrimary,
        )
        Text(
            text = record.manifestSnapshot.entrySummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textSecondary,
        )
        PluginKeyValueSection(
            title = stringResource(R.string.plugin_detail_identity_group_title),
            items = listOf(
                stringResource(R.string.plugin_field_author) to record.manifestSnapshot.author,
                stringResource(R.string.plugin_field_source_label) to (metadata.sourceBadge?.label ?: sourceTypeLabel(record.source.sourceType)),
                stringResource(R.string.plugin_field_repository_name_or_host) to metadata.repositoryNameOrHost.ifBlank {
                    stringResource(R.string.plugin_value_not_available)
                },
                stringResource(R.string.plugin_field_install_status) to installStatusLabel(record),
            ),
        )
        PluginKeyValueSection(
            title = stringResource(R.string.plugin_detail_compatibility_group_title),
            items = listOf(
                stringResource(R.string.plugin_field_compatibility_status) to compatibilityLabel(record.compatibilityState.status),
                stringResource(R.string.plugin_field_compatibility_notes) to record.compatibilityState.notes.ifBlank {
                    stringResource(R.string.plugin_value_no_notes)
                },
            ),
        )
        PluginKeyValueSection(
            title = stringResource(R.string.plugin_detail_permissions_group_title),
            items = if (record.permissionSnapshot.isEmpty()) {
                listOf(
                    stringResource(R.string.plugin_detail_permissions_title) to stringResource(R.string.plugin_value_not_available),
                )
            } else {
                record.permissionSnapshot.map { permission ->
                    permission.title to stringResource(
                        R.string.plugin_permission_summary_format,
                        if (permission.required) {
                            stringResource(R.string.plugin_permission_required)
                        } else {
                            stringResource(R.string.plugin_permission_optional)
                        },
                        permission.description,
                    )
                }
            },
        )
        metadata.governanceState?.let { governanceState ->
            PluginKeyValueSection(
                title = stringResource(R.string.plugin_detail_governance_group_title),
                items = buildGovernanceDisplayItems(governanceState).map { item ->
                    stringResource(item.labelRes) to stringResource(item.valueRes)
                } + listOf(
                    "Runtime health" to runtimeHealthLabel(governanceState.runtimeHealth.status),
                    "Registration" to buildRegistrationSummaryText(governanceState),
                    "Capability grants" to buildCapabilityGrantSummaryText(governanceState),
                    "Diagnostics" to buildDiagnosticsSummaryText(governanceState),
                ),
            )
        }
    }
}

@Composable
private fun PluginDetailManageSection(
    pluginTitle: String,
    actionState: PluginDetailActionState,
    onOpenLogs: () -> Unit,
    onOpenConfig: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRequestUpgrade: () -> Unit,
    onUninstall: () -> Boolean,
    onReturnToInstalledPlugins: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onClearCache: () -> Unit,
) {
    var pendingConfirmAction by rememberSaveable(pluginTitle) {
        mutableStateOf<PluginDetailConfirmAction?>(null)
    }
    PluginSectionCard(
        title = stringResource(R.string.plugin_detail_manage_title),
        tag = PluginUiSpec.DetailManageTag,
    ) {
        actionState.failureState?.let { failureState ->
            PluginDetailFailureBanner(failureState = failureState)
        }
        actionState.lastActionMessage?.let { message ->
            detailHintCard(message.asText())
        }
        actionState.enableBlockedReason?.let { message ->
            detailHintCard(message.asText())
        }
        actionState.updateBlockedReason?.let { message ->
            detailHintCard(message.asText())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PluginDetailManageButton(
                onClick = onEnable,
                enabled = actionState.manageAvailability.canEnable,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailEnableActionTag),
                label = stringResource(R.string.plugin_action_enable),
            )
            PluginDetailManageButton(
                onClick = { pendingConfirmAction = PluginDetailConfirmAction.Disable },
                enabled = actionState.manageAvailability.canDisable,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailDisableActionTag),
                label = stringResource(R.string.plugin_action_disable),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PluginDetailManageButton(
                onClick = onRequestUpgrade,
                enabled = actionState.manageAvailability.canUpgrade,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailUpgradeActionTag),
                label = stringResource(R.string.plugin_action_check_update),
            )
            PluginDetailManageButton(
                onClick = { pendingConfirmAction = PluginDetailConfirmAction.Uninstall },
                enabled = actionState.manageAvailability.canUninstall,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailUninstallActionTag),
                label = stringResource(R.string.plugin_action_uninstall),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PluginDetailManageButton(
                onClick = onOpenConfig,
                enabled = actionState.manageAvailability.canOpenConfig,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailOpenConfigActionTag),
                label = stringResource(R.string.plugin_action_open_config),
            )
            PluginDetailManageButton(
                onClick = onOpenLogs,
                enabled = actionState.manageAvailability.canViewLogs,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailOpenLogsActionTag),
                label = stringResource(R.string.plugin_action_view_logs),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PluginDetailManageButton(
                onClick = { pendingConfirmAction = PluginDetailConfirmAction.ClearCache },
                enabled = actionState.manageAvailability.canClearCache,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailClearCacheActionTag),
                label = stringResource(R.string.plugin_action_clear_cache),
            )
            PluginDetailManageButton(
                onClick = { pendingConfirmAction = PluginDetailConfirmAction.RestoreDefaults },
                enabled = actionState.manageAvailability.canRestoreDefaults,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailRestoreDefaultsActionTag),
                label = stringResource(R.string.plugin_action_restore_defaults),
            )
        }
    }

    pendingConfirmAction?.let { action ->
        PluginDetailConfirmationDialog(
            spec = buildPluginDetailConfirmationDialogSpec(
                action = action,
                pluginTitle = pluginTitle,
            ),
            onConfirm = {
                when (action) {
                    PluginDetailConfirmAction.Disable -> onDisable()
                    PluginDetailConfirmAction.Uninstall -> {
                        if (onUninstall()) {
                            onReturnToInstalledPlugins()
                        }
                    }
                    PluginDetailConfirmAction.ClearCache -> onClearCache()
                    PluginDetailConfirmAction.RestoreDefaults -> onRestoreDefaults()
                }
                pendingConfirmAction = null
            },
            onDismiss = {
                pendingConfirmAction = null
            },
        )
    }
}

@Composable
private fun PluginDetailManageButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MonochromeSecondaryActionButton(
        label = label,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    )
}

internal fun pluginDetailUsesUnifiedManageButtonStyle(): Boolean = true

internal enum class PluginDetailConfirmAction {
    Disable,
    Uninstall,
    ClearCache,
    RestoreDefaults,
}

internal data class PluginDetailConfirmationDialogSpec(
    val titleRes: Int,
    val messageRes: Int,
    val pluginTitle: String,
)

internal fun buildPluginDetailConfirmationDialogSpec(
    action: PluginDetailConfirmAction,
    pluginTitle: String,
): PluginDetailConfirmationDialogSpec {
    return when (action) {
        PluginDetailConfirmAction.Disable -> PluginDetailConfirmationDialogSpec(
            titleRes = R.string.plugin_action_confirm_disable_title,
            messageRes = R.string.plugin_action_confirm_disable_message,
            pluginTitle = pluginTitle,
        )
        PluginDetailConfirmAction.Uninstall -> PluginDetailConfirmationDialogSpec(
            titleRes = R.string.plugin_action_confirm_uninstall_title,
            messageRes = R.string.plugin_action_confirm_uninstall_message,
            pluginTitle = pluginTitle,
        )
        PluginDetailConfirmAction.ClearCache -> PluginDetailConfirmationDialogSpec(
            titleRes = R.string.plugin_action_confirm_clear_cache_title,
            messageRes = R.string.plugin_action_confirm_clear_cache_message,
            pluginTitle = pluginTitle,
        )
        PluginDetailConfirmAction.RestoreDefaults -> PluginDetailConfirmationDialogSpec(
            titleRes = R.string.plugin_action_confirm_restore_defaults_title,
            messageRes = R.string.plugin_action_confirm_restore_defaults_message,
            pluginTitle = pluginTitle,
        )
    }
}

internal fun pluginDetailUsesConfirmationDialogs(): Boolean = true

@Composable
private fun PluginDetailConfirmationDialog(
    spec: PluginDetailConfirmationDialogSpec,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PluginUiSpec.DetailConfirmDialogTag),
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = {
            Text(text = stringResource(spec.titleRes))
        },
        text = {
            Text(
                text = stringResource(spec.messageRes, spec.pluginTitle),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
            ) {
                Text(text = stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun PluginSectionCard(
    title: String,
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
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
private fun PluginKeyValueSection(
    title: String,
    items: List<Pair<String, String>>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardAltBackground,
        border = BorderStroke(1.dp, MonochromeUi.border.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            items.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MonochromeUi.textSecondary)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MonochromeUi.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun detailHintCard(message: String) {
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardAltBackground,
        border = BorderStroke(1.dp, MonochromeUi.border),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MonochromeUi.textSecondary,
        )
    }
}

@Composable
private fun PluginDetailFailureBanner(
    failureState: com.astrbot.android.ui.viewmodel.PluginFailureUiState,
) {
    val palette = PluginUiSpec.failureBannerPalette(failureState.isSuspended)
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = palette.containerColor,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = failureState.statusMessage.asText(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.contentColor,
            )
            Text(
                text = failureState.summaryMessage.asText(),
                style = MaterialTheme.typography.bodySmall,
                color = palette.contentColor,
            )
            Text(
                text = failureState.recoveryMessage.asText(),
                style = MaterialTheme.typography.bodySmall,
                color = palette.contentColor,
            )
        }
    }
}

@Composable
private fun assistBadge(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = "$label: $value",
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MonochromeUi.cardAltBackground,
            labelColor = MonochromeUi.textPrimary,
        ),
    )
}

@Composable
private fun PluginUpgradeDialog(
    state: com.astrbot.android.ui.viewmodel.PluginUpgradeDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val permissionDiff = state.availability.permissionDiff
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = {
            Text(
                text = if (state.isSecondaryConfirmationStep) {
                    stringResource(R.string.plugin_upgrade_confirm_secondary_title)
                } else {
                    stringResource(R.string.plugin_update_available_dialog_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.plugin_update_available_summary,
                        state.availability.installedVersion,
                        state.availability.latestVersion,
                    ),
                )
                state.availability.publishedAt?.let { publishedAt ->
                    Text(
                        text = stringResource(R.string.plugin_upgrade_published_at, publishedAt.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
                state.availability.changelogSummary.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
                if (permissionDiff.added.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.plugin_upgrade_added_permissions,
                            permissionDiff.added.joinToString { permission -> permission.title },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
                if (permissionDiff.riskUpgraded.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.plugin_upgrade_upgraded_permissions,
                            permissionDiff.riskUpgraded.joinToString { upgrade -> upgrade.to.title },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
                state.message?.let { message ->
                    Text(
                        text = message.asText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.isInstalling,
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
            ) {
                Text(
                    text = if (state.requiresSecondaryConfirmation && !state.isSecondaryConfirmationStep) {
                        stringResource(R.string.plugin_upgrade_continue)
                    } else {
                        stringResource(R.string.plugin_action_update)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isInstalling,
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun sourceTypeLabel(sourceType: PluginSourceType): String {
    return when (sourceType) {
        PluginSourceType.LOCAL_FILE -> stringResource(R.string.plugin_source_local_file)
        PluginSourceType.MANUAL_IMPORT -> stringResource(R.string.plugin_source_manual_import)
        PluginSourceType.REPOSITORY -> stringResource(R.string.plugin_source_repository)
        PluginSourceType.DIRECT_LINK -> stringResource(R.string.plugin_source_direct_link)
    }
}

@Composable
private fun compatibilityLabel(status: PluginCompatibilityStatus): String {
    return when (status) {
        PluginCompatibilityStatus.COMPATIBLE -> stringResource(R.string.plugin_compatibility_compatible)
        PluginCompatibilityStatus.INCOMPATIBLE -> stringResource(R.string.plugin_compatibility_incompatible)
        PluginCompatibilityStatus.UNKNOWN -> stringResource(R.string.plugin_compatibility_unknown)
    }
}

@Composable
private fun installStatusLabel(record: PluginInstallRecord): String {
    return if (record.enabled) {
        stringResource(R.string.common_enabled)
    } else {
        stringResource(R.string.plugin_status_installed)
    }
}

@Composable
private fun PluginActionFeedback.asText(): String {
    return when (this) {
        is PluginActionFeedback.Resource -> stringResource(resId, *formatArgs.toTypedArray())
        is PluginActionFeedback.Text -> value
    }
}

@Composable
private fun BoxWithPageBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        content()
    }
}
