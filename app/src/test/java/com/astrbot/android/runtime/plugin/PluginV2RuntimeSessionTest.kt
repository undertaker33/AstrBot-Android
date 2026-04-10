package com.astrbot.android.runtime.plugin

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2RuntimeSessionTest {
    @Test
    fun runtimeSessionState_enum_matches_phase2_contract() {
        assertEquals(
            listOf(
                "Unloaded",
                "Loading",
                "BootstrapRunning",
                "Active",
                "BootstrapFailed",
                "Disposed",
            ),
            enumConstantNames("PluginV2RuntimeSessionState"),
        )
    }

    @Test
    fun runtimeSession_starts_with_session_identity_state_and_empty_registry_refs() {
        val installRecord = samplePluginV2InstallRecord()
        val session = PluginV2RuntimeSession(
            installRecord = installRecord,
            sessionInstanceId = "session-alpha",
        )

        assertEquals(installRecord.pluginId, session.pluginId)
        assertEquals("session-alpha", session.sessionInstanceId)
        assertEquals("Unloaded", session.state.toString())
        assertNotNull(session.packageContractSnapshot)
        assertNull(session.rawRegistry)
        assertNull(session.compiledRegistry)
    }

    @Test
    fun runtimeSession_rejects_manifest_only_install_records_before_bootstrap() {
        val failure = runCatching {
            PluginV2RuntimeSession(
                installRecord = samplePluginV2ManifestOnlyInstallRecord(),
                sessionInstanceId = "session-manifest-only",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("packageContractSnapshot") == true)
    }

    @Test
    fun runtimeSession_rejects_blank_session_instance_id() {
        val failure = runCatching {
            PluginV2RuntimeSession(
                installRecord = samplePluginV2InstallRecord(),
                sessionInstanceId = " ",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("sessionInstanceId") == true)
    }

    @Test
    fun runtimeSession_allocates_callback_tokens_in_bootstrap_running_state_and_caps_the_store() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-alpha",
        )

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        assertEquals("cb::session-alpha::1", session.allocateCallbackToken().toString())
        assertEquals("cb::session-alpha::2", session.allocateCallbackToken().toString())

        repeat(1_022) {
            session.allocateCallbackToken()
        }

        val failure = runCatching {
            session.allocateCallbackToken()
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("1024") == true)
    }

    @Test
    fun runtimeSession_rejects_callback_token_allocation_outside_bootstrap_running_state() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-outside-bootstrap",
        )

        val failure = runCatching {
            session.allocateCallbackToken()
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("BootstrapRunning") == true)
    }

    @Test
    fun runtimeSession_collects_callback_handles_by_token_until_disposed() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-handles",
        )
        val handle = PluginV2CallbackHandle {}

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        val token = session.allocateCallbackToken(handle)

        assertSame(handle, session.requireCallbackHandle(token))

        session.dispose()

        val failure = runCatching {
            session.requireCallbackHandle(token)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun runtimeSession_dispose_moves_to_disposed_and_rejects_more_tokens() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-dispose",
        )

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        session.allocateCallbackToken()
        session.dispose()

        assertEquals("Disposed", session.state.toString())
        val failure = runCatching {
            session.allocateCallbackToken()
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun runtimeSession_rejects_invalid_and_duplicate_transitions() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-transitions",
        )

        val invalidStart = runCatching {
            session.transitionTo(PluginV2RuntimeSessionState.Active)
        }.exceptionOrNull()
        assertTrue(invalidStart is IllegalStateException)

        session.transitionTo(PluginV2RuntimeSessionState.Loading)

        val duplicate = runCatching {
            session.transitionTo(PluginV2RuntimeSessionState.Loading)
        }.exceptionOrNull()
        assertTrue(duplicate is IllegalStateException)

        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        val invalidBacktrack = runCatching {
            session.transitionTo(PluginV2RuntimeSessionState.Loading)
        }.exceptionOrNull()
        assertTrue(invalidBacktrack is IllegalStateException)
    }

    @Test
    fun runtimeSession_registry_attachment_requires_bootstrap_running_state() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-attach",
        )
        val rawRegistry = PluginV2RawRegistry(session.pluginId)
        val compiledRegistry = newPluginV2CompiledRegistry() as PluginV2CompiledRegistry

        val rawBeforeBootstrap = runCatching {
            session.attachRawRegistry(rawRegistry)
        }.exceptionOrNull()
        assertTrue(rawBeforeBootstrap is IllegalStateException)

        val compiledBeforeBootstrap = runCatching {
            session.attachCompiledRegistry(compiledRegistry)
        }.exceptionOrNull()
        assertTrue(compiledBeforeBootstrap is IllegalStateException)

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        session.attachRawRegistry(rawRegistry)
        session.attachCompiledRegistry(compiledRegistry)

        assertNotNull(session.rawRegistry)
        assertNotNull(session.compiledRegistry)
    }

    @Test
    fun runtimeSession_rejects_raw_registry_for_a_different_plugin_id() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-ownership",
        )
        val rawRegistry = PluginV2RawRegistry("com.example.v2.other")

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val failure = runCatching {
            session.attachRawRegistry(rawRegistry)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("pluginId") == true)
    }

    @Test
    fun runtimeSession_rejects_replacing_attached_raw_registry_for_same_plugin_id() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-replacement",
        )
        val firstRawRegistry = PluginV2RawRegistry(session.pluginId)
        val secondRawRegistry = PluginV2RawRegistry(session.pluginId)

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        session.attachRawRegistry(firstRawRegistry)
        assertNotSame(firstRawRegistry, secondRawRegistry)

        val failure = runCatching {
            session.attachRawRegistry(secondRawRegistry)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("replacement") == true)
        assertSame(firstRawRegistry, session.rawRegistry)
    }

    @Test
    fun runtimeSession_rejects_null_compiled_registry_attachment() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-null-compiled",
        )

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val failure = runCatching {
            pluginV2Class("PluginV2RuntimeSession")
                .getMethod("attachCompiledRegistry", pluginV2Class("PluginV2CompiledRegistry"))
                .invoke(session, null)
        }.exceptionOrNull()

        assertNotNull(failure)
        assertNull(session.compiledRegistry)
    }

    @Test
    fun runtimeSession_dispose_invalidates_registry_attachment_and_token_allocation() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-post-dispose",
        )
        val rawRegistry = PluginV2RawRegistry(session.pluginId)
        val compiledRegistry = newPluginV2CompiledRegistry() as PluginV2CompiledRegistry

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        session.attachRawRegistry(rawRegistry)
        session.attachCompiledRegistry(compiledRegistry)
        session.dispose()

        val rawAfterDispose = runCatching {
            session.attachRawRegistry(rawRegistry)
        }.exceptionOrNull()
        val compiledAfterDispose = runCatching {
            session.attachCompiledRegistry(compiledRegistry)
        }.exceptionOrNull()
        val tokenAfterDispose = runCatching {
            session.allocateCallbackToken()
        }.exceptionOrNull()

        assertEquals("Disposed", session.state.toString())
        assertNull(session.rawRegistry)
        assertNull(session.compiledRegistry)
        assertTrue(rawAfterDispose is IllegalStateException)
        assertTrue(compiledAfterDispose is IllegalStateException)
        assertTrue(tokenAfterDispose is IllegalStateException)
    }

    @Test
    fun maxCallbacksPerPlugin_constant_is_pinned_to_phase2_limit() {
        assertEquals(1_024, MAX_CALLBACKS_PER_PLUGIN)
    }

    @Test
    fun rawRegistry_schema_version_is_pinned_to_1() {
        val rawRegistry = PluginV2RawRegistry("com.example.v2.raw-registry")

        assertEquals(1, rawRegistry.schemaVersion)
    }

    @Test
    fun dispatchEnvelope_only_contains_phase2_fields() {
        val envelopeFields = pluginV2Class("PluginV2DispatchEnvelope")
            .declaredFields
            .filterNot { field -> Modifier.isStatic(field.modifiers) }
            .map { field -> field.name }
            .sorted()

        assertEquals(
            listOf("callbackToken", "payloadRef", "stage", "traceId"),
            envelopeFields,
        )
    }

    @Test
    fun dispatchPayloadRef_uses_frozen_phase2_shape() {
        val payloadRefFields = pluginV2Class("PluginV2DispatchPayloadRef")
            .declaredFields
            .filterNot { field -> Modifier.isStatic(field.modifiers) }
            .map { field -> field.name }
            .sorted()

        assertEquals(
            listOf("attributes", "kind", "refId"),
            payloadRefFields,
        )
    }

    @Test
    fun dispatchEnvelope_uses_stage_and_payload_ref_contract_types() {
        val payloadRef = pluginV2Class("PluginV2DispatchPayloadRef")
            .getConstructor(
                pluginV2Class("PluginV2PayloadKind"),
                String::class.java,
                Map::class.java,
            )
            .newInstance(
                firstEnumConstant("PluginV2PayloadKind"),
                "payload-opaque-ref",
                linkedMapOf("scope" to "minimal"),
            )
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-envelope",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val envelope = pluginV2Class("PluginV2DispatchEnvelope")
            .getConstructor(
                pluginV2Class("PluginV2DispatchStage"),
                pluginV2Class("PluginV2CallbackToken"),
                pluginV2Class("PluginV2DispatchPayloadRef"),
                String::class.java,
            )
            .newInstance(
                firstEnumConstant("PluginV2DispatchStage"),
                session.allocateCallbackToken(),
                payloadRef,
                "trace-123",
            )

        assertEquals("PluginV2DispatchStage", readProperty(envelope, "stage")?.javaClass?.simpleName)
        assertEquals("PluginV2PayloadKind", readProperty(payloadRef, "kind")?.javaClass?.simpleName)
        assertEquals("payload-opaque-ref", readProperty(payloadRef, "refId"))
        assertEquals(mapOf("scope" to "minimal"), readProperty(payloadRef, "attributes"))
        assertNotNull(envelope)
    }

    @Test
    fun dispatchPayloadRef_rejects_blank_ref_id_and_blank_attribute_keys() {
        val payloadRefClass = pluginV2Class("PluginV2DispatchPayloadRef")
        val constructor = payloadRefClass.getConstructor(
            pluginV2Class("PluginV2PayloadKind"),
            String::class.java,
            Map::class.java,
        )

        val blankRefId = runCatching {
            constructor.newInstance(firstEnumConstant("PluginV2PayloadKind"), " ", emptyMap<String, String>())
        }.exceptionOrNull()
        val blankAttributeKey = runCatching {
            constructor.newInstance(
                firstEnumConstant("PluginV2PayloadKind"),
                "payload-ref",
                linkedMapOf("" to "bad"),
            )
        }.exceptionOrNull()

        assertTrue(blankRefId is java.lang.reflect.InvocationTargetException)
        assertTrue(blankRefId?.cause is IllegalArgumentException)
        assertTrue(blankAttributeKey is java.lang.reflect.InvocationTargetException)
        assertTrue(blankAttributeKey?.cause is IllegalArgumentException)
    }

    @Test
    fun dispatchEnvelope_rejects_blank_trace_id() {
        val payloadRef = pluginV2Class("PluginV2DispatchPayloadRef")
            .getConstructor(
                pluginV2Class("PluginV2PayloadKind"),
                String::class.java,
                Map::class.java,
            )
            .newInstance(
                firstEnumConstant("PluginV2PayloadKind"),
                "payload-opaque-ref",
                emptyMap<String, String>(),
            )
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-trace",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        val constructor = pluginV2Class("PluginV2DispatchEnvelope")
            .getConstructor(
                pluginV2Class("PluginV2DispatchStage"),
                pluginV2Class("PluginV2CallbackToken"),
                pluginV2Class("PluginV2DispatchPayloadRef"),
                String::class.java,
            )

        val failure = runCatching {
            constructor.newInstance(
                firstEnumConstant("PluginV2DispatchStage"),
                session.allocateCallbackToken(),
                payloadRef,
                " ",
            )
        }.exceptionOrNull()

        assertTrue(failure is java.lang.reflect.InvocationTargetException)
        assertTrue(failure?.cause is IllegalArgumentException)
    }
}
