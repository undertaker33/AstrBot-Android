package com.astrbot.android.runtime

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuntimeLogRepository {
    private const val LOG_TAG = "AstrBotRuntime"
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _logs = MutableStateFlow(listOf("System initialized"))
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun append(message: String) {
        val next = "${formatter.format(Date())}  $message"
        _logs.value = (_logs.value + next).takeLast(500)
        runCatching {
            Log.i(LOG_TAG, message)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
