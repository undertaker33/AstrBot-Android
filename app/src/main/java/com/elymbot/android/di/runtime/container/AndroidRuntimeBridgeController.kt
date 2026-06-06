package com.elymbot.android.di.runtime.container

import android.content.Context
import android.content.Intent
import android.os.Build
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.container.ContainerBridgeService
import com.elymbot.android.core.runtime.container.RuntimeBridgeController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidRuntimeBridgeController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val runtimeLogger: RuntimeLogger,
) : RuntimeBridgeController {
    override fun startBridge() {
        startService(
            action = ContainerBridgeService.ACTION_START_BRIDGE,
            foreground = true,
            logLabel = "Bridge start",
        )
    }

    override fun stopBridge() {
        startService(
            action = ContainerBridgeService.ACTION_STOP_BRIDGE,
            foreground = false,
            logLabel = "Bridge stop",
        )
    }

    override fun checkBridge() {
        startService(
            action = ContainerBridgeService.ACTION_CHECK_BRIDGE,
            foreground = false,
            logLabel = "Bridge health check",
        )
    }

    private fun startService(action: String, foreground: Boolean, logLabel: String) {
        runCatching {
            runtimeLogger.append("$logLabel requested")
            val intent = Intent(appContext, ContainerBridgeService::class.java).apply {
                this.action = action
            }
            if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure { error ->
            runtimeLogger.append(
                buildRuntimeBridgeStartFailureLog(
                    logLabel = logLabel,
                    foreground = foreground,
                    error = error,
                ),
            )
        }
    }
}

internal fun buildRuntimeBridgeStartFailureLog(
    logLabel: String,
    foreground: Boolean,
    error: Throwable,
): String {
    val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    val errorName = error.javaClass.simpleName
    return when {
        error is SecurityException -> {
            "$logLabel failed: Android service permission/security policy blocked the request: $detail"
        }
        foreground && errorName == "ForegroundServiceStartNotAllowedException" -> {
            "$logLabel failed: Android 12+ foreground service background start restriction blocked the request: $detail"
        }
        foreground && errorName.contains("ForegroundService") -> {
            "$logLabel failed: foreground service start failed: $detail"
        }
        else -> {
            "$logLabel failed: service start failed: $detail"
        }
    }
}
