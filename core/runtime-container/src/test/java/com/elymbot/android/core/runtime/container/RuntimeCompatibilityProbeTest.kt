package com.elymbot.android.core.runtime.container

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCompatibilityProbeTest {

    @Test
    fun `rootfs missing is blocking`() {
        val fixture = RuntimeFixture(rootfsReady = false, nativeRuntimeReady = true)
        val runner = RecordingCommandRunner()

        val snapshot = fixture.probe(runner).runPreflight()

        assertEquals(RuntimeCompatibilityCategory.ROOTFS_MISSING, snapshot.category)
        assertTrue(snapshot.blocking)
        assertTrue(snapshot.userMessage.contains("Ubuntu rootfs"))
        assertEquals(0, runner.executions)
        fixture.cleanup()
    }

    @Test
    fun `missing native runtime link is blocking`() {
        val fixture = RuntimeFixture(rootfsReady = true, nativeRuntimeReady = false)
        val runner = RecordingCommandRunner()

        val snapshot = fixture.probe(runner).runPreflight()

        assertEquals(RuntimeCompatibilityCategory.NATIVE_RUNTIME_MISSING, snapshot.category)
        assertTrue(snapshot.blocking)
        assertTrue(snapshot.technicalDetails.contains("proot"))
        assertEquals(0, runner.executions)
        fixture.cleanup()
    }

    @Test
    fun `smoke permission denied is classified as app private exec restriction`() {
        val fixture = RuntimeFixture()
        val runner = RecordingCommandRunner(
            result = CommandExecutionResult(
                exitCode = 126,
                stdout = "",
                stderr = "/data/user/0/com.elymbot.android/files/runtime/bin/proot: Permission denied",
            ),
        )

        val snapshot = fixture.probe(runner).runPreflight()

        assertEquals(RuntimeCompatibilityCategory.APP_PRIVATE_EXEC_RESTRICTED, snapshot.category)
        assertTrue(snapshot.blocking)
        assertTrue(snapshot.userMessage.contains("execution"))
        assertTrue(snapshot.technicalDetails.contains("Permission denied"))
        assertEquals(1, runner.executions)
        fixture.cleanup()
    }

    @Test
    fun `smoke non zero without permission denied is classified as proot smoke failure`() {
        val fixture = RuntimeFixture()
        val runner = RecordingCommandRunner(
            result = CommandExecutionResult(
                exitCode = 5,
                stdout = "",
                stderr = "segmentation fault",
            ),
        )

        val snapshot = fixture.probe(runner).runPreflight()

        assertEquals(RuntimeCompatibilityCategory.PROOT_SMOKE_FAILED, snapshot.category)
        assertTrue(snapshot.blocking)
        assertTrue(snapshot.technicalDetails.contains("segmentation fault"))
        fixture.cleanup()
    }

    @Test
    fun `smoke command does not force proot no seccomp`() {
        val fixture = RuntimeFixture()
        val runner = RecordingCommandRunner()

        fixture.probe(runner).runPreflight()

        assertFalse(runner.lastSpec?.env?.containsKey("PROOT_NO_SECCOMP") == true)
        fixture.cleanup()
    }

    @Test
    fun `smoke command sets library path for linked native dependencies`() {
        val fixture = RuntimeFixture()
        val runner = RecordingCommandRunner()

        fixture.probe(runner).runPreflight()

        val libraryPath = runner.lastSpec?.env?.get("LD_LIBRARY_PATH").orEmpty()
        assertTrue(libraryPath.contains(fixture.runtimeBinDir.absolutePath))
        assertTrue(libraryPath.contains(fixture.nativeLibraryDir.absolutePath))
        fixture.cleanup()
    }

    @Test
    fun `unreadable external storage bind is non blocking`() {
        val fixture = RuntimeFixture(externalStorageReady = false)

        val snapshot = fixture.probe(RecordingCommandRunner()).runPreflight()

        assertEquals(RuntimeCompatibilityCategory.EXTERNAL_STORAGE_BIND_SKIPPED, snapshot.category)
        assertFalse(snapshot.blocking)
        assertTrue(snapshot.userMessage.contains("External storage"))
        fixture.cleanup()
    }

    @Test
    fun `android thirteen notification denial is non blocking`() {
        val fixture = RuntimeFixture(
            sdkInt = 33,
            notificationPermissionState = NotificationPermissionState.DENIED,
        )

        val snapshot = fixture.probe(RecordingCommandRunner()).runPreflight()

        assertEquals(RuntimeCompatibilityCategory.NOTIFICATION_PERMISSION_DENIED, snapshot.category)
        assertFalse(snapshot.blocking)
        assertTrue(snapshot.userMessage.contains("notification"))
        fixture.cleanup()
    }

    private class RuntimeFixture(
        rootfsReady: Boolean = true,
        nativeRuntimeReady: Boolean = true,
        externalStorageReady: Boolean = true,
        private val sdkInt: Int = 29,
        private val notificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_APPLICABLE,
    ) {
        private val root = createTempDirectory(prefix = "runtime-compat").toFile()
        private val filesDir = File(root, "files")
        val nativeLibraryDir: File = File(root, "native-libs")
        val runtimeBinDir: File = File(filesDir, "runtime/bin")
        private val externalStorageDir = File(root, "external-storage")

        init {
            filesDir.mkdirs()
            nativeLibraryDir.mkdirs()
            if (rootfsReady) {
                File(filesDir, "runtime/rootfs/ubuntu/usr/bin").mkdirs()
                File(filesDir, "runtime/rootfs/ubuntu/usr/bin/env").writeText("")
            }
            File(filesDir, "runtime/scripts").mkdirs()
            File(filesDir, "runtime/scripts/start_napcat.sh").writeText("#!/system/bin/sh\n")
            if (nativeRuntimeReady) {
                runtimeBinDir.mkdirs()
                File(runtimeBinDir, "proot").writeText("")
                File(runtimeBinDir, "loader").writeText("")
            }
            if (externalStorageReady) {
                externalStorageDir.mkdirs()
            }
        }

        fun probe(commandRunner: CommandRunner): RuntimeCompatibilityProbe {
            return RuntimeCompatibilityProbe(
                environment = FakeRuntimeCompatibilityEnvironment(
                    RuntimeCompatibilityEnvironmentSnapshot(
                        sdkInt = sdkInt,
                        manufacturer = "TestVendor",
                        model = "TestModel",
                        targetSdk = 36,
                        appHome = filesDir,
                        nativeLibraryDir = nativeLibraryDir,
                        externalStoragePaths = listOf(externalStorageDir),
                        notificationPermissionState = notificationPermissionState,
                    ),
                ),
                commandRunner = commandRunner,
            )
        }

        fun cleanup() {
            root.deleteRecursively()
        }
    }

    private class FakeRuntimeCompatibilityEnvironment(
        private val snapshot: RuntimeCompatibilityEnvironmentSnapshot,
    ) : RuntimeCompatibilityEnvironment {
        override fun snapshot(): RuntimeCompatibilityEnvironmentSnapshot = snapshot
    }

    private class RecordingCommandRunner(
        private val result: CommandExecutionResult = CommandExecutionResult(
            exitCode = 0,
            stdout = "elymbot-proot-smoke-ok",
            stderr = "",
        ),
    ) : CommandRunner {
        var executions: Int = 0
            private set
        var lastSpec: CommandSpec? = null
            private set

        override fun execute(spec: CommandSpec): CommandExecutionResult {
            executions += 1
            lastSpec = spec
            return result
        }
    }
}
