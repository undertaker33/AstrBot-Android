package com.astrbot.android.feature.cron.runtime

import android.content.Context
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.model.CronJob

class AndroidCronSchedulerPort(
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
