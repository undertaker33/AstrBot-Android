package com.astrbot.android.feature.cron.data

import android.content.Context
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.feature.cron.runtime.CronJobScheduler

@Suppress("DEPRECATION")
/**
 * Compat-only adapter for targeted tests and transitional callers.
 * Production mainline should use a Hilt-owned cron scheduler port.
 */
@Deprecated(
    "Compat-only seam. Production mainline should use a Hilt-owned CronSchedulerPort.",
    level = DeprecationLevel.WARNING,
)
class LegacyCronSchedulerAdapter(
    private val contextProvider: () -> Context?,
) : CronSchedulerPort {

    override fun schedule(job: CronJob) {
        val context = contextProvider() ?: return
        CronJobScheduler.scheduleJob(context, job)
    }

    override fun cancel(jobId: String) {
        val context = contextProvider() ?: return
        CronJobScheduler.cancelJob(context, jobId)
    }
}
