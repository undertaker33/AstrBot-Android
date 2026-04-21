@file:Suppress("DEPRECATION")

package com.astrbot.android.core.runtime.container

import android.content.Context
import android.system.Os
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.secret.RuntimeSecretRepository
import com.astrbot.android.model.NapCatBridgeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ContainerRuntimeInstaller @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bridgeStatePort: ContainerBridgeStatePort,
) {
    private val scriptNames = listOf(
        "container_env.sh",
        "bootstrap_container.sh",
        "prepare_tts_assets.sh",
        "clear_tts_assets.sh",
        "convert_tencent_silk.sh",
        "encode_tencent_silk.py",
        "root_launcher.sh",
        "start_napcat.sh",
        "logout_qq.sh",
        "stop_napcat.sh",
        "status_napcat.sh",
    )

    private val bundledAssetNames = listOf(
        "ubuntu-rootfs.tar.xz",
        "napcat-installer.sh",
        "NapCat.Shell.zip",
        "QQ.deb",
        "launcher.cpp",
        "offline-debs.tar",
        "offline-rootfs-overlay.tar.xz",
    )

    private val installMutex = Mutex()
    @Volatile
    private var installCompleted = false
    @Volatile
    private var warmupJob: Job? = null

    fun warmUpAsync(scope: CoroutineScope) {
        if (installCompleted || warmupJob?.isActive == true) return
        warmupJob = scope.launch(Dispatchers.IO) {
            ensureInstalled()
        }
    }

    suspend fun ensureInstalled() {
        if (installCompleted) return

        installMutex.withLock {
            if (installCompleted) return
            installInternal()
            installCompleted = true
            warmupJob = null
        }
    }

    private fun installInternal() {
        RuntimeSecretRepository.initialize(appContext)
        val runtimeDir = File(appContext.filesDir, "runtime")
        val binDir = File(runtimeDir, "bin")
        val scriptDir = File(runtimeDir, "scripts")
        val assetsDir = File(runtimeDir, "assets")
        binDir.mkdirs()
        scriptDir.mkdirs()
        assetsDir.mkdirs()

        scriptNames.forEach { name ->
            val target = File(scriptDir, name)
            appContext.assets.open("runtime/scripts/$name").use { input ->
                val normalizedScript = input.readBytes()
                    .toString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                target.writeText(normalizedScript, StandardCharsets.UTF_8)
            }
            target.setExecutable(true, true)
        }

        val nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir
        RuntimeLogRepository.append("nativeLibraryDir=$nativeLibraryDir")
        installRuntimeLinks(binDir, nativeLibraryDir)
        RuntimeLogRepository.append("Using native binaries from runtime symlinks")

        bundledAssetNames.forEach { name ->
            copyBundledAsset(appContext, "runtime/assets/$name", File(assetsDir, name))
        }

        val ubuntuArchive = File(assetsDir, "ubuntu-rootfs.tar.xz")

        val rootfsDir = File(runtimeDir, "rootfs/ubuntu")
        try {
            RootfsExtractor.ensureExtracted(ubuntuArchive, rootfsDir)
            RuntimeLogRepository.append("Network install mode enabled: only Ubuntu rootfs is bundled")
            RuntimeLogRepository.append("Ubuntu rootfs ready at ${rootfsDir.absolutePath}")
        } catch (error: Exception) {
            RuntimeLogRepository.append("Rootfs extraction failed: ${error.message ?: error.javaClass.simpleName}")
        }

        val appHome = appContext.filesDir.absolutePath
        bridgeStatePort.applyRuntimeDefaults(
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

    companion object {
        fun warmUpAsync(context: Context, scope: CoroutineScope) {
            context.containerRuntimeEntryPoint().containerRuntimeInstaller().warmUpAsync(scope)
        }

        suspend fun ensureInstalled(context: Context) {
            context.containerRuntimeEntryPoint().containerRuntimeInstaller().ensureInstalled()
        }
    }
}
