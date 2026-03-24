package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.runtime.RuntimeLogRepository
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

object SherpaOnnxAssetManager {
    private const val FRAMEWORK_ROOT = "runtime/sherpa-onnx"
    private const val FRAMEWORK_VERSION = "1.12.30"
    private const val FRAMEWORK_MARKER = ".framework-ready"
    private const val TEMP_DIR = "runtime/downloads"
    private const val STT_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2"
    private const val KOKORO_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2"
    private val deprecatedTtsDirNames = listOf("matcha", "melo", "fanchen-c", "zh-ll")

    data class SubAssetState(
        val installed: Boolean,
        val details: String,
    )

    data class TtsAssetState(
        val framework: SubAssetState,
        val kokoro: SubAssetState,
    )

    fun frameworkState(context: Context): SubAssetState {
        val installed = frameworkMarkerFile(context).exists()
        return SubAssetState(
            installed = installed,
            details = if (installed) {
                "Sherpa ONNX Android runtime is bundled in the app. The on-device framework has been activated and is ready for local STT/TTS assets."
            } else {
                "Activate the bundled Sherpa ONNX Android runtime before downloading local STT or TTS model assets."
            },
        )
    }

    fun sttState(context: Context): SubAssetState {
        val installed = sttModelFile(context).exists() && sttTokensFile(context).exists()
        return SubAssetState(
            installed = installed,
            details = if (installed) {
                "Offline Paraformer STT assets are ready."
            } else {
                "Offline Paraformer STT assets are not downloaded."
            },
        )
    }

    fun ttsState(context: Context): TtsAssetState {
        val kokoroReady = listOf(
            kokoroModelFile(context),
            kokoroVoicesFile(context),
            File(kokoroDir(context), "lexicon-us-en.txt"),
            File(kokoroDir(context), "lexicon-zh.txt"),
            File(kokoroDir(context), "espeak-ng-data"),
            File(kokoroDir(context), "dict"),
            File(kokoroDir(context), "phone-zh.fst"),
            File(kokoroDir(context), "date-zh.fst"),
            File(kokoroDir(context), "number-zh.fst"),
        ).all { it.exists() }
        return TtsAssetState(
            framework = frameworkState(context),
            kokoro = SubAssetState(
                installed = kokoroReady,
                details = if (kokoroReady) {
                    "Kokoro local TTS assets are ready."
                } else {
                    "Kokoro local TTS assets are missing or incomplete."
                },
            ),
        )
    }

    fun frameworkDir(context: Context): File = File(context.filesDir, FRAMEWORK_ROOT)

    fun sttDir(context: Context): File = File(frameworkDir(context), "stt")

    fun kokoroDir(context: Context): File = File(frameworkDir(context), "tts/kokoro")

    fun ensureFrameworkActivated(context: Context) {
        val frameworkDir = frameworkDir(context)
        if (!frameworkDir.exists()) {
            frameworkDir.mkdirs()
        }
        frameworkMarkerFile(context).writeText(FRAMEWORK_VERSION)
        clearDeprecatedTtsAssets(context)
        RuntimeLogRepository.append("Sherpa ONNX framework activated: version=$FRAMEWORK_VERSION")
    }

    fun clearFramework(context: Context) {
        frameworkDir(context).deleteRecursively()
        RuntimeLogRepository.append("Sherpa ONNX framework cleared")
    }

    fun downloadSttAssets(context: Context) {
        require(frameworkState(context).installed) {
            "Download the Sherpa ONNX framework asset first."
        }
        downloadAndExtract(
            context = context,
            url = STT_URL,
            tempName = "sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2",
            targetDir = sttDir(context),
        )
    }

    fun clearSttAssets(context: Context) {
        sttDir(context).deleteRecursively()
        RuntimeLogRepository.append("Sherpa ONNX STT assets cleared")
    }

    fun downloadKokoroAssets(context: Context) {
        require(frameworkState(context).installed) {
            "Download the Sherpa ONNX framework asset first."
        }
        downloadAndExtract(
            context = context,
            url = KOKORO_URL,
            tempName = "kokoro-int8-multi-lang-v1_1.tar.bz2",
            targetDir = kokoroDir(context),
        )
    }

