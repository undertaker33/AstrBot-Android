package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2RuntimeSessionFactoryTest {

    @Test
    fun awaitQuickJsBootstrapCompletion_waits_until_async_bootstrap_settles() {
        val observedPolls = mutableListOf<String?>()
        var pollCount = 0

        val settledState = awaitQuickJsBootstrapCompletion(
            initialState = QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING,
            timeoutMs = 50L,
            pollIntervalMs = 0L,
        ) {
            val nextState = if (pollCount++ == 0) {
                QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING
            } else {
                QUICKJS_BOOTSTRAP_COMPLETION_STATE_FULFILLED
            }
            observedPolls += nextState
            nextState
        }

        assertEquals(QUICKJS_BOOTSTRAP_COMPLETION_STATE_FULFILLED, settledState)
        assertEquals(
            listOf(
                QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING,
                QUICKJS_BOOTSTRAP_COMPLETION_STATE_FULFILLED,
            ),
            observedPolls,
        )
    }

    @Test
    fun awaitQuickJsBootstrapCompletion_times_out_when_async_bootstrap_never_settles() {
        val failure = runCatching {
            awaitQuickJsBootstrapCompletion(
                initialState = QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING,
                timeoutMs = 5L,
                pollIntervalMs = 0L,
            ) {
                QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("timed out"))
    }

    @Test
    fun resolveQuickJsModuleFile_rejects_relative_escape_outside_plugin_root() {
        val rootDir = createTempDirectory("plugin-v2-module-root")
        val runtimeDir = File(rootDir, "runtime").apply { mkdirs() }
        val bootstrapFile = File(runtimeDir, "bootstrap.js").apply {
            writeText("export default function bootstrap() {}", Charsets.UTF_8)
        }
        File(rootDir.parentFile, "outside-relative.js").writeText(
            "export const escaped = true",
            Charsets.UTF_8,
        )

        val failure = runCatching {
            resolveQuickJsModuleFile(
                pluginRootDirectory = rootDir.absolutePath,
                baseName = bootstrapFile.absolutePath,
                moduleName = "../../outside-relative.js",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("plugin root"))
    }

    @Test
    fun resolveQuickJsModuleFile_rejects_absolute_escape_outside_plugin_root() {
        val rootDir = createTempDirectory("plugin-v2-module-absolute")
        val outsideFile = File(rootDir.parentFile, "outside-absolute.js").apply {
            writeText("export const escaped = true", Charsets.UTF_8)
        }

        val failure = runCatching {
            resolveQuickJsModuleFile(
                pluginRootDirectory = rootDir.absolutePath,
                baseName = rootDir.absolutePath,
                moduleName = outsideFile.absolutePath,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("plugin root"))
    }

    @Test
    fun createSession_rejects_missing_extracted_dir() {
        val missingDir = File(createTempDirectory("plugin-v2-missing-root"), "missing-root")
        val installRecord = sampleInstallRecord(
            extractedDir = missingDir.absolutePath,
            bootstrap = "runtime/bootstrap.js",
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = RecordingPluginV2ScriptExecutor(),
        )

        val failure = runCatching {
            factory.createSession(installRecord)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("extractedDir"))
        assertTrue(failure?.message.orEmpty().contains(installRecord.pluginId))
    }

    @Test
    fun createSession_rejects_bootstrap_path_outside_plugin_root() {
        val rootDir = createTempDirectory("plugin-v2-root")
        File(rootDir, "runtime").mkdirs()
        File(rootDir.parentFile, "outside.js").writeText("export default function bootstrap() {}", Charsets.UTF_8)
        val installRecord = sampleInstallRecord(
            extractedDir = rootDir.absolutePath,
            bootstrap = "../outside.js",
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = RecordingPluginV2ScriptExecutor(),
        )

        val failure = runCatching {
            factory.createSession(installRecord)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("plugin root"))
        assertTrue(failure?.message.orEmpty().contains("bootstrap"))
    }

    @Test
    fun createSession_pins_bootstrap_timeout_to_phase2_constant() {
        val rootDir = createTempDirectory("plugin-v2-timeout")
        val bootstrapFile = createBootstrapFile(rootDir)
        val bootstrapSession = FakeExternalPluginBootstrapSession()
        val executor = RecordingPluginV2ScriptExecutor(
            bootstrapSessions = listOf(bootstrapSession),
        )
        val installRecord = sampleInstallRecord(
            extractedDir = rootDir.absolutePath,
            bootstrap = rootDir.toPath().relativize(bootstrapFile.toPath()).toString(),
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = executor,
        )

        val handle = factory.createSession(installRecord)

        assertEquals(10_000L, PluginV2RuntimeSessionFactory.DEFAULT_BOOTSTRAP_TIMEOUT_MS)
        assertEquals(10_000L, handle.bootstrapTimeoutMs)
        assertEquals(10_000L, executor.bootstrapRequests.single().bootstrapTimeoutMs)
    }

    @Test
    fun executeBootstrap_keeps_session_in_bootstrap_running_until_registry_attachment_finishes() {
        val rootDir = createTempDirectory("plugin-v2-bootstrap-running")
        createBootstrapFile(rootDir)
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = RecordingPluginV2ScriptExecutor(
                bootstrapSessions = listOf(FakeExternalPluginBootstrapSession()),
            ),
        )

        val handle = factory.createSession(
            sampleInstallRecord(
                extractedDir = rootDir.absolutePath,
                bootstrap = "runtime/bootstrap.js",
            ),
        )

        handle.executeBootstrap()

        assertEquals(PluginV2RuntimeSessionState.BootstrapRunning, handle.session.state)
    }

    @Test
    fun executeBootstrap_marks_session_failed_when_async_bootstrap_rejects() {
        val rootDir = createTempDirectory("plugin-v2-async-rejection")
        createBootstrapFile(rootDir)
        val bootstrapSession = ThrowingExternalPluginBootstrapSession(
            failure = IllegalStateException("Async bootstrap rejected"),
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = RecordingPluginV2ScriptExecutor(
                bootstrapSessions = listOf(bootstrapSession),
            ),
        )

        val handle = factory.createSession(
            sampleInstallRecord(
                extractedDir = rootDir.absolutePath,
                bootstrap = "runtime/bootstrap.js",
            ),
        )
        val failure = runCatching {
            handle.executeBootstrap()
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("Async bootstrap rejected", failure?.message)
        assertEquals(PluginV2RuntimeSessionState.BootstrapFailed, handle.session.state)
    }

    @Test
    fun executeBootstrap_marks_session_failed_when_async_bootstrap_wait_times_out() {
        val rootDir = createTempDirectory("plugin-v2-async-timeout")
        createBootstrapFile(rootDir)
        val bootstrapSession = ThrowingExternalPluginBootstrapSession(
            failure = IllegalStateException(
                "QuickJS bootstrap timed out after 10000ms while waiting for async bootstrap completion.",
            ),
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = RecordingPluginV2ScriptExecutor(
                bootstrapSessions = listOf(bootstrapSession),
            ),
        )

        val handle = factory.createSession(
            sampleInstallRecord(
                extractedDir = rootDir.absolutePath,
                bootstrap = "runtime/bootstrap.js",
            ),
        )
        val failure = runCatching {
            handle.executeBootstrap()
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("timed out"))
        assertEquals(PluginV2RuntimeSessionState.BootstrapFailed, handle.session.state)
    }

    @Test
    fun dispose_waits_for_inflight_bootstrap_to_finish_without_invalid_transition_race() {
        val rootDir = createTempDirectory("plugin-v2-dispose-race")
        createBootstrapFile(rootDir)
        val bootstrapStarted = CountDownLatch(1)
        val allowBootstrapToFinish = CountDownLatch(1)
        val bootstrapSession = BlockingExternalPluginBootstrapSession(
            started = bootstrapStarted,
            release = allowBootstrapToFinish,
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = RecordingPluginV2ScriptExecutor(
                bootstrapSessions = listOf(bootstrapSession),
            ),
        )
        val handle = factory.createSession(
            sampleInstallRecord(
                extractedDir = rootDir.absolutePath,
                bootstrap = "runtime/bootstrap.js",
            ),
        )
        val executeFailure = AtomicReference<Throwable?>()
        val disposeFailure = AtomicReference<Throwable?>()

        val executeThread = Thread {
            runCatching {
                handle.executeBootstrap()
            }.exceptionOrNull()?.let(executeFailure::set)
        }
        executeThread.start()
        assertTrue(bootstrapStarted.await(1, TimeUnit.SECONDS))

        val disposeThread = Thread {
            runCatching {
                handle.dispose()
            }.exceptionOrNull()?.let(disposeFailure::set)
        }
        disposeThread.start()

        Thread.sleep(50)
        assertFalse(bootstrapSession.disposed)
        assertEquals(PluginV2RuntimeSessionState.BootstrapRunning, handle.session.state)

        allowBootstrapToFinish.countDown()
        executeThread.join(1_000L)
        disposeThread.join(1_000L)

        assertFalse(executeThread.isAlive)
        assertFalse(disposeThread.isAlive)
        assertEquals(null, executeFailure.get())
        assertEquals(null, disposeFailure.get())
        assertTrue(bootstrapSession.disposed)
        assertEquals(PluginV2RuntimeSessionState.Disposed, handle.session.state)
    }

    @Test
    fun createSession_creates_fresh_runtime_sessions_and_dispose_releases_runtime_and_handles() {
        val rootDir = createTempDirectory("plugin-v2-reload")
        createBootstrapFile(rootDir)
        val firstBootstrapSession = FakeExternalPluginBootstrapSession()
        val secondBootstrapSession = FakeExternalPluginBootstrapSession()
        val executor = RecordingPluginV2ScriptExecutor(
            bootstrapSessions = listOf(firstBootstrapSession, secondBootstrapSession),
        )
        val installRecord = sampleInstallRecord(
            extractedDir = rootDir.absolutePath,
            bootstrap = "runtime/bootstrap.js",
        )
        val factory = PluginV2RuntimeSessionFactory(
            scriptExecutor = executor,
        )

        val firstHandle = factory.createSession(installRecord)
        val secondHandle = factory.createSession(installRecord)

        assertNotEquals(firstHandle.session.sessionInstanceId, secondHandle.session.sessionInstanceId)
        assertSame(firstBootstrapSession, firstHandle.bootstrapSession)
        assertSame(secondBootstrapSession, secondHandle.bootstrapSession)
        assertEquals(2, firstHandle.liveHandleCount)

        firstHandle.dispose()

        assertTrue(firstBootstrapSession.disposed)
        assertEquals(0, firstHandle.liveHandleCount)
        assertEquals(0, firstBootstrapSession.liveHandleCount)
        assertEquals(PluginV2RuntimeSessionState.Disposed, firstHandle.session.state)
    }

    private fun sampleInstallRecord(
        extractedDir: String,
        bootstrap: String,
    ): PluginInstallRecord {
        return samplePluginV2InstallRecord().copyWith(
            extractedDir = extractedDir,
            bootstrap = bootstrap,
        )
    }

    private fun createTempDirectory(prefix: String): File {
        return Files.createTempDirectory(prefix).toFile()
    }

    private fun createBootstrapFile(rootDir: File): File {
        val runtimeDir = File(rootDir, "runtime").apply { mkdirs() }
        return File(runtimeDir, "bootstrap.js").apply {
            writeText("export default function bootstrap() {}", Charsets.UTF_8)
        }
    }
}

@Suppress("TestFunctionName")
private fun PluginInstallRecord.copyWith(
    extractedDir: String = this.extractedDir,
    bootstrap: String = this.packageContractSnapshot?.runtime?.bootstrap.orEmpty(),
): PluginInstallRecord {
    val contractSnapshot = requireNotNull(packageContractSnapshot) {
        "PluginV2RuntimeSessionFactory tests require packageContractSnapshot."
    }
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifestSnapshot,
        source = source,
        packageContractSnapshot = contractSnapshot.copy(
            runtime = contractSnapshot.runtime.copy(
                bootstrap = bootstrap,
            ),
        ),
        permissionSnapshot = permissionSnapshot,
        compatibilityState = compatibilityState,
        uninstallPolicy = uninstallPolicy,
        enabled = enabled,
        failureState = failureState,
        catalogSourceId = catalogSourceId,
        installedPackageUrl = installedPackageUrl,
        lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
        installedAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = localPackagePath,
        extractedDir = extractedDir,
    )
}

private class RecordingPluginV2ScriptExecutor(
    private val bootstrapSessions: List<ExternalPluginBootstrapSession> = emptyList(),
) : ExternalPluginScriptExecutor {
    val legacyRequests = mutableListOf<ExternalPluginScriptExecutionRequest>()
    val bootstrapRequests = mutableListOf<ExternalPluginBootstrapSessionRequest>()
    private var bootstrapIndex = 0

    override fun execute(request: ExternalPluginScriptExecutionRequest): String {
        legacyRequests += request
        error("Legacy execute should not be used by PluginV2RuntimeSessionFactory tests.")
    }

    override fun openBootstrapSession(
        request: ExternalPluginBootstrapSessionRequest,
    ): ExternalPluginBootstrapSession {
        bootstrapRequests += request
        return bootstrapSessions.getOrElse(bootstrapIndex++) {
            FakeExternalPluginBootstrapSession()
        }
    }
}

private open class FakeExternalPluginBootstrapSession : ExternalPluginBootstrapSession {
    override val pluginId: String = "plugin-v2-fake"
    override val bootstrapAbsolutePath: String = "C:/tmp/plugin-v2/runtime/bootstrap.js"
    override val bootstrapTimeoutMs: Long = 10_000L

    private val handleStore = linkedMapOf(
        "globalObject" to Any(),
        "bootstrapCallable" to Any(),
    )

    var disposed: Boolean = false
        private set

    override val liveHandleCount: Int
        get() = handleStore.size

    override open fun installGlobal(name: String, value: Any?) {
        handleStore[name] = value ?: "undefined"
    }

    override open fun executeBootstrap() = Unit

    override open fun dispose() {
        disposed = true
        handleStore.clear()
    }
}

private class ThrowingExternalPluginBootstrapSession(
    private val failure: IllegalStateException,
) : FakeExternalPluginBootstrapSession() {
    override fun executeBootstrap() {
        throw failure
    }
}

private class BlockingExternalPluginBootstrapSession(
    private val started: CountDownLatch,
    private val release: CountDownLatch,
) : FakeExternalPluginBootstrapSession() {
    override fun executeBootstrap() {
        started.countDown()
        assertTrue(release.await(1, TimeUnit.SECONDS))
    }
}
