package com.astrbot.android.ui.settings
import com.astrbot.android.ui.common.MonochromePrimaryActionButton
import com.astrbot.android.ui.common.MonochromeSecondaryActionButton
import com.astrbot.android.ui.common.TimedCleanupDialog
import com.astrbot.android.ui.common.formatTimedCleanupInterval
import com.astrbot.android.ui.plugin.RuntimeLogPanel

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.astrbot.android.core.common.logging.RuntimeLogCleanupRepository
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.viewmodel.ChatViewModel
import com.astrbot.android.ui.viewmodel.ConversationViewModel

@Composable
fun LogScreen(
    conversationViewModel: ConversationViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel(),
    showContext: Boolean,
) {
    val logs by RuntimeLogRepository.logs.collectAsState()
    val cleanupSettings by RuntimeLogCleanupRepository.settings.collectAsState()
    val sessions by conversationViewModel.sessions.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val joinedLogs = remember(logs) { logs.joinToString(separator = "\n") }
    var showCleanupDialog by remember { mutableStateOf(false) }
    val contextPreview = if (sessions.isEmpty()) "" else conversationViewModel.contextPreview(uiState.selectedSessionId)
    val displayTitle = if (showContext) {
        stringResource(R.string.log_screen_context_title)
    } else {
        stringResource(R.string.log_screen_logs_title)
    }
    val displayText = if (showContext) {
        contextPreview.ifBlank { context.getString(R.string.log_screen_context_empty) }
    } else {
        joinedLogs.ifBlank { context.getString(R.string.log_screen_logs_empty) }
    }

    LaunchedEffect(context) {
        RuntimeLogCleanupRepository.initialize(context.applicationContext)
    }
    LaunchedEffect(
        showContext,
        cleanupSettings.enabled,
        cleanupSettings.intervalHours,
        cleanupSettings.intervalMinutes,
        cleanupSettings.lastCleanupAtEpochMillis,
    ) {
        if (!showContext) {
            RuntimeLogCleanupRepository.maybeAutoClear {
                RuntimeLogRepository.clear()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                        if (showContext) {
                            context.getString(R.string.log_screen_context_copy_success)
                        } else {
                            context.getString(R.string.log_screen_logs_copy_success)
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                enabled = displayText.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            MonochromeSecondaryActionButton(
                label = stringResource(R.string.plugin_logs_clear),
                onClick = {
                    if (showContext) {
                        conversationViewModel.replaceMessages(uiState.selectedSessionId, emptyList())
                        Toast.makeText(
                            context,
                            context.getString(R.string.log_screen_context_clear_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        RuntimeLogRepository.clear()
                        RuntimeLogCleanupRepository.recordCleanup()
                        Toast.makeText(
                            context,
                            context.getString(R.string.log_screen_logs_clear_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                enabled = if (showContext) contextPreview.isNotBlank() else logs.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
            if (!showContext) {
                MonochromeSecondaryActionButton(
                    label = stringResource(R.string.plugin_logs_auto_clear_action),
                    onClick = { showCleanupDialog = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!showContext) {
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
        }
        RuntimeLogPanel(
            title = displayTitle,
            content = displayText,
            modifier = Modifier.weight(1f),
        )
    }

    if (showCleanupDialog && !showContext) {
        TimedCleanupDialog(
            enabled = cleanupSettings.enabled,
            intervalHours = cleanupSettings.intervalHours,
            intervalMinutes = cleanupSettings.intervalMinutes,
            titleResId = R.string.plugin_logs_cleanup_dialog_title,
            hoursResId = R.string.plugin_logs_cleanup_hours,
            minutesResId = R.string.plugin_logs_cleanup_minutes,
            enableResId = R.string.plugin_logs_cleanup_enable,
            invalidIntervalResId = R.string.plugin_logs_cleanup_interval_invalid,
            dialogTag = "runtime-log-cleanup-dialog",
            onDismiss = { showCleanupDialog = false },
            onConfirm = { enabled, hours, minutes ->
                RuntimeLogCleanupRepository.updateSettings(
                    enabled = enabled,
                    intervalHours = hours,
                    intervalMinutes = minutes,
                )
                showCleanupDialog = false
            },
        )
    }
}

internal fun runtimeLogUsesUnifiedActionButtons(): Boolean = true
