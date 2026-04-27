package com.astrbot.android.feature.cron.domain.model

data class CronJobExecutionRecord(
    val executionId: String = "",
    val jobId: String = "",
    val status: String = "",
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val durationMs: Long = 0L,
    val attempt: Int = 0,
    val trigger: String = "",
    val errorCode: String = "",
    val errorMessage: String = "",
    val deliverySummary: String = "",
)
