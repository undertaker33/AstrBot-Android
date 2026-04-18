package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginLifecycleDiagnosticsStore
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginV2LifecycleManagerTest {
    @Before
    fun setUp() {
        PluginLifecycleDiagnosticsStore.clear()
    }

    @After
    fun tearDown() {
        PluginLifecycleDiagnosticsStore.clear()
    }

    @Test
    fun astrbot_loaded_is_one_shot_for_active_plugins() = runTest {
        val calls = mutableListOf<String>()
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 64)
        val store = PluginV2ActiveRuntimeStore(logBus = NoOpPluginRuntimeLogBus)
        val fixtureA = activeFixture(
            pluginId = "com.example.lifecycle.alpha",
            sessionInstanceId = "session-alpha",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnAstrbotLoaded,
                    registrationKey = "astrbot.alpha",
                    handle = recordingHandle("alpha-astrbot", calls),
                ),
            ),
        )
        val fixtureB = activeFixture(
            pluginId = "com.example.lifecycle.beta",
            sessionInstanceId = "session-beta",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnAstrbotLoaded,
                    registrationKey = "astrbot.beta",
                    handle = recordingHandle("beta-astrbot", calls),
                ),
            ),
        )
        store.commitLoadedRuntime(fixtureA.entry)
        store.commitLoadedRuntime(fixtureB.entry)

        val manager = newLifecycleManager(store = store, logBus = logBus)

        manager.onAstrbotLoaded()
        manager.onAstrbotLoaded()

        assertEquals(listOf("alpha-astrbot", "beta-astrbot"), calls)
        val codes = logBus.snapshot().map(PluginRuntimeLogRecord::code)
        assertEquals(2, codes.size)
        assertTrue(codes.contains("lifecycle_broadcast_started"))
        assertTrue(codes.contains("lifecycle_broadcast_completed"))
    }

    @Test
    fun platform_loaded_is_deduped_by_platform_instance_key() = runTest {
        val calls = mutableListOf<String>()
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 64)
        val store = PluginV2ActiveRuntimeStore(logBus = NoOpPluginRuntimeLogBus)
        val fixture = activeFixture(
            pluginId = "com.example.lifecycle.platform",
            sessionInstanceId = "session-platform",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPlatformLoaded,
                    registrationKey = "platform.loaded",
                    handle = recordingHandle("platform-loaded", calls),
                ),
            ),
        )
        store.commitLoadedRuntime(fixture.entry)

        val manager = newLifecycleManager(store = store, logBus = logBus)

        manager.onPlatformLoaded("platform-instance-a")
        manager.onPlatformLoaded("platform-instance-a")
        manager.onPlatformLoaded("platform-instance-b")

        assertEquals(listOf("platform-loaded", "platform-loaded"), calls)
    }

    @Test
    fun plugin_loaded_broadcasts_to_target_and_remaining_actives_in_priority_order() = runTest {
        val calls = mutableListOf<String>()
        val receivedPayloads = mutableListOf<PluginErrorEventPayload>()
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 64)
        val store = PluginV2ActiveRuntimeStore(logBus = NoOpPluginRuntimeLogBus)
        val fixtureAlpha = activeFixture(
            pluginId = "com.example.lifecycle.alpha",
            sessionInstanceId = "session-alpha",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginLoaded,
                    priority = 1,
                    registrationKey = "loaded.alpha",
                    handle = recordingEventAwareHandle("alpha-loaded", calls, receivedPayloads),
                ),
            ),
        )
        val fixtureBeta = activeFixture(
            pluginId = "com.example.lifecycle.beta",
            sessionInstanceId = "session-beta",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginLoaded,
                    priority = 10,
                    registrationKey = "loaded.beta",
                    handle = recordingEventAwareHandle("beta-loaded", calls, receivedPayloads),
                ),
            ),
        )
        store.commitLoadedRuntime(fixtureAlpha.entry)
        store.commitLoadedRuntime(fixtureBeta.entry)

        val manager = newLifecycleManager(store = store, logBus = logBus)

        manager.onPluginLoaded("com.example.lifecycle.beta")

        assertEquals(listOf("beta-loaded", "alpha-loaded"), calls)
        val receivedMetadata = receivedPayloads.map {
            requireNotNull(it as? PluginLifecycleMetadata) {
                "Expected PluginLifecycleMetadata."
            }
        }
        assertEquals(
            listOf(
                PluginLifecycleMetadata(
                    pluginName = "com.example.lifecycle.beta",
                    pluginVersion = fixtureBeta.session.installRecord.installedVersion,
                ),
                PluginLifecycleMetadata(
                    pluginName = "com.example.lifecycle.beta",
                    pluginVersion = fixtureBeta.session.installRecord.installedVersion,
                ),
            ),
            receivedMetadata,
        )
        assertTrue(logBus.snapshot().map(PluginRuntimeLogRecord::code).contains("lifecycle_broadcast_started"))
        assertTrue(logBus.snapshot().map(PluginRuntimeLogRecord::code).contains("lifecycle_broadcast_completed"))
    }

    @Test
    fun unloaded_plugin_is_excluded_after_store_removal() = runTest {
        val calls = mutableListOf<String>()
        val receivedPayloads = mutableListOf<PluginErrorEventPayload>()
        val store = PluginV2ActiveRuntimeStore(logBus = NoOpPluginRuntimeLogBus)
        val fixtureAlpha = activeFixture(
            pluginId = "com.example.lifecycle.alpha",
            sessionInstanceId = "session-alpha",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginUnloaded,
                    registrationKey = "unloaded.alpha",
                    handle = recordingEventAwareHandle("alpha-unloaded", calls, receivedPayloads),
                ),
            ),
        )
        val fixtureBeta = activeFixture(
            pluginId = "com.example.lifecycle.beta",
            sessionInstanceId = "session-beta",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginUnloaded,
                    registrationKey = "unloaded.beta",
                    handle = recordingEventAwareHandle("beta-unloaded", calls, receivedPayloads),
                ),
            ),
        )
        store.commitLoadedRuntime(fixtureAlpha.entry)
        store.commitLoadedRuntime(fixtureBeta.entry)
        val manager = newLifecycleManager(store = store, logBus = InMemoryPluginRuntimeLogBus(capacity = 64))
        manager.onPluginLoaded("com.example.lifecycle.alpha")
        calls.clear()
        store.unload("com.example.lifecycle.alpha")

        manager.onPluginUnloaded(
            pluginId = "com.example.lifecycle.alpha",
            pluginVersion = fixtureAlpha.session.installRecord.installedVersion,
        )

        assertEquals(listOf("beta-unloaded"), calls)
        val receivedMetadata = receivedPayloads.map {
            requireNotNull(it as? PluginLifecycleMetadata) {
                "Expected PluginLifecycleMetadata."
            }
        }
        assertEquals(
            PluginLifecycleMetadata(
                pluginName = "com.example.lifecycle.alpha",
                pluginVersion = fixtureAlpha.session.installRecord.installedVersion,
            ),
            receivedMetadata.single(),
        )
    }

    @Test
    fun lifecycle_handler_failure_emits_plugin_error_args_with_failing_plugin_name() = runTest {
        val calls = mutableListOf<String>()
        val receivedErrorArgs = mutableListOf<PluginErrorHookArgs>()
        val store = PluginV2ActiveRuntimeStore(logBus = NoOpPluginRuntimeLogBus)
        val fixtureAlpha = activeFixture(
            pluginId = "com.example.lifecycle.alpha",
            sessionInstanceId = "session-alpha",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginLoaded,
                    registrationKey = "loaded.alpha",
                    handle = throwingHandle("alpha-loaded"),
                ),
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginError,
                    registrationKey = "error.alpha",
                    handle = throwingHandle("alpha-error"),
                ),
            ),
        )
        val fixtureBeta = activeFixture(
            pluginId = "com.example.lifecycle.beta",
            sessionInstanceId = "session-beta",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginLoaded,
                    registrationKey = "loaded.beta",
                    handle = recordingEventAwareHandle("beta-loaded", calls, mutableListOf()),
                ),
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginError,
                    registrationKey = "error.beta",
                    handle = recordingErrorHookHandle(receivedErrorArgs),
                ),
            ),
        )
        store.commitLoadedRuntime(fixtureAlpha.entry)
        store.commitLoadedRuntime(fixtureBeta.entry)

        val manager = newLifecycleManager(store = store, logBus = InMemoryPluginRuntimeLogBus(capacity = 64))

        manager.onPluginLoaded("com.example.lifecycle.beta")

        assertEquals(listOf("beta-loaded"), calls)
        assertEquals(1, receivedErrorArgs.size)
        val errorArgs = receivedErrorArgs.single()
        assertEquals("com.example.lifecycle.alpha", errorArgs.plugin_name)
        assertEquals("com.example.lifecycle.beta", (errorArgs.event as PluginLifecycleMetadata).pluginName)
        assertEquals("hdl::com.example.lifecycle.alpha::lifecycle::loaded.alpha", errorArgs.handler_name)
        assertEquals("boom:alpha-loaded", errorArgs.error.message)
        assertTrue(errorArgs.traceback_text.contains("boom:alpha-loaded"))
    }

    @Test
    fun lifecycle_error_hook_failure_is_logged_without_recursive_emit() = runTest {
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 64)
        val store = PluginV2ActiveRuntimeStore(logBus = NoOpPluginRuntimeLogBus)
        val fixtureAlpha = activeFixture(
            pluginId = "com.example.lifecycle.alpha",
            sessionInstanceId = "session-alpha",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginLoaded,
                    registrationKey = "loaded.alpha",
                    handle = throwingHandle("alpha-loaded"),
                ),
            ),
        )
        val fixtureBeta = activeFixture(
            pluginId = "com.example.lifecycle.beta",
            sessionInstanceId = "session-beta",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginError,
                    registrationKey = "error.beta",
                    handle = throwingHandle("beta-error"),
                ),
            ),
        )
        store.commitLoadedRuntime(fixtureAlpha.entry)
        store.commitLoadedRuntime(fixtureBeta.entry)

        val manager = newLifecycleManager(store = store, logBus = logBus)

        manager.onPluginLoaded("com.example.lifecycle.beta")

        val codes = logBus.snapshot().map(PluginRuntimeLogRecord::code)
        assertTrue(codes.contains("plugin_error_hook_emitted"))
        assertTrue(codes.contains("plugin_error_hook_failed"))
        assertEquals(1, codes.count { it == "plugin_error_hook_failed" })
        assertTrue(
            PluginLifecycleDiagnosticsStore.snapshot().any { record ->
                record.pluginId == "com.example.lifecycle.beta" && record.code == "plugin_error_hook_failed"
            },
        )
    }

    @Test
    fun concurrent_plugin_errors_both_enter_on_plugin_error_without_global_suppression() = runTest {
        val receivedErrorArgs = CopyOnWriteArrayList<PluginErrorHookArgs>()
        val hookEntryCount = AtomicInteger(0)
        val firstHookEntered = CompletableDeferred<Unit>()
        val releaseFirstHook = CompletableDeferred<Unit>()
        val store = PluginV2ActiveRuntimeStore(logBus = NoOpPluginRuntimeLogBus)
        val fixture = activeFixture(
            pluginId = "com.example.lifecycle.error-hook",
            sessionInstanceId = "session-error-hook",
            handlers = listOf(
                lifecycleHandler(
                    hook = PluginLifecycleHookSurface.OnPluginError,
                    registrationKey = "error.hook",
                    handle = object : PluginV2EventAwareCallbackHandle {
                        override fun invoke() = Unit

                        override suspend fun handleEvent(event: PluginErrorEventPayload) {
                            val current = hookEntryCount.incrementAndGet()
                            if (current == 1) {
                                firstHookEntered.complete(Unit)
                                releaseFirstHook.await()
                            }
                            receivedErrorArgs += requireNotNull(event as? PluginErrorHookArgs) {
                                "Expected PluginErrorHookArgs."
                            }
                        }
                    },
                ),
            ),
        )
        store.commitLoadedRuntime(fixture.entry)
        val manager = newLifecycleManager(store = store, logBus = InMemoryPluginRuntimeLogBus(capacity = 64))

        val first = async {
            manager.emitPluginError(
                event = PluginLifecycleMetadata(
                    pluginName = "com.example.lifecycle.alpha",
                    pluginVersion = "1.0.0",
                ),
                pluginName = "com.example.lifecycle.alpha",
                handlerName = "handler.alpha",
                error = IllegalStateException("boom-alpha"),
                tracebackText = "trace-alpha",
            )
        }
        firstHookEntered.await()
        val second = async {
            manager.emitPluginError(
                event = PluginLifecycleMetadata(
                    pluginName = "com.example.lifecycle.beta",
                    pluginVersion = "1.0.0",
                ),
                pluginName = "com.example.lifecycle.beta",
                handlerName = "handler.beta",
                error = IllegalStateException("boom-beta"),
                tracebackText = "trace-beta",
            )
        }
        releaseFirstHook.complete(Unit)
        first.await()
        second.await()

        assertEquals(2, receivedErrorArgs.size)
        assertEquals(
            setOf("com.example.lifecycle.alpha", "com.example.lifecycle.beta"),
            receivedErrorArgs.map { it.plugin_name }.toSet(),
        )
    }

    private data class LifecycleHandlerSpec(
        val hook: PluginLifecycleHookSurface,
        val registrationKey: String,
        val priority: Int = 0,
        val handle: PluginV2CallbackHandle,
    )

    private data class RuntimeFixture(
        val session: PluginV2RuntimeSession,
        val entry: PluginV2ActiveRuntimeEntry,
    )

    private fun activeFixture(
        pluginId: String,
        sessionInstanceId: String,
        handlers: List<LifecycleHandlerSpec>,
    ): RuntimeFixture {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = sessionInstanceId,
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = NoOpPluginRuntimeLogBus,
            clock = { 1L },
        )
        handlers.forEach { spec ->
            hostApi.registerLifecycleHandler(
                LifecycleHandlerRegistrationInput(
                    registrationKey = spec.registrationKey,
                    hook = spec.hook.wireValue,
                    priority = spec.priority,
                    handler = spec.handle,
                ),
            )
        }

        val compileResult = PluginV2RegistryCompiler(
            logBus = NoOpPluginRuntimeLogBus,
            clock = { 1L },
        ).compile(requireNotNull(session.rawRegistry))
        val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
        session.attachCompiledRegistry(compiledRegistry)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        return RuntimeFixture(
            session = session,
            entry = PluginV2ActiveRuntimeEntry(
                session = session,
                compiledRegistry = compiledRegistry,
                lastBootstrapSummary = PluginV2BootstrapSummary(
                    pluginId = pluginId,
                    sessionInstanceId = sessionInstanceId,
                    compiledAtEpochMillis = 1L,
                    handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                    warningCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                    errorCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Error },
                ),
                diagnostics = compileResult.diagnostics,
                callbackTokens = session.snapshotCallbackTokens(),
            ),
        )
    }

    private fun lifecycleHandler(
        hook: PluginLifecycleHookSurface,
        registrationKey: String,
        priority: Int = 0,
        handle: PluginV2CallbackHandle,
    ): LifecycleHandlerSpec {
        return LifecycleHandlerSpec(
            hook = hook,
            registrationKey = registrationKey,
            priority = priority,
            handle = handle,
        )
    }

    private fun recordingHandle(
        label: String,
        calls: MutableList<String>,
    ): PluginV2CallbackHandle {
        return PluginV2CallbackHandle {
            calls += label
        }
    }

    private fun recordingEventAwareHandle(
        label: String,
        calls: MutableList<String>,
        payloads: MutableList<PluginErrorEventPayload>,
    ): PluginV2EventAwareCallbackHandle {
        return object : PluginV2EventAwareCallbackHandle {
            override fun invoke() {
                calls += label
            }

            override suspend fun handleEvent(event: PluginErrorEventPayload) {
                calls += label
                payloads += event
            }
        }
    }

    private fun recordingErrorHookHandle(
        payloads: MutableList<PluginErrorHookArgs>,
    ): PluginV2EventAwareCallbackHandle {
        return object : PluginV2EventAwareCallbackHandle {
            override fun invoke() {
                error("error hook should receive payload")
            }

            override suspend fun handleEvent(event: PluginErrorEventPayload) {
                payloads += requireNotNull(event as? PluginErrorHookArgs) {
                    "Expected PluginErrorHookArgs."
                }
            }
        }
    }

    private fun throwingHandle(label: String): PluginV2CallbackHandle {
        return PluginV2CallbackHandle {
            error("boom:$label")
        }
    }

    private fun newLifecycleManager(
        store: PluginV2ActiveRuntimeStore,
        logBus: InMemoryPluginRuntimeLogBus,
    ): PluginV2LifecycleManager {
        return PluginV2LifecycleManager(
            store = store,
            logBus = logBus,
            clock = { 1L },
        )
    }
}
