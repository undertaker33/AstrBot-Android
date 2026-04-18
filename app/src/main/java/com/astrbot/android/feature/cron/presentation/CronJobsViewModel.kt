package com.astrbot.android.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.chat.data.FeatureConversationRepository
import com.astrbot.android.feature.provider.data.FeatureProviderRepository
import com.astrbot.android.feature.cron.data.FeatureCronJobRepository
import com.astrbot.android.feature.cron.domain.CronJobUseCases
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.feature.cron.presentation.CronJobsPresentationController
import com.astrbot.android.feature.cron.runtime.AndroidCronSchedulerPort
import com.astrbot.android.feature.cron.runtime.CronRuntimeService
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTargetContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

internal class CronJobsViewModel : ViewModel() {
    private val repository = object : CronJobRepositoryPort {
        override val jobs: StateFlow<List<CronJob>>
            get() = FeatureCronJobRepository.jobs

        override suspend fun create(job: CronJob): CronJob = FeatureCronJobRepository.create(job)

        override suspend fun update(job: CronJob): CronJob = FeatureCronJobRepository.update(job)

        override suspend fun delete(jobId: String) = FeatureCronJobRepository.delete(jobId)

        override suspend fun getByJobId(jobId: String): CronJob? = FeatureCronJobRepository.getByJobId(jobId)

        override suspend fun listAll(): List<CronJob> = FeatureCronJobRepository.listAll()

        override suspend fun listEnabled(): List<CronJob> = FeatureCronJobRepository.listEnabled()

        override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) =
            FeatureCronJobRepository.updateStatus(jobId, status, lastRunAt, lastError)

        override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord =
            FeatureCronJobRepository.recordExecutionStarted(record)

        override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord =
            FeatureCronJobRepository.updateExecutionRecord(record)

        override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> =
            FeatureCronJobRepository.listRecentExecutionRecords(jobId, limit)

        override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? =
            FeatureCronJobRepository.latestExecutionRecord(jobId)
    }
    private val scheduler: CronSchedulerPort = AndroidCronSchedulerPort { appContextRef }
    private val controller = CronJobsPresentationController(
        useCases = CronJobUseCases(repository = repository, scheduler = scheduler),
        taskPort = CronRuntimeService(schedulerPort = scheduler),
    )

    val jobs: StateFlow<List<CronJob>> = repository.jobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tracks whether the create/edit dialog is showing. */
    val editingJob = mutableStateOf<CronJob?>(null)
    val showCreateDialog = mutableStateOf(false)

    fun toggleEnabled(job: CronJob) {
        viewModelScope.launch(Dispatchers.IO) {
            controller.toggleEnabled(job)
        }
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

    fun createJob(
        name: String,
        cronExpression: String,
        runAt: String,
        note: String,
        runOnce: Boolean,
        targetContext: ActiveCapabilityTargetContext? = null,
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
        return runCatching { defaultCronJobTargetContext() }
            .getOrElse {
                AppLogger.append(
                    "CronJobsViewModel defaultTargetContext fallback: ${it.message ?: it.javaClass.simpleName}",
                )
                ActiveCapabilityTargetContext(
                    platform = RuntimePlatform.APP_CHAT.wireValue,
                    conversationId = FeatureConversationRepository.DEFAULT_SESSION_ID,
                    botId = "",
                    configProfileId = FeatureConfigRepository.selectedProfileId.value,
                    personaId = "",
                    providerId = "",
                    origin = "ui",
                )
            }
    }

    companion object {
        /** Set by the Screen composable to give scheduling access. */
        @Volatile
        internal var appContextRef: android.content.Context? = null
    }
}

internal fun buildCronJobCreateRequest(
    name: String,
    cronExpression: String,
    runAt: String,
    note: String,
    runOnce: Boolean,
    targetContext: ActiveCapabilityTargetContext? = null,
): CronTaskCreateRequest {
    val resolvedTarget = targetContext ?: defaultCronJobTargetContext()
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
        targetPlatform = resolvedTarget.platform,
        targetConversationId = resolvedTarget.conversationId,
        targetBotId = resolvedTarget.botId,
        targetConfigProfileId = resolvedTarget.configProfileId,
        targetPersonaId = resolvedTarget.personaId,
        targetProviderId = resolvedTarget.providerId,
        targetOrigin = resolvedTarget.origin,
    )
}

private fun defaultCronJobTargetContext(): ActiveCapabilityTargetContext {
    val selectedBot = FeatureBotRepository.snapshotProfiles()
        .firstOrNull { it.id == FeatureBotRepository.selectedBotId.value }
        ?: FeatureBotRepository.snapshotProfiles().firstOrNull()
        ?: error("No bot profiles available for scheduled task creation")
    return selectedBot.toCronJobTargetContext()
}

internal fun BotProfile.toCronJobTargetContext(
    platform: String = RuntimePlatform.APP_CHAT.wireValue,
    conversationId: String = FeatureConversationRepository.DEFAULT_SESSION_ID,
    origin: String = "ui",
): ActiveCapabilityTargetContext {
    val requestedConfigId = configProfileId.ifBlank { FeatureConfigRepository.selectedProfileId.value }
    val config = FeatureConfigRepository.resolve(requestedConfigId)
    val resolvedConfigId = configProfileId.ifBlank { config.id }
    val providerId = defaultProviderId
        .ifBlank { config.defaultChatProviderId }
        .ifBlank {
            FeatureProviderRepository.providers.value.firstOrNull { provider ->
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

