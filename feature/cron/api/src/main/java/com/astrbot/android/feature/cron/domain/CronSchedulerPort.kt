package com.astrbot.android.feature.cron.domain

import com.astrbot.android.feature.cron.domain.model.CronJob

interface CronSchedulerPort {
    fun schedule(job: CronJob)
    fun cancel(jobId: String)
    fun cancelAll()
}
