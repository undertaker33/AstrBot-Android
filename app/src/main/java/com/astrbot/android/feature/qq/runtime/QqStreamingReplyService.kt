package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.core.runtime.llm.LlmResponseSegmenter
import com.astrbot.android.core.runtime.search.WebSearchTriggerIntent
import com.astrbot.android.core.runtime.search.WebSearchTriggerRules
import com.astrbot.android.feature.plugin.runtime.PluginMessageEventResult
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessagePartDto
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessageRole
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineResult
import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqReplyPayload
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.delay

internal class QqStreamingReplyService(
    private val replySender: QqReplySender,
    private val synthesizeSpeech: (ProviderProfile, String, String, Boolean) -> ConversationAttachment,
    private val log: (String) -> Unit = {},
) {
    fun prepareReply(
        result: PluginV2LlmPipelineResult,
        sessionId: String,
        config: ConfigProfile,
        wantsTts: Boolean,
        ttsProvider: ProviderProfile?,
    ): PluginV2HostPreparedReply {
        val sendableResult = result.sendableResult
        val decoratedAttachments = sendableResult.attachments.toConversationAttachments()
        val assistantAttachments = if (wantsTts && ttsProvider != null) {
            buildVoiceReplyAttachments(
                provider = ttsProvider,
                response = sendableResult.text,
                config = config,
            )
        } else {
            decoratedAttachments
        }
        val outboundBlocked = config.keywordDetectionEnabled &&
            QqKeywordDetector(config.keywordPatterns).matches(sendableResult.text)
        val outboundText = if (outboundBlocked) {
            log("QQ outbound keyword blocked: session=$sessionId")
            KEYWORD_BLOCK_NOTICE
        } else {
            sendableResult.text
        }
        val outboundAttachments = if (outboundBlocked) emptyList() else assistantAttachments
        log(
            buildPreparedReplyLog(
                requestId = result.admission.requestId,
                text = outboundText,
                attachmentCount = outboundAttachments.size,
                hookTrace = result.hookInvocationTrace,
                decoratingHandlers = result.decoratingRunResult.appliedHandlerIds,
            ),
        )
        val deliveryTags = if (result.hasDirectNewsDelivery()) {
            setOf(NEWS_COMMENTARY_FOLLOWUP_TAG)
        } else {
            emptySet()
        }
        return PluginV2HostPreparedReply(
            text = outboundText,
            attachments = outboundAttachments,
            deliveryTags = deliveryTags,
            deliveredEntries = listOf(
                com.astrbot.android.feature.plugin.runtime.PluginV2AfterSentView.DeliveredEntry(
                    entryId = result.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                    entryType = "assistant",
                    textPreview = outboundText.take(160),
                    attachmentCount = outboundAttachments.size,
                ),
            ),
        )
    }

    suspend fun sendPreparedReply(
        message: IncomingQqMessage,
        prepared: PluginV2HostPreparedReply,
        config: ConfigProfile,
        streamingMode: PluginV2StreamingMode,
    ): PluginV2HostSendResult {
        return if (
            prepared.attachments.size > 1 &&
            prepared.attachments.all { attachment -> attachment.type == "audio" }
        ) {
            sendStreamingVoiceReplyWithOutcome(
                message = message,
                attachments = prepared.attachments,
                config = config,
            )
        } else if (
            prepared.attachments.isEmpty() &&
            WebSearchTriggerRules.evaluate(message.text).intent == WebSearchTriggerIntent.NEWS &&
            NEWS_COMMENTARY_FOLLOWUP_TAG !in prepared.deliveryTags
        ) {
            sendNewsReplyWithOutcome(
                message = message,
                response = prepared.text,
            )
        } else if (
            streamingMode != PluginV2StreamingMode.NON_STREAM &&
            prepared.attachments.isEmpty()
        ) {
            sendPseudoStreamingReplyWithOutcome(
                message = message,
                response = prepared.text,
                config = config,
            )
        } else {
            sendReplyWithOutcome(
                message = message,
                text = prepared.text,
                attachments = prepared.attachments,
            )
        }
    }

    fun deliverNewsSearchResultIfNeeded(
        message: IncomingQqMessage,
        toolName: String,
        result: PluginToolResult,
    ): PluginToolResult {
        if (toolName != WEB_SEARCH_TOOL_NAME) return result
        if (result.status != PluginToolResultStatus.SUCCESS) return result
        if (WebSearchTriggerRules.evaluate(message.text).intent != WebSearchTriggerIntent.NEWS) return result

        val factText = QqNewsReplyFormatter.formatSearchResultFacts(result.structuredContent)
        val sendResult = sendNewsReplyWithOutcome(
            message = message,
            response = factText,
        )
        log(
            "QQ news facts direct delivery: target=${message.conversationId} success=${sendResult.success} " +
                "chars=${factText.length}",
        )
        return rewriteNewsSearchResultForCommentary(
            result = result,
            factText = factText,
            sent = sendResult.success,
        )
    }

    private fun sendNewsReplyWithOutcome(
        message: IncomingQqMessage,
        response: String,
    ): PluginV2HostSendResult {
        val segments = QqNewsReplyFormatter.formatSegments(response)
        if (segments.isEmpty()) {
            return sendReplyWithOutcome(message, response)
        }
        log(
            "QQ news reply segmented: target=${message.conversationId} segments=${segments.size} chars=${response.length}",
        )
        val receiptIds = mutableListOf<String>()
        for (segment in segments) {
            val sendResult = sendReplyWithOutcome(message, segment)
            if (!sendResult.success) {
                return sendResult
            }
            receiptIds += sendResult.receiptIds
        }
        return PluginV2HostSendResult(success = true, receiptIds = receiptIds)
    }

    fun buildVoiceReplyAttachments(
        provider: ProviderProfile,
        response: String,
        config: ConfigProfile,
    ): List<ConversationAttachment> {
        if (!config.voiceStreamingEnabled) {
            return synthesizeSingleVoiceReply(
                provider = provider,
                response = response,
                voiceId = config.ttsVoiceId,
                readBracketedContent = config.ttsReadBracketedContent,
            )?.let(::listOf).orEmpty()
        }
        val segments = LlmResponseSegmenter.splitForVoiceStreaming(response)
        if (segments.size <= 1) {
            return synthesizeSingleVoiceReply(
                provider = provider,
                response = response,
                voiceId = config.ttsVoiceId,
                readBracketedContent = config.ttsReadBracketedContent,
            )?.let(::listOf).orEmpty()
        }
        val streamedAttachments = mutableListOf<ConversationAttachment>()
        for (segment in segments) {
            val attachment = synthesizeSingleVoiceReply(
                provider = provider,
                response = segment,
                voiceId = config.ttsVoiceId,
                readBracketedContent = config.ttsReadBracketedContent,
            ) ?: return synthesizeSingleVoiceReply(
                provider = provider,
                response = response,
                voiceId = config.ttsVoiceId,
                readBracketedContent = config.ttsReadBracketedContent,
            )?.let(::listOf).orEmpty()
            streamedAttachments += attachment
        }
        log("QQ voice streaming prepared: provider=${provider.name} segments=${streamedAttachments.size}")
        return streamedAttachments
    }

    private suspend fun sendPseudoStreamingReplyWithOutcome(
        message: IncomingQqMessage,
        response: String,
        config: ConfigProfile,
    ): PluginV2HostSendResult {
        val segments = LlmResponseSegmenter.split(
            text = response,
            stripTrailingBoundaryPunctuation = true,
        )
        if (segments.isEmpty()) {
            return sendReplyWithOutcome(message, response)
        }
        log(
            "QQ pseudo streaming started: target=${message.conversationId} segments=${segments.size} chars=${response.length}",
        )
        val receiptIds = mutableListOf<String>()
        segments.forEachIndexed { index, segment ->
            val sendResult = sendReplyWithOutcome(message, segment)
            if (!sendResult.success) {
                return sendResult
            }
            receiptIds += sendResult.receiptIds
            if (index < segments.lastIndex) {
                delay(streamingDelayMs(config))
            }
        }
        return PluginV2HostSendResult(success = true, receiptIds = receiptIds)
    }

    private suspend fun sendStreamingVoiceReplyWithOutcome(
        message: IncomingQqMessage,
        attachments: List<ConversationAttachment>,
        config: ConfigProfile,
    ): PluginV2HostSendResult {
        log("QQ voice streaming started: target=${message.conversationId} segments=${attachments.size}")
        val receiptIds = mutableListOf<String>()
        attachments.forEachIndexed { index, attachment ->
            val sendResult = sendReplyWithOutcome(
                message = message,
                text = "",
                attachments = listOf(attachment),
            )
            if (!sendResult.success) {
                return sendResult
            }
            receiptIds += sendResult.receiptIds
            if (index < attachments.lastIndex) {
                delay(streamingDelayMs(config))
            }
        }
        return PluginV2HostSendResult(success = true, receiptIds = receiptIds)
    }

    private fun sendReplyWithOutcome(
        message: IncomingQqMessage,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): PluginV2HostSendResult {
        return replySender.sendWithOutcome(
            QqReplyPayload(
                conversationId = message.conversationId,
                messageType = message.messageType,
                text = text,
                attachments = attachments,
            ),
            message,
        )
    }

    private fun synthesizeSingleVoiceReply(
        provider: ProviderProfile,
        response: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment? {
        return runCatching {
            synthesizeSpeech(
                provider,
                response,
                voiceId,
                readBracketedContent,
            )
        }.onFailure { error ->
            log("QQ TTS failed: ${error.message ?: error.javaClass.simpleName}")
        }.onSuccess { attachment ->
            log("QQ TTS success: provider=${provider.name} mime=${attachment.mimeType} size=${attachment.base64Data.length}")
        }.getOrNull()
    }

    private fun streamingDelayMs(config: ConfigProfile): Long {
        return config.streamingMessageIntervalMs.coerceIn(0, 5000).toLong()
    }

    private fun buildPreparedReplyLog(
        requestId: String,
        text: String,
        attachmentCount: Int,
        hookTrace: List<String>,
        decoratingHandlers: List<String>,
    ): String {
        val compactText = text.replace("\r", " ")
            .replace("\n", "\\n")
            .take(240)
        val leakedTag = Regex("\\[[a-zA-Z0-9_-]+]$").containsMatchIn(text.trim())
        return buildString {
            append("QQ prepared reply: request=").append(requestId)
            append(" attachments=").append(attachmentCount)
            append(" leakedTag=").append(leakedTag)
            append(" hooks=").append(hookTrace.joinToString(separator = ",").ifBlank { "none" })
            append(" decorators=").append(decoratingHandlers.joinToString(separator = ",").ifBlank { "none" })
            append(" text=\"").append(compactText).append('"')
        }
    }

    private fun PluginV2LlmPipelineResult.hasDirectNewsDelivery(): Boolean {
        return finalRequest.messages.any { message ->
            message.role == PluginProviderMessageRole.TOOL &&
                message.parts
                    .filterIsInstance<PluginProviderMessagePartDto.TextPart>()
                    .any { part -> NEWS_DIRECT_DELIVERY_MARKER in part.text }
        }
    }

    private fun rewriteNewsSearchResultForCommentary(
        result: PluginToolResult,
        factText: String,
        sent: Boolean,
    ): PluginToolResult {
        val originalText = result.text.orEmpty()
        val rewrittenText = buildString {
            if (originalText.isNotBlank()) {
                appendLine(originalText)
                appendLine()
            }
            appendLine(NEWS_DIRECT_DELIVERY_MARKER)
            appendLine("The factual news summary has already been sent to the user by the host.")
            appendLine("Direct delivery status: ${if (sent) "sent" else "failed"}.")
            appendLine("Sent summary:")
            appendLine(factText)
            appendLine()
            appendLine("Do not repeat the news items, sources, dates, URLs, or numeric details.")
            append("Only provide a brief evaluation or commentary in the configured persona. ")
            append("If the summary says no confirmed news was found, evaluate that uncertainty and do not invent facts.")
        }.trim()
        val metadata = buildMap<String, Any?> {
            result.metadata?.let(::putAll)
            put("direct_news_delivery", sent)
            put("commentary_only_followup", true)
        }
        return PluginToolResult(
            toolCallId = result.toolCallId,
            requestId = result.requestId,
            toolId = result.toolId,
            status = result.status,
            errorCode = result.errorCode,
            text = rewrittenText,
            structuredContent = result.structuredContent,
            metadata = metadata,
        )
    }

    private fun List<PluginMessageEventResult.Attachment>.toConversationAttachments(): List<ConversationAttachment> {
        return mapIndexed { index, attachment ->
            ConversationAttachment(
                id = "llm-result-$index-${attachment.uri.hashCode()}",
                type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                remoteUrl = attachment.uri,
            )
        }
    }

    companion object {
        internal const val NEWS_COMMENTARY_FOLLOWUP_TAG = "news_commentary_followup"
        private const val WEB_SEARCH_TOOL_NAME = "web_search"
        private const val NEWS_DIRECT_DELIVERY_MARKER = "[host_direct_news_delivery]"
        private const val KEYWORD_BLOCK_NOTICE = "你的消息或者大模型的响应中包含不适当的内容，已被屏蔽。"
    }
}
