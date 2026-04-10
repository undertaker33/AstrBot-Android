package com.astrbot.android.runtime.plugin

import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ActiveRuntimeStoreTest {
    @Test
    fun atomic_reference_and_mutex_commit_model_does_not_expose_half_built_snapshots() {
        val store = PluginV2ActiveRuntimeStore()
        val entry = activeRuntimeEntry(
            pluginId = "com.example.v2.store.atomic",
            sessionInstanceId = "session-atomic",
            callbackTokens = 1,
        )
        val pluginId = entry.pluginId
        val readFailure = AtomicReference<Throwable?>(null)
        val writeFailure = AtomicReference<Throwable?>(null)

        val writerThread = thread(name = "plugin-v2-store-writer") {
            runBlocking {
                try {
                    repeat(200) {
                        store.commitLoadedRuntime(entry)
                        store.unload(pluginId)
                    }
                } catch (error: Throwable) {
                    writeFailure.set(error)
                }
            }
        }

        val readerThread = thread(name = "plugin-v2-store-reader") {
            try {
                while (writerThread.isAlive) {
                    assertSnapshotConsistency(store.snapshot(), pluginId)
                }
            } catch (error: Throwable) {
                readFailure.set(error)
            }
        }

        writerThread.join(5_000L)
        readerThread.join(5_000L)

        writeFailure.get()?.let { throw it }
        readFailure.get()?.let { throw it }
        assertFalse(writerThread.isAlive)
        assertFalse(readerThread.isAlive)
    }

    @Test
    fun reload_and_unload_clean_old_tokens_and_diagnostics_indexes() {
        val store = PluginV2ActiveRuntimeStore()
        val pluginId = "com.example.v2.store.reload"
        val firstEntry = activeRuntimeEntry(
            pluginId = pluginId,
            sessionInstanceId = "session-first",
            callbackTokens = 2,
            diagnostics = listOf(
                PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    code = "first-warning",
                    message = "first warning",
                    pluginId = pluginId,
                ),
            ),
        )
        val secondEntry = activeRuntimeEntry(
            pluginId = pluginId,
            sessionInstanceId = "session-second",
            callbackTokens = 1,
            diagnostics = listOf(
                PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    code = "second-warning",
                    message = "second warning",
                    pluginId = pluginId,
                ),
            ),
        )

        runBlocking {
            store.commitLoadedRuntime(firstEntry)
        }

        val firstSnapshot = store.snapshot()
        assertEquals(listOf("cb::session-first::1", "cb::session-first::2"), firstSnapshot.callbackTokenIndexByPluginId[pluginId])
        assertEquals(listOf("first-warning"), firstSnapshot.diagnosticsByPluginId[pluginId]?.map { it.code })

        runBlocking {
            store.commitLoadedRuntime(secondEntry)
        }

        val reloadedSnapshot = store.snapshot()
        assertEquals(listOf("cb::session-second::1"), reloadedSnapshot.callbackTokenIndexByPluginId[pluginId])
        assertEquals(listOf("second-warning"), reloadedSnapshot.diagnosticsByPluginId[pluginId]?.map { it.code })
        assertEquals(secondEntry.session, reloadedSnapshot.activeSessionsByPluginId[pluginId])
        assertEquals(secondEntry.compiledRegistry, reloadedSnapshot.compiledRegistriesByPluginId[pluginId])

        runBlocking {
            store.unload(pluginId)
        }

        val unloadedSnapshot = store.snapshot()
        assertNull(unloadedSnapshot.activeRuntimeEntriesByPluginId[pluginId])
        assertNull(unloadedSnapshot.callbackTokenIndexByPluginId[pluginId])
        assertNull(unloadedSnapshot.diagnosticsByPluginId[pluginId])
    }

    @Test
    fun commit_publishes_runtime_store_committed_and_diagnostics_feedback_logs() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 2_000L })
        val store = PluginV2ActiveRuntimeStore(
            logBus = logBus,
            clock = { 2_000L },
        )
        val pluginId = "com.example.v2.store.feedback"
        val entry = activeRuntimeEntry(
            pluginId = pluginId,
            sessionInstanceId = "session-feedback",
            callbackTokens = 1,
            diagnostics = listOf(
                PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    code = "warn.feedback",
                    message = "warning feedback",
                    pluginId = pluginId,
                ),
            ),
        )

        runBlocking {
            store.commitLoadedRuntime(entry)
        }

        val snapshot = store.snapshot()
        assertEquals(listOf("warn.feedback"), snapshot.diagnosticsByPluginId[pluginId]?.map { it.code })
        val codes = logBus.snapshot(limit = 20, pluginId = pluginId).map { it.code }
        assertTrue(codes.contains("runtime_store_committed"))
        assertTrue(codes.contains("runtime_diagnostic_feedback"))
    }

    @Test
    fun commit_rejects_inconsistent_entry_payload_instead_of_publishing_divergent_snapshot() {
        val store = PluginV2ActiveRuntimeStore()
        val pluginId = "com.example.v2.store.reject"
        val validEntry = activeRuntimeEntry(
            pluginId = pluginId,
            sessionInstanceId = "session-reject",
            callbackTokens = 1,
            diagnostics = listOf(
                PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    code = "warn.valid",
                    message = "valid warning",
                    pluginId = pluginId,
                ),
            ),
        )
        val staleEntry = validEntry.copy(
            callbackTokens = emptyList(),
            lastBootstrapSummary = validEntry.lastBootstrapSummary.copy(
                warningCount = 99,
                handlerCount = 99,
            ),
        )

        val failure = runCatching {
            runBlocking {
                store.commitLoadedRuntime(staleEntry)
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(store.snapshot().activeRuntimeEntriesByPluginId.isEmpty())
    }

    @Test
    fun commit_freezes_compiled_registry_and_diagnostics_snapshot_against_source_mutation() {
        val store = PluginV2ActiveRuntimeStore()
        val pluginId = "com.example.v2.store.freeze"
        val mutableDiagnostics = mutableListOf(
            PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Warning,
                code = "warn.freeze",
                message = "before freeze",
                pluginId = pluginId,
            ),
        )
        val entry = activeRuntimeEntry(
            pluginId = pluginId,
            sessionInstanceId = "session-freeze",
            callbackTokens = 1,
            diagnostics = mutableDiagnostics,
        )
        val mutableRegistry = entry.compiledRegistry as PluginV2CompiledRegistrySnapshot
        val mutableMessageHandlers = mutableRegistry.handlerRegistry.messageHandlers.toMutableList()
        val mutableDispatchIndex = mutableRegistry.dispatchIndex.handlerIdsByStage.mapValues { (_, ids) ->
            ids.toMutableList()
        }.toMutableMap()
        val mutableRegistryPayload = mutableRegistry.copy(
            handlerRegistry = mutableRegistry.handlerRegistry.copy(
                messageHandlers = mutableMessageHandlers,
            ),
            dispatchIndex = mutableRegistry.dispatchIndex.copy(
                handlerIdsByStage = mutableDispatchIndex,
            ),
        )
        val mutableEntry = entry.copy(compiledRegistry = mutableRegistryPayload)

        runBlocking {
            store.commitLoadedRuntime(mutableEntry)
        }
        mutableDiagnostics.clear()
        mutableMessageHandlers.clear()
        mutableDispatchIndex.clear()

        val snapshot = store.snapshot()
        val publishedEntry = snapshot.activeRuntimeEntriesByPluginId[pluginId]!!
        assertNotSame(mutableEntry, publishedEntry)
        val publishedRegistry = snapshot.compiledRegistriesByPluginId[pluginId] as PluginV2CompiledRegistrySnapshot
        assertEquals(1, publishedRegistry.handlerRegistry.totalHandlerCount)
        assertTrue(publishedRegistry.dispatchIndex.handlerIdsByStage.isNotEmpty())
        assertEquals(listOf("warn.freeze"), snapshot.diagnosticsByPluginId[pluginId]?.map { it.code })
    }

    @Test
    fun commit_rejects_non_snapshot_compiled_registry_payload() {
        val store = PluginV2ActiveRuntimeStore()
        val entry = activeRuntimeEntry(
            pluginId = "com.example.v2.store.non-snapshot",
            sessionInstanceId = "session-non-snapshot",
            callbackTokens = 1,
        ).copy(
            compiledRegistry = newPluginV2CompiledRegistry() as PluginV2CompiledRegistry,
        )

        val failure = runCatching {
            runBlocking {
                store.commitLoadedRuntime(entry)
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(store.snapshot().activeRuntimeEntriesByPluginId.isEmpty())
    }

    @Test
    fun publication_bypass_paths_are_not_exposed_as_public_store_api() {
        assertTrue(
            PluginV2ActiveRuntimeStore::class.java.methods.none { method ->
                method.name == "update"
            },
        )
        assertTrue(
            PluginV2ActiveRuntimeSnapshot::class.java.methods.none { method ->
                method.name == "withLoadedRuntime"
            },
        )
    }

    @Test
    fun published_snapshot_maps_and_lists_reject_in_place_mutation_attempts() {
        val store = PluginV2ActiveRuntimeStore()
        val pluginId = "com.example.v2.store.immutable"
        val entry = activeRuntimeEntry(
            pluginId = pluginId,
            sessionInstanceId = "session-immutable",
            callbackTokens = 1,
            includeCommandAndRegexHandlers = true,
            diagnostics = listOf(
                PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    code = "warn.immutable",
                    message = "immutable warning",
                    pluginId = pluginId,
                ),
            ),
        )
        runBlocking {
            store.commitLoadedRuntime(entry)
        }
        val snapshot = store.snapshot()

        val mutateEntries = runCatching {
            @Suppress("UNCHECKED_CAST")
            (snapshot.activeRuntimeEntriesByPluginId as MutableMap<String, PluginV2ActiveRuntimeEntry>)["x"] = entry
        }.exceptionOrNull()
        val mutateTokenList = runCatching {
            @Suppress("UNCHECKED_CAST")
            (snapshot.callbackTokenIndexByPluginId[pluginId] as MutableList<String>).add("bad-token")
        }.exceptionOrNull()
        val mutateDispatchIndex = runCatching {
            val registry = snapshot.compiledRegistriesByPluginId[pluginId]!!
            @Suppress("UNCHECKED_CAST")
            (registry.dispatchIndex.handlerIdsByStage as MutableMap<PluginV2InternalStage, List<String>>).clear()
        }.exceptionOrNull()
        val publishedRegistryFromEntry =
            snapshot.activeRuntimeEntriesByPluginId[pluginId]!!.compiledRegistry as PluginV2CompiledRegistrySnapshot
        val publishedRegistryFromIndex = snapshot.compiledRegistriesByPluginId[pluginId]!!
        val commandHandler = publishedRegistryFromEntry.handlerRegistry.commandHandlers.single()
        val regexHandler = publishedRegistryFromEntry.handlerRegistry.regexHandlers.single()
        val mutateAliases = runCatching {
            @Suppress("UNCHECKED_CAST")
            (commandHandler.aliases as MutableList<String>).add("new-alias")
        }.exceptionOrNull()
        val mutateGroupPath = runCatching {
            @Suppress("UNCHECKED_CAST")
            (commandHandler.groupPath as MutableList<String>).add("new-group")
        }.exceptionOrNull()
        val mutateFlags = runCatching {
            @Suppress("UNCHECKED_CAST")
            (regexHandler.flags as MutableSet<String>).add("x")
        }.exceptionOrNull()
        val mutateFilterArguments = runCatching {
            @Suppress("UNCHECKED_CAST")
            (commandHandler.filterAttachments.first().arguments as MutableMap<String, String>)["value"] = "changed"
        }.exceptionOrNull()

        assertTrue(mutateEntries is UnsupportedOperationException)
        assertTrue(mutateTokenList is UnsupportedOperationException)
        assertTrue(mutateDispatchIndex is UnsupportedOperationException)
        assertTrue(mutateAliases is UnsupportedOperationException)
        assertTrue(mutateGroupPath is UnsupportedOperationException)
        assertTrue(mutateFlags is UnsupportedOperationException)
        assertTrue(mutateFilterArguments is UnsupportedOperationException)
        assertEquals(publishedRegistryFromEntry, publishedRegistryFromIndex)
        assertEquals(
            listOf("warn.immutable"),
            store.snapshot().diagnosticsByPluginId[pluginId]?.map { it.code },
        )
    }

    private fun assertSnapshotConsistency(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        pluginId: String,
    ) {
        val entry = snapshot.activeRuntimeEntriesByPluginId[pluginId]
        if (entry == null) {
            assertNull(snapshot.activeSessionsByPluginId[pluginId])
            assertNull(snapshot.compiledRegistriesByPluginId[pluginId])
            assertNull(snapshot.callbackTokenIndexByPluginId[pluginId])
            assertNull(snapshot.diagnosticsByPluginId[pluginId])
            assertNull(snapshot.lastBootstrapSummariesByPluginId[pluginId])
            return
        }

        assertEquals(entry.session, snapshot.activeSessionsByPluginId[pluginId])
        assertEquals(entry.compiledRegistry, snapshot.compiledRegistriesByPluginId[pluginId])
        assertEquals(
            entry.session.snapshotCallbackTokens().map { it.value },
            snapshot.callbackTokenIndexByPluginId[pluginId],
        )
        assertEquals(
            entry.diagnostics.map { it.code },
            snapshot.diagnosticsByPluginId[pluginId]?.map { it.code },
        )
        assertEquals(
            entry.lastBootstrapSummary,
            snapshot.lastBootstrapSummariesByPluginId[pluginId],
        )
    }

    private fun activeRuntimeEntry(
        pluginId: String,
        sessionInstanceId: String,
        callbackTokens: Int,
        includeCommandAndRegexHandlers: Boolean = false,
        diagnostics: List<PluginV2CompilerDiagnostic> = emptyList(),
    ): PluginV2ActiveRuntimeEntry {
        require(callbackTokens >= 1) { "callbackTokens must be at least 1." }
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = sessionInstanceId,
        ).also { runtimeSession ->
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.Loading)
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }

        repeat(callbackTokens - 1) {
            session.allocateCallbackToken(PluginV2CallbackHandle {})
        }

        val rawRegistry = PluginV2RawRegistry(pluginId)
        rawRegistry.appendMessageHandler(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "handler.$sessionInstanceId",
                ),
                handler = PluginV2CallbackHandle {},
            ),
        )
        if (includeCommandAndRegexHandlers) {
            rawRegistry.appendCommandHandler(
                callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
                descriptor = CommandHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "command.$sessionInstanceId",
                        declaredFilters = listOf(
                            BootstrapFilterDescriptor.command("/immutable"),
                        ),
                    ),
                    command = "/immutable",
                    aliases = listOf("/immut"),
                    groupPath = listOf("quality"),
                    handler = PluginV2CallbackHandle {},
                ),
            )
            rawRegistry.appendRegexHandler(
                callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
                descriptor = RegexHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "regex.$sessionInstanceId",
                    ),
                    pattern = "^immut$",
                    flags = setOf("i"),
                    handler = PluginV2CallbackHandle {},
                ),
            )
        }
        val compiler = PluginV2RegistryCompiler()
        val compiled = compiler.compile(rawRegistry).compiledRegistry
            ?: error("Test setup expected a compiled registry.")

        return PluginV2ActiveRuntimeEntry(
            session = session,
            compiledRegistry = compiled,
            lastBootstrapSummary = PluginV2BootstrapSummary(
                pluginId = pluginId,
                sessionInstanceId = sessionInstanceId,
                compiledAtEpochMillis = 123L,
                handlerCount = compiled.handlerRegistry.totalHandlerCount,
                warningCount = diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                errorCount = diagnostics.count { it.severity == DiagnosticSeverity.Error },
            ),
            diagnostics = diagnostics,
            callbackTokens = session.snapshotCallbackTokens(),
        )
    }
}
