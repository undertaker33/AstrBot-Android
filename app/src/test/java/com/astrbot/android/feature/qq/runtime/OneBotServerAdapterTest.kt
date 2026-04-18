package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqRuntimePort
import com.astrbot.android.feature.qq.domain.QqRuntimeResult
import com.astrbot.android.feature.qq.domain.QqSendResult
import com.astrbot.android.model.chat.MessageType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class OneBotServerAdapterTest {

    @Test
    fun raw_message_payload_is_parsed_and_delivered_to_runtime() = runBlocking {
        var received: IncomingQqMessage? = null
        val fakeRuntime = object : QqRuntimePort {
            override suspend fun handleIncomingMessage(message: IncomingQqMessage): QqRuntimeResult {
                received = message
                return QqRuntimeResult.Replied(QqSendResult(success = true))
            }
        }
        val adapter = OneBotServerAdapter(
            parser = OneBotPayloadParser(),
            runtime = fakeRuntime,
        )

        val payload = """
        {
            "post_type": "message",
            "message_type": "private",
            "self_id": "111",
            "user_id": "222",
            "message_id": "m1",
            "raw_message": "Hello",
            "sender": {"nickname": "Test"}
        }
        """.trimIndent()

        val result = adapter.handlePayload(payload)
        assertTrue("Expected Handled result", result is OneBotServerAdapterResult.Handled)
        assertTrue("Runtime should have received message", received != null)
        assertTrue("Message text should match", received!!.text == "Hello")
    }

    @Test
    fun ignored_payload_does_not_call_runtime() = runBlocking {
        var called = false
        val fakeRuntime = object : QqRuntimePort {
            override suspend fun handleIncomingMessage(message: IncomingQqMessage): QqRuntimeResult {
                called = true
                return QqRuntimeResult.Replied(QqSendResult(success = true))
            }
        }
        val adapter = OneBotServerAdapter(
            parser = OneBotPayloadParser(),
            runtime = fakeRuntime,
        )

        val payload = """{"post_type": "notice", "notice_type": "group_increase"}"""
        val result = adapter.handlePayload(payload)
        assertTrue("Expected Ignored result", result is OneBotServerAdapterResult.Ignored)
        assertTrue("Runtime should not be called", !called)
    }

    @Test
    fun invalid_payload_does_not_call_runtime() = runBlocking {
        var called = false
        val fakeRuntime = object : QqRuntimePort {
            override suspend fun handleIncomingMessage(message: IncomingQqMessage): QqRuntimeResult {
                called = true
                return QqRuntimeResult.Replied(QqSendResult(success = true))
            }
        }
        val adapter = OneBotServerAdapter(
            parser = OneBotPayloadParser(),
            runtime = fakeRuntime,
        )

        val result = adapter.handlePayload("not json")
        assertTrue("Expected Invalid result", result is OneBotServerAdapterResult.Invalid)
        assertTrue("Runtime should not be called", !called)
    }

    @Test
    fun parser_exception_returns_invalid_adapter_result() = runBlocking {
        var called = false
        val fakeRuntime = object : QqRuntimePort {
            override suspend fun handleIncomingMessage(message: IncomingQqMessage): QqRuntimeResult {
                called = true
                return QqRuntimeResult.Replied(QqSendResult(success = true))
            }
        }
        val throwingParser = object : OneBotPayloadParser() {
            override fun parse(payload: String): OneBotPayloadParseResult {
                throw RuntimeException("Simulated parser failure")
            }
        }
        val adapter = OneBotServerAdapter(
            parser = throwingParser,
            runtime = fakeRuntime,
        )

        val result = adapter.handlePayload("""{"post_type": "message"}""")
        assertTrue("Expected Invalid result", result is OneBotServerAdapterResult.Invalid)
        assertTrue("Runtime should not be called", !called)
    }
}
