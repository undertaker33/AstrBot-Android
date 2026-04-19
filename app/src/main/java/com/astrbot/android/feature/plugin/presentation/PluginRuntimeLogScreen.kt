package com.astrbot.android.ui.plugin
import com.astrbot.android.ui.common.MonochromePrimaryActionButton
import com.astrbot.android.ui.common.MonochromeSecondaryActionButton
import com.astrbot.android.ui.common.SubPageScaffold
import com.astrbot.android.ui.common.TimedCleanupDialog
import com.astrbot.android.ui.common.formatTimedCleanupInterval

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBusProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogCleanupRepository
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogCleanupSettings
import com.astrbot.android.ui.navigation.AppDestination
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PluginRuntimeLogScreenRoute(
    pluginId: String,
    onBack: () -> Unit,
    pluginViewModel: PluginViewModel = hiltViewModel(),
) {
    val uiState by pluginViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val logBus = remember { PluginRuntimeLogBusProvider.bus() }
    val logRecords by logBus.records.collectAsState()
    val cleanupSettingsByPluginId by PluginRuntimeLogCleanupRepository.settings.collectAsState()
    val cleanupSettings = cleanupSettingsByPluginId[pluginId] ?: PluginRuntimeLogCleanupSettings()
    val pluginTitle = uiState.selectedPlugin?.manifestSnapshot?.title ?: pluginId
    val pluginRecords = remember(logRecords, pluginId) {
        logRecords.filter { record -> record.pluginId == pluginId }.reversed()
    }
    val displayText = buildPluginRuntimeLogText(pluginRecords)
        .ifBlank { context.getString(R.string.plugin_logs_empty) }
    var showCleanupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        PluginRuntimeLogCleanupRepository.initialize(context.applicationContext)
    }
    LaunchedEffect(pluginId) {
        pluginViewModel.selectPluginForDetail(pluginId)
    }
    LaunchedEffect(
        pluginId,
        cleanupSettings.enabled,
        cleanupSettings.intervalHours,
        cleanupSettings.intervalMinutes,
        cleanupSettings.lastCleanupAtEpochMillis,
    ) {
        PluginRuntimeLogCleanupRepository.maybeAutoClear(pluginId) {
            logBus.clearPlugin(pluginId)
        }
    }

    SubPageScaffold(
        route = AppDestination.PluginLogs.route,
        title = stringResource(R.string.plugin_logs_title),
        onBack = onBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MonochromeUi.pageBackground)
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
                .testTag(PluginUiSpec.PluginLogsPageTag),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = pluginTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MonochromeUi.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonochromePrimaryActionButton(
                    label = stringResource(R.string.plugin_logs_copy_all),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(displayText))
                        Toast.makeText(
                            context,
                            context.getString(R.string.plugin_logs_copy_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    enabled = pluginRecords.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PluginUiSpec.PluginLogsCopyActionTag),
                )
                MonochromeSecondaryActionButton(
                    label = stringResource(R.string.plugin_logs_clear),
                    onClick = {
                        logBus.clearPlugin(pluginId)
                        PluginRuntimeLogCleanupRepository.recordCleanup(pluginId)
                        Toast.makeText(
                            context,
                            context.getString(R.string.plugin_logs_clear_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    enabled = pluginRecords.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PluginUiSpec.PluginLogsClearActionTag),
                )
                MonochromeSecondaryActionButton(
                    label = stringResource(R.string.plugin_logs_auto_clear_action),
                    onClick = { showCleanupDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PluginUiSpec.PluginLogsCleanupActionTag),
                )
            }
            Text(
                text = stringResource(
                    R.string.plugin_logs_auto_clear_summary,
                    formatTimedCleanupInterval(
                        enabled = cleanupSettings.enabled,
                        intervalHours = cleanupSettings.intervalHours,
                        intervalMinutes = cleanupSettings.intervalMinutes,
                    ),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MonochromeUi.textSecondary,
            )
            RuntimeLogPanel(
                title = stringResource(R.string.plugin_logs_panel_title),
                content = displayText,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showCleanupDialog) {
        TimedCleanupDialog(
            enabled = cleanupSettings.enabled,
            intervalHours = cleanupSettings.intervalHours,
            intervalMinutes = cleanupSettings.intervalMinutes,
            titleResId = R.string.plugin_logs_cleanup_dialog_title,
            hoursResId = R.string.plugin_logs_cleanup_hours,
            minutesResId = R.string.plugin_logs_cleanup_minutes,
            enableResId = R.string.plugin_logs_cleanup_enable,
            invalidIntervalResId = R.string.plugin_logs_cleanup_interval_invalid,
            dialogTag = PluginUiSpec.PluginLogsCleanupDialogTag,
            onDismiss = { showCleanupDialog = false },
            onConfirm = { enabled, hours, minutes ->
                PluginRuntimeLogCleanupRepository.updateSettings(
                    pluginId = pluginId,
                    enabled = enabled,
                    intervalHours = hours,
                    intervalMinutes = minutes,
                )
                showCleanupDialog = false
            },
        )
    }
}

internal fun pluginRuntimeLogUsesUnifiedActionButtons(): Boolean = true

internal fun buildPluginRuntimeLogText(
    records: List<com.astrbot.android.model.plugin.PluginRuntimeLogRecord>,
): String {
    if (records.isEmpty()) return ""
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    return records.joinToString(separator = "\n\n") { record ->
        val structuredFields = buildList {
            add("plugin=${record.pluginId}")
            if (record.pluginVersion.isNotBlank()) {
                add("version=${record.pluginVersion}")
            }
            if (record.runtimeSessionId.isNotBlank()) {
                add("session=${record.runtimeSessionId}")
            }
            if (record.requestId.isNotBlank()) {
                add("request=${record.requestId}")
            }
            if (record.stage.isNotBlank()) {
                add("stage=${record.stage}")
            }
            if (record.handlerName.isNotBlank()) {
                add("handler=${record.handlerName}")
            }
            if (record.toolId.isNotBlank()) {
                add("tool=${record.toolId}")
            }
            if (record.toolCallId.isNotBlank()) {
                add("toolCall=${record.toolCallId}")
            }
            record.hostAction?.let { hostAction ->
                add("hostAction=${hostAction.wireValue}")
            }
            record.succeeded?.let { succeeded ->
                add("succeeded=$succeeded")
            }
            if (record.outcome.isNotBlank()) {
                add("outcome=${record.outcome}")
            }
            record.durationMillis?.let { duration ->
                add("durationMs=$duration")
            }
        }
        val consumedMetadataKeys = setOf(
            "runtimeSessionId",
            "sessionInstanceId",
            "requestId",
            "stage",
            "handlerName",
            "handlerId",
            "toolId",
            "toolCallId",
            "outcome",
        )
        buildString {
            append("[")
            append(formatter.format(Date(record.occurredAtEpochMillis)))
            append("] ")
            append(record.level.wireValue.uppercase(Locale.US))
            append(" ")
            append(record.category.wireValue)
            append(" ")
            append(record.code)
            appendLine()
            append(structuredFields.joinToString(separator = " "))
            appendLine()
            append(record.message.ifBlank { "-" })
            val remainingMetadata = record.metadata
                .filterKeys { key -> key !in consumedMetadataKeys }
            if (remainingMetadata.isNotEmpty()) {
                appendLine()
                append(
                    remainingMetadata.entries
                        .sortedBy { it.key }
                        .joinToString(separator = " ") { (key, value) -> "$key=$value" },
                )
            }
        }.trim()
    }
}

@Composable
internal fun RuntimeLogPanel(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        color = MonochromeUi.cardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MonochromeUi.textPrimary,
            )
            SelectionContainer {
                Text(
                    text = content,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MonochromeUi.inputBackground, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .verticalScroll(scrollState),
                    color = MonochromeUi.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
