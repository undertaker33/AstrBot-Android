package com.astrbot.android.feature.cron.domain

data class CronJobRunNowResult(
    val success: Boolean,
    val status: String,
    val message: String,
    val errorCode: String = "",
)

fun interface CronJobRunNowPort {
    suspend fun runNow(jobId: String): CronJobRunNowResult
}
