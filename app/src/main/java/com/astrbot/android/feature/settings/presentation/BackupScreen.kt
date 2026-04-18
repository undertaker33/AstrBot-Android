package com.astrbot.android.ui.settings
import com.astrbot.android.ui.common.SubPageScaffold

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.astrbot.android.R
import com.astrbot.android.core.db.backup.ConversationBackupItem
import com.astrbot.android.core.db.backup.ConversationImportResult
import com.astrbot.android.core.db.backup.ConversationImportSource
import com.astrbot.android.core.db.backup.ConversationBackupRepository
import com.astrbot.android.core.db.backup.AppBackupRepository
import com.astrbot.android.core.db.backup.AppBackupImportPlan
import com.astrbot.android.core.db.backup.AppBackupImportMode
import com.astrbot.android.core.db.backup.AppBackupImportSource
import com.astrbot.android.core.db.backup.AppBackupItem
import com.astrbot.android.core.db.backup.AppBackupModuleKind
import com.astrbot.android.core.db.backup.AppBackupRestoreResult
import com.astrbot.android.core.db.backup.ModuleBackupImportSource
import com.astrbot.android.core.db.backup.ModuleBackupItem
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.monochromeOutlinedButtonBorder
import com.astrbot.android.ui.app.monochromeOutlinedButtonColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DataBackupHubScreen(
    onBack: () -> Unit,
    onOpenBotBackup: () -> Unit,
    onOpenModelBackup: () -> Unit,
    onOpenPersonaBackup: () -> Unit,
    onOpenConversationBackup: () -> Unit,
    onOpenConfigBackup: () -> Unit,
    onOpenTtsBackup: () -> Unit,
    onOpenFullBackup: () -> Unit,
) {
    val modules = backupModuleCardsForDisplay(
        listOf(
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_full_title),
            subtitle = stringResource(R.string.backup_module_full_desc),
            icon = Icons.Outlined.Memory,
            enabled = true,
            onClick = onOpenFullBackup,
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_conversations_title),
            subtitle = stringResource(R.string.backup_module_conversations_desc),
            icon = Icons.Outlined.ChatBubbleOutline,
            enabled = true,
            onClick = onOpenConversationBackup,
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_bots_title),
            subtitle = stringResource(R.string.backup_module_bots_desc),
            icon = Icons.Outlined.SmartToy,
            enabled = true,
            onClick = onOpenBotBackup,
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_models_title),
            subtitle = stringResource(R.string.backup_module_models_desc),
            icon = Icons.Outlined.Memory,
            enabled = true,
            onClick = onOpenModelBackup,
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_personas_title),
            subtitle = stringResource(R.string.backup_module_personas_desc),
            icon = Icons.Outlined.Face,
            enabled = true,
            onClick = onOpenPersonaBackup,
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_configs_title),
            subtitle = stringResource(R.string.backup_module_configs_desc),
            icon = Icons.Outlined.Settings,
            enabled = true,
            onClick = onOpenConfigBackup,
        ),
        BackupModuleCardState(
            title = stringResource(R.string.backup_module_tts_title),
            subtitle = stringResource(R.string.backup_module_tts_desc),
            icon = Icons.Outlined.Memory,
            enabled = true,
            onClick = onOpenTtsBackup,
        ),
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
                    shape = RoundedCornerShape(16.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 0.dp,
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
fun ModuleBackupScreen(
    module: AppBackupModuleKind,
    title: String,
    description: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backups by AppBackupRepository.backupsForModule(module).collectAsState()
    var pendingImport by remember { mutableStateOf<ModuleBackupImportSource?>(null) }
    var deletingBackupId by remember { mutableStateOf<String?>(null) }
    var exportingBackupId by remember { mutableStateOf<String?>(null) }
    var isPreparingImport by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isPreparingImport = true
            AppBackupRepository.prepareModuleImportFromUri(context, module, uri)
                .onSuccess { pendingImport = it }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
                }
            isPreparingImport = false
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val backupId = exportingBackupId
        exportingBackupId = null
        if (uri == null || backupId == null) return@rememberLauncherForActivityResult
        scope.launch {
            AppBackupRepository.exportModuleBackupToUri(context, module, backupId, uri)
                .onSuccess {
                    Toast.makeText(context, context.getString(R.string.backup_export_success), Toast.LENGTH_LONG).show()
                }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
                }
        }
    }

    SubPageScaffold(
        title = title,
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
                    shape = RoundedCornerShape(16.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = description,
                            color = MonochromeUi.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        AppBackupRepository.createModuleBackup(module).onSuccess {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.backup_create_success, it.fileName),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }.onFailure { error ->
                                            Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
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
                                onClick = { importLauncher.launch(backupImportDocumentMimeTypes()) },
                                colors = monochromeOutlinedButtonColors(),
                            ) {
                                Text(text = stringResource(R.string.backup_import_file_action))
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
                        shape = RoundedCornerShape(12.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = stringResource(R.string.backup_empty_module, title),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            } else {
                items(backups, key = { it.id }) { backup ->
                    ModuleBackupCard(
                        item = backup,
                        onRestore = {
                            scope.launch {
                                isPreparingImport = true
                                AppBackupRepository.prepareModuleImportFromBackup(module, backup.id)
                                    .onSuccess { pendingImport = it }
                                    .onFailure { error ->
                                        Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
                                    }
                                isPreparingImport = false
                            }
                        },
                        onExport = {
                            exportingBackupId = backup.id
                            exportLauncher.launch("${backup.fileName}.zip")
                        },
                        onDelete = { deletingBackupId = backup.id },
                    )
                }
            }
        }
    }

    pendingImport?.let { importSource ->
        ModuleBackupImportDialog(
            title = stringResource(R.string.backup_restore_module_confirm_title, title),
            message = stringResource(
                R.string.backup_restore_module_confirm_message,
                title,
                importSource.preview.newCount,
                importSource.preview.duplicateCount,
            ),
            onDismiss = { pendingImport = null },
            onImport = { mode ->
                pendingImport = null
                scope.launch {
                    AppBackupRepository.importModuleBackup(importSource, mode)
                        .onSuccess { restoredCount ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_restore_module_success, title, restoredCount),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        .onFailure { error ->
                            Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
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
                        AppBackupRepository.deleteModuleBackup(module, backup.id).onSuccess {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_delete_success, backup.fileName),
                                Toast.LENGTH_LONG,
                            ).show()
                        }.onFailure { error ->
                            Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun FullBackupScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backups by AppBackupRepository.backups.collectAsState()
    var pendingImport by remember { mutableStateOf<AppBackupImportSource?>(null) }
    var deletingBackupId by remember { mutableStateOf<String?>(null) }
    var exportingBackupId by remember { mutableStateOf<String?>(null) }
    var isPreparingImport by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isPreparingImport = true
            AppBackupRepository.prepareImportFromUri(context, uri)
                .onSuccess { pendingImport = it }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
                }
            isPreparingImport = false
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val backupId = exportingBackupId
        exportingBackupId = null
        if (uri == null || backupId == null) return@rememberLauncherForActivityResult
        scope.launch {
            AppBackupRepository.exportBackupToUri(context, backupId, uri)
                .onSuccess {
                    Toast.makeText(context, context.getString(R.string.backup_export_success), Toast.LENGTH_LONG).show()
                }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
                }
        }
    }

    SubPageScaffold(
        title = stringResource(R.string.backup_module_full_title),
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
                    shape = RoundedCornerShape(16.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.backup_module_full_desc),
                            color = MonochromeUi.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        AppBackupRepository.createBackup().onSuccess {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.backup_create_success, it.fileName),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }.onFailure { error ->
                                            Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
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
                                onClick = { importLauncher.launch(backupImportDocumentMimeTypes()) },
                                colors = monochromeOutlinedButtonColors(),
                            ) {
                                Text(text = stringResource(R.string.backup_import_file_action))
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
                        shape = RoundedCornerShape(12.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = stringResource(R.string.backup_empty_full),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            } else {
                items(backups, key = { it.id }) { backup ->
                    FullBackupCard(
                        item = backup,
                        onRestore = {
                            scope.launch {
                                isPreparingImport = true
                                AppBackupRepository.prepareImportFromBackup(backup.id)
                                    .onSuccess { pendingImport = it }
                                    .onFailure { error ->
                                        Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
                                    }
                                isPreparingImport = false
                            }
                        },
                        onExport = {
                            exportingBackupId = backup.id
                            exportLauncher.launch("${backup.fileName}.zip")
                        },
                        onDelete = { deletingBackupId = backup.id },
                    )
                }
            }
        }
    }

    pendingImport?.let { importSource ->
        FullBackupImportDialog(
            title = stringResource(R.string.backup_restore_full_confirm_title),
            message = buildFullBackupImportSummary(context, importSource),
            onDismiss = { pendingImport = null },
            onImport = { plan ->
                pendingImport = null
                scope.launch {
                    AppBackupRepository.importBackup(importSource, plan)
                        .onSuccess { result ->
                            Toast.makeText(context, buildFullRestoreSummary(context, result), Toast.LENGTH_LONG).show()
                        }
                        .onFailure { error ->
                            Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
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
                        AppBackupRepository.deleteBackup(backup.id).onSuccess {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_delete_success, backup.fileName),
                                Toast.LENGTH_LONG,
                            ).show()
                        }.onFailure { error ->
                            Toast.makeText(context, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
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
                    shape = RoundedCornerShape(16.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 0.dp,
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
                                colors = monochromeOutlinedButtonColors(),
                            ) {
                                Text(
                                    text = stringResource(R.string.backup_import_file_action),
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
                        shape = RoundedCornerShape(12.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 0.dp,
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
        shape = RoundedCornerShape(12.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
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
                    .background(MonochromeUi.mutedSurface, RoundedCornerShape(10.dp))
                    .padding(8.dp),
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
        OutlinedButton(
            onClick = onClick,
            colors = monochromeOutlinedButtonColors(),
            border = monochromeOutlinedButtonBorder(),
        ) {
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
        shape = RoundedCornerShape(12.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MonochromeUi.strong,
                        contentColor = MonochromeUi.strongText,
                    ),
                ) {
                    Text(stringResource(R.string.backup_restore_action))
                }
                OutlinedButton(
                    onClick = onExport,
                    colors = monochromeOutlinedButtonColors(),
                ) {
                    Text(
                        text = stringResource(R.string.backup_export_action),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(
                    onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC43C35)),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
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
private fun FullBackupCard(
    item: AppBackupItem,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
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
                text = item.moduleCounts.entries.joinToString(" | ") { entry -> "${entry.key}:${entry.value}" },
                color = MonochromeUi.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MonochromeUi.strong,
                        contentColor = MonochromeUi.strongText,
                    ),
                ) {
                    Text(stringResource(R.string.backup_restore_action))
                }
                OutlinedButton(
                    onClick = onExport,
                    colors = monochromeOutlinedButtonColors(),
                ) {
                    Text(text = stringResource(R.string.backup_export_action))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC43C35)),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleBackupCard(
    item: ModuleBackupItem,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
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
                text = if (item.hasFiles) {
                    stringResource(R.string.backup_module_stats_with_files, item.recordCount)
                } else {
                    stringResource(R.string.backup_module_stats, item.recordCount)
                },
                color = MonochromeUi.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MonochromeUi.strong,
                        contentColor = MonochromeUi.strongText,
                    ),
                ) {
                    Text(stringResource(R.string.backup_restore_action))
                }
                OutlinedButton(
                    onClick = onExport,
                    colors = monochromeOutlinedButtonColors(),
                ) {
                    Text(text = stringResource(R.string.backup_export_action))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC43C35)),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                }
            }
        }
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
        .joinToString(separator = "\n") { "閳?${it.title}" }
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

internal data class BackupModuleCardState(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

internal fun backupModuleCardStateForTest(title: String): BackupModuleCardState {
    return BackupModuleCardState(
        title = title,
        subtitle = title,
        icon = Icons.Outlined.Memory,
        enabled = true,
        onClick = {},
    )
}

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

@Composable
private fun ModuleBackupImportDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onImport: (AppBackupImportMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                moduleImportActionRows().forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { mode ->
                            QuickImportModeButton(
                                modifier = Modifier.weight(1f),
                                label = stringResource(appBackupImportModeLabelRes(mode)),
                                onClick = { onImport(mode) },
                            )
                        }
                        if (index == moduleImportActionRows().lastIndex && moduleImportUsesInlineCancel()) {
                            QuickImportModeButton(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.common_cancel),
                                onClick = onDismiss,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun FullBackupImportDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onImport: (AppBackupImportPlan) -> Unit,
) {
    var plan by remember { mutableStateOf(AppBackupImportPlan()) }
    var expandedModuleKey by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = { Text(title) },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .heightIn(max = fullBackupImportDialogScrollableMaxHeightDp().dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(message)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quickActionRows = appBackupImportQuickActionRows()
                    quickActionRows.forEachIndexed { rowIndex, rowModes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowModes.forEach { mode ->
                                val rowWeight = if (rowIndex == 0) 1f else 0.5f
                                QuickImportModeButton(
                                    modifier = Modifier.weight(rowWeight),
                                    label = stringResource(appBackupImportModeLabelRes(mode)),
                                    onClick = { plan = plan.withAllModules(mode) },
                                )
                            }
                        }
                    }
                }
                ModuleImportStrategyRow(
                    title = stringResource(R.string.backup_module_bots_title),
                    mode = plan.bots,
                    expanded = expandedModuleKey == "bots",
                    onExpandedChange = { expanded -> expandedModuleKey = if (expanded) "bots" else null },
                    onModeChange = { plan = plan.copy(bots = it) },
                )
                ModuleImportStrategyRow(
                    title = stringResource(R.string.backup_module_models_title),
                    mode = plan.providers,
                    expanded = expandedModuleKey == "providers",
                    onExpandedChange = { expanded -> expandedModuleKey = if (expanded) "providers" else null },
                    onModeChange = { plan = plan.copy(providers = it) },
                )
                ModuleImportStrategyRow(
                    title = stringResource(R.string.backup_module_personas_title),
                    mode = plan.personas,
                    expanded = expandedModuleKey == "personas",
                    onExpandedChange = { expanded -> expandedModuleKey = if (expanded) "personas" else null },
                    onModeChange = { plan = plan.copy(personas = it) },
                )
                ModuleImportStrategyRow(
                    title = stringResource(R.string.backup_module_configs_title),
                    mode = plan.configs,
                    expanded = expandedModuleKey == "configs",
                    onExpandedChange = { expanded -> expandedModuleKey = if (expanded) "configs" else null },
                    onModeChange = { plan = plan.copy(configs = it) },
                )
                ModuleImportStrategyRow(
                    title = stringResource(R.string.backup_module_conversations_title),
                    mode = plan.conversations,
                    expanded = expandedModuleKey == "conversations",
                    onExpandedChange = { expanded -> expandedModuleKey = if (expanded) "conversations" else null },
                    onModeChange = { plan = plan.copy(conversations = it) },
                )
                ModuleImportStrategyRow(
                    title = stringResource(R.string.nav_qq_account),
                    mode = plan.qqAccounts,
                    expanded = expandedModuleKey == "qqAccounts",
                    onExpandedChange = { expanded -> expandedModuleKey = if (expanded) "qqAccounts" else null },
                    onModeChange = { plan = plan.copy(qqAccounts = it) },
                )
                ModuleImportStrategyRow(
                    title = stringResource(R.string.backup_module_tts_title),
                    mode = plan.ttsAssets,
                    expanded = expandedModuleKey == "ttsAssets",
                    onExpandedChange = { expanded -> expandedModuleKey = if (expanded) "ttsAssets" else null },
                    onModeChange = { plan = plan.copy(ttsAssets = it) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(plan) },
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
            ) {
                Text(stringResource(R.string.backup_import_apply_plan))
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
private fun ModuleImportStrategyRow(
    title: String,
    mode: AppBackupImportMode,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onModeChange: (AppBackupImportMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MonochromeUi.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box {
            OutlinedButton(
                onClick = { onExpandedChange(!expanded) },
                shape = CircleShape,
                colors = monochromeOutlinedButtonColors(),
                border = monochromeOutlinedButtonBorder(),
            ) {
                Text(
                    text = stringResource(appBackupImportModeLabelRes(mode)),
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                containerColor = MonochromeUi.elevatedSurface,
            ) {
                appBackupImportModesInDisplayOrder().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(appBackupImportModeLabelRes(option))) },
                        colors = MenuDefaults.itemColors(
                            textColor = MonochromeUi.textPrimary,
                        ),
                        onClick = {
                            onModeChange(option)
                            onExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickImportModeButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = monochromeOutlinedButtonColors(),
        border = monochromeOutlinedButtonBorder(),
    ) {
        Text(
            text = label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun buildFullBackupImportSummary(
    context: android.content.Context,
    source: AppBackupImportSource,
): String {
    return context.getString(
        R.string.backup_restore_full_confirm_message,
        source.manifest.modules.bots.count,
        source.manifest.modules.providers.count,
        source.manifest.modules.personas.count,
        source.manifest.modules.configs.count,
        source.manifest.modules.conversations.count,
        source.manifest.modules.qqLogin.count,
        source.manifest.modules.ttsAssets.count,
        source.preview.bots.duplicateCount,
        source.preview.providers.duplicateCount,
        source.preview.personas.duplicateCount,
        source.preview.configs.duplicateCount,
        source.preview.conversations.duplicateCount,
        source.preview.qqAccounts.duplicateCount,
        source.preview.ttsAssets.duplicateCount,
    )
}

private fun buildFullRestoreSummary(
    context: android.content.Context,
    result: AppBackupRestoreResult,
): String {
    return context.getString(
        R.string.backup_restore_full_success,
        result.botCount,
        result.providerCount,
        result.personaCount,
        result.configCount,
        result.conversationCount,
        result.qqAccountCount,
        result.ttsAssetCount,
    )
}

internal fun appBackupImportQuickActionRows(): List<List<AppBackupImportMode>> {
    return listOf(
        listOf(
            AppBackupImportMode.REPLACE_ALL,
            AppBackupImportMode.MERGE_SKIP_DUPLICATES,
        ),
        listOf(AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES),
    )
}

internal fun moduleImportActionRows(): List<List<AppBackupImportMode>> = appBackupImportQuickActionRows()

internal fun moduleImportUsesInlineCancel(): Boolean = true

internal fun backupImportDocumentMimeTypes(): Array<String> {
    return arrayOf("application/zip", "application/json")
}

internal fun fullBackupImportDialogScrollableMaxHeightDp(): Int = 460

internal fun appBackupImportModesInDisplayOrder(): List<AppBackupImportMode> {
    return listOf(
        AppBackupImportMode.REPLACE_ALL,
        AppBackupImportMode.MERGE_SKIP_DUPLICATES,
        AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES,
    )
}

internal fun appBackupImportModeLabelRes(mode: AppBackupImportMode): Int {
    return when (mode) {
        AppBackupImportMode.REPLACE_ALL -> R.string.backup_import_mode_replace_all
        AppBackupImportMode.MERGE_SKIP_DUPLICATES -> R.string.backup_import_skip_duplicates
        AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES -> R.string.backup_import_overwrite_duplicates
    }
}

private fun AppBackupImportPlan.withAllModules(mode: AppBackupImportMode): AppBackupImportPlan {
    return AppBackupImportPlan(
        bots = mode,
        providers = mode,
        personas = mode,
        configs = mode,
        conversations = mode,
        qqAccounts = mode,
        ttsAssets = mode,
    )
}

internal fun sortBackupModuleTitlesForDisplay(titles: List<String>): List<String> {
    val remainingTitles = titles.toMutableList()
    val orderedTitles = mutableListOf<String>()
    backupModuleTitleMatchersInDisplayOrder().forEach { matcher ->
        val matched = remainingTitles.filter(matcher)
        orderedTitles += matched.sortedWith(compareBy<String> { it.length }.thenBy { it })
        remainingTitles.removeAll(matched)
    }
    orderedTitles += remainingTitles.sortedWith(compareBy<String> { it.length }.thenBy { it })
    return orderedTitles
}

private fun backupModuleTitleMatchersInDisplayOrder(): List<(String) -> Boolean> {
    return listOf(
        ::isFullBackupTitle,
        ::isConversationBackupTitle,
        ::isBotBackupTitle,
        ::isModelBackupTitle,
        ::isPersonaBackupTitle,
        ::isConfigBackupTitle,
        ::isTtsBackupTitle,
    )
}

private fun isFullBackupTitle(title: String): Boolean {
    return title.contains("Full", ignoreCase = true)
}

private fun isConversationBackupTitle(title: String): Boolean {
    return title.contains("Conversation", ignoreCase = true)
}

private fun isBotBackupTitle(title: String): Boolean {
    return title.contains("Bot", ignoreCase = true)
}

private fun isModelBackupTitle(title: String): Boolean {
    return title.contains("Model", ignoreCase = true)
}

private fun isPersonaBackupTitle(title: String): Boolean {
    return title.contains("Persona", ignoreCase = true)
}

private fun isConfigBackupTitle(title: String): Boolean {
    return title.contains("Config", ignoreCase = true)
}

private fun isTtsBackupTitle(title: String): Boolean {
    return title.contains("TTS", ignoreCase = true)
}

internal fun backupModuleCardsForDisplay(modules: List<BackupModuleCardState>): List<BackupModuleCardState> {
    return sortBackupModuleCardsForDisplay(modules)
}

private fun sortBackupModuleCardsForDisplay(modules: List<BackupModuleCardState>): List<BackupModuleCardState> {
    val orderedTitles = sortBackupModuleTitlesForDisplay(modules.map { it.title })
    return orderedTitles.mapNotNull { title -> modules.firstOrNull { it.title == title } }
}

