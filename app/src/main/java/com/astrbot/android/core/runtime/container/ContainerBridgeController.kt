package com.astrbot.android.core.runtime.container

import android.content.Context
import android.content.Intent
import android.os.Build
import com.astrbot.android.core.common.logging.RuntimeLogRepository

@Deprecated("Will move to core/runtime/container. Direct access from feature code is forbidden.")
object ContainerBridgeController {
    fun start(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            RuntimeLogRepository.append("Bridge start requested")
            val intent = Intent(appContext, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_START_BRIDGE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure { error ->
            RuntimeLogRepository.append("Bridge start failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            RuntimeLogRepository.append("Bridge stop requested")
            val intent = Intent(appContext, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_STOP_BRIDGE
            }
            appContext.startService(intent)
        }.onFailure { error ->
            RuntimeLogRepository.append("Bridge stop failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun check(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            RuntimeLogRepository.append("Bridge health check requested")
            val intent = Intent(appContext, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_CHECK_BRIDGE
            }
            appContext.startService(intent)
        }.onFailure { error ->
            RuntimeLogRepository.append("Bridge check failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}
