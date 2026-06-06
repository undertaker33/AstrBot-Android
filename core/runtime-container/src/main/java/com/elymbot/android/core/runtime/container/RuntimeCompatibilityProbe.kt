package com.elymbot.android.core.runtime.container

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class RuntimeCompatibilityCategory {
    READY,
    ROOTFS_MISSING,
    NATIVE_RUNTIME_MISSING,
    APP_PRIVATE_EXEC_RESTRICTED,
    PROOT_SMOKE_FAILED,
    EXTERNAL_STORAGE_BIND_SKIPPED,
    NOTIFICATION_PERMISSION_DENIED,
    FOREGROUND_SERVICE_START_BLOCKED,
    NETWORK_INSTALL_UNAVAILABLE,
    ROM_PROCESS_RESTRICTED,
    UNKNOWN,
}

enum class NotificationPermissionState {
    NOT_APPLICABLE,
    GRANTED,
    DENIED,
    UNKNOWN,
}

enum class ExternalStorageBindMode {
    AVAILABLE,
    SKIPPED,
}

data class RuntimeCompatibilityIssue(
    val category: RuntimeCompatibilityCategory,
    val blocking: Boolean,
    val userMessage: String,
    val technicalDetails: String,
)

data class RuntimeCompatibilityEnvironmentSnapshot(
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val targetSdk: Int,
    val appHome: File,
    val nativeLibraryDir: File,
    val externalStoragePaths: List<File>,
    val notificationPermissionState: NotificationPermissionState,
)

interface RuntimeCompatibilityEnvironment {
    fun snapshot(): RuntimeCompatibilityEnvironmentSnapshot
}

data class RuntimeCompatibilitySnapshot(
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val targetSdk: Int,
    val appHome: File,
    val nativeLibraryDir: File,
    val scriptDirReady: Boolean,
    val rootfsReady: Boolean,
    val prootLinkReady: Boolean,
    val loaderLinkReady: Boolean,
    val systemShellReady: Boolean,
    val appPrivateScriptReadable: Boolean,
    val prootSmokeResult: CommandExecutionResult?,
    val sdcardReadable: Boolean,
    val externalStorageBindMode: ExternalStorageBindMode,
    val notificationPermissionState: NotificationPermissionState,
    val foregroundServiceStartMode: String = "not-checked",
    val issues: List<RuntimeCompatibilityIssue> = emptyList(),
) {
    val category: RuntimeCompatibilityCategory
        get() = primaryIssue?.category ?: RuntimeCompatibilityCategory.READY

    val blocking: Boolean
        get() = issues.any { it.blocking }

    val userMessage: String
        get() = primaryIssue?.userMessage ?: "Runtime compatibility checks passed"

    val technicalDetails: String
        get() = if (issues.isEmpty()) {
            "Runtime compatibility ready: sdk=$sdkInt targetSdk=$targetSdk manufacturer=$manufacturer model=$model"
        } else {
            issues.joinToString(separator = "\n") { it.technicalDetails }
        }

    val primaryIssue: RuntimeCompatibilityIssue?
        get() = issues.firstOrNull { it.blocking } ?: issues.firstOrNull()
}

