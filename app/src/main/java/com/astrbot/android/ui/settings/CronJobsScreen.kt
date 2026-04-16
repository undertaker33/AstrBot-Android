package com.astrbot.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
import com.astrbot.android.model.CronJob
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.common.SubPageScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun CronJobsScreen(
    onBack: () -> Unit,
    viewModel: CronJobsViewModel = viewModel(),
) {
    val context = LocalContext.current
    CronJobsViewModel.appContextRef = context.applicationContext

    val jobs by viewModel.jobs.collectAsState()
    val showCreate by remember { viewModel.showCreateDialog }

    SubPageScaffold(
        title = stringResource(R.string.me_card_cron_title),
        onBack = onBack,
    ) { innerPadding ->
        Scaffold(
            containerColor = MonochromeUi.pageBackground,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.showCreateDialog.value = true },
                    containerColor = MonochromeUi.fabBackground,
                    contentColor = MonochromeUi.fabContent,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create task")
                }
            },
        ) { scaffoldPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(scaffoldPadding),
            ) {
                if (jobs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.cron_empty_hint),
                            color = MonochromeUi.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(jobs, key = { it.jobId }) { job ->
                            CronJobCard(
                                job = job,
                                onToggle = { viewModel.toggleEnabled(job) },
                                onDelete = { viewModel.deleteJob(job.jobId) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateCronJobDialog(
            onDismiss = { viewModel.showCreateDialog.value = false },
            onCreate = { name, cron, runAt, note, runOnce ->
                viewModel.createJob(name, cron, runAt, note, runOnce)
                viewModel.showCreateDialog.value = false
            },
        )
    }
}

@Composable
private fun CronJobCard(
    job: CronJob,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MonochromeUi.radiusCard,
        color = MonochromeUi.cardBackground,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.name,
                        color = MonochromeUi.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (job.description.isNotBlank()) {
                        Text(
                            text = job.description,
                            color = MonochromeUi.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = job.enabled,
                    onCheckedChange = { onToggle() },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val scheduleLabel = if (job.runOnce) {
                        stringResource(R.string.cron_label_run_once)
                    } else {
                        job.cronExpression.ifBlank { "-" }
                    }
                    Text(
                        text = scheduleLabel,
                        color = MonochromeUi.textSecondary,
                        fontSize = 12.sp,
                    )
                    if (job.nextRunTime > 0) {
                        val nextStr = formatEpochMillis(job.nextRunTime)
                        Text(
                            text = stringResource(R.string.cron_label_next_run, nextStr),
                            color = MonochromeUi.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = stringResource(R.string.cron_label_status, job.status),
                        color = MonochromeUi.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MonochromeUi.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCronJobDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, cron: String, runAt: String, note: String, runOnce: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var cronExpr by remember { mutableStateOf("") }
    var runAt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var runOnce by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        title = {
            Text(
                text = stringResource(R.string.cron_create_title),
                color = MonochromeUi.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.cron_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.cron_field_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                OutlinedTextField(
                    value = cronExpr,
                    onValueChange = { cronExpr = it },
                    label = { Text(stringResource(R.string.cron_field_cron_expression)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("0 9 * * *", color = MonochromeUi.textSecondary) },
                )
                OutlinedTextField(
                    value = runAt,
                    onValueChange = { runAt = it },
                    label = { Text(stringResource(R.string.cron_field_run_at)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("2025-01-20T09:00:00+08:00", color = MonochromeUi.textSecondary) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.cron_field_run_once),
                        color = MonochromeUi.textPrimary,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = runOnce, onCheckedChange = { runOnce = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, cronExpr, runAt, note, runOnce) },
                enabled = name.isNotBlank() && (cronExpr.isNotBlank() || runAt.isNotBlank()),
            ) {
                Text(stringResource(R.string.cron_create_confirm), color = MonochromeUi.strong)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cron_create_cancel), color = MonochromeUi.textSecondary)
            }
        },
    )
}

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatEpochMillis(millis: Long): String {
    return formatter.format(Instant.ofEpochMilli(millis))
}
