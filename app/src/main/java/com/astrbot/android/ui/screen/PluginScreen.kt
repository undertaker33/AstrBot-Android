package com.astrbot.android.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.screen.plugin.PluginBadgePalette
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginDetailActionState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginSummaryMetrics
import com.astrbot.android.ui.viewmodel.PluginViewModel

@Composable
fun PluginScreen(
    pluginViewModel: PluginViewModel = astrBotViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        AnimatedContent(
            targetState = uiState.isShowingDetail,
            transitionSpec = { PluginUiSpec.detailTransition(targetState) },
            modifier = Modifier.fillMaxSize(),
            label = "plugin-workspace",
        ) { isShowingDetail ->
            if (isShowingDetail && uiState.selectedPlugin != null) {
                PluginDetailWorkspace(
                    uiState = uiState,
                    onBack = pluginViewModel::showList,
                    onEnable = pluginViewModel::enableSelectedPlugin,
                    onDisable = pluginViewModel::disableSelectedPlugin,
                    onSelectPolicy = pluginViewModel::updateSelectedUninstallPolicy,
                    onUninstall = pluginViewModel::uninstallSelectedPlugin,
                )
            } else {
                PluginListWorkspace(
                    uiState = uiState,
                    onSelectPlugin = pluginViewModel::selectPlugin,
                )
            }
        }
    }
}

@Composable
private fun PluginListWorkspace(
    uiState: PluginScreenUiState,
    onSelectPlugin: (String) -> Unit,
) {
    if (uiState.records.isEmpty()) {
        PluginEmptyState()
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
            .testTag(PluginUiSpec.PluginListTag),
        contentPadding = PaddingValues(
            top = PluginUiSpec.ScreenVerticalPadding,
            bottom = PluginUiSpec.ListContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
    ) {
        item {
            uiState.detailActionState.lastActionMessage?.let { message ->
                PluginActionFeedbackCard(message = message)
            }
        }
        item {
            PluginSummaryCard(metrics = uiState.summaryMetrics)
        }
        items(uiState.records, key = { it.pluginId }) { record ->
            PluginRecordCard(
                record = record,
                selected = uiState.selectedPluginId == record.pluginId,
                onClick = { onSelectPlugin(record.pluginId) },
            )
        }
    }
}

@Composable
private fun PluginDetailWorkspace(
    uiState: PluginScreenUiState,
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onSelectPolicy: (PluginUninstallPolicy) -> Unit,
    onUninstall: () -> Unit,
) {
    val record = uiState.selectedPlugin ?: return
    val actionState = uiState.detailActionState

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
            .testTag(PluginUiSpec.DetailPanelTag),
        contentPadding = PaddingValues(
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
        item { PluginDetailHero(record) }
        item {
            PluginDetailSection(
                title = stringResource(R.string.plugin_detail_overview_title),
                body = record.manifestSnapshot.description,
            )
        }
        item {
            PluginKeyValueSection(
                title = stringResource(R.string.plugin_detail_meta_title),
                items = listOf(
                    stringResource(R.string.plugin_field_author) to record.manifestSnapshot.author,
                    stringResource(R.string.plugin_field_protocol) to record.manifestSnapshot.protocolVersion.toString(),
                    stringResource(R.string.plugin_field_min_host) to record.manifestSnapshot.minHostVersion,
                    stringResource(R.string.plugin_field_max_host) to record.manifestSnapshot.maxHostVersion.ifBlank {
                        stringResource(R.string.plugin_value_not_limited)
                    },
                    stringResource(R.string.plugin_field_source_location) to record.source.location,
                ),
            )
        }
        item { PluginCompatibilitySection(record) }
        item { PluginPermissionsSection(record) }
        item {
            PluginActionSection(
                actionState = actionState,
                onEnable = onEnable,
                onDisable = onDisable,
                onSelectPolicy = onSelectPolicy,
                onUninstall = onUninstall,
            )
        }
    }
}

@Composable
private fun PluginSummaryCard(metrics: PluginSummaryMetrics) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.SummaryCardTag),
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
                text = stringResource(R.string.plugin_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = stringResource(R.string.plugin_summary_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PluginUiSpec.CardSpacing),
            ) {
                PluginMetricCard(Modifier.weight(1f), stringResource(R.string.plugin_metric_installed), metrics.totalInstalled.toString())
                PluginMetricCard(Modifier.weight(1f), stringResource(R.string.plugin_metric_attention), metrics.highRisk.toString())
                PluginMetricCard(Modifier.weight(1f), stringResource(R.string.plugin_metric_incompatible), metrics.incompatible.toString())
            }
        }
    }
}