@Singleton
class RuntimeCompatibilityProbe(
    private val environment: RuntimeCompatibilityEnvironment,
    private val commandRunner: CommandRunner,
) {
    @Inject
    constructor(
        @ApplicationContext appContext: Context,
        commandRunner: CommandRunner,
    ) : this(
        environment = AndroidRuntimeCompatibilityEnvironment(appContext),
        commandRunner = commandRunner,
    )

    fun runPreflight(): RuntimeCompatibilitySnapshot {
        val env = environment.snapshot()
        val runtimeDir = File(env.appHome, "runtime")
        val rootfsDir = File(runtimeDir, "rootfs/ubuntu")
        val prootLink = File(runtimeDir, "bin/proot")
        val loaderLink = File(runtimeDir, "bin/loader")
        val startScript = ContainerRuntimeScripts.scriptFile(env.appHome, ContainerRuntimeScript.START_NAPCAT)
        val systemShell = File("/system/bin/sh")

        val rootfsReady = rootfsDir.isDirectory && File(rootfsDir, "usr/bin/env").isFile
        val prootLinkReady = prootLink.isFile && prootLink.canRead()
        val loaderLinkReady = loaderLink.isFile && loaderLink.canRead()
        val scriptDirReady = startScript.parentFile?.isDirectory == true
        val appPrivateScriptReadable = startScript.isFile && startScript.canRead()
        val systemShellReady = systemShell.exists() && systemShell.canRead()
        val sdcardReadable = env.externalStoragePaths.any { path ->
            path.exists() && path.isDirectory && path.canRead()
        }
        val externalStorageBindMode = if (sdcardReadable) {
            ExternalStorageBindMode.AVAILABLE
        } else {
            ExternalStorageBindMode.SKIPPED
        }

        val issues = mutableListOf<RuntimeCompatibilityIssue>()
        var smokeResult: CommandExecutionResult? = null

        if (!rootfsReady) {
            issues += RuntimeCompatibilityIssue(
                category = RuntimeCompatibilityCategory.ROOTFS_MISSING,
                blocking = true,
                userMessage = "Ubuntu rootfs is missing. Reinstall the runtime assets before starting NapCat.",
                technicalDetails = buildString {
                    append("rootfs missing or incomplete at ${rootfsDir.absolutePath}; ")
                    append("sdk=${env.sdkInt} targetSdk=${env.targetSdk} rom=${env.manufacturer}/${env.model}")
                },
            )
        } else if (!prootLinkReady || !loaderLinkReady) {
            issues += RuntimeCompatibilityIssue(
                category = RuntimeCompatibilityCategory.NATIVE_RUNTIME_MISSING,
                blocking = true,
                userMessage = "Native container runtime files are missing. Reinstall runtime assets before starting NapCat.",
                technicalDetails = "native runtime missing: proot=${prootLink.absolutePath} ready=$prootLinkReady, " +
                    "loader=${loaderLink.absolutePath} ready=$loaderLinkReady",
            )
        } else {
            smokeResult = runProotSmoke(env, rootfsDir, prootLink, loaderLink)
            if (!smokeResult.isSuccess) {
                issues += classifySmokeFailure(env, smokeResult)
            }
        }

        if (!sdcardReadable) {
            issues += RuntimeCompatibilityIssue(
                category = RuntimeCompatibilityCategory.EXTERNAL_STORAGE_BIND_SKIPPED,
                blocking = false,
                userMessage = "External storage is not readable, so the /sdcard bind will be skipped.",
                technicalDetails = "external storage bind skipped; checked=" +
                    env.externalStoragePaths.joinToString { it.absolutePath },
            )
        }

        if (env.notificationPermissionState == NotificationPermissionState.DENIED) {
            issues += RuntimeCompatibilityIssue(
                category = RuntimeCompatibilityCategory.NOTIFICATION_PERMISSION_DENIED,
                blocking = false,
                userMessage = "Runtime notification permission is denied. NapCat can start, but its notification may be hidden.",
                technicalDetails = "POST_NOTIFICATIONS denied on sdk=${env.sdkInt}; foreground service start is not blocked by this probe",
            )
        }

        return RuntimeCompatibilitySnapshot(
            sdkInt = env.sdkInt,
            manufacturer = env.manufacturer,
            model = env.model,
            targetSdk = env.targetSdk,
            appHome = env.appHome,
            nativeLibraryDir = env.nativeLibraryDir,
            scriptDirReady = scriptDirReady,
            rootfsReady = rootfsReady,
            prootLinkReady = prootLinkReady,
            loaderLinkReady = loaderLinkReady,
            systemShellReady = systemShellReady,
            appPrivateScriptReadable = appPrivateScriptReadable,
            prootSmokeResult = smokeResult,
            sdcardReadable = sdcardReadable,
            externalStorageBindMode = externalStorageBindMode,
            notificationPermissionState = env.notificationPermissionState,
            issues = issues,
        )
    }

    private fun runProotSmoke(
        env: RuntimeCompatibilityEnvironmentSnapshot,
        rootfsDir: File,
        prootLink: File,
        loaderLink: File,
    ): CommandExecutionResult {
        val tmpDir = File(env.appHome, "runtime/usr/tmp/proot-smoke").apply { mkdirs() }
        val spec = CommandSpec(
            executable = prootLink,
            args = listOf(
                "-0",
                "-r",
                rootfsDir.absolutePath,
                "--link2symlink",
                "-b",
                "/system",
                "-b",
                "/apex",
                "-b",
                "${tmpDir.absolutePath}:${tmpDir.absolutePath}",
                "-w",
                "/",
                "/usr/bin/env",
                "-i",
                "HOME=/root",
                "TMPDIR=${tmpDir.absolutePath}",
                "PATH=/system/bin:/system/xbin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/bin/sh",
                "-c",
                "echo elymbot-proot-smoke-ok",
            ),
            env = mapOf(
                "PROOT_LOADER" to loaderLink.absolutePath,
                "PROOT_TMP_DIR" to tmpDir.absolutePath,
            ),
            timeoutMs = PROOT_SMOKE_TIMEOUT_MS,
            maxStdoutBytes = PROOT_SMOKE_OUTPUT_LIMIT_BYTES,
            maxStderrBytes = PROOT_SMOKE_OUTPUT_LIMIT_BYTES,
        )
        return runCatching { commandRunner.execute(spec) }.getOrElse { error ->
            CommandExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun classifySmokeFailure(
        env: RuntimeCompatibilityEnvironmentSnapshot,
        result: CommandExecutionResult,
    ): RuntimeCompatibilityIssue {
        val output = result.stderr.ifBlank { result.stdout }
        val normalized = output.lowercase()
        return if (normalized.contains("permission denied")) {
            RuntimeCompatibilityIssue(
                category = RuntimeCompatibilityCategory.APP_PRIVATE_EXEC_RESTRICTED,
                blocking = true,
                userMessage = "Android blocked container execution from the app runtime directory.",
                technicalDetails = "proot smoke permission denied: exit=${result.exitCode}; output=${output.singleLine(500)}; " +
                    "sdk=${env.sdkInt} targetSdk=${env.targetSdk} rom=${env.manufacturer}/${env.model}",
            )
        } else {
            RuntimeCompatibilityIssue(
                category = RuntimeCompatibilityCategory.PROOT_SMOKE_FAILED,
                blocking = true,
                userMessage = "Container smoke test failed before NapCat could start.",
                technicalDetails = "proot smoke failed: exit=${result.exitCode}; output=${output.singleLine(500)}; " +
                    "sdk=${env.sdkInt} targetSdk=${env.targetSdk} rom=${env.manufacturer}/${env.model}",
            )
        }
    }

    private fun String.singleLine(limit: Int): String {
        val normalized = replace('\n', ' ').replace('\r', ' ').trim()
        return if (normalized.length <= limit) normalized else normalized.take(limit) + "..."
    }

    private companion object {
        const val PROOT_SMOKE_TIMEOUT_MS = 15_000L
        const val PROOT_SMOKE_OUTPUT_LIMIT_BYTES = 8 * 1024
    }
}

private class AndroidRuntimeCompatibilityEnvironment(
    private val appContext: Context,
) : RuntimeCompatibilityEnvironment {
    override fun snapshot(): RuntimeCompatibilityEnvironmentSnapshot {
        val notificationState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationPermissionState.GRANTED
            } else {
                NotificationPermissionState.DENIED
            }
        } else {
            NotificationPermissionState.NOT_APPLICABLE
        }
        return RuntimeCompatibilityEnvironmentSnapshot(
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            targetSdk = appContext.applicationInfo.targetSdkVersion,
            appHome = appContext.filesDir,
            nativeLibraryDir = File(appContext.applicationInfo.nativeLibraryDir),
            externalStoragePaths = listOf(
                File("/sdcard"),
                File("/storage/emulated/0"),
            ),
            notificationPermissionState = notificationState,
        )
    }
}
