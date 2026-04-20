package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.context.RuntimeIngressEvent
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.core.runtime.context.SenderInfo
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryResult
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.qq.runtime.QqScheduledMessageSender
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginTriggerSource

internal data class ScheduledTaskRuntimeDependencies(
    val llmClient: LlmClientPort,
    val botPort: BotRepositoryPort,
    val conversationPort: ConversationRepositoryPort,
    val orchestrator: RuntimeLlmOrchestratorPort,
    val runtimeContextResolverPort: RuntimeContextResolverPort,
    val qqScheduledMessageSender: QqScheduledMessageSender,
    val appChatPluginRuntime: AppChatLlmPipelineRuntime,
    val hostCapabilityGateway: PluginHostCapabilityGateway,
)

internal object ScheduledTaskRuntimeExecutor {

    suspend fun execute(
        context: CronJobExecutionContext,
        runtimeDependencies: ScheduledTaskRuntimeDependencies,
    ): CronJobDeliverySummary {
        val conversationPort = runtimeDependencies.conversationPort
        val note = context.note.trim().ifBlank { context.description.trim() }
        if (note.isBlank()) {
            throw CronJobExecutionFailure(
                code = "empty_note",
                retryable = false,
                message = "Scheduled task note is empty for job=${context.jobId}",
            )
        }

        require(context.platform.isNotBlank()) { "Scheduled task missing platform for job=${context.jobId}" }
        require(context.conversationId.isNotBlank() || context.sessionId.isNotBlank()) {
            "Scheduled task missing conversation target for job=${context.jobId}"
        }
        require(context.botId.isNotBlank()) { "Scheduled task missing bot target for job=${context.jobId}" }
        require(context.configProfileId.isNotBlank()) { "Scheduled task missing config profile for job=${context.jobId}" }
        require(context.providerId.isNotBlank()) { "Scheduled task missing provider target for job=${context.jobId}" }

        val platform = resolvePlatform(context.platform)
        val conversationId = resolveConversationId(context)
        val bot = resolveBot(runtimeDependencies.botPort, context.botId)
        require(bot.configProfileId == context.configProfileId) {
            "Scheduled task config mismatch for job=${context.jobId}: bot=${bot.id} config=${bot.configProfileId} payload=${context.configProfileId}"
        }

        val userMessageId = conversationPort.appendMessage(
            sessionId = conversationId,
            role = "user",
            content = note,
        )
        val userMessage = conversationPort.session(conversationId).messages
            .firstOrNull { it.id == userMessageId }
            ?: ConversationMessage(
                id = userMessageId,
                role = "user",
                content = note,
                timestamp = System.currentTimeMillis(),
            )

        val ingressEvent = RuntimeIngressEvent(
            platform = platform,
            conversationId = conversationId,
            messageId = "cron:${context.jobId}",
            sender = SenderInfo(userId = "cron:${context.jobId}", nickname = "scheduled-task"),
            messageType = resolveMessageType(platform, conversationId),
            text = note,
            trigger = com.astrbot.android.core.runtime.context.IngressTrigger.SCHEDULED_TASK,
            rawPlatformPayload = mapOf(
                "jobId" to context.jobId,
                "trigger" to PluginTriggerSource.BeforeSendMessage.wireValue,
                "scheduledTask" to mapOf(
                    "jobId" to context.jobId,
                    "name" to context.name,
                    "description" to context.description,
                    "note" to note,
                    "jobType" to context.jobType,
                    "sessionId" to context.sessionId,
                    "platform" to context.platform,
                    "conversationId" to context.conversationId,
                    "botId" to context.botId,
                    "configProfileId" to context.configProfileId,
                    "personaId" to context.personaId,
                    "providerId" to context.providerId,
                    "origin" to context.origin,
                    "runOnce" to context.runOnce,
                    "runAt" to context.runAt,
                ),
            ),
        )

        val resolvedContext = runtimeDependencies.runtimeContextResolverPort.resolve(
            event = ingressEvent,
            bot = bot,
            overrideProviderId = context.providerId.takeIf { it.isNotBlank() },
            overridePersonaId = context.personaId.takeIf { it.isNotBlank() },
        )

        val callbacks = ScheduledTaskLlmCallbacksFactory(
            conversationPort = conversationPort,
            providerInvocationService = ScheduledTaskProviderInvocationService(runtimeDependencies.llmClient),
            qqScheduledMessageSender = runtimeDependencies.qqScheduledMessageSender,
            hostCapabilityGateway = runtimeDependencies.hostCapabilityGateway,
        ).create(
            context = context,
            platform = platform,
            conversationId = conversationId,
            bot = bot,
        )

        val deliveryResult = runtimeDependencies.orchestrator.dispatchLlm(
            ctx = resolvedContext,
            llmRuntime = runtimeDependencies.appChatPluginRuntime,
            callbacks = callbacks,
            userMessage = userMessage,
        )
        if (deliveryResult is PluginV2HostLlmDeliveryResult.SendFailed) {
            error(deliveryResult.sendResult.errorSummary.ifBlank { "scheduled_task_send_failed" })
        }
        AppLogger.append(
            "CronJobBridge: job=${context.jobId} completed with ${deliveryResult::class.simpleName.orEmpty()} conversation=$conversationId",
        )
        return when (deliveryResult) {
            is PluginV2HostLlmDeliveryResult.Sent -> CronJobDeliverySummary(
                platform = context.platform,
                conversationId = conversationId,
                deliveredMessageCount = deliveryResult.preparedReply.deliveredEntries.size.coerceAtLeast(1),
                receiptIds = deliveryResult.sendResult.receiptIds,
                textPreview = deliveryResult.preparedReply.text.take(160),
            )
            is PluginV2HostLlmDeliveryResult.Suppressed -> {
                AppLogger.append(
                    "CronJobBridge: job=${context.jobId} suppressed without sending conversation=$conversationId",
                )
                throw CronJobExecutionFailure(
                    code = "scheduled_task_suppressed",
                    retryable = false,
                    message = "Scheduled task completed without sending a reminder for job=${context.jobId}",
                )
            }
            is PluginV2HostLlmDeliveryResult.SendFailed -> error("unreachable")
        }
    }

    private fun resolvePlatform(platform: String): RuntimePlatform {
        return when (platform.trim().lowercase()) {
            "qq",
            "onebot",
            RuntimePlatform.QQ_ONEBOT.wireValue -> RuntimePlatform.QQ_ONEBOT
            else -> RuntimePlatform.APP_CHAT
        }
    }

    private fun resolveConversationId(context: CronJobExecutionContext): String {
        return context.conversationId.takeIf { it.isNotBlank() }
            ?: context.sessionId.takeIf { it.isNotBlank() }
            ?: error("Scheduled task missing conversation target for job=${context.jobId}")
    }

    private fun resolveMessageType(platform: RuntimePlatform, conversationId: String): MessageType {
        if (platform != RuntimePlatform.QQ_ONEBOT) return MessageType.OtherMessage
        return if (conversationId.startsWith("group:")) {
            MessageType.GroupMessage
        } else {
            MessageType.FriendMessage
        }
    }

    private fun resolveBot(botPort: BotRepositoryPort, botId: String): BotProfile {
        val snapshot = botPort.snapshotProfiles()
        return snapshot.firstOrNull { it.id == botId && it.autoReplyEnabled }
            ?: error("Scheduled task bot not found or auto reply disabled: $botId")
    }
}

