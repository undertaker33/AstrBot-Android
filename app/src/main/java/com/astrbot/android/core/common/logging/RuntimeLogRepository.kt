package com.astrbot.android.core.common.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuntimeLogRepository {
    private const val LOG_TAG = "AstrBotRuntime"
    private const val MAX_ENTRIES = 500
    private const val THROTTLE_INTERVAL_MS = 200L

    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    // Ring buffer: writes are synchronized on `ringLock`, reads snapshot under the same lock.
    private val ringLock = Any()
    private val ring = arrayOfNulls<String>(MAX_ENTRIES)
    private var head = 0   // next write position
    private var size = 0

    private val _logs = MutableStateFlow(listOf("System initialized"))
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    @Volatile
    private var lastEmitMs = 0L
    @Volatile
    private var dirty = false

    fun append(message: String) {
        val entry = "${formatter.format(Date())}  $message"
        synchronized(ringLock) {
            ring[head] = entry
            head = (head + 1) % MAX_ENTRIES
            if (size < MAX_ENTRIES) size++
            dirty = true
        }
        runCatching { Log.i(LOG_TAG, message) }

        // Throttle StateFlow emissions to avoid flooding the UI during high-frequency updates.
        val now = System.currentTimeMillis()
        if (now - lastEmitMs >= THROTTLE_INTERVAL_MS) {
            flush()
        }
    }

    /** Force-publish current ring buffer contents to the StateFlow. */
    fun flush() {
        if (!dirty) return
        lastEmitMs = System.currentTimeMillis()
        dirty = false
        _logs.value = snapshot()
    }

    fun clear() {
        synchronized(ringLock) {
            ring.fill(null)
            head = 0
            size = 0
            dirty = false
        }
        _logs.value = emptyList()
    }

    private fun snapshot(): List<String> = synchronized(ringLock) {
        if (size == 0) return@synchronized emptyList()
        val start = (head - size + MAX_ENTRIES) % MAX_ENTRIES
        List(size) { i -> ring[(start + i) % MAX_ENTRIES]!! }
    }
}
