package com.astrbot.android.feature.qq.runtime

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import java.io.IOException

internal class OneBotReverseWebSocketTransport(
    private val port: Int,
    private val path: String,
    private val authToken: String,
    private val onPayload: (String) -> Unit,
    private val log: (String) -> Unit,
) {
    @Volatile
    private var server: OneBotWebSocketServer? = null

    @Volatile
    private var activeSocket: OneBotWebSocket? = null

    fun start(): Boolean {
        if (server != null) return true
        return runCatching {
            val nextServer = OneBotWebSocketServer()
            nextServer.start(0, true)
            server = nextServer
            log("OneBot reverse WS listening on ws://127.0.0.1:$port$path")
            true
        }.getOrElse { error ->
            log("OneBot reverse WS start failed: ${error.message ?: error.javaClass.simpleName}")
            false
        }
    }

    fun send(payload: String): Result<Unit> {
        val socket = activeSocket
            ?: return Result.failure(IllegalStateException("reverse_ws_not_connected"))
        return runCatching {
            socket.send(payload)
        }
    }

    fun isConnected(): Boolean = activeSocket != null

    private fun onSocketOpened(socket: OneBotWebSocket, handshake: IHTTPSession) {
        val requestPath = handshake.uri.orEmpty()
        if (requestPath != path) {
            log("OneBot reverse WS rejected: unexpected path=$requestPath")
            socket.closeSafely(WebSocketFrame.CloseCode.PolicyViolation, "Unexpected path")
            return
        }

        val headers = handshake.headers.orEmpty()
        val authorization = headers["authorization"].orEmpty()
        val token = when {
            authorization.startsWith("Bearer ", ignoreCase = true) ->
                authorization.substringAfter("Bearer ", "").trim()

            else -> authorization.trim()
        }
        if (token.isNotBlank() && token != authToken) {
            log("OneBot reverse WS rejected: invalid authorization header")
            socket.closeSafely(WebSocketFrame.CloseCode.PolicyViolation, "Unauthorized")
            return
        }

        activeSocket = socket
        val selfId = headers["x-self-id"].orEmpty().ifBlank { "unknown" }
        val role = headers["x-client-role"].orEmpty().ifBlank { "unknown" }
        log("OneBot reverse WS connected: self=$selfId role=$role")
    }

    private fun onSocketClosed(socket: OneBotWebSocket, reason: String) {
        if (activeSocket === socket) {
            activeSocket = null
        }
        log("OneBot reverse WS disconnected: $reason")
    }

    private inner class OneBotWebSocketServer : NanoWSD(port) {
        override fun openWebSocket(handshake: IHTTPSession): WebSocket {
            return OneBotWebSocket(handshake)
        }
    }

    private inner class OneBotWebSocket(
        private val handshakeRequest: IHTTPSession,
    ) : WebSocket(handshakeRequest) {
        override fun onOpen() {
            onSocketOpened(this, handshakeRequest)
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode,
            reason: String,
            initiatedByRemote: Boolean,
        ) {
            val source = if (initiatedByRemote) "remote" else "local"
            onSocketClosed(this, "$reason ($source, $code)")
        }

        override fun onMessage(message: WebSocketFrame) {
            onPayload(message.textPayload)
        }

        override fun onPong(pong: WebSocketFrame) = Unit

        override fun onException(exception: IOException) {
            log("OneBot reverse WS exception: ${exception.message ?: exception.javaClass.simpleName}")
        }

        fun closeSafely(code: WebSocketFrame.CloseCode, reason: String) {
            runCatching {
                close(code, reason, false)
            }
        }
    }
}
