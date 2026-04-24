package com.astrbot.android.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.feature.cron.presentation.CronJobsPresentationController
import com.astrbot.android.feature.cron.runtime.CronExpressionParser
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTargetContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.OffsetDateTime

@HiltViewModel
internal class CronJobsViewModel @Inject constructor(
    private val repository: CronJobRepositoryPort,
    private val controller: CronJobsPresentationController,
    private val botPort: BotRepositoryPort,
    private val conversationPort: ConversationRepositoryPort,
    private val configPort: ConfigRepositoryPort,
    private val providerPort: ProviderRepositoryPort,
) : ViewModel() {
    val jobs: StateFlow<List<CronJob>> = repository.jobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val botProfiles: StateFlow<List<BotProfile>> = botPort.bots
    val selectedBotId: StateFlow<String> = botPort.selectedBotId

    /** Tracks whether the create/edit dialog is showing. */
    val editingJob = mutableStateOf<CronJob?>(null)
    val showCreateDialog = mutableStateOf(false)
    val runHistoryState = mutableStateOf(CronJobRunHistoryUiState())

    fun toggleEnabled(job: CronJob) {
        viewModelScope.launch(Dispatchers.IO) {
            controller.toggleEnabled(job)
        }
    }

    fun pauseJob(jobId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            controller.pauseJob(jobId)
        }
    }

    fun resumeJob(jobId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            controller.resumeJob(jobId)
        }
    }

    fun showRuns(job: CronJob) {
        runHistoryState.value = CronJobRunHistoryUiState(
            jobId = job.jobId,
            jobName = job.name.ifBlank { job.jobId },
            loading = true,
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                controller.listRuns(job.jobId, limit = CronJobRunHistoryLimit)
            }.onSuccess { records ->
                runHistoryState.value = CronJobRunHistoryUiState(
                    jobId = job.jobId,
                    jobName = job.name.ifBlank { job.jobId },
                    runs = records,
                    loading = false,
                )
            }.onFailure { error ->
                AppLogger.append(
                    "CronJobsViewModel listRuns failed: ${error.message ?: error.javaClass.simpleName}",
                )
                runHistoryState.value = CronJobRunHistoryUiState(
                    jobId = job.jobId,
                    jobName = job.name.ifBlank { job.jobId },
                    loading = false,
                    errorMessage = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    fun dismissRuns() {
        runHistoryState.value = CronJobRunHistoryUiState()
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            controller.deleteJob(jobId)
        }
    }

    fun createJob(
        draft: CronJobEditorDraft,
        selectedBot: BotProfile,
    ) {
        createJob(
            name = draft.name.trim(),
            cronExpression = draft.cronExpression.trim(),
            runAt = draft.runAt.trim(),
            note = draft.note.trim(),
            runOnce = draft.runOnce,
            targetContext = draft.toTargetContext(selectedBot),
        )
    }

    fun updateJob(
        existing: CronJob,
        draft: CronJobEditorDraft,
        selectedBot: BotProfile,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val timezone = ZoneId.systemDefault().id
                val now = System.currentTimeMillis()
                val nextRunTime = resolveCronJobEditorNextRunTime(draft, now, timezone)
                val updated = draft.toUpdatedCronJob(
                    existing = existing,
                    selectedBot = selectedBot,
                    timezone = timezone,
                    nextRunTime = nextRunTime,
                    updatedAt = now,
                )
                controller.updateJob(updated)
            }.onFailure { error ->
                AppLogger.append(
                    "CronJobsViewModel updateJob failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    fun createJob(
        name: String,
        cronExpression: String,
        runAt: String,
        note: String,
        runOnce: Boolean,
        targetContext: ActiveCapabilityTargetContext,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = buildCronJobCreateRequest(
                name = name,
                cronExpression = cronExpression,
                runAt = runAt,
                note = note,
                runOnce = runOnce,
                targetContext = targetContext,
            )
            when (val result = controller.createFutureTask(request)) {
                is CronTaskCreateResult.Created -> Unit
                is CronTaskCreateResult.Failed -> {
                    AppLogger.append(
                        "CronJobsViewModel createJob failed: ${result.code} ${result.message}",
                    )
                }
            }
        }
    }

    fun defaultTargetContext(): ActiveCapabilityTargetContext {
        return runCatching { resolveDefaultCronJobTargetContext(botPort, configPort, providerPort) }
            .getOrElse {
                AppLogger.append(
                    "CronJobsViewModel defaultTargetContext fallback: ${it.message ?: it.javaClass.simpleName}",
                )
                ActiveCapabilityTargetContext(
                    platform = RuntimePlatform.APP_CHAT.wireValue,
                    conversationId = conversationPort.defaultSessionId,
                    botId = "",
                    configProfileId = configPort.selectedProfileId.value,
                    personaId = "",
                    providerId = "",
                    origin = "ui",
                )
            }
    }
}

internal const val CronJobRunHistoryLimit = 10

internal data class CronJobRunHistoryUiState(
    val jobId: String = "",
    val jobName: String = "",
    val runs: List<CronJobExecutionRecord> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String = "",
) {
    val visible: Boolean = jobId.isNotBlank()
}

internal fun resolveCronJobEditorNextRunTime(
    draft: CronJobEditorDraft,
    now: Long,
    timezone: String,
): Long {
    val runAt = draft.runAt.trim()
    return if (runAt.isNotBlank()) {
        OffsetDateTime.parse(runAt).toInstant().toEpochMilli()
    } else {
        CronExpressionParser.nextFireTime(draft.cronExpression.trim(), now, timezone)
    }
}

internal fun buildCronJobCreateRequest(
    name: String,
    cronExpression: String,
    runAt: String,
    note: String,
    runOnce: Boolean,
    targetContext: ActiveCapabilityTargetContext,
): CronTaskCreateRequest {
    return CronTaskCreateRequest(
        payload = mapOf(
            "name" to name,
            "note" to note,
            "cron_expression" to cronExpression,
            "run_at" to runAt,
            "run_once" to runOnce,
            "timezone" to ZoneId.systemDefault().id,
            "enabled" to true,
            "origin" to "ui",
        ),
        targetPlatform = targetContext.platform,
        targetConversationId = targetContext.conversationId,
        targetBotId = targetContext.botId,
        targetConfigProfileId = targetContext.configProfileId,
        targetPersonaId = targetContext.personaId,
        targetProviderId = targetContext.providerId,
        targetOrigin = targetContext.origin,
    )
}

internal fun resolveDefaultCronJobTargetContext(
    botPort: BotRepositoryPort,
    configPort: ConfigRepositoryPort,
    providerPort: ProviderRepositoryPort,
): ActiveCapabilityTargetContext {
    val snapshot = botPort.snapshotProfiles()
    val selectedBot = snapshot
        .firstOrNull { it.id == botPort.selectedBotId.value }
        ?: snapshot.firstOrNull()
        ?: error("No bot profiles available for scheduled task creation")
    return selectedBot.toCronJobTargetContext(
        configPort = configPort,
        providerPort = providerPort,
    )
}

internal fun BotProfile.toCronJobTargetContext(
    configPort: ConfigRepositoryPort,
    providerPort: ProviderRepositoryPort,
    platform: String = RuntimePlatform.APP_CHAT.wireValue,
    conversationId: String = DefaultAppChatConversationId,
    origin: String = "ui",
): ActiveCapabilityTargetContext {
    val requestedConfigId = configProfileId.ifBlank { configPort.selectedProfileId.value }
    val config = configPort.resolve(requestedConfigId)
    val resolvedConfigId = configProfileId.ifBlank { config.id }
    val providerId = defaultProviderId
        .ifBlank { config.defaultChatProviderId }
        .ifBlank {
            providerPort.providers.value.firstOrNull { provider ->
                provider.enabled && ProviderCapability.CHAT in provider.capabilities
            }?.id.orEmpty()
        }
    return ActiveCapabilityTargetContext(
        platform = platform,
        conversationId = conversationId,
        botId = id,
        configProfileId = resolvedConfigId,
        personaId = defaultPersonaId,
        providerId = providerId,
        origin = origin,
    )
}

internal fun BotProfile.toCronJobTargetContext(
    platform: String = RuntimePlatform.APP_CHAT.wireValue,
    conversationId: String = DefaultAppChatConversationId,
    origin: String = "ui",
): ActiveCapabilityTargetContext {
    return ActiveCapabilityTargetContext(
        platform = platform,
        conversationId = conversationId,
        botId = id,
        configProfileId = configProfileId,
        personaId = defaultPersonaId,
        providerId = defaultProviderId,
        origin = origin,
    )
}

