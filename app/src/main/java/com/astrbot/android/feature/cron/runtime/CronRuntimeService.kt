package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityCreateTaskRequest
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityRuntimeFacade
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTargetContext
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTaskCreation
import javax.inject.Inject

class CronRuntimeService @Inject constructor(
    private val facade: ActiveCapabilityRuntimeFacade,
) : ActiveCapabilityTaskPort {
    override suspend fun createFutureTask(request: CronTaskCreateRequest): CronTaskCreateResult {
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
