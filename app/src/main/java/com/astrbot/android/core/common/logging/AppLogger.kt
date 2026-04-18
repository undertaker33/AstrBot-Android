package com.astrbot.android.core.common.logging

object AppLogger {
    fun append(message: String) {
        RuntimeLogRepository.append(message)
    }
}
