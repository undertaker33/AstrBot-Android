package com.astrbot.android.feature.qq.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneBotReverseWebSocketTransportTest {
    @Test
    fun send_without_active_socket_returns_failure() {
        val transport = OneBotReverseWebSocketTransport(
            port = 6199,
            path = "/ws",
            authToken = "token",
            onPayload = {},
            log = {},
        )

        val result = transport.send("""{"action":"send_private_msg"}""")

        assertTrue(result.isFailure)
        assertEquals("reverse_ws_not_connected", result.exceptionOrNull()?.message)
    }
}
