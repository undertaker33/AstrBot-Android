package com.astrbot.android.core.runtime.session

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ConversationSessionLockManager {
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()
    private val sessionLockCounts = ConcurrentHashMap<String, Int>()
    private val accessLock = Mutex()

    suspend fun <T> withLock(
        sessionId: String,
        block: suspend () -> T,
    ): T {
        val lock = accessLock.withLock {
            val existingLock = sessionLocks[sessionId]
            if (existingLock != null) {
                sessionLockCounts[sessionId] = (sessionLockCounts[sessionId] ?: 0) + 1
                existingLock
            } else {
                Mutex().also { newLock ->
                    sessionLocks[sessionId] = newLock
                    sessionLockCounts[sessionId] = 1
                }
            }
        }

        try {
            return lock.withLock {
                block()
            }
        } finally {
            accessLock.withLock {
                val nextCount = (sessionLockCounts[sessionId] ?: 1) - 1
                if (nextCount <= 0) {
                    sessionLocks.remove(sessionId)
                    sessionLockCounts.remove(sessionId)
                } else {
                    sessionLockCounts[sessionId] = nextCount
                }
            }
        }
    }
}