@Composable
private fun PluginMetricCard(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardAltBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MonochromeUi.textPrimary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MonochromeUi.textSecondary)
        }
    }
}

@Composable
private fun PluginRecordCard(
    record: PluginInstallRecord,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.pluginCardTag(record.pluginId))
            .clickable(onClick = onClick),
        shape = PluginUiSpec.SectionShape,
        color = if (selected) MonochromeUi.cardAltBackground else MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
        border = if (selected) BorderStroke(1.5.dp, MonochromeUi.fabBackground.copy(alpha = 0.5f)) else PluginUiSpec.CardBorder,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MonochromeUi.mutedSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = MonochromeUi.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = record.manifestSnapshot.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MonochromeUi.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.plugin_card_subtitle, record.installedVersion, sourceTypeLabel(record.source.sourceType), installStatusLabel(record)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PluginBadge(riskLabel(record.manifestSnapshot.riskLevel), PluginUiSpec.riskBadgePalette(record.manifestSnapshot.riskLevel))
                    PluginBadge(compatibilityLabel(record.compatibilityState.status), PluginUiSpec.compatibilityBadgePalette(record.compatibilityState.status))
                }
            }
        }
    }
}

@Composable
private fun PluginDetailHero(record: PluginInstallRecord) {
    Surface(
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
                text = stringResource(R.string.plugin_detail_subtitle, record.installedVersion, sourceTypeLabel(record.source.sourceType), installStatusLabel(record)),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PluginBadge(riskLabel(record.manifestSnapshot.riskLevel), PluginUiSpec.riskBadgePalette(record.manifestSnapshot.riskLevel))
                PluginBadge(compatibilityLabel(record.compatibilityState.status), PluginUiSpec.compatibilityBadgePalette(record.compatibilityState.status))
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
private fun PluginDetailSection(title: String, body: String) {
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MonochromeUi.textSecondary)
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
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
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
private fun PluginCompatibilitySection(record: PluginInstallRecord) {
    PluginKeyValueSection(
        title = stringResource(R.string.plugin_detail_compatibility_title),
        items = listOf(
            stringResource(R.string.plugin_field_compatibility_status) to compatibilityLabel(record.compatibilityState.status),
            stringResource(R.string.plugin_field_compatibility_notes) to record.compatibilityState.notes.ifBlank {
                stringResource(R.string.plugin_value_no_notes)
            },
        ),
    )
}

@Composable
private fun PluginPermissionsSection(record: PluginInstallRecord) {
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_detail_permissions_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            record.permissionSnapshot.forEach { permission ->
                Surface(
                    shape = PluginUiSpec.SectionShape,
                    color = MonochromeUi.cardAltBackground,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = permission.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MonochromeUi.textPrimary,
                            )
                            PluginBadge(riskLabel(permission.riskLevel), PluginUiSpec.riskBadgePalette(permission.riskLevel))
                        }
                        Text(permission.description, style = MaterialTheme.typography.bodySmall, color = MonochromeUi.textSecondary)
                        Text(
                            text = if (permission.required) stringResource(R.string.plugin_permission_required) else stringResource(R.string.plugin_permission_optional),
                            style = MaterialTheme.typography.labelMedium,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginActionSection(
    actionState: PluginDetailActionState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onSelectPolicy: (PluginUninstallPolicy) -> Unit,
    onUninstall: () -> Unit,
) {
    val incompatiblePalette = PluginUiSpec.compatibilityBadgePalette(PluginCompatibilityStatus.INCOMPATIBLE)
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_detail_actions_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            actionState.enableBlockedReason?.let { blockedReason ->
                Surface(
                    shape = PluginUiSpec.SectionShape,
                    color = incompatiblePalette.containerColor,
                ) {
                    Text(
                        text = blockedReason.asText(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = incompatiblePalette.contentColor,
                    )
                }
            }
            Text(
                text = stringResource(R.string.plugin_field_compatibility_notes),
                style = MaterialTheme.typography.labelMedium,
                color = MonochromeUi.textSecondary,
            )
            Text(
                text = actionState.compatibilityNotes.ifBlank {
                    stringResource(R.string.plugin_value_no_notes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
            Text(
                text = stringResource(R.string.plugin_action_uninstall_policy_title),
                style = MaterialTheme.typography.labelMedium,
                color = MonochromeUi.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PolicyToggleButton(
                    label = stringResource(R.string.plugin_action_uninstall_policy_keep_data),
                    selected = actionState.uninstallPolicy == PluginUninstallPolicy.KEEP_DATA,
                    tag = PluginUiSpec.DetailKeepDataPolicyTag,
                    onClick = { onSelectPolicy(PluginUninstallPolicy.KEEP_DATA) },
                )
                PolicyToggleButton(
                    label = stringResource(R.string.plugin_action_uninstall_policy_remove_data),
                    selected = actionState.uninstallPolicy == PluginUninstallPolicy.REMOVE_DATA,
                    tag = PluginUiSpec.DetailRemoveDataPolicyTag,
                    onClick = { onSelectPolicy(PluginUninstallPolicy.REMOVE_DATA) },
                )
            }
            actionState.lastActionMessage?.let { message ->
                PluginActionFeedbackCard(message = message)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onEnable,
                    enabled = actionState.isEnableActionEnabled,
                    modifier = Modifier.testTag(PluginUiSpec.DetailEnableActionTag),
                ) {
                    Text(stringResource(R.string.plugin_action_enable))
                }
                OutlinedButton(
                    onClick = onDisable,
                    enabled = actionState.isDisableActionEnabled,
                    modifier = Modifier.testTag(PluginUiSpec.DetailDisableActionTag),
                ) {
                    Text(stringResource(R.string.plugin_action_disable))
                }
                OutlinedButton(
                    onClick = onUninstall,
                    modifier = Modifier.testTag(PluginUiSpec.DetailUninstallActionTag),
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
}

@Composable
private fun PolicyToggleButton(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        border = if (selected) BorderStroke(1.5.dp, MonochromeUi.textPrimary) else PluginUiSpec.CardBorder,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MonochromeUi.cardAltBackground else MonochromeUi.cardBackground,
            contentColor = MonochromeUi.textPrimary,
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun PluginActionFeedbackCard(message: PluginActionFeedback) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.DetailActionMessageTag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardAltBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Text(
            text = message.asText(),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textPrimary,
        )
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
private fun PluginBadge(
    label: String,
    palette: PluginBadgePalette,
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text = label, color = palette.contentColor) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = palette.containerColor,
            disabledLabelColor = palette.contentColor,
            disabledLeadingIconContentColor = palette.contentColor,
        ),
        border = null,
    )
}

@Composable
private fun PluginEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(PluginUiSpec.ScreenHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = PluginUiSpec.EmptyStateShape,
            color = PluginUiSpec.EmptyStateContainerColor,
            border = PluginUiSpec.CardBorder,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(PluginUiSpec.EmptyStateAccentColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = MonochromeUi.textPrimary)
                }
                Text(
                    text = stringResource(R.string.plugin_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MonochromeUi.textPrimary,
                )
                Text(
                    text = stringResource(R.string.plugin_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PluginUiSpec.EmptyStateBodyColor,
                )
            }
        }
    }
}

@Composable
private fun sourceTypeLabel(sourceType: PluginSourceType): String {
    return when (sourceType) {
        PluginSourceType.LOCAL_FILE -> stringResource(R.string.plugin_source_local_file)
        PluginSourceType.MANUAL_IMPORT -> stringResource(R.string.plugin_source_manual_import)
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
private fun riskLabel(level: PluginRiskLevel): String {
    return when (level) {
        PluginRiskLevel.LOW -> stringResource(R.string.plugin_risk_low)
        PluginRiskLevel.MEDIUM -> stringResource(R.string.plugin_risk_medium)
        PluginRiskLevel.HIGH -> stringResource(R.string.plugin_risk_high)
        PluginRiskLevel.CRITICAL -> stringResource(R.string.plugin_risk_critical)
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
