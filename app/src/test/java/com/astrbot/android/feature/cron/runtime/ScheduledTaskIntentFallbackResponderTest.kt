package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.feature.plugin.runtime.LlmPipelineAdmission
import com.astrbot.android.feature.plugin.runtime.PluginLlmResponse
import com.astrbot.android.feature.plugin.runtime.PluginMessageEventResult
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessageDto
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessagePartDto
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessageRole
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2EventResultCoordinator
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderInvocationResult
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityNaturalLanguageParser
import com.astrbot.android.model.plugin.AppChatLlm
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledTaskIntentFallbackResponderTest {
    @Test
    fun creates_task_and_asks_llm_for_final_reply_when_model_skips_create_future_task() = runBlocking {
        val port = FallbackRecordingTaskPort()
        val responder = ScheduledTaskIntentFallbackResponder(
            intentGuard = ScheduledTaskIntentGuard(
                taskPort = port,
                naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
                promptStrings = TestActiveCapabilityPromptStrings,
                clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
            ),
            promptStrings = TestActiveCapabilityPromptStrings,
        )
        var followupRequest: PluginProviderRequest? = null

        val result = responder.applyFallbackIfNeeded(
            userText = "\u0035\u0030\u5206\u63d0\u9192\u559d\u6c34",
            context = guardContext(),
            pipelineResult = pipelineResult(executedToolNames = emptyList()),
            invokeProvider = { request ->
                followupRequest = request
                PluginV2ProviderInvocationResult.NonStreaming(
                    PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "\u597d\u7684\uff0c\u5df2\u7ecf\u8bbe\u7f6e\u597d\u4e86\u3002",
                    ),
                )
            },
        )

        assertEquals(1, port.requests.size)
        assertEquals("\u597d\u7684\uff0c\u5df2\u7ecf\u8bbe\u7f6e\u597d\u4e86\u3002", result.sendableResult.text)
        val request = requireNotNull(followupRequest)
        assertTrue(request.requestId.endsWith("-scheduled-fallback"))
        assertTrue(request.tools.isEmpty())
        assertFalse(request.streamingEnabled)
        assertTrue(request.systemPrompt.orEmpty().contains("Base persona instruction."))
        assertTrue(request.systemPrompt.orEmpty().contains("host_scheduled_task_fallback"))
        assertTrue(request.systemPrompt.orEmpty().contains("created"))
        assertFalse(request.messages.any { message -> message.role == PluginProviderMessageRole.SYSTEM })
    }

    @Test
    fun does_not_create_fallback_when_model_already_called_create_future_task() = runBlocking {
        val port = FallbackRecordingTaskPort()
        val responder = ScheduledTaskIntentFallbackResponder(
            intentGuard = ScheduledTaskIntentGuard(
                taskPort = port,
                naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
                promptStrings = TestActiveCapabilityPromptStrings,
                clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
            ),
            promptStrings = TestActiveCapabilityPromptStrings,
        )
        val original = pipelineResult(executedToolNames = listOf("create_future_task"))

        val result = responder.applyFallbackIfNeeded(
            userText = "\u0035\u0030\u5206\u63d0\u9192\u559d\u6c34",
            context = guardContext(),
            pipelineResult = original,
            invokeProvider = { error("No follow-up provider call expected.") },
        )

        assertSame(original, result)
        assertEquals(0, port.requests.size)
    }

    @Test
    fun creates_recurring_task_and_asks_llm_for_final_reply_when_model_skips_create_future_task() = runBlocking {
        val port = FallbackRecordingTaskPort()
        val responder = ScheduledTaskIntentFallbackResponder(
            intentGuard = ScheduledTaskIntentGuard(
                taskPort = port,
                naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
                promptStrings = TestActiveCapabilityPromptStrings,
                clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
            ),
            promptStrings = TestActiveCapabilityPromptStrings,
        )
        var followupRequest: PluginProviderRequest? = null

        val result = responder.applyFallbackIfNeeded(
            userText = "\u6bcf\u5929\u65e9\u4e0a\u0038\u70b9\u63d0\u9192\u6211\u559d\u6c34",
            context = guardContext(),
            pipelineResult = pipelineResult(executedToolNames = emptyList()),
            invokeProvider = { request ->
                followupRequest = request
                PluginV2ProviderInvocationResult.NonStreaming(
                    PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "\u597d\u7684\uff0c\u5df2\u8bbe\u7f6e\u6210\u6bcf\u5929\u65e9\u4e0a\u63d0\u9192\u4f60\u3002",
                    ),
                )
            },
        )

        assertEquals(1, port.requests.size)
        assertEquals(false, port.requests.single().payload["run_once"])
        assertEquals("0 8 * * *", port.requests.single().payload["cron_expression"])
        assertEquals("\u597d\u7684\uff0c\u5df2\u8bbe\u7f6e\u6210\u6bcf\u5929\u65e9\u4e0a\u63d0\u9192\u4f60\u3002", result.sendableResult.text)
        val request = requireNotNull(followupRequest)
        assertTrue(request.tools.isEmpty())
        assertTrue(request.systemPrompt.orEmpty().contains("Base persona instruction."))
        assertTrue(request.systemPrompt.orEmpty().contains("host_scheduled_task_fallback"))
        assertTrue(request.systemPrompt.orEmpty().contains("created"))
        assertFalse(request.messages.any { message -> message.role == PluginProviderMessageRole.SYSTEM })
    }

    private fun guardContext() = ScheduledTaskIntentGuardContext(
        proactiveEnabled = true,
        platform = "qq_onebot",
        conversationId = "friend:934457024",
        botId = "bot-1",
        configProfileId = "config-1",
        personaId = "persona-1",
        providerId = "deepseek-chat",
    )

    private fun pipelineResult(executedToolNames: List<String>): PluginV2LlmPipelineResult {
        val request = PluginProviderRequest(
            requestId = "req-1",
            availableProviderIds = listOf("deepseek-chat"),
            availableModelIdsByProvider = mapOf("deepseek-chat" to listOf("deepseek-chat")),
            conversationId = "friend:934457024",
            messageIds = listOf("msg-1"),
            llmInputSnapshot = "\u0035\u0030\u5206\u63d0\u9192\u559d\u6c34",
            selectedProviderId = "deepseek-chat",
            selectedModelId = "deepseek-chat",
            systemPrompt = "Base persona instruction.",
            messages = listOf(
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.USER,
                    parts = listOf(
                        PluginProviderMessagePartDto.TextPart("\u0035\u0030\u5206\u63d0\u9192\u559d\u6c34"),
                    ),
                ),
            ),
            tools = emptyList(),
        )
        val response = PluginLlmResponse(
            requestId = "req-1",
            providerId = "deepseek-chat",
            modelId = "deepseek-chat",
            text = "\u6211\u4f1a\u63d0\u9192\u4f60\u3002",
        )
        return PluginV2LlmPipelineResult(
            admission = LlmPipelineAdmission(
                requestId = "req-1",
                conversationId = "friend:934457024",
                messageIds = listOf("msg-1"),
                llmInputSnapshot = "\u0035\u0030\u5206\u63d0\u9192\u559d\u6c34",
                routingTarget = AppChatLlm.AppChat,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ),
            finalRequest = request,
            finalResponse = response,
            sendableResult = PluginMessageEventResult(
                requestId = "req-1",
                conversationId = "friend:934457024",
                text = response.text,
            ),
            hookInvocationTrace = emptyList(),
            decoratingRunResult = PluginV2EventResultCoordinator.DecoratingRunResult(
                finalResult = PluginMessageEventResult(
                    requestId = "req-1",
                    conversationId = "friend:934457024",
                    text = response.text,
                ),
                appliedHandlerIds = emptyList(),
            ),
            executedToolNames = executedToolNames,
        )
    }
}

private class FallbackRecordingTaskPort : ActiveCapabilityTaskPort {
    val requests = mutableListOf<CronTaskCreateRequest>()

    override suspend fun createFutureTask(request: CronTaskCreateRequest): CronTaskCreateResult {
        requests += request
        return CronTaskCreateResult.Created("job-${requests.size}")
    }
}
