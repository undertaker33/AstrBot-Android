package com.astrbot.android.feature.chat.domain

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendAppMessageUseCaseTest {

    @Test
    fun send_rejects_empty_input_without_creating_messages() = runTest {
        val fakeConversations = FakeConversationRepository()
        val fakeRuntime = FakeAppChatRuntime()
        val useCase = SendAppMessageUseCase(
            conversations = fakeConversations,
            runtime = fakeRuntime,
        )

        val events = useCase.send(sessionId = "s1", text = "   ", attachments = emptyList()).toList()

        assertEquals(1, events.size)
        assertTrue(events[0] is SendAppMessageEvent.Rejected)
        assertEquals(0, fakeConversations.appendCalls.size)
    }

    @Test
    fun send_creates_user_and_assistant_messages_through_use_case() = runTest {
        val signals = mutableListOf<String>()
        val fakeConversations = FakeConversationRepository(signals)
        val fakeRuntime = FakeAppChatRuntime(
            events = listOf(
            AppChatRuntimeEvent.AssistantFinal("done"),
            ),
            signals = signals,
        )
        val useCase = SendAppMessageUseCase(
            conversations = fakeConversations,
            runtime = fakeRuntime,
        )

        val events = useCase.send(sessionId = "s1", text = "hello").toList()

        assertEquals(2, fakeConversations.appendCalls.size)
        assertEquals("user", fakeConversations.appendCalls[0].role)
        assertEquals("assistant", fakeConversations.appendCalls[1].role)
        assertTrue(events[0] is SendAppMessageEvent.Started)
        assertTrue(events[1] is SendAppMessageEvent.Completed)
    }

    @Test
    fun send_invokes_before_runtime_after_user_append_and_before_runtime_dispatch() = runTest {
        val signals = mutableListOf<String>()
        val fakeConversations = FakeConversationRepository(signals)
        val fakeRuntime = FakeAppChatRuntime(
            events = listOf(AppChatRuntimeEvent.AssistantFinal("done")),
            signals = signals,
        )
        val useCase = SendAppMessageUseCase(
            conversations = fakeConversations,
            runtime = fakeRuntime,
        )

        useCase.send(
            sessionId = "s1",
            text = "hello",
            beforeRuntime = { context ->
                signals += "before:${context.userMessageId}"
                assertEquals(listOf("user"), fakeConversations.appendCalls.map { it.role })
                SendAppMessageRuntimeDecision.Continue
            },
        ).toList()

        assertTrue(signals.indexOf("append:user") < signals.indexOf("before:user-1"))
        assertTrue(signals.indexOf("before:user-1") < signals.indexOf("runtime:user-1:"))
        assertTrue(signals.indexOf("runtime:user-1:") < signals.indexOf("append:assistant"))
    }

    @Test
    fun send_skips_assistant_placeholder_and_runtime_when_before_runtime_consumes_message() = runTest {
        val signals = mutableListOf<String>()
        val fakeConversations = FakeConversationRepository(signals)
        val fakeRuntime = FakeAppChatRuntime(
            events = listOf(AppChatRuntimeEvent.AssistantFinal("should not run")),
            signals = signals,
        )
        val useCase = SendAppMessageUseCase(
            conversations = fakeConversations,
            runtime = fakeRuntime,
        )

        val events = useCase.send(
            sessionId = "s1",
            text = "hello",
            beforeRuntime = {
                SendAppMessageRuntimeDecision.Skip(reason = "plugin_consumed")
            },
        ).toList()

        assertEquals(listOf("user"), fakeConversations.appendCalls.map { it.role })
        assertTrue(events.any { it is SendAppMessageEvent.RuntimeSkipped })
        assertEquals(0, fakeRuntime.sendCalls)
    }

    @Test
    fun send_throttles_small_streaming_deltas() = runTest {
        val fakeConversations = FakeConversationRepository()
        var time = 0L
        val fakeRuntime = FakeAppChatRuntime(events = listOf(
            AppChatRuntimeEvent.AssistantDelta("hi"),
            AppChatRuntimeEvent.AssistantDelta("hi there"),
            AppChatRuntimeEvent.AssistantFinal("hi there final"),
        ))
        val useCase = SendAppMessageUseCase(
            conversations = fakeConversations,
            runtime = fakeRuntime,
            updatePolicy = ChatMessageUpdatePolicy(minIntervalMs = 120L, minDeltaChars = 24),
            nowMs = { time },
        )

        val events = useCase.send(sessionId = "s1", text = "go").toList()

        // Started + first delta published (empty->hi) + final = 3 events
        // "hi there" is throttled (small delta, same time)
        val assistantUpdates = events.filterIsInstance<SendAppMessageEvent.AssistantUpdated>()
        assertEquals(1, assistantUpdates.size)
        assertEquals("hi", assistantUpdates[0].content)
    }

    @Test
    fun send_persists_final_answer_even_when_delta_was_throttled() = runTest {
        val fakeConversations = FakeConversationRepository()
        var time = 0L
        val fakeRuntime = FakeAppChatRuntime(events = listOf(
            AppChatRuntimeEvent.AssistantDelta("partial"),
            AppChatRuntimeEvent.AssistantFinal("partial complete"),
        ))
        val useCase = SendAppMessageUseCase(
            conversations = fakeConversations,
            runtime = fakeRuntime,
            nowMs = { time },
        )

        val events = useCase.send(sessionId = "s1", text = "go").toList()

        val completed = events.filterIsInstance<SendAppMessageEvent.Completed>()
        assertEquals(1, completed.size)
        assertEquals("partial complete", completed[0].content)
        // Verify final was persisted
        val finalUpdate = fakeConversations.updateCalls.last()
        assertEquals("partial complete", finalUpdate.content)
    }

    @Test
    fun send_writes_failure_message_to_assistant_placeholder() = runTest {
        val fakeConversations = FakeConversationRepository()
        val error = RuntimeException("network error")
        val fakeRuntime = FakeAppChatRuntime(events = listOf(
            AppChatRuntimeEvent.Failure("network error", error),
        ))
        val useCase = SendAppMessageUseCase(
            conversations = fakeConversations,
            runtime = fakeRuntime,
        )

        val events = useCase.send(sessionId = "s1", text = "go").toList()

        val failed = events.filterIsInstance<SendAppMessageEvent.Failed>()
        assertEquals(1, failed.size)
        assertEquals("network error", failed[0].message)
        val lastUpdate = fakeConversations.updateCalls.last()
        assertEquals("network error", lastUpdate.content)
    }

    @Test
    fun send_reuses_single_assistant_placeholder_across_mixed_runtime_events() = runTest {
        val fakeConversations = FakeConversationRepository()
        val firstAttachments = listOf(
            ConversationAttachment(
                id = "att-1",
                type = "image",
                mimeType = "image/png",
                remoteUrl = "https://example.com/1.png",
            ),
        )
        val secondAttachments = listOf(
            ConversationAttachment(
                id = "att-2",
                type = "audio",
                mimeType = "audio/mpeg",
                remoteUrl = "https://example.com/2.mp3",
            ),
        )
        val fakeRuntime = FakeAppChatRuntime(
            events = listOf(
                AppChatRuntimeEvent.AttachmentUpdate(firstAttachments),
                AppChatRuntimeEvent.AssistantDelta("partial"),
                AppChatRuntimeEvent.AttachmentUpdate(secondAttachments),
                AppChatRuntimeEvent.AssistantFinal("complete"),
            ),
        )
        val useCase = SendAppMessageUseCase(
            conversations = fakeConversations,
            runtime = fakeRuntime,
        )

        val events = useCase.send(sessionId = "s1", text = "hello").toList()

        val assistantAppends = fakeConversations.appendCalls.filter { it.role == "assistant" }
        val assistantMessageIds = fakeConversations.updateCalls.map { it.messageId }.distinct()
        val startedEvents = events.filterIsInstance<SendAppMessageEvent.Started>()

        assertEquals(1, assistantAppends.size)
        assertEquals(1, startedEvents.size)
        assertEquals(1, assistantMessageIds.size)
        assertEquals(startedEvents.single().assistantMessageId, assistantMessageIds.single())
        assertEquals(4, fakeConversations.updateCalls.size)
        assertEquals("complete", fakeConversations.updateCalls.last().content)
    }

    // -- Fakes --

    private class FakeConversationRepository(
        private val signals: MutableList<String> = mutableListOf(),
    ) : ConversationRepositoryPort {
        data class AppendCall(val sessionId: String, val role: String, val content: String)
        data class UpdateCall(val sessionId: String, val messageId: String, val content: String?, val attachments: List<ConversationAttachment>?)

        val appendCalls = mutableListOf<AppendCall>()
        val updateCalls = mutableListOf<UpdateCall>()
        private var appendCounter = 0

        override val defaultSessionId: String = "s1"
        override val sessions: StateFlow<List<ConversationSession>> = MutableStateFlow(emptyList())

        override fun contextPreview(sessionId: String): String = ""

        override fun session(sessionId: String): ConversationSession {
            return ConversationSession(
                id = sessionId,
                title = "Test",
                botId = "bot-1",
                personaId = "",
                providerId = "",
                maxContextMessages = 12,
                messages = emptyList(),
            )
        }

        override fun syncSystemSessionTitle(sessionId: String, title: String) = Unit

        override fun updateSessionBindings(
            sessionId: String,
            providerId: String,
            personaId: String,
            botId: String,
        ) = Unit

        override fun updateSessionServiceFlags(
            sessionId: String,
            sessionSttEnabled: Boolean?,
            sessionTtsEnabled: Boolean?,
        ) = Unit

        override fun appendMessage(
            sessionId: String,
            role: String,
            content: String,
            attachments: List<ConversationAttachment>,
        ): String {
            appendCalls.add(AppendCall(sessionId, role, content))
            signals += "append:$role"
            appendCounter++
            return if (role == "user") "user-$appendCounter" else "assistant-$appendCounter"
        }

        override fun updateMessage(
            sessionId: String,
            messageId: String,
            content: String?,
            attachments: List<ConversationAttachment>?,
        ) {
            updateCalls.add(UpdateCall(sessionId, messageId, content, attachments))
            signals += "update:$messageId"
        }

        override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {}

        override fun renameSession(sessionId: String, title: String) {}
        override fun deleteSession(sessionId: String) {}
    }

    private class FakeAppChatRuntime(
        private val events: List<AppChatRuntimeEvent> = emptyList(),
        private val signals: MutableList<String> = mutableListOf(),
    ) : AppChatRuntimePort {
        var sendCalls = 0

        override fun send(request: AppChatRequest): Flow<AppChatRuntimeEvent> = flow {
            sendCalls++
            signals += "runtime:${request.userMessageId}:${request.assistantMessageId}"
            events.forEach { emit(it) }
        }
    }
}
