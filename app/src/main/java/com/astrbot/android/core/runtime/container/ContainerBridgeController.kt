package com.astrbot.android.core.runtime.container

import com.astrbot.android.core.common.logging.RuntimeLogRepository

import android.content.Context
import android.content.Intent
import android.os.Build

@Deprecated("Will move to core/runtime/container. Direct access from feature code is forbidden.")
object ContainerBridgeController {
    fun start(context: Context) {
        val bridgeState = ContainerBridgeStateRegistry.port
        runCatching {
            bridgeState.markStarting()
            RuntimeLogRepository.append("Bridge start requested")
            val intent = Intent(context, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_START_BRIDGE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { error ->
            bridgeState.markError("启动失败：${error.message ?: error.javaClass.simpleName}")
            RuntimeLogRepository.append("Bridge start failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun stop(context: Context) {
        val bridgeState = ContainerBridgeStateRegistry.port
        runCatching {
            RuntimeLogRepository.append("Bridge stop requested")
            val intent = Intent(context, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_STOP_BRIDGE
            }
            context.startService(intent)
        }.onFailure { error ->
            bridgeState.markError("停止失败：${error.message ?: error.javaClass.simpleName}")
            RuntimeLogRepository.append("Bridge stop failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun check(context: Context) {
        val bridgeState = ContainerBridgeStateRegistry.port
        runCatching {
            bridgeState.markChecking()
            RuntimeLogRepository.append("Bridge health check requested")
            val intent = Intent(context, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_CHECK_BRIDGE
            }
            context.startService(intent)
        }.onFailure { error ->
            bridgeState.markError("检查失败：${error.message ?: error.javaClass.simpleName}")
            RuntimeLogRepository.append("Bridge check failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}