    fun clearKokoroAssets(context: Context) {
        kokoroDir(context).deleteRecursively()
        RuntimeLogRepository.append("Sherpa ONNX kokoro assets cleared")
    }

    fun clearDeprecatedTtsAssets(context: Context) {
        val deprecatedDirs = deprecatedTtsDirNames.map { dirName ->
            File(frameworkDir(context), "tts/$dirName")
        }
        deprecatedDirs.forEach { dir ->
            if (dir.exists()) {
                dir.deleteRecursively()
                RuntimeLogRepository.append("Sherpa ONNX deprecated TTS assets cleared: ${dir.absolutePath}")
            }
        }
    }

    private fun frameworkMarkerFile(context: Context): File = File(frameworkDir(context), FRAMEWORK_MARKER)

    private fun sttModelFile(context: Context): File = File(sttDir(context), "model.int8.onnx")

    private fun sttTokensFile(context: Context): File = File(sttDir(context), "tokens.txt")

    private fun kokoroModelFile(context: Context): File = File(kokoroDir(context), "model.int8.onnx")

    private fun kokoroVoicesFile(context: Context): File = File(kokoroDir(context), "voices.bin")

    private fun downloadAndExtract(
        context: Context,
        url: String,
        tempName: String,
        targetDir: File,
    ) {
        val tempDir = File(context.filesDir, TEMP_DIR).apply { mkdirs() }
        val archiveFile = File(tempDir, tempName)
        val stagingDir = File(tempDir, "${targetDir.name}.staging")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        downloadFile(url = url, outputFile = archiveFile)
        extractTarBz2(archiveFile, stagingDir)
        targetDir.parentFile?.mkdirs()
        targetDir.deleteRecursively()
        stagingDir.copyRecursively(targetDir, overwrite = true)
        stagingDir.deleteRecursively()
        RuntimeLogRepository.append("Sherpa ONNX asset extracted: target=${targetDir.absolutePath}")
    }

    private fun downloadFile(
        url: String,
        outputFile: File,
    ) {
        RuntimeLogRepository.append("Sherpa ONNX asset download started: $url")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code while downloading Sherpa ONNX assets.")
            }
            outputFile.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
        RuntimeLogRepository.append("Sherpa ONNX asset download finished: ${outputFile.absolutePath}")
    }

    private fun extractTarBz2(
        archiveFile: File,
        destinationDir: File,
    ) {
        TarArchiveInputStream(BZip2CompressorInputStream(archiveFile.inputStream().buffered())).use { input ->
            while (true) {
                val entry = input.nextTarEntry ?: break
                if (entry.name.isBlank()) continue
                val relativePath = normalizeTarEntry(entry)
                if (relativePath.isBlank()) continue
                val outputFile = resolveDestination(destinationDir, relativePath)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output -> input.copyTo(output) }
                    setExecutableIfNeeded(outputFile, entry)
                }
            }
        }
    }

    private fun normalizeTarEntry(entry: TarArchiveEntry): String {
        val normalized = entry.name.replace('\\', '/').trim('/')
        if (normalized.isBlank()) return ""
        val segments = normalized.split('/')
        return if (segments.size <= 1) {
            ""
        } else {
            segments.drop(1).joinToString(File.separator)
        }
    }

    private fun resolveDestination(
        destinationDir: File,
        relativePath: String,
    ): File {
        val resolved = File(destinationDir, relativePath)
        val normalizedBase = destinationDir.toPath().normalize()
        val normalizedResolved = resolved.toPath().normalize()
        check(normalizedResolved.startsWith(normalizedBase)) {
            "Invalid archive entry: $relativePath"
        }
        return resolved
    }

    private fun setExecutableIfNeeded(
        file: File,
        entry: TarArchiveEntry,
    ) {
        val mode = entry.mode
        if (mode and 0b001_001_001 != 0) {
            runCatching { file.setExecutable(true, false) }
        }
    }
}
