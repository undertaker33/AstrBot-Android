package com.astrbot.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import com.astrbot.android.R
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.BotProfile
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.common.SubPageScaffold
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTargetContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun CronJobsScreen(
    onBack: () -> Unit,
    viewModel: CronJobsViewModel = hiltViewModel(),
) {
    val jobs by viewModel.jobs.collectAsState()
    val botProfiles by viewModel.botProfiles.collectAsState()
    val selectedBotId by viewModel.selectedBotId.collectAsState()
    val defaultTargetContext = viewModel.defaultTargetContext()

    SubPageScaffold(
        title = stringResource(R.string.cron_jobs_title),
        onBack = onBack,
    ) { innerPadding ->
        CronJobsContent(
            jobs = jobs,
            defaultTargetContext = defaultTargetContext,
            botProfiles = botProfiles,
            selectedBotId = selectedBotId,
            onCreateJob = { draft, selectedBot ->
                viewModel.createJob(draft, selectedBot)
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
internal fun CronJobsContent(
    jobs: List<CronJob>,
    defaultTargetContext: ActiveCapabilityTargetContext = ActiveCapabilityTargetContext(
        platform = RuntimePlatform.APP_CHAT.wireValue,
        conversationId = "",
        botId = "",
        configProfileId = "",
        personaId = "",
        providerId = "",
        origin = "ui",
    ),
    botProfiles: List<BotProfile> = emptyList(),
    selectedBotId: String = "",
    onCreateJob: (CronJobEditorDraft, BotProfile) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var requestedPage by rememberSaveable { mutableStateOf(1) }
    var showPageJumpDialog by rememberSaveable { mutableStateOf(false) }
    var pageJumpDraft by rememberSaveable { mutableStateOf("") }
    var pageJumpHasError by rememberSaveable { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    val page = buildCronJobsPresentation(jobs = jobs, requestedPage = requestedPage)

    LaunchedEffect(jobs.size) {
        requestedPage = buildCronJobsPresentation(jobs = jobs, requestedPage = requestedPage).currentPage
    }

    if (showCreateDialog) {
        CreateCronJobDialog(
            initialTargetContext = defaultTargetContext,
            botProfiles = botProfiles,
            initialSelectedBotId = selectedBotId,
            onDismiss = { showCreateDialog = false },
            onCreate = { draft, selectedBot ->
                onCreateJob(draft, selectedBot)
                requestedPage = Int.MAX_VALUE
                showCreateDialog = false
            },
        )
    }

    if (showPageJumpDialog) {
        AlertDialog(
            onDismissRequest = { showPageJumpDialog = false },
            title = { Text(stringResource(R.string.resource_list_page_jump_title)) },
            text = {
                OutlinedTextField(
                    value = pageJumpDraft,
                    onValueChange = { value ->
                        pageJumpDraft = value
                        pageJumpHasError = false
                    },
                    label = { Text(stringResource(R.string.resource_list_page_jump_label)) },
                    singleLine = true,
                    isError = pageJumpHasError,
                    modifier = Modifier.testTag("pager-jump-input"),
                    supportingText = {
                        if (pageJumpHasError) {
                            Text(stringResource(R.string.resource_list_page_jump_error, page.totalPages))
                        }
                    },
                    colors = com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetPage = pageJumpDraft.trim().toIntOrNull()
                        if (targetPage != null && targetPage in 1..page.totalPages) {
                            requestedPage = targetPage
                            pageJumpHasError = false
                            showPageJumpDialog = false
                        } else {
                            pageJumpHasError = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageJumpDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = MonochromeUi.pageBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = FloatingBottomNavFabBottomPadding)
                    .testTag("cron-jobs-add-fab"),
                containerColor = MonochromeUi.actionFabBackground,
                contentColor = MonochromeUi.actionFabContent,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.cron_jobs_add_content_description),
                )
            }
        },
        bottomBar = {
            SettingsPagerBar(
                currentPage = page.currentPage,
                totalPages = page.totalPages,
                canGoPrevious = page.canGoPrevious,
                canGoNext = page.canGoNext,
                onPrevious = { requestedPage = (page.currentPage - 1).coerceAtLeast(1) },
                onNext = { requestedPage = (page.currentPage + 1).coerceAtMost(page.totalPages) },
                onJump = {
                    pageJumpDraft = page.currentPage.toString()
                    pageJumpHasError = false
                    showPageJumpDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        },
    ) { scaffoldPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(MonochromeUi.pageBackground),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (page.visibleJobs.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp),
                            color = MonochromeUi.cardBackground,
                            shape = MonochromeUi.radiusCard,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.cron_jobs_empty_hint),
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = page.visibleJobs,
                        key = { _, job -> job.jobId },
                    ) { index, job ->
                        CronJobCard(
                            job = job,
                            backgroundColor = if (index % 2 == 0) MonochromeUi.cardBackground else MonochromeUi.cardAltBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCronJobDialog(
    initialTargetContext: ActiveCapabilityTargetContext,
    botProfiles: List<BotProfile>,
    initialSelectedBotId: String,
    onDismiss: () -> Unit,
    onCreate: (CronJobEditorDraft, BotProfile) -> Unit,
) {
    val initialDraft = CronJobEditorDraft.fromTargetContext(initialTargetContext)
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var cronExpression by rememberSaveable { mutableStateOf("") }
    var runAt by rememberSaveable { mutableStateOf("") }
    var runOnce by rememberSaveable { mutableStateOf(false) }
    var platform by rememberSaveable { mutableStateOf(initialDraft.platform) }
    var conversationId by rememberSaveable { mutableStateOf(initialDraft.conversationId) }
    var selectedBotId by rememberSaveable {
        mutableStateOf(
            initialDraft.selectedBotId.ifBlank { initialSelectedBotId }.ifBlank { botProfiles.firstOrNull()?.id.orEmpty() },
        )
    }
    val selectedBot = botProfiles.firstOrNull { it.id == selectedBotId }
    val draft = CronJobEditorDraft(
        name = name,
        note = note,
        cronExpression = cronExpression,
        runAt = runAt,
        runOnce = runOnce,
        platform = platform,
        conversationId = conversationId,
        selectedBotId = selectedBotId,
    )

    LaunchedEffect(botProfiles) {
        if (selectedBotId.isBlank() || botProfiles.none { it.id == selectedBotId }) {
            selectedBotId = botProfiles.firstOrNull()?.id.orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cron_create_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.cron_field_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cron-create-name"),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.cron_field_note)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cron-create-note"),
                    minLines = 2,
                    maxLines = 4,
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = cronExpression,
                    onValueChange = { cronExpression = it },
                    label = { Text(stringResource(R.string.cron_field_cron_expression)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cron-create-cron"),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = runAt,
                    onValueChange = { runAt = it },
                    label = { Text(stringResource(R.string.cron_field_run_at)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cron-create-run-at"),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.cron_field_platform),
                        color = MonochromeUi.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = platform == RuntimePlatform.APP_CHAT.wireValue,
                            onClick = { platform = RuntimePlatform.APP_CHAT.wireValue },
                            label = { Text(stringResource(R.string.cron_platform_app_chat)) },
                        )
                        FilterChip(
                            selected = platform == RuntimePlatform.QQ_ONEBOT.wireValue,
                            onClick = { platform = RuntimePlatform.QQ_ONEBOT.wireValue },
                            label = { Text(stringResource(R.string.cron_platform_qq_onebot)) },
                        )
                    }
                }
                BotSelectionField(
                    bots = botProfiles,
                    selectedBotId = selectedBotId,
                    onSelect = { selectedBotId = it },
                )
                OutlinedTextField(
                    value = conversationId,
                    onValueChange = { conversationId = it },
                    label = { Text(stringResource(R.string.cron_field_conversation_id)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cron-create-conversation"),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.cron_field_run_once),
                        color = MonochromeUi.textPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = runOnce,
                        onCheckedChange = { runOnce = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bot = selectedBot ?: return@TextButton
                    onCreate(draft, bot)
                },
                enabled = draft.canSubmit() && selectedBot != null,
            ) {
                Text(stringResource(R.string.cron_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cron_create_cancel))
            }
        },
    )
}

@Composable
private fun CronJobCard(
    job: CronJobListItemPresentation,
    backgroundColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = MonochromeUi.radiusCard,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = job.name,
                color = MonochromeUi.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CronJobInfoRow(
                label = stringResource(R.string.cron_jobs_field_cron),
                value = job.cronExpression,
            )
            CronJobInfoRow(
                label = stringResource(R.string.cron_jobs_field_session),
                value = job.conversationId.ifBlank { stringResource(R.string.common_not_available) },
            )
            CronJobInfoRow(
                label = stringResource(R.string.cron_jobs_field_next_run),
                value = formatTimestampOrUnavailable(job.nextRunTime),
            )
            CronJobInfoRow(
                label = stringResource(R.string.cron_jobs_field_last_run),
                value = formatTimestampOrUnavailable(job.lastRunAt),
            )
            CronJobInfoRow(
                label = stringResource(R.string.cron_jobs_field_description),
                value = job.description.ifBlank { stringResource(R.string.common_not_available) },
            )
        }
    }
}

@Composable
private fun BotSelectionField(
    bots: List<BotProfile>,
    selectedBotId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(selectedBotId, bots) { mutableStateOf(false) }
    val selectedBot = bots.firstOrNull { it.id == selectedBotId }
    val summary = selectedBot?.displayName ?: stringResource(R.string.common_not_selected)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.cron_field_bot),
            color = MonochromeUi.textPrimary,
        )
        Surface(
            onClick = { expanded = true },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            color = MonochromeUi.inputBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    color = MonochromeUi.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MonochromeUi.textSecondary,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (bots.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cron_field_bot_no_options)) },
                    onClick = { expanded = false },
                )
            } else {
                bots.forEach { bot ->
                    DropdownMenuItem(
                        text = { Text(bot.displayName) },
                        onClick = {
                            onSelect(bot.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CronJobInfoRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = MonochromeUi.textSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = MonochromeUi.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val cronJobTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatTimestampOrUnavailable(timestamp: Long): String {
    return if (timestamp > 0L) {
        cronJobTimeFormatter.format(Instant.ofEpochMilli(timestamp))
    } else {
        "-"
    }
}

