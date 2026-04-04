package com.astrbot.android.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginDetailActionState
import com.astrbot.android.ui.viewmodel.PluginDetailMetadataState
import com.astrbot.android.ui.viewmodel.PluginFailureUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PluginDetailScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenConfig: (String) -> Unit,
    pluginViewModel: PluginViewModel = astrBotViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()
    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForDetail(pluginId)
    }

    BoxWithPageBackground {
        if (uiState.selectedPlugin != null) {
            PluginDetailRouteWorkspace(
                uiState = uiState,
                onBack = onBack,
                onOpenWorkspace = onOpenWorkspace,
                onOpenConfig = onOpenConfig,
                onEnable = pluginViewModel::enableSelectedPlugin,
                onDisable = pluginViewModel::disableSelectedPlugin,
                onRequestUpgrade = pluginViewModel::requestUpgradeForSelectedPlugin,
                onSelectPolicy = pluginViewModel::updateSelectedUninstallPolicy,
                onUninstall = pluginViewModel::uninstallSelectedPlugin,
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
    onBack: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenConfig: (String) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRequestUpgrade: () -> Unit,
    onSelectPolicy: (PluginUninstallPolicy) -> Unit,
    onUninstall: () -> Unit,
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
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(PluginUiSpec.DetailBackActionTag),
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

        items(buildPluginDetailSections(uiState), key = { it.name }) { section ->
            when (section) {
                PluginDetailSection.TopSummary -> PluginDetailTopSummarySection(record)
                PluginDetailSection.PrimaryActions -> PluginDetailPrimaryActionsSection(
                    record = record,
                    actionState = uiState.detailActionState,
                    onOpenWorkspace = { onOpenWorkspace(record.pluginId) },
                    onOpenConfig = { onOpenConfig(record.pluginId) },
                    onEnable = onEnable,
                    onDisable = onDisable,
                    onRequestUpgrade = onRequestUpgrade,
                    onSelectPolicy = onSelectPolicy,
                    onUninstall = onUninstall,
                )
                PluginDetailSection.Overview -> PluginDetailOverviewSection(record, uiState.detailMetadataState)
                PluginDetailSection.SafetyCompatibility -> PluginDetailSafetyCompatibilitySection(
                    record = record,
                    actionState = uiState.detailActionState,
                    metadata = uiState.detailMetadataState,
                )
                PluginDetailSection.TechnicalMetadata -> PluginDetailTechnicalMetadataSection(record, uiState.detailMetadataState)
            }
        }
    }
}

@Composable
private fun PluginDetailTopSummarySection(record: PluginInstallRecord) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.DetailTopSummaryTag),
        shape = PluginUiSpec.SummaryShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                assistBadge(stringResource(R.string.plugin_field_author), record.manifestSnapshot.author)
                assistBadge(stringResource(R.string.plugin_field_source_label), sourceTypeLabel(record.source.sourceType))
                assistBadge(stringResource(R.string.plugin_field_compatibility_status), compatibilityLabel(record.compatibilityState.status))
                assistBadge(stringResource(R.string.plugin_field_install_status), installStatusLabel(record))
            }
            Text(
                text = record.manifestSnapshot.entrySummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textPrimary,
            )
        }
    }
}

