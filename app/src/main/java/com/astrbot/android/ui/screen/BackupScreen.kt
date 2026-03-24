package com.astrbot.android.ui.screen

import android.app.TimePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.data.ConversationBackupItem
import com.astrbot.android.data.ConversationImportResult
import com.astrbot.android.data.ConversationImportSource
import com.astrbot.android.data.ConversationBackupRepository
import com.astrbot.android.ui.MonochromeUi
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DataBackupHubScreen(
    onBack: () -> Unit,
    onOpenConversationBackup: () -> Unit,
) {
    val modules = listOf(
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_conversations_title),
            subtitle = stringResource(R.string.backup_module_conversations_desc),
            icon = Icons.Outlined.ChatBubbleOutline,
            enabled = true,
            onClick = onOpenConversationBackup,
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_bots_title),
            subtitle = stringResource(R.string.backup_module_coming_soon),
            icon = Icons.Outlined.SmartToy,
            enabled = false,
            onClick = {},
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_models_title),
            subtitle = stringResource(R.string.backup_module_coming_soon),
            icon = Icons.Outlined.Memory,
            enabled = false,
            onClick = {},
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_personas_title),
            subtitle = stringResource(R.string.backup_module_coming_soon),
            icon = Icons.Outlined.Face,
            enabled = false,
            onClick = {},
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_configs_title),
            subtitle = stringResource(R.string.backup_module_coming_soon),
            icon = Icons.Outlined.Settings,
            enabled = false,
            onClick = {},
        ),
    )

    SubPageScaffold(
        title = stringResource(R.string.backup_data_title),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.backup_data_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MonochromeUi.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.backup_data_desc),
                            color = MonochromeUi.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(modules) { module ->
                BackupModuleCard(module = module)
            }
        }
    }
}

