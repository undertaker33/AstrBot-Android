package com.astrbot.android.ui.plugin

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.ui.navigation.AppDestination
import com.astrbot.android.ui.app.RegisterSecondaryTopBar
import com.astrbot.android.ui.app.SecondaryTopBarPlaceholder
import com.astrbot.android.ui.app.SecondaryTopBarSpec
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginViewModel

private enum class PluginTriggerManagementSection {
    Summary,
    Readiness,
    Notes,
}

internal enum class PluginTriggerManagementStatus {
    Unsupported,
    NotReady,
    InvalidContract,
}

internal data class PluginTriggerItemUiState(
    val trigger: PluginTriggerSource,
)

internal data class PluginTriggerManagementState(
    val status: PluginTriggerManagementStatus,
    val statusDetail: String = "",
    val runtimeLabel: String = "-",
    val entryPath: String = "",
    val contractVersion: String = "-",
    val canManualOpen: Boolean = false,
    val openTriggers: List<PluginTriggerItemUiState> = emptyList(),
    val closedTriggers: List<PluginTriggerItemUiState> = emptyList(),
)

@Composable
fun PluginTriggerManagementScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    pluginViewModel: PluginViewModel = astrBotViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()

    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForDetail(pluginId)
    }

    RegisterSecondaryTopBar(
        route = AppDestination.PluginTriggers.route,
        spec = SecondaryTopBarSpec.SubPage(
            title = stringResource(R.string.plugin_trigger_title),
            onBack = onBack,
        ),
    )

    val record = uiState.selectedPlugin
    val triggerState = record?.let(::buildPluginTriggerManagementState)

    BoxWithTriggerPageBackground {
        if (record != null && triggerState != null) {
            PluginTriggerManagementWorkspace(
                record = record,
                state = triggerState,
            )
        }
    }
}

@Composable
private fun PluginTriggerManagementWorkspace(
    record: PluginInstallRecord,
    state: PluginTriggerManagementState,
) {
    val sections = remember { buildTriggerManagementSections() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
            .testTag(PluginUiSpec.PluginTriggersPageTag),
        contentPadding = PaddingValues(
            top = PluginUiSpec.ScreenVerticalPadding,
            bottom = PluginUiSpec.ListContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
    ) {
        item {
            SecondaryTopBarPlaceholder()
        }

        items(sections, key = { it.name }) { section ->
            when (section) {
                PluginTriggerManagementSection.Summary -> {
                    TriggerSectionCard(title = stringResource(R.string.plugin_trigger_title)) {
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

                PluginTriggerManagementSection.Readiness -> {
                    TriggerSectionCard(title = stringResource(R.string.plugin_trigger_readiness_title)) {
                        TriggerKeyValue(
                            label = stringResource(R.string.plugin_trigger_runtime_label),
                            value = state.runtimeLabel,
                        )
                        TriggerKeyValue(
                            label = stringResource(R.string.plugin_trigger_binding_status_label),
                            value = triggerStatusLabel(state.status),
                        )
                        TriggerKeyValue(
                            label = stringResource(R.string.plugin_trigger_contract_version_label),
                            value = state.contractVersion,
                        )
                        TriggerKeyValue(
                            label = stringResource(R.string.plugin_trigger_entry_path_label),
                            value = state.entryPath.ifBlank { stringResource(R.string.plugin_trigger_not_available) },
                        )
                        if (state.statusDetail.isNotBlank()) {
                            Text(
                                text = state.statusDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                    }
                }

                PluginTriggerManagementSection.Notes -> {
                    TriggerSectionCard(title = stringResource(R.string.plugin_trigger_notes_title)) {
                        Text(
                            text = state.statusDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.plugin_trigger_notes_dispatcher),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

internal fun buildPluginTriggerManagementState(
    record: PluginInstallRecord,
): PluginTriggerManagementState {
    val packageContract = record.packageContractSnapshot
    val runtimeKind = packageContract?.runtime?.kind?.takeIf { it.isNotBlank() }
    val bootstrapPath = packageContract?.runtime?.bootstrap?.takeIf { it.isNotBlank() }
    val status = when {
        record.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE -> {
            PluginTriggerManagementStatus.Unsupported
        }

        packageContract == null ||
            runtimeKind == null ||
            bootstrapPath == null -> {
            PluginTriggerManagementStatus.InvalidContract
        }

        else -> PluginTriggerManagementStatus.NotReady
    }
    val statusDetail = when (status) {
        PluginTriggerManagementStatus.Unsupported -> {
            record.compatibilityState.notes.takeIf { it.isNotBlank() }
                ?: "Legacy v1 trigger contracts are unsupported in phase 1."
        }

        PluginTriggerManagementStatus.InvalidContract -> {
            "This plugin is missing a valid protocol v2 package contract snapshot."
        }

        PluginTriggerManagementStatus.NotReady -> {
            "This legacy trigger page is not ready for protocol v2 runtime execution in phase 1."
        }
    }
    val runtimeLabel = when (status) {
        PluginTriggerManagementStatus.NotReady -> pluginRuntimeDisplayLabel(runtimeKind)
        PluginTriggerManagementStatus.Unsupported,
        PluginTriggerManagementStatus.InvalidContract,
        -> "-"
    }
    val entryPath = when (status) {
        PluginTriggerManagementStatus.NotReady -> bootstrapPath ?: "-"
        PluginTriggerManagementStatus.Unsupported,
        PluginTriggerManagementStatus.InvalidContract,
        -> "-"
    }
    val contractVersion = when (status) {
        PluginTriggerManagementStatus.NotReady -> packageContract?.protocolVersion?.toString() ?: "-"
        PluginTriggerManagementStatus.Unsupported,
        PluginTriggerManagementStatus.InvalidContract,
        -> "-"
    }
    return PluginTriggerManagementState(
        status = status,
        statusDetail = statusDetail,
        runtimeLabel = runtimeLabel,
        entryPath = entryPath,
        contractVersion = contractVersion,
        canManualOpen = false,
        openTriggers = emptyList(),
        closedTriggers = emptyList(),
    )
}

internal fun pluginRuntimeDisplayLabel(runtimeKind: String?): String {
    return when (runtimeKind) {
        "js_quickjs" -> "JavaScript (QuickJS)"
        null, "" -> "-"
        else -> runtimeKind
    }
}

private fun buildTriggerManagementSections(
): List<PluginTriggerManagementSection> {
    return buildList {
        add(PluginTriggerManagementSection.Summary)
        add(PluginTriggerManagementSection.Readiness)
        add(PluginTriggerManagementSection.Notes)
    }
}

@Composable
private fun TriggerSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
private fun TriggerKeyValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MonochromeUi.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textPrimary,
        )
    }
}

@Composable
private fun triggerStatusLabel(status: PluginTriggerManagementStatus): String {
    return when (status) {
        PluginTriggerManagementStatus.Unsupported -> "Unsupported"
        PluginTriggerManagementStatus.NotReady -> "Not ready"
        PluginTriggerManagementStatus.InvalidContract -> stringResource(R.string.plugin_trigger_binding_invalid_contract)
    }
}

@Composable
private fun BoxWithTriggerPageBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        content()
    }
}
