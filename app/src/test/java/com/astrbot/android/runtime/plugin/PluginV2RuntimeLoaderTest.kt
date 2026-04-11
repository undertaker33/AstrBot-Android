package com.astrbot.android.runtime.plugin

import com.astrbot.android.di.syncPluginRuntimeRecordsAndSignalReady
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashMap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2RuntimeLoaderTest {
    @Test
    fun sync_loads_only_enabled_compatible_v2_records() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-sync")
        createBootstrapFile(rootDir)
        val eligible = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_eligible",
        ).withBootstrapRoot(rootDir)
        val disabled = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_disabled",
        ).withBootstrapRoot(rootDir).copyWith(enabled = false)
        val incompatible = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_incompatible",
        ).withBootstrapRoot(rootDir).copyWith(
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = false,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
        )
        val legacy = samplePluginInstallRecord(
            pluginId = "com.astrbot.samples.loader_legacy",
        )

        val executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
            bootstrapSessions = listOf(
                BootstrappingSession(registrations = 1),
            ),
        )
        val loader = newLoader(executor)

        val result = loader.sync(listOf(eligible, disabled, incompatible, legacy))

        assertEquals(listOf(eligible.pluginId), executor.bootstrapRequests.map { it.pluginId })
        assertEquals(listOf(eligible.pluginId), result.loads.map { it.pluginId })
        assertEquals(PluginV2RuntimeLoadStatus.Loaded, result.loads.single().status)
        assertEquals(setOf(eligible.pluginId), loaderStore(loader).snapshot().activeRuntimeEntriesByPluginId.keys)
    }

    @Test
    fun reload_replaces_session_instance_and_invalidates_old_tokens() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-reload")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_reload",
        ).withBootstrapRoot(rootDir)

        val executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
            bootstrapSessions = listOf(
                BootstrappingSession(registrations = 1),
                BootstrappingSession(registrations = 1),
            ),
        )
        val loader = newLoader(executor)

        val firstLoad = loader.load(record)
        val firstEntry = loaderStore(loader).snapshot().activeRuntimeEntriesByPluginId[record.pluginId]!!
        val firstSession = firstEntry.session
        val firstToken = firstSession.snapshotCallbackTokens().single()

        val reloadResult = loader.reload(record.pluginId)
        val secondEntry = loaderStore(loader).snapshot().activeRuntimeEntriesByPluginId[record.pluginId]!!
        val secondSession = secondEntry.session

        assertEquals(PluginV2RuntimeLoadStatus.Loaded, firstLoad.status)
        assertEquals(PluginV2RuntimeLoadStatus.Loaded, reloadResult.status)
        assertNotEquals(firstSession.sessionInstanceId, secondSession.sessionInstanceId)
        assertEquals(firstSession.sessionInstanceId, reloadResult.previousSessionInstanceId)
        assertEquals(PluginV2RuntimeSessionState.Disposed, firstSession.state)
        assertFalse(firstSession.hasCallbackToken(firstToken))
        assertEquals(PluginV2RuntimeSessionState.Active, secondSession.state)
        assertTrue(secondSession.snapshotCallbackTokens().isNotEmpty())
    }

    @Test
    fun unload_removes_active_snapshot_and_disposes_session() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-unload")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_unload",
        ).withBootstrapRoot(rootDir)

        val executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
            bootstrapSessions = listOf(
                BootstrappingSession(registrations = 1),
            ),
        )
        val loader = newLoader(executor)

        loader.load(record)
        val entry = loaderStore(loader).snapshot().activeRuntimeEntriesByPluginId[record.pluginId]!!
        val unloadResult = loader.unload(record.pluginId)

        assertTrue(unloadResult.removed)
        assertEquals(record.pluginId, unloadResult.pluginId)
        assertEquals(entry.session.sessionInstanceId, unloadResult.sessionInstanceId)
        assertFalse(loaderStore(loader).snapshot().activeRuntimeEntriesByPluginId.containsKey(record.pluginId))
        assertEquals(PluginV2RuntimeSessionState.Disposed, entry.session.state)
    }

    @Test
    fun runtime_loader_publishes_task4_lifecycle_codes_and_broadcasts_loaded_unloaded_hooks() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-logs")
        createBootstrapFile(rootDir)
        val calls = mutableListOf<String>()
        val recordAlpha = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_logs_alpha",
        ).withBootstrapRoot(rootDir)
        val recordBeta = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_logs_beta",
        ).withBootstrapRoot(rootDir)
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 64)
        val store = PluginV2ActiveRuntimeStore()
        val loader = PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(
                scriptExecutor = RecordingPluginV2RuntimeLoaderScriptExecutor(
                    bootstrapSessions = listOf(
                        BootstrappingSession(
                            lifecycleRegistrations = listOf(
                                lifecycleRegistration(
                                    hook = PluginLifecycleHookSurface.OnPluginLoaded,
                                    registrationKey = "loaded.alpha",
                                    handle = PluginV2CallbackHandle { calls += "alpha-loaded" },
                                ),
                                lifecycleRegistration(
                                    hook = PluginLifecycleHookSurface.OnPluginUnloaded,
                                    registrationKey = "unloaded.alpha",
                                    handle = PluginV2CallbackHandle { calls += "alpha-unloaded" },
                                ),
                            ),
                        ),
                        BootstrappingSession(
                            lifecycleRegistrations = listOf(
                                lifecycleRegistration(
                                    hook = PluginLifecycleHookSurface.OnPluginLoaded,
                                    registrationKey = "loaded.beta",
                                    handle = PluginV2CallbackHandle { calls += "beta-loaded" },
                                ),
                                lifecycleRegistration(
                                    hook = PluginLifecycleHookSurface.OnPluginUnloaded,
                                    registrationKey = "unloaded.beta",
                                    handle = PluginV2CallbackHandle { calls += "beta-unloaded" },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            store = store,
            compiler = PluginV2RegistryCompiler(),
            logBus = logBus,
            lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
            clock = { 1L },
        )

        loader.sync(listOf(recordAlpha, recordBeta))
        loader.unload(recordAlpha.pluginId)

        assertEquals(
            listOf(
                "alpha-loaded",
                "alpha-loaded",
                "beta-loaded",
                "beta-unloaded",
            ),
            calls,
        )

        val codes = logBus.snapshot().map { it.code }
        assertTrue(codes.contains("lifecycle_broadcast_started"))
        assertTrue(codes.contains("lifecycle_broadcast_completed"))
        assertTrue(codes.contains("runtime_load_started"))
        assertTrue(codes.contains("runtime_load_succeeded"))
        assertTrue(codes.contains("runtime_unloaded"))
        assertFalse(codes.any { code ->
            code == "load_started" ||
                code == "load_succeeded" ||
                code == "reload_started" ||
                code == "reload_succeeded" ||
                code == "load_failed" ||
                code == "reload_failed"
        })
    }

    @Test
    fun sync_rejects_cross_plugin_command_alias_conflicts_before_registry_becomes_active() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-conflict")
        createBootstrapFile(rootDir)
        val alpha = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_conflict_alpha",
        ).withBootstrapRoot(rootDir)
        val beta = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_conflict_beta",
        ).withBootstrapRoot(rootDir)
        val executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
            bootstrapSessions = listOf(
                BootstrappingSession(
                    commandRegistrations = listOf(
                        commandRegistration(
                            registrationKey = "alpha.list",
                            command = "list",
                            aliases = listOf("astrbot plugin ls"),
                            groupPath = listOf("astrbot", "plugin"),
                        ),
                    ),
                ),
                BootstrappingSession(
                    commandRegistrations = listOf(
                        commandRegistration(
                            registrationKey = "beta.install",
                            command = "install",
                            aliases = listOf("astrbot plugin ls"),
                            groupPath = listOf("astrbot", "plugin"),
                        ),
                    ),
                ),
            ),
        )
        val loader = newLoader(executor)

        val result = loader.sync(listOf(alpha, beta))

        assertEquals(PluginV2RuntimeLoadStatus.Loaded, result.loads[0].status)
        assertEquals(PluginV2RuntimeLoadStatus.Failed, result.loads[1].status)
        assertTrue(result.loads[1].diagnostics.any { it.code == "alias_chain_conflict" })
        val activePluginIds = loaderStore(loader).snapshot().activeRuntimeEntriesByPluginId.keys
        assertEquals(setOf(alpha.pluginId), activePluginIds)
        assertNull(loaderStore(loader).snapshot().activeRuntimeEntriesByPluginId[beta.pluginId])
    }

    @Test
    fun initial_runtime_sync_emits_astrbot_and_platform_loaded_once_through_container_wiring() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-ready")
        createBootstrapFile(rootDir)
        val calls = mutableListOf<String>()
        val record = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.loader_ready",
        ).withBootstrapRoot(rootDir)
        val store = PluginV2ActiveRuntimeStore()
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 64)
        val lifecycleManager = PluginV2LifecycleManager(
            store = store,
            logBus = logBus,
            clock = { 1L },
        )
        val loader = PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(
                scriptExecutor = RecordingPluginV2RuntimeLoaderScriptExecutor(
                    bootstrapSessions = listOf(
                        BootstrappingSession(
                            lifecycleRegistrations = listOf(
                                lifecycleRegistration(
                                    hook = PluginLifecycleHookSurface.OnAstrbotLoaded,
                                    registrationKey = "ready.astrbot",
                                    handle = PluginV2CallbackHandle { calls += "astrbot" },
                                ),
                                lifecycleRegistration(
                                    hook = PluginLifecycleHookSurface.OnPlatformLoaded,
                                    registrationKey = "ready.platform",
                                    handle = PluginV2CallbackHandle { calls += "platform" },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            store = store,
            compiler = PluginV2RegistryCompiler(),
            logBus = logBus,
            lifecycleManager = lifecycleManager,
            clock = { 1L },
        )

        syncPluginRuntimeRecordsAndSignalReady(
            records = listOf(record),
            loader = loader,
            lifecycleManager = lifecycleManager,
        )
        syncPluginRuntimeRecordsAndSignalReady(
            records = listOf(record),
            loader = loader,
            lifecycleManager = lifecycleManager,
        )

        assertEquals(listOf("astrbot", "platform"), calls)
    }

    private fun newLoader(
        executor: RecordingPluginV2RuntimeLoaderScriptExecutor,
        store: PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStore(),
        logBus: InMemoryPluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
        lifecycleManager: PluginV2LifecycleManager = PluginV2LifecycleManager(
            store = store,
            logBus = logBus,
            clock = { 1L },
        ),
    ): PluginV2RuntimeLoader {
        return PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(scriptExecutor = executor),
            store = store,
            compiler = PluginV2RegistryCompiler(),
            logBus = logBus,
            lifecycleManager = lifecycleManager,
        )
    }

    private fun loaderStore(loader: PluginV2RuntimeLoader): PluginV2ActiveRuntimeStore {
        val field = PluginV2RuntimeLoader::class.java.getDeclaredField("store").apply {
            isAccessible = true
        }
        return field.get(loader) as PluginV2ActiveRuntimeStore
    }

    private fun createTempDir(prefix: String): File {
        return Files.createTempDirectory(prefix).toFile()
    }

    private fun createBootstrapFile(rootDir: File) {
        val runtimeDir = File(rootDir, "runtime").apply { mkdirs() }
        File(runtimeDir, "index.js").writeText("export default function bootstrap(api) { return api; }", Charsets.UTF_8)
    }

    private fun PluginInstallRecord.withBootstrapRoot(rootDir: File): PluginInstallRecord {
        val contract = requireNotNull(packageContractSnapshot) {
            "Plugin v2 test record must include packageContractSnapshot."
        }
        return copyWith(
            extractedDir = rootDir.absolutePath,
            packageContractSnapshot = contract.copy(
                runtime = contract.runtime.copy(
                    bootstrap = "runtime/index.js",
                ),
            ),
        )
    }
}

private class RecordingPluginV2RuntimeLoaderScriptExecutor(
    bootstrapSessions: List<ExternalPluginBootstrapSession>,
) : ExternalPluginScriptExecutor {
    private val bootstrapSessions = bootstrapSessions.toMutableList()
    val bootstrapRequests = mutableListOf<ExternalPluginBootstrapSessionRequest>()

    override fun execute(request: ExternalPluginScriptExecutionRequest): String {
        error("Legacy execute is not expected in PluginV2RuntimeLoaderTest.")
    }

    override fun openBootstrapSession(
        request: ExternalPluginBootstrapSessionRequest,
    ): ExternalPluginBootstrapSession {
        bootstrapRequests += request
        return if (bootstrapSessions.isNotEmpty()) {
            bootstrapSessions.removeAt(0)
        } else {
            BootstrappingSession()
        }
    }
}

private class BootstrappingSession(
    private val registrations: Int = 1,
    private val commandRegistrations: List<CommandHandlerRegistrationInput> = emptyList(),
    private val lifecycleRegistrations: List<LifecycleHandlerRegistrationInput> = emptyList(),
) : ExternalPluginBootstrapSession {
    private val globals = LinkedHashMap<String, Any?>()
    private val handleCounter = AtomicInteger(0)

    override val pluginId: String = "plugin-v2-test"
    override val bootstrapAbsolutePath: String = "bootstrap.js"
    override val bootstrapTimeoutMs: Long = 10_000L

    var disposed: Boolean = false
        private set

    override val liveHandleCount: Int
        get() = globals.size + handleCounter.get()

    override fun installGlobal(name: String, value: Any?) {
        globals[name] = value
    }

    override fun executeBootstrap() {
        val hostApi = globals["__astrbotBootstrapHostApi"] as? PluginV2BootstrapHostApi
            ?: error("Missing bootstrap host api global.")
        repeat(registrations) {
            hostApi.registerMessageHandler(
                MessageHandlerRegistrationInput(
                    handler = PluginV2CallbackHandle { },
                ),
            )
            handleCounter.incrementAndGet()
        }
        commandRegistrations.forEach { descriptor ->
            hostApi.registerCommandHandler(descriptor)
            handleCounter.incrementAndGet()
        }
        lifecycleRegistrations.forEach { descriptor ->
            hostApi.registerLifecycleHandler(descriptor)
            handleCounter.incrementAndGet()
        }
    }

    override fun dispose() {
        disposed = true
        globals.clear()
    }
}

private fun lifecycleRegistration(
    hook: PluginLifecycleHookSurface,
    registrationKey: String,
    handle: PluginV2CallbackHandle,
): LifecycleHandlerRegistrationInput {
    return LifecycleHandlerRegistrationInput(
        registrationKey = registrationKey,
        hook = hook.wireValue,
        handler = handle,
    )
}

private fun commandRegistration(
    registrationKey: String,
    command: String,
    aliases: List<String> = emptyList(),
    groupPath: List<String> = emptyList(),
): CommandHandlerRegistrationInput {
    return CommandHandlerRegistrationInput(
        base = BaseHandlerRegistrationInput(
            registrationKey = registrationKey,
        ),
        command = command,
        aliases = aliases,
        groupPath = groupPath,
        handler = PluginV2CallbackHandle {},
    )
}
