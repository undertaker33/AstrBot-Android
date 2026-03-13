package com.astrbot.android.runtime

import android.content.Context
import android.system.Os
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.model.NapCatBridgeConfig
import java.io.File
import java.nio.charset.StandardCharsets

object ContainerRuntimeInstaller {
    private val scriptNames = listOf(
        "container_env.sh",
        "bootstrap_container.sh",
        "root_launcher.sh",
        "start_napcat.sh",
        "stop_napcat.sh",
        "status_napcat.sh",
    )

    private val bundledAssetNames = listOf(
        "ubuntu-rootfs.tar.xz",
    )

    fun install(context: Context) {
        val runtimeDir = File(context.filesDir, "runtime")
        val binDir = File(runtimeDir, "bin")
        val scriptDir = File(runtimeDir, "scripts")
        val assetsDir = File(runtimeDir, "assets")
        binDir.mkdirs()
        scriptDir.mkdirs()
        assetsDir.mkdirs()

        scriptNames.forEach { name ->
            val target = File(scriptDir, name)
            context.assets.open("runtime/scripts/$name").use { input ->
                val normalizedScript = input.readBytes()
                    .toString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                target.writeText(normalizedScript, StandardCharsets.UTF_8)
            }
            target.setExecutable(true, true)
        }

        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        RuntimeLogRepository.append("nativeLibraryDir=$nativeLibraryDir")
        installRuntimeLinks(binDir, nativeLibraryDir)
        RuntimeLogRepository.append("Using native binaries from runtime symlinks")

        bundledAssetNames.forEach { name ->
            copyBundledAsset(context, "runtime/assets/$name", File(assetsDir, name))
        }

        val ubuntuArchive = File(assetsDir, "ubuntu-rootfs.tar.xz")

        val rootfsDir = File(runtimeDir, "rootfs/ubuntu")
        try {
            RootfsExtractor.ensureExtracted(context, ubuntuArchive, rootfsDir)
            RuntimeLogRepository.append("Network install mode enabled: only Ubuntu rootfs is bundled")
            RuntimeLogRepository.append("Ubuntu rootfs ready at ${rootfsDir.absolutePath}")
        } catch (error: Exception) {
            RuntimeLogRepository.append("Rootfs extraction failed: ${error.message ?: error.javaClass.simpleName}")
        }

        val appHome = context.filesDir.absolutePath
        NapCatBridgeRepository.applyRuntimeDefaults(
            NapCatBridgeConfig(
                endpoint = "ws://127.0.0.1:6199/ws",
                healthUrl = "http://127.0.0.1:6099",
                autoStart = false,
                startCommand = "/system/bin/sh ${File(scriptDir, "start_napcat.sh").absolutePath} $appHome $nativeLibraryDir",
                stopCommand = "/system/bin/sh ${File(scriptDir, "stop_napcat.sh").absolutePath} $appHome $nativeLibraryDir",
                statusCommand = "/system/bin/sh ${File(scriptDir, "status_napcat.sh").absolutePath} $appHome $nativeLibraryDir",
                commandPreview = "/system/bin/sh ${File(scriptDir, "start_napcat.sh").absolutePath} $appHome $nativeLibraryDir",
            ),
        )
        RuntimeLogRepository.append("Runtime scripts installed to ${scriptDir.absolutePath}")
    }

    private fun copyBundledAsset(context: Context, assetPath: String, target: File) {
        runCatching {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            RuntimeLogRepository.append("Bundled asset ready: ${target.name}")
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "Bundled asset missing or skipped: ${target.name} (${error.message ?: error.javaClass.simpleName})",
            )
        }
    }

    private fun installRuntimeLinks(binDir: File, nativeLibraryDir: String) {
        val linkMap = listOf(
            "proot" to "libproot.so",
            "loader" to "libloader.so",
            "busybox" to "libbusybox.so",
            "libtalloc.so.2" to "liblibtalloc.so.2.so",
            "bash" to "libbash.so",
            "sudo" to "libsudo.so",
        )

        linkMap.forEach { (linkName, targetName) ->
            val targetFile = File(nativeLibraryDir, targetName)
            if (!targetFile.exists()) {
                RuntimeLogRepository.append("Native dependency missing: ${targetFile.absolutePath}")
                return@forEach
            }

            val linkFile = File(binDir, linkName)
            runCatching {
                if (linkFile.exists() || linkFile.isSymbolicLink()) {
                    linkFile.delete()
                }
                Os.symlink(targetFile.absolutePath, linkFile.absolutePath)
                RuntimeLogRepository.append("Linked $linkName -> $targetName")
            }.onFailure { error ->
                RuntimeLogRepository.append("Failed to link $linkName: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private fun File.isSymbolicLink(): Boolean = runCatching {
        java.nio.file.Files.isSymbolicLink(toPath())
    }.getOrDefault(false)
}
