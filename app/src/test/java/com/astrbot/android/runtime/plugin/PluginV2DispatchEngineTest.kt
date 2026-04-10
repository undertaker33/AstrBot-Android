package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityState
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2DispatchEngineTest {
    @Test
    fun dispatch_orders_envelopes_by_priority_within_stage() {
        val engine = PluginV2DispatchEngine()
        val activeFixture = buildFixture(
            pluginId = "com.astrbot.samples.dispatch_active",
            activate = true,
            registrationKind = RegistrationKind.Message,
            priorities = listOf(5, 20),
        )
        val snapshot = PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = mapOf(activeFixture.session.pluginId to activeFixture.entry),
            activeSessionsByPluginId = mapOf(activeFixture.session.pluginId to activeFixture.session),
            compiledRegistriesByPluginId = mapOf(activeFixture.session.pluginId to activeFixture.compiledRegistry),
        )

        val plan = engine.dispatch(
            stage = PluginV2InternalStage.AdapterMessage,
            snapshot = snapshot,
        )

        assertTrue(plan.observations.isEmpty())
        assertEquals(2, plan.envelopes.size)
        assertEquals(
            listOf(
                activeFixture.tokens[1].value,
                activeFixture.tokens[0].value,
            ),
            plan.envelopes.map { envelope -> envelope.callbackToken.value },
        )
        assertTrue(plan.envelopes.all { envelope -> envelope.stage == PluginV2DispatchStage.Skeleton })
    }

    @Test
    fun dispatch_reports_skip_missing_and_inactive_observations() {
        val engine = PluginV2DispatchEngine()
        val activeFixture = buildFixture(
            pluginId = "com.astrbot.samples.dispatch_active",
            activate = true,
            registrationKind = RegistrationKind.Message,
            priorities = listOf(10),
        )
        val inactiveFixture = buildFixture(
            pluginId = "com.astrbot.samples.dispatch_inactive",
            activate = false,
            registrationKind = RegistrationKind.Message,
            priorities = listOf(10),
        )
        val skipFixture = buildFixture(
            pluginId = "com.astrbot.samples.dispatch_skip",
            activate = true,
            registrationKind = RegistrationKind.Command,
            priorities = listOf(10),
        )
        val missingFixture = buildFixture(
            pluginId = "com.astrbot.samples.dispatch_missing",
            activate = true,
            registrationKind = RegistrationKind.Message,
            priorities = listOf(10),
        )

        val snapshot = PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = linkedMapOf(
                activeFixture.session.pluginId to activeFixture.entry,
                inactiveFixture.session.pluginId to inactiveFixture.entry,
                skipFixture.session.pluginId to skipFixture.entry,
                missingFixture.session.pluginId to missingFixture.entry,
            ),
            activeSessionsByPluginId = linkedMapOf(
                activeFixture.session.pluginId to activeFixture.session,
                inactiveFixture.session.pluginId to inactiveFixture.session,
                skipFixture.session.pluginId to skipFixture.session,
                missingFixture.session.pluginId to missingFixture.session,
            ),
            compiledRegistriesByPluginId = linkedMapOf(
                activeFixture.session.pluginId to activeFixture.compiledRegistry,
                inactiveFixture.session.pluginId to inactiveFixture.compiledRegistry,
                skipFixture.session.pluginId to skipFixture.compiledRegistry,
            ),
        )

        val plan = engine.dispatch(
            stage = PluginV2InternalStage.AdapterMessage,
            snapshot = snapshot,
        )

        assertEquals(1, plan.envelopes.size)
        assertEquals(
            listOf(
                PluginV2DispatchObservationKind.Inactive,
                PluginV2DispatchObservationKind.Missing,
                PluginV2DispatchObservationKind.Skip,
            ),
            plan.observations.map { observation -> observation.kind },
        )
    }

    private fun buildFixture(
        pluginId: String,
        activate: Boolean,
        registrationKind: RegistrationKind,
        priorities: List<Int>,
    ): RuntimeFixture {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-${UUID.randomUUID()}",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        val hostApi = PluginV2BootstrapHostApi(session)
        val tokens = priorities.map { priority ->
            when (registrationKind) {
                RegistrationKind.Message -> hostApi.registerMessageHandler(
                    com.astrbot.android.runtime.plugin.MessageHandlerRegistrationInput(
                        base = BaseHandlerRegistrationInput(priority = priority),
                        handler = PluginV2CallbackHandle { },
                    ),
                )

                RegistrationKind.Command -> hostApi.registerCommandHandler(
                    com.astrbot.android.runtime.plugin.CommandHandlerRegistrationInput(
                        base = BaseHandlerRegistrationInput(priority = priority),
                        command = "cmd-$priority",
                        handler = PluginV2CallbackHandle { },
                    ),
                )
            }
        }

        val rawRegistry = requireNotNull(session.rawRegistry)
        val compiler = PluginV2RegistryCompiler()
        val compileResult = compiler.compile(rawRegistry)
        val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
        session.attachCompiledRegistry(compiledRegistry)
        if (activate) {
            session.transitionTo(PluginV2RuntimeSessionState.Active)
        }

        return RuntimeFixture(
            session = session,
            compiledRegistry = compiledRegistry,
            entry = PluginV2ActiveRuntimeEntry(
                session = session,
                compiledRegistry = compiledRegistry,
                lastBootstrapSummary = PluginV2BootstrapSummary(
                    pluginId = pluginId,
                    sessionInstanceId = session.sessionInstanceId,
                    compiledAtEpochMillis = 0L,
                    handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                    warningCount = compileResult.diagnostics.count { diagnostic -> diagnostic.severity == DiagnosticSeverity.Warning },
                    errorCount = compileResult.diagnostics.count { diagnostic -> diagnostic.severity == DiagnosticSeverity.Error },
                ),
                diagnostics = compileResult.diagnostics,
                callbackTokens = session.snapshotCallbackTokens(),
            ),
            tokens = tokens,
        )
    }

    private enum class RegistrationKind {
        Message,
        Command,
    }

    private data class RuntimeFixture(
        val session: PluginV2RuntimeSession,
        val compiledRegistry: PluginV2CompiledRegistrySnapshot,
        val entry: PluginV2ActiveRuntimeEntry,
        val tokens: List<PluginV2CallbackToken>,
    )
}
