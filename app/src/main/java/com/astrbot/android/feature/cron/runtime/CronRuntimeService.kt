package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityCreateTaskRequest
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityRuntimeFacade
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityScheduler
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityTargetContext
import com.astrbot.android.runtime.plugin.toolsource.ActiveCapabilityTaskCreation
import com.astrbot.android.model.CronJob

class CronRuntimeService(
    private val schedulerPort: CronSchedulerPort,
) : ActiveCapabilityTaskPort {

    override suspend fun createFutureTask(request: CronTaskCreateRequest): CronTaskCreateResult {
        val facade = ActiveCapabilityRuntimeFacade(
            scheduler = object : ActiveCapabilityScheduler {
                override fun schedule(job: CronJob) = schedulerPort.schedule(job)
                override fun cancel(jobId: String) = schedulerPort.cancel(jobId)
            },
        )
        val runtimeRequest = ActiveCapabilityCreateTaskRequest(
            payload = request.payload,
            metadata = null,
            toolSourceContext = null,
            targetContext = ActiveCapabilityTargetContext(
                platform = request.targetPlatform,
                conversationId = request.targetConversationId,
                botId = request.targetBotId,
                configProfileId = request.targetConfigProfileId,
                personaId = request.targetPersonaId,
                providerId = request.targetProviderId,
                origin = request.targetOrigin,
            ),
        )
        return when (val result = facade.createFutureTask(runtimeRequest)) {
            is ActiveCapabilityTaskCreation.Created ->
                CronTaskCreateResult.Created(result.job.jobId)
            is ActiveCapabilityTaskCreation.Failed ->
                CronTaskCreateResult.Failed(result.error.code, result.error.message)
        }
    }
}
