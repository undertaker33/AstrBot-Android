package com.astrbot.android.core.runtime.audio

import com.astrbot.android.core.runtime.container.BridgeCommandRunner

import com.astrbot.android.core.runtime.container.ContainerRuntimeInstaller

import com.astrbot.android.core.common.logging.RuntimeLogRepository

import android.content.Context
import java.io.File
import kotlinx.coroutines.runBlocking

@Deprecated("Will move to core/runtime/audio. Direct access from feature code is forbidden.")
object TencentSilkEncoder {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun encode(inputFile: File): File {
        val context = appContext ?: error("TencentSilkEncoder is not initialized")
        runBlocking {
            ContainerRuntimeInstaller.ensureInstalled(context)
        }

        val appHome = context.filesDir.absolutePath
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val scriptFile = File(context.filesDir, "runtime/scripts/convert_tencent_silk.sh")
        val outputFile = File(
            inputFile.parentFile,
            inputFile.nameWithoutExtension + ".silk",
        )

        val command = buildString {
            append("/system/bin/sh ")
            append(scriptFile.absolutePath.shellQuote())
            append(' ')
            append(appHome.shellQuote())
            append(' ')
            append(nativeLibraryDir.shellQuote())
            append(' ')
            append(inputFile.absolutePath.shellQuote())
            append(' ')
            append(outputFile.absolutePath.shellQuote())
        }

        RuntimeLogRepository.append("QQ TTS silk conversion started: ${inputFile.name} -> ${outputFile.name}")
        val result = BridgeCommandRunner.execute(command)
        if (result.exitCode != 0 || !outputFile.exists() || outputFile.length() <= 0L) {
            if (result.stderr.contains("tts assets are not prepared", ignoreCase = true)) {
                RuntimeLogRepository.append("QQ TTS silk conversion skipped: assets not installed")
                throw IllegalStateException("TTS conversion assets are not installed. Download them in Asset Management.")
            }
            throw IllegalStateException(
                "Tencent silk conversion failed: ${result.stderr.ifBlank { result.stdout }.ifBlank { "unknown error" }}",
            )
        }
        RuntimeLogRepository.append("QQ TTS silk conversion finished: ${outputFile.absolutePath}")
        return outputFile
    }

    private fun String.shellQuote(): String {
        return "'" + replace("'", "'\"'\"'") + "'"
    }
}
