package com.astrbot.android.feature.cron.data

import android.content.Context
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.feature.cron.runtime.CronJobScheduler
import com.astrbot.android.model.CronJob

class FeatureCronSchedulerPortAdapter(
    private val appContext: Context,
) : CronSchedulerPort {

    override fun schedule(job: CronJob) {
        CronJobScheduler.scheduleJob(appContext, job)
    }

    override fun cancel(jobId: String) {
        CronJobScheduler.cancelJob(appContext, jobId)
    }

    override fun cancelAll() {
        CronJobScheduler.cancelAll(appContext)
    }
}
