package com.astrbot.android.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.data.CronJobRepository
import com.astrbot.android.model.CronJob
import com.astrbot.android.runtime.cron.CronExpressionParser
import com.astrbot.android.runtime.cron.CronJobScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

internal class CronJobsViewModel : ViewModel() {

    val jobs: StateFlow<List<CronJob>> = CronJobRepository.jobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tracks whether the create/edit dialog is showing. */
    val editingJob = mutableStateOf<CronJob?>(null)
    val showCreateDialog = mutableStateOf(false)

    fun toggleEnabled(job: CronJob) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = job.copy(enabled = !job.enabled, updatedAt = System.currentTimeMillis())
            CronJobRepository.update(updated)
            val ctx = appContextRef
            if (ctx != null) {
                if (updated.enabled) {
                    CronJobScheduler.scheduleJob(ctx, updated)
                } else {
                    CronJobScheduler.cancelJob(ctx, updated.jobId)
                }
            }
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            CronJobRepository.delete(jobId)
            appContextRef?.let { CronJobScheduler.cancelJob(it, jobId) }
        }
    }

    fun createJob(name: String, cronExpression: String, runAt: String, note: String, runOnce: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val jobId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val nextRunTime = when {
                runAt.isNotBlank() -> runCatching {
                    java.time.OffsetDateTime.parse(runAt).toInstant().toEpochMilli()
                }.getOrDefault(now + 60_000L)
                cronExpression.isNotBlank() -> CronExpressionParser.nextFireTime(cronExpression, now, "")
                else -> now + 60_000L
            }
            val payload = org.json.JSONObject().apply {
                put("note", note)
                if (runAt.isNotBlank()) put("run_at", runAt)
            }
            val job = CronJob(
                jobId = jobId,
                name = name.ifBlank { "Unnamed Task" },
                description = note,
                jobType = "active_agent",
                cronExpression = cronExpression,
                payloadJson = payload.toString(),
                enabled = true,
                runOnce = runOnce,
                status = "scheduled",
                nextRunTime = nextRunTime,
                createdAt = now,
                updatedAt = now,
            )
            CronJobRepository.create(job)
            appContextRef?.let { CronJobScheduler.scheduleJob(it, job) }
        }
    }

    companion object {
        /** Set by the Screen composable to give scheduling access. */
        @Volatile
        internal var appContextRef: android.content.Context? = null
    }
}