@Composable
fun ConversationBackupScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by ConversationBackupRepository.settings.collectAsState()
    val backups by ConversationBackupRepository.backups.collectAsState()
    var pendingImport by remember { mutableStateOf<ConversationImportSource?>(null) }
    var deletingBackupId by remember { mutableStateOf<String?>(null) }
    var exportingBackupId by remember { mutableStateOf<String?>(null) }
    var isPreparingImport by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isPreparingImport = true
            ConversationBackupRepository.prepareImportFromUri(context, uri)
                .onSuccess { pendingImport = it }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: error.javaClass.simpleName,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            isPreparingImport = false
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val backupId = exportingBackupId
        exportingBackupId = null
        if (uri == null || backupId == null) return@rememberLauncherForActivityResult
        scope.launch {
            ConversationBackupRepository.exportBackupToUri(context, backupId, uri)
                .onSuccess {
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_export_success),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: error.javaClass.simpleName,
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    SubPageScaffold(
        title = stringResource(R.string.backup_module_conversations_title),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AutoBackupSettingRow(
                            title = stringResource(R.string.backup_auto_title),
                            value = settings.autoBackupEnabled,
                            onValueChange = { ConversationBackupRepository.setAutoBackupEnabled(it) },
                        )
                        TimeSettingRow(
                            label = stringResource(R.string.backup_auto_time_title),
                            value = formatBackupTime(settings.autoBackupHour, settings.autoBackupMinute),
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        ConversationBackupRepository.setAutoBackupTime(hour, minute)
                                    },
                                    settings.autoBackupHour,
                                    settings.autoBackupMinute,
                                    true,
                                ).show()
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        ConversationBackupRepository.createBackup().onSuccess {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.backup_create_success, it.fileName),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }.onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                error.message ?: error.javaClass.simpleName,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MonochromeUi.strong,
                                    contentColor = MonochromeUi.strongText,
                                ),
                            ) {
                                Text(stringResource(R.string.backup_create_action))
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.wrapContentWidth(),
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.backup_import_file_action),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                        if (isPreparingImport) {
                            Text(
                                text = stringResource(R.string.backup_import_analyzing),
                                color = MonochromeUi.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.backup_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MonochromeUi.textPrimary,
                )
            }

            if (backups.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 2.dp,
                    ) {
                        Text(
                            text = stringResource(R.string.backup_empty_conversations),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            } else {
                items(backups, key = { it.id }) { backup ->
                    ConversationBackupCard(
                        item = backup,
                        onRestore = {
                            scope.launch {
                                isPreparingImport = true
                                ConversationBackupRepository.prepareImportFromBackup(backup.id)
                                    .onSuccess { pendingImport = it }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            error.message ?: error.javaClass.simpleName,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                isPreparingImport = false
                            }
                        },
                        onExport = {
                            exportingBackupId = backup.id
                            exportLauncher.launch("${backup.fileName}.json")
                        },
                        onDelete = { deletingBackupId = backup.id },
                    )
                }
            }
        }
    }

    pendingImport?.let { importSource ->
        ImportConflictDialog(
            source = importSource,
            onDismiss = { pendingImport = null },
            onImport = { overwriteDuplicates ->
                pendingImport = null
                scope.launch {
                    ConversationBackupRepository.importSessions(
                        sessions = importSource.sessions,
                        overwriteDuplicates = overwriteDuplicates,
                    ).onSuccess { result ->
                        Toast.makeText(
                            context,
                            buildImportSummary(context, result),
                            Toast.LENGTH_LONG,
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: error.javaClass.simpleName,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }

    deletingBackupId?.let { backupId ->
        val backup = backups.firstOrNull { it.id == backupId }
        if (backup != null) {
            ConfirmBackupDialog(
                title = stringResource(R.string.backup_delete_confirm_title),
                message = stringResource(R.string.backup_delete_confirm_message, backup.fileName),
                confirmLabel = stringResource(R.string.common_delete),
                onDismiss = { deletingBackupId = null },
                onConfirm = {
                    deletingBackupId = null
                    scope.launch {
                        ConversationBackupRepository.deleteBackup(backup.id).onSuccess {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_delete_success, backup.fileName),
                                Toast.LENGTH_LONG,
                            ).show()
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: error.javaClass.simpleName,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun BackupModuleCard(module: BackupModuleCardState) {
    Surface(
        onClick = module.onClick,
        enabled = module.enabled,
        shape = RoundedCornerShape(20.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Icon(module.icon, contentDescription = null, tint = if (module.enabled) MonochromeUi.textPrimary else MonochromeUi.textSecondary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (module.enabled) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                )
                Text(
                    text = module.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MonochromeUi.textSecondary,
                )
            }
            if (!module.enabled) {
                Text(
                    text = stringResource(R.string.backup_module_disabled_tag),
                    color = MonochromeUi.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun AutoBackupSettingRow(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = MonochromeUi.textPrimary, fontWeight = FontWeight.Medium)
            Switch(checked = value, onCheckedChange = onValueChange)
        }
        Text(
            text = stringResource(R.string.backup_auto_hint),
            color = MonochromeUi.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun TimeSettingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Text(label, color = MonochromeUi.textPrimary, fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = onClick) {
            Text(value)
        }
    }
}

@Composable
private fun ConversationBackupCard(
    item: ConversationBackupItem,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.fileName,
                        color = MonochromeUi.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatBackupDate(item.createdAt),
                        color = MonochromeUi.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                BackupTriggerTag(trigger = item.trigger)
            }
            Text(
                text = stringResource(R.string.backup_conversation_stats, item.sessionCount, item.messageCount),
                color = MonochromeUi.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MonochromeUi.strong,
                        contentColor = MonochromeUi.strongText,
                    ),
                ) {
                    Text(stringResource(R.string.backup_restore_action))
                }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Text(
                        text = stringResource(R.string.backup_export_action),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
}

@Composable
private fun BackupTriggerTag(trigger: String) {
    val label = when (trigger) {
        "auto" -> stringResource(R.string.backup_trigger_auto)
        else -> stringResource(R.string.backup_trigger_manual)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MonochromeUi.mutedSurface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = MonochromeUi.textPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ConfirmBackupDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ImportConflictDialog(
    source: ConversationImportSource,
    onDismiss: () -> Unit,
    onImport: (overwriteDuplicates: Boolean) -> Unit,
) {
    val preview = source.preview
    val duplicateTitles = preview.duplicateSessions
        .take(4)
        .joinToString(separator = "\n") { "• ${it.title}" }
    val duplicateSummary = if (preview.duplicateSessions.isEmpty()) {
        stringResource(R.string.backup_import_no_duplicates, preview.newSessions.size)
    } else {
        stringResource(
            R.string.backup_import_conflict_summary,
            preview.newSessions.size,
            preview.duplicateSessions.size,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = { Text(stringResource(R.string.backup_import_review_title, source.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(duplicateSummary)
                if (duplicateTitles.isNotBlank()) {
                    Text(
                        text = duplicateTitles,
                        color = MonochromeUi.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = { onImport(false) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
                ) {
                    Text(stringResource(R.string.backup_import_skip_duplicates))
                }
                if (preview.duplicateSessions.isNotEmpty()) {
                    TextButton(
                        onClick = { onImport(true) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
                    ) {
                        Text(stringResource(R.string.backup_import_overwrite_duplicates))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun formatBackupTime(hour: Int, minute: Int): String = String.format("%02d:%02d", hour, minute)

private fun formatBackupDate(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}

private data class BackupModuleCardState(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private fun buildImportSummary(
    context: android.content.Context,
    result: ConversationImportResult,
): String {
    return context.getString(
        R.string.backup_import_result_summary,
        result.importedCount,
        result.overwrittenCount,
        result.skippedCount,
    )
}
