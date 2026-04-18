package com.astrbot.android.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.feature.cron.data.LegacyCronJobRepositoryAdapter
import com.astrbot.android.feature.cron.data.LegacyCronSchedulerAdapter
import com.astrbot.android.feature.cron.domain.CronJobUseCases
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.feature.cron.presentation.CronJobsPresentationController
import com.astrbot.android.feature.cron.runtime.CronRuntimeService
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.context.RuntimePlatform
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityTargetContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

internal class CronJobsViewModel : ViewModel() {
    private val repository = LegacyCronJobRepositoryAdapter()
    private val scheduler = LegacyCronSchedulerAdapter(contextProvider = { appContextRef })
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
                    RuntimeLogRepository.append(
                        "CronJobsViewModel createJob failed: ${result.code} ${result.message}",
                    )
                }
            }
        }
    }

    fun defaultTargetContext(): ActiveCapabilityTargetContext {
        return runCatching { defaultCronJobTargetContext() }
            .getOrElse {
                RuntimeLogRepository.append(
                    "CronJobsViewModel defaultTargetContext fallback: ${it.message ?: it.javaClass.simpleName}",
                )
                ActiveCapabilityTargetContext(
                    platform = RuntimePlatform.APP_CHAT.wireValue,
                    conversationId = ConversationRepository.DEFAULT_SESSION_ID,
                    botId = "",
                    configProfileId = ConfigRepository.selectedProfileId.value,
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
    val selectedBot = BotRepository.snapshotProfiles()
        .firstOrNull { it.id == BotRepository.selectedBotId.value }
        ?: BotRepository.snapshotProfiles().firstOrNull()
        ?: error("No bot profiles available for scheduled task creation")
    return selectedBot.toCronJobTargetContext()
}

internal fun BotProfile.toCronJobTargetContext(
    platform: String = RuntimePlatform.APP_CHAT.wireValue,
    conversationId: String = ConversationRepository.DEFAULT_SESSION_ID,
    origin: String = "ui",
): ActiveCapabilityTargetContext {
    val requestedConfigId = configProfileId.ifBlank { ConfigRepository.selectedProfileId.value }
    val config = ConfigRepository.resolve(requestedConfigId)
    val resolvedConfigId = configProfileId.ifBlank { config.id }
    val providerId = defaultProviderId
        .ifBlank { config.defaultChatProviderId }
        .ifBlank {
            ProviderRepository.providers.value.firstOrNull { provider ->
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
