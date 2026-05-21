package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginCompatibilityState
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginManifest
import com.elymbot.android.model.plugin.PluginPackageContractSnapshot
import com.elymbot.android.model.plugin.PluginPermissionDeclaration
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.elymbot.android.model.plugin.PluginSource
import com.elymbot.android.model.plugin.PluginSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ScheduledHandlerRegistryTest {

    @Test
    fun registerScheduledHandler_compiles_cron_descriptor_into_v2_snapshot() {
        val session = bootstrapSession()
        val hostApi = hostApiFor(session)

        hostApi.registerScheduledHandler(
            ScheduledHandlerRegistrationInput(
                key = "daily-summary",
                cron = "0 9 * * *",
                conversationId = "conversation-1",
                handler = NoOpHandle,
            ),
        )

        val raw = checkNotNull(session.rawRegistry)
        assertEquals("daily-summary", raw.scheduledHandlers.single().descriptor.key)

        val compileResult = PluginV2RegistryCompiler().compile(raw)
        val snapshot = checkNotNull(compileResult.compiledRegistry)
        val handler = snapshot.handlerRegistry.scheduledHandlers.single()
        assertEquals("daily-summary", handler.handlerKey)
        assertEquals("0 9 * * *", handler.cron)
        assertEquals("conversation-1", handler.conversationId)
        assertEquals("onebot", handler.platformAdapterType)
    }

    @Test
    fun registerScheduledHandler_requires_schedule_permission_at_registration() {
        val session = bootstrapSession(permissionIds = emptySet())
        val hostApi = hostApiFor(session)

        val error = assertThrows(IllegalStateException::class.java) {
            hostApi.registerScheduledHandler(
                ScheduledHandlerRegistrationInput(
                    key = "daily-summary",
                    cron = "0 9 * * *",
                    conversationId = "conversation-1",
                    handler = NoOpHandle,
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains(PluginV2HostApiPermissions.SCHEDULE_MANAGE))
    }

    @Test
    fun registerScheduledHandler_rejects_duplicate_key_within_same_plugin() {
        val session = bootstrapSession()
        val hostApi = hostApiFor(session)

        repeat(2) {
            hostApi.registerScheduledHandler(
                ScheduledHandlerRegistrationInput(
                    key = "daily-summary",
                    cron = "0 9 * * *",
                    conversationId = "conversation-1",
                    handler = NoOpHandle,
                ),
            )
        }

        val compileResult = PluginV2RegistryCompiler().compile(checkNotNull(session.rawRegistry))

        assertNull(compileResult.compiledRegistry)
        assertEquals("duplicate_schedule_handler_key", compileResult.diagnostics.single().code)
    }

    @Test
    fun registerScheduledHandler_requires_exactly_one_time_source() {
        val session = bootstrapSession()
        val hostApi = hostApiFor(session)

        assertThrows(IllegalArgumentException::class.java) {
            hostApi.registerScheduledHandler(
                ScheduledHandlerRegistrationInput(
                    key = "bad",
                    cron = "0 9 * * *",
                    runAt = 1_000L,
                    conversationId = "conversation-1",
                    handler = NoOpHandle,
                ),
            )
        }
    }

    @Test
    fun registerScheduledHandler_defaults_blank_target_to_current_event_context() {
        val session = bootstrapSession()
        val hostApi = hostApiFor(session)

        hostApi.registerScheduledHandler(
            ScheduledHandlerRegistrationInput(
                key = "daily-summary",
                cron = "0 9 * * *",
                handler = NoOpHandle,
            ),
        )

        val snapshot = checkNotNull(PluginV2RegistryCompiler().compile(checkNotNull(session.rawRegistry)).compiledRegistry)
        val handler = snapshot.handlerRegistry.scheduledHandlers.single()
        assertEquals("conversation-1", handler.conversationId)
        assertEquals("onebot", handler.platformAdapterType)
    }

    @Test
    fun registerScheduledHandler_rejects_target_context_outside_current_event_binding() {
        val session = bootstrapSession()
        val hostApi = hostApiFor(session)

        val conversationError = assertThrows(IllegalArgumentException::class.java) {
            hostApi.registerScheduledHandler(
                ScheduledHandlerRegistrationInput(
                    key = "bad-conversation",
                    cron = "0 9 * * *",
                    conversationId = "conversation-2",
                    handler = NoOpHandle,
                ),
            )
        }
        val platformError = assertThrows(IllegalArgumentException::class.java) {
            hostApi.registerScheduledHandler(
                ScheduledHandlerRegistrationInput(
                    key = "bad-platform",
                    cron = "0 9 * * *",
                    conversationId = "conversation-1",
                    platformAdapterType = "app_chat",
                    handler = NoOpHandle,
                ),
            )
        }

        assertTrue(conversationError.message.orEmpty().contains("current event context"))
        assertTrue(platformError.message.orEmpty().contains("current event context"))
        assertNull(session.rawRegistry)
    }

    @Test
    fun registerScheduledHandler_allows_targetless_bootstrap_without_event_context() {
        val session = bootstrapSession()
        val hostApi = PluginV2BootstrapHostApi(session)

        hostApi.registerScheduledHandler(
            ScheduledHandlerRegistrationInput(
                key = "daily-summary",
                cron = "0 9 * * *",
                handler = NoOpHandle,
            ),
        )

        val snapshot = checkNotNull(PluginV2RegistryCompiler().compile(checkNotNull(session.rawRegistry)).compiledRegistry)
        val handler = snapshot.handlerRegistry.scheduledHandlers.single()
        assertEquals("", handler.conversationId)
        assertEquals("", handler.platformAdapterType)
    }

    @Test
    fun registerScheduledHandler_rejects_explicit_target_without_event_context() {
        val session = bootstrapSession()
        val hostApi = PluginV2BootstrapHostApi(session)

        val error = assertThrows(IllegalArgumentException::class.java) {
            hostApi.registerScheduledHandler(
                ScheduledHandlerRegistrationInput(
                    key = "daily-summary",
                    cron = "0 9 * * *",
                    conversationId = "conversation-1",
                    platformAdapterType = "onebot",
                    handler = NoOpHandle,
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("host-authorized schedule target"))
        assertNull(session.rawRegistry)
    }

    private fun bootstrapSession(
        pluginId: String = "plugin.schedule",
        permissionIds: Set<String> = setOf(PluginV2HostApiPermissions.SCHEDULE_MANAGE),
    ): PluginV2RuntimeSession {
        return PluginV2RuntimeSession(
            installRecord = installRecord(pluginId, permissionIds),
            sessionInstanceId = "session-$pluginId",
        ).also { session ->
            session.transitionTo(PluginV2RuntimeSessionState.Loading)
            session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }
    }

    private fun hostApiFor(
        session: PluginV2RuntimeSession,
        eventContext: PluginV2HostApiEventContext = PluginV2HostApiEventContext(
            eventId = "event-1",
            conversationId = "conversation-1",
            platformAdapterType = "onebot",
            messageType = "group",
        ),
    ): PluginV2BootstrapHostApi {
        return PluginV2BootstrapHostApi(session).also { hostApi ->
            hostApi.attachHostApiEventContextProvider { eventContext }
        }
    }

    private fun installRecord(
        pluginId: String,
        permissionIds: Set<String>,
        version: String = "1.0.0",
    ): PluginInstallRecord {
        val permissions = permissionIds.map { permissionId ->
            PluginPermissionDeclaration(
                permissionId = permissionId,
                title = permissionId,
                description = permissionId,
                riskLevel = PluginRiskLevel.MEDIUM,
                required = true,
            )
        }
        val manifest = PluginManifest(
            pluginId = pluginId,
            version = version,
            protocolVersion = 2,
            author = "ElymBot",
            title = "Schedule Plugin",
            description = "Schedule plugin",
            permissions = permissions,
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Schedule entry",
        )
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(PluginSourceType.LOCAL_FILE, "/tmp/$pluginId.zip", 1L),
            packageContractSnapshot = PluginPackageContractSnapshot(
                protocolVersion = 2,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "quickjs",
                    bootstrap = "runtime/bootstrap.js",
                    apiVersion = 2,
                ),
            ),
            permissionSnapshot = permissions,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            enabled = true,
            installedAt = 1L,
            lastUpdatedAt = 1L,
            extractedDir = "/tmp/$pluginId",
        )
    }

    private object NoOpHandle : PluginV2CallbackHandle {
        override fun invoke() = Unit
    }
}