@Composable
private fun PluginDetailPrimaryActionsSection(
    record: PluginInstallRecord,
    actionState: PluginDetailActionState,
    onOpenWorkspace: () -> Unit,
    onOpenConfig: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRequestUpgrade: () -> Unit,
    onSelectPolicy: (PluginUninstallPolicy) -> Unit,
    onUninstall: () -> Unit,
) {
    PluginSectionCard(
        title = stringResource(R.string.plugin_detail_actions_title),
        tag = PluginUiSpec.DetailPrimaryActionsTag,
    ) {
        actionState.enableBlockedReason?.let { blockedReason ->
            detailHintCard(blockedReason.asText())
        }
        actionState.updateBlockedReason?.let { blockedReason ->
            detailHintCard(blockedReason.asText())
        }
        actionState.lastActionMessage?.let { message ->
            detailHintCard(message.asText())
        }
        actionState.failureState?.let { failureState ->
            PluginFailureBanner(failureState)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEnable,
                enabled = actionState.isEnableActionEnabled,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailEnableActionTag),
            ) { Text(stringResource(R.string.plugin_action_enable)) }
            OutlinedButton(
                onClick = onDisable,
                enabled = actionState.isDisableActionEnabled,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailDisableActionTag),
            ) { Text(stringResource(R.string.plugin_action_disable)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRequestUpgrade,
                enabled = actionState.isUpgradeActionEnabled,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailUpgradeActionTag),
            ) { Text(stringResource(R.string.plugin_action_update)) }
            OutlinedButton(
                onClick = onUninstall,
                modifier = Modifier.weight(1f).testTag(PluginUiSpec.DetailUninstallActionTag),
            ) { Text(stringResource(R.string.plugin_action_uninstall)) }
        }
        OutlinedButton(
            onClick = onOpenWorkspace,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PluginUiSpec.DetailOpenWorkspaceActionTag),
        ) {
            Text(stringResource(R.string.plugin_action_open_workspace))
        }
        OutlinedButton(
            onClick = onOpenConfig,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PluginUiSpec.DetailOpenConfigActionTag),
        ) {
            Text(stringResource(R.string.plugin_action_open_config))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = actionState.uninstallPolicy == PluginUninstallPolicy.KEEP_DATA,
                onClick = { onSelectPolicy(PluginUninstallPolicy.KEEP_DATA) },
                label = { Text(stringResource(R.string.plugin_action_uninstall_policy_keep_data)) },
                modifier = Modifier.testTag(PluginUiSpec.DetailKeepDataPolicyTag),
                colors = FilterChipDefaults.filterChipColors(),
            )
            FilterChip(
                selected = actionState.uninstallPolicy == PluginUninstallPolicy.REMOVE_DATA,
                onClick = { onSelectPolicy(PluginUninstallPolicy.REMOVE_DATA) },
                label = { Text(stringResource(R.string.plugin_action_uninstall_policy_remove_data)) },
                modifier = Modifier.testTag(PluginUiSpec.DetailRemoveDataPolicyTag),
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun PluginDetailOverviewSection(record: PluginInstallRecord, metadata: PluginDetailMetadataState) {
    PluginSectionCard(
        title = stringResource(R.string.plugin_detail_overview_title),
        tag = PluginUiSpec.DetailOverviewTag,
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
    }
}

@Composable
private fun PluginDetailSafetyCompatibilitySection(
    record: PluginInstallRecord,
    actionState: PluginDetailActionState,
    metadata: PluginDetailMetadataState,
) {
    PluginSectionCard(
        title = stringResource(R.string.plugin_detail_safety_compatibility_title),
        tag = PluginUiSpec.DetailSafetyCompatibilityTag,
    ) {
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
                listOf(stringResource(R.string.plugin_detail_permissions_title) to stringResource(R.string.plugin_value_not_available))
            } else {
                record.permissionSnapshot.map { permission ->
                    permission.title to buildString {
                        append(if (permission.required) stringResource(R.string.plugin_permission_required) else stringResource(R.string.plugin_permission_optional))
                        append(" | ")
                        append(permission.description)
                    }
                }
            },
        )
        metadata.governanceState?.let { governanceState ->
            PluginKeyValueSection(
                title = stringResource(R.string.plugin_detail_governance_group_title),
                items = buildGovernanceDisplayItems(governanceState).map { item ->
                    stringResource(item.labelRes) to stringResource(item.valueRes)
                },
            )
        }
        Text(
            text = stringResource(R.string.plugin_detail_runtime_health_group_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MonochromeUi.textPrimary,
        )
        if (record.failureState.hasFailures) {
            actionState.failureState?.let { PluginFailureBanner(it) }
        } else {
            detailHintCard(stringResource(R.string.plugin_detail_runtime_health_clear))
        }
    }
}

@Composable
private fun PluginDetailTechnicalMetadataSection(
    record: PluginInstallRecord,
    metadata: PluginDetailMetadataState,
) {
    PluginSectionCard(
        title = stringResource(R.string.plugin_detail_technical_metadata_title),
        tag = PluginUiSpec.DetailTechnicalMetadataTag,
    ) {
        PluginKeyValueSection(
            title = stringResource(R.string.plugin_detail_meta_title),
            items = listOf(
                stringResource(R.string.plugin_field_protocol) to record.manifestSnapshot.protocolVersion.toString(),
                stringResource(R.string.plugin_field_min_host) to record.manifestSnapshot.minHostVersion,
                stringResource(R.string.plugin_field_max_host) to record.manifestSnapshot.maxHostVersion.ifBlank {
                    stringResource(R.string.plugin_value_not_limited)
                },
                stringResource(R.string.plugin_field_author) to record.manifestSnapshot.author,
                stringResource(R.string.plugin_field_source_label) to (metadata.sourceBadge?.label ?: sourceTypeLabel(record.source.sourceType)),
                stringResource(R.string.plugin_field_source_location) to record.source.location.ifBlank {
                    stringResource(R.string.plugin_value_not_available)
                },
                stringResource(R.string.plugin_field_last_updated) to formatPluginTimestamp(record.lastUpdatedAt),
                stringResource(R.string.plugin_field_last_sync) to (metadata.lastSyncAtEpochMillis?.let(::formatPluginTimestamp)
                    ?: stringResource(R.string.plugin_value_not_synced)),
                stringResource(R.string.plugin_field_version_history) to metadata.versionHistorySummary.ifBlank {
                    stringResource(R.string.plugin_value_not_available)
                },
                stringResource(R.string.plugin_field_changelog_summary) to metadata.changelogSummary.ifBlank {
                    stringResource(R.string.plugin_value_no_changelog)
                },
            ),
        )
    }
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
private fun PluginFailureBanner(failureState: PluginFailureUiState) {
    val palette = PluginUiSpec.failureBannerPalette(failureState.isSuspended)
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = palette.containerColor,
        border = BorderStroke(1.dp, palette.contentColor.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = failureState.statusMessage.asText(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plugin_upgrade_confirm_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.plugin_update_available_summary,
                    state.availability.installedVersion,
                    state.availability.latestVersion,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.plugin_action_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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

private fun formatPluginTimestamp(epochMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

@Composable
private fun PluginActionFeedback.asText(): String {
    return when (this) {
        is PluginActionFeedback.Resource -> stringResource(resId, *formatArgs.toTypedArray())
        is PluginActionFeedback.Text -> value
    }
}

@Composable
private fun PluginActionFeedback?.orEmptyText(): String {
    return this?.asText().orEmpty()
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
