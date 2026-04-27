package com.astrbot.android.feature.cron.domain

data class CronTaskCreateRequest(
    val payload: Map<String, Any>,
    val targetPlatform: String,
    val targetConversationId: String,
    val targetBotId: String,
    val targetConfigProfileId: String,
    val targetPersonaId: String,
    val targetProviderId: String,
    val targetOrigin: String,
)

sealed interface CronTaskCreateResult {
    data class Created(val jobId: String) : CronTaskCreateResult
    data class Failed(val code: String, val message: String) : CronTaskCreateResult
}

interface ActiveCapabilityTaskPort {
    suspend fun createFutureTask(request: CronTaskCreateRequest): CronTaskCreateResult
}
