package com.astrbot.android.feature.qq.domain

interface QqRuntimePort {
    suspend fun handleIncomingMessage(message: IncomingQqMessage): QqRuntimeResult
}

sealed interface QqRuntimeResult {
    data class Ignored(val reason: String) : QqRuntimeResult
    data class Replied(val result: QqSendResult) : QqRuntimeResult
    data class Failed(val message: String, val cause: Throwable? = null) : QqRuntimeResult
}
