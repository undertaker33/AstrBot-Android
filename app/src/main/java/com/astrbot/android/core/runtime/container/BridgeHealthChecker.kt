package com.astrbot.android.core.runtime.container

import java.net.HttpURLConnection
import java.net.URL

data class HealthCheckResult(
    val ok: Boolean,
    val code: Int,
    val message: String,
)

object BridgeHealthChecker {
    fun check(url: String): HealthCheckResult {
        if (url.isBlank()) {
            return HealthCheckResult(false, -1, "Health URL is blank")
        }

        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()
            val code = connection.responseCode
            val ok = code in 200..399
            HealthCheckResult(ok, code, "HTTP $code")
        } catch (e: Exception) {
            HealthCheckResult(false, -1, e.message ?: e.javaClass.simpleName)
        }
    }

    fun checkWithRetry(
        url: String,
        attempts: Int = 10,
        delayMs: Long = 1500,
    ): HealthCheckResult {
        var lastResult = check(url)
        repeat((attempts - 1).coerceAtLeast(0)) {
            if (lastResult.ok) {
                return lastResult
            }
            Thread.sleep(delayMs)
            lastResult = check(url)
        }
        return lastResult
    }
}
