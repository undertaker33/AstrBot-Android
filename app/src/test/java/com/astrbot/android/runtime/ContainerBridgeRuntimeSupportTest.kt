package com.astrbot.android.core.runtime.container

import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.RuntimeStatus
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerBridgeRuntimeSupportTest {
    @Test
    fun `load progress snapshot localizes labels and installer cache state`() {
        val root = createTempDirectory(prefix = "bridge-runtime").toFile()
        val runDir = File(root, "runtime/run").apply { mkdirs() }
        runDir.resolve("napcat_progress").writeText("48")
        runDir.resolve("napcat_progress_label").writeText("download-installer")
        runDir.resolve("napcat_progress_mode").writeText("1")
        runDir.resolve("napcat_installer_cached").writeText("1")

        val snapshot = ContainerBridgeRuntimeSupport.loadProgressSnapshot(root)

        assertEquals("Downloading upstream installer", snapshot.label)
        assertEquals(48, snapshot.percent)
        assertTrue(snapshot.indeterminate)
        assertTrue(snapshot.installerCached)
        root.deleteRecursively()
    }

    @Test
    fun `build progress notification text reflects runtime status and progress label`() {
        assertEquals(
            "NapCat running",
            ContainerBridgeRuntimeSupport.buildProgressNotificationText(
                NapCatRuntimeState(statusType = RuntimeStatus.RUNNING),
            ),
        )
        assertEquals(
            "NapCat starting: Preparing container",
            ContainerBridgeRuntimeSupport.buildProgressNotificationText(
                NapCatRuntimeState(
                    statusType = RuntimeStatus.STARTING,
                    progressLabel = "Preparing container",
                ),
            ),
        )
        assertEquals(
            "NapCat warming up",
            ContainerBridgeRuntimeSupport.buildProgressNotificationText(
                NapCatRuntimeState(statusType = RuntimeStatus.CHECKING),
            ),
        )
    }

    @Test
    fun `build pending health details includes stage percent and elapsed time`() {
        val details = ContainerBridgeRuntimeSupport.buildPendingHealthDetails(
            snapshot = RuntimeProgressSnapshot(
                label = "Writing NapCat config",
                percent = 77,
            ),
            health = HealthCheckResult(ok = false, code = -1, message = "Connection refused"),
            startedAtMs = 10_000L,
            nowMs = 95_000L,
        )

        assertEquals(
            "NapCat process is running. Current stage: Writing NapCat config (77%). Waiting for HTTP endpoint: Connection refused. Elapsed 1 min 25 s.",
            details,
        )
    }

    @Test
    fun `runtime activity timestamp uses newest progress or log file`() {
        val root = createTempDirectory(prefix = "bridge-activity").toFile()
        val logsDir = File(root, "runtime/logs").apply { mkdirs() }
        val runDir = File(root, "runtime/run").apply { mkdirs() }
        val logFile = logsDir.resolve("napcat.log").apply {
            writeText("log")
            setLastModified(1_000L)
        }
        runDir.resolve("napcat_progress").apply {
            writeText("10")
            setLastModified(5_000L)
        }

        val timestamp = ContainerBridgeRuntimeSupport.runtimeActivityTimestamp(root)

        assertEquals(5_000L, timestamp)
        logFile.delete()
        root.deleteRecursively()
    }
}
