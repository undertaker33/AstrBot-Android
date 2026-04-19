package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.plugin.runtime.DefaultPluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PlatformLlmCallbacks
import com.astrbot.android.feature.plugin.runtime.PluginMessageEventResult
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2AfterSentView
import com.astrbot.android.feature.plugin.runtime.PluginV2FollowupSender
import com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderInvocationResult
import com.astrbot.android.feature.qq.runtime.QqOneBotBridgeServer
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.plugin.PluginV2StreamingMode

internal class ScheduledTaskLlmCallbacksFactory(
    private val conversationPort: ConversationRepositoryPort,
    private val providerInvocationService: ScheduledTaskProviderInvocationService,
) {
    fun create(
        context: CronJobExecutionContext,
        platform: RuntimePlatform,
        conversationId: String,
        bot: BotProfile,
    ): PlatformLlmCallbacks {
        return object : PlatformLlmCallbacks {
            override val platformInstanceKey: String = "cron:${context.jobId}"
            override val hostCapabilityGateway = DefaultPluginHostCapabilityGateway()
            override val followupSender: PluginV2FollowupSender? = null

            override suspend fun prepareReply(
                result: PluginV2LlmPipelineResult,
            ): PluginV2HostPreparedReply {
                val sendable = result.sendableResult
                return PluginV2HostPreparedReply(
                    text = sendable.text,
                    attachments = sendable.attachments.toConversationAttachments(),
                    deliveredEntries = listOf(
                        PluginV2AfterSentView.DeliveredEntry(
                            entryId = result.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                            entryType = "assistant",
                            textPreview = sendable.text.take(160),
                            attachmentCount = sendable.attachments.size,
                        ),
                    ),
                )
            }

            override suspend fun sendReply(prepared: PluginV2HostPreparedReply): PluginV2HostSendResult {
                return if (platform == RuntimePlatform.QQ_ONEBOT) {
                    QqOneBotBridgeServer.sendScheduledMessage(
                        conversationId = conversationId,
                        text = prepared.text,
                        attachments = prepared.attachments,
                        botId = bot.id,
                    ).toHostSendResult()
                } else {
                    PluginV2HostSendResult(success = true)
                }
            }

            override suspend fun persistDeliveredReply(
                prepared: PluginV2HostPreparedReply,
                sendResult: PluginV2HostSendResult,
                pipelineResult: PluginV2LlmPipelineResult,
            ) {
                if (!sendResult.success) return
                conversationPort.appendMessage(
                    sessionId = conversationId,
                    role = "assistant",
                    content = prepared.text,
                    attachments = prepared.attachments,
                )
            }

            override suspend fun invokeProvider(
                request: PluginProviderRequest,
                mode: PluginV2StreamingMode,
                ctx: ResolvedRuntimeContext,
            ): PluginV2ProviderInvocationResult {
                return providerInvocationService.invokeProvider(
                    request = request,
                    mode = mode,
                    ctx = ctx,
                )
            }
        }
    }

    private fun List<PluginMessageEventResult.Attachment>.toConversationAttachments(): List<ConversationAttachment> {
        return mapIndexed { index, attachment ->
            ConversationAttachment(
                id = "cron-llm-result-$index-${attachment.uri.hashCode()}",
                type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                remoteUrl = attachment.uri,
            )
        }
    }

    private fun com.astrbot.android.feature.qq.runtime.OneBotSendResult.toHostSendResult(): PluginV2HostSendResult {
        return PluginV2HostSendResult(
            success = success,
            receiptIds = receiptIds,
            errorSummary = errorSummary,
        )
    }
}
