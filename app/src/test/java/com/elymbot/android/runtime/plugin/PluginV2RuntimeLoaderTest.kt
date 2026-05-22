package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.di.startup.syncPluginRuntimeRecordsAndSignalReady
import com.elymbot.android.feature.cron.domain.CronJobRepositoryPort
import com.elymbot.android.feature.cron.domain.CronSchedulerPort
import com.elymbot.android.feature.cron.domain.model.CronJob
import com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord
import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginPermissionDeclaration
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginCompatibilityState
import com.elymbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            pluginId = "com.elymbot.samples.loader_eligible",
        ).withBootstrapRoot(rootDir)
        val disabled = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_disabled",
        ).withBootstrapRoot(rootDir).copyWith(enabled = false)
        val incompatible = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_incompatible",
        ).withBootstrapRoot(rootDir).copyWith(
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = false,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
        )
        val legacy = samplePluginInstallRecord(
            pluginId = "com.elymbot.samples.loader_legacy",
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
            pluginId = "com.elymbot.samples.loader_reload",
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
    fun reload_failure_evicts_previous_active_entry_and_disposes_old_session() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-reload-failed")
        createBootstrapFile(rootDir)
        val initialRecord = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_reload_failed",
            version = "1.0.0",
        ).withBootstrapRoot(rootDir)
        val upgradedRecord = samplePluginV2InstallRecord(
            pluginId = initialRecord.pluginId,
            version = "1.1.0",
        ).withBootstrapRoot(rootDir)

        val executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
            bootstrapSessions = listOf(
                BootstrappingSession(registrations = 1),
                BootstrappingSession(executeFailure = IllegalStateException("bootstrap exploded")),
            ),
        )
        val loader = newLoader(executor)

        val firstLoad = loader.load(initialRecord)
        val firstEntry = loaderStore(loader).snapshot().activeRuntimeEntriesByPluginId[initialRecord.pluginId]!!
        val firstSession = firstEntry.session

        val reloadResult = loader.reload(upgradedRecord)
        val snapshotAfterFailure = loaderStore(loader).snapshot()

        assertEquals(PluginV2RuntimeLoadStatus.Loaded, firstLoad.status)
        assertEquals(PluginV2RuntimeLoadStatus.Failed, reloadResult.status)
        assertEquals(firstSession.sessionInstanceId, reloadResult.previousSessionInstanceId)
        assertEquals(PluginV2RuntimeSessionState.Disposed, firstSession.state)
        assertFalse(snapshotAfterFailure.activeRuntimeEntriesByPluginId.containsKey(initialRecord.pluginId))
        assertFalse(snapshotAfterFailure.activeSessionsByPluginId.containsKey(initialRecord.pluginId))
        assertNull(snapshotAfterFailure.lastBootstrapSummariesByPluginId[initialRecord.pluginId])
        assertNull(snapshotAfterFailure.diagnosticsByPluginId[initialRecord.pluginId])
    }

    @Test
    fun unload_removes_active_snapshot_and_disposes_session() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-unload")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_unload",
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
    fun sync_uninstall_deletes_plugin_v2_schedules_for_removed_record() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-uninstall-schedules")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_uninstall_schedules",
        ).withBootstrapRoot(rootDir)
        val scheduleJob = pluginScheduleJob(record.pluginId)
        val repository = RecordingCronRepository()
        val scheduler = RecordingCronScheduler()
        val loader = newLoader(
            executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
                bootstrapSessions = listOf(BootstrappingSession(registrations = 1)),
            ),
            scheduledHandlerLifecycle = PluginV2ScheduledHandlerLifecycle(
                repository = repository,
                scheduler = scheduler,
            ),
        )

        loader.load(record)
        repository.create(scheduleJob)
        repository.updated.clear()
        repository.deleted.clear()
        scheduler.cancelled.clear()
        loader.sync(emptyList())

        assertEquals(listOf(scheduleJob.jobId), scheduler.cancelled)
        assertEquals(listOf(scheduleJob.jobId), repository.deleted)
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun sync_disable_pauses_plugin_v2_schedules_without_deleting_them() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-disable-schedules")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_disable_schedules",
        ).withBootstrapRoot(rootDir)
        val scheduleJob = pluginScheduleJob(record.pluginId)
        val repository = RecordingCronRepository()
        val scheduler = RecordingCronScheduler()
        val loader = newLoader(
            executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
                bootstrapSessions = listOf(BootstrappingSession(registrations = 1)),
            ),
            scheduledHandlerLifecycle = PluginV2ScheduledHandlerLifecycle(
                repository = repository,
                scheduler = scheduler,
            ),
        )

        loader.load(record)
        repository.create(scheduleJob)
        repository.updated.clear()
        repository.deleted.clear()
        scheduler.cancelled.clear()
        loader.sync(listOf(record.copyWith(enabled = false)))

        assertEquals(listOf(scheduleJob.jobId), scheduler.cancelled)
        assertEquals(listOf(scheduleJob.jobId), repository.updated.map { it.jobId })
        assertFalse(repository.updated.single().enabled)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun unload_fails_open_message_streams_for_plugin() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-unload-streams")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_unload_streams",
        ).withBootstrapRoot(rootDir)
        val streamPort = RecordingMessageStreamPort()
        val streamApi = streamApi(streamPort)
        val loader = newLoader(
            executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
                bootstrapSessions = listOf(BootstrappingSession(registrations = 1)),
            ),
            messageStreamApi = streamApi,
        )

        loader.load(record)
        streamApi.openStream(
            context = streamContext(pluginId = record.pluginId),
            request = PluginV2MessageStreamOpenRequest(),
        )
        loader.unload(record.pluginId)

        assertEquals(listOf("stream-1"), streamPort.failed.map { it.streamId })
        assertTrue(streamPort.failed.single().message.contains("unloaded"))
    }

    @Test
    fun load_reconciles_targetless_bootstrap_schedule_without_event_context() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-targetless-schedule")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_targetless_schedule",
        ).withSchedulePermission().withBootstrapRoot(rootDir)
        val repository = RecordingCronRepository()
        val scheduler = RecordingCronScheduler()
        val loader = newLoader(
            executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
                bootstrapSessions = listOf(
                    BootstrappingSession(
                        registrations = 0,
                        scheduledRegistrations = listOf(scheduleRegistration()),
                    ),
                ),
            ),
            scheduledHandlerLifecycle = PluginV2ScheduledHandlerLifecycle(
                repository = repository,
                scheduler = scheduler,
                clock = { 1_000L },
                nextFireTime = { _, _, _ -> 2_000L },
            ),
        )

        val result = loader.load(record)

        assertEquals(PluginV2RuntimeLoadStatus.Loaded, result.status)
        val job = repository.created.single()
        assertEquals("", job.conversationId)
        assertEquals("", job.platform)
        assertEquals("daily-summary", job.pluginSchedulePayload().handlerKey)
        assertEquals(job.jobId, scheduler.scheduled.single().jobId)
    }

    @Test
    fun load_rejects_bootstrap_schedule_with_arbitrary_target_without_event_context() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-arbitrary-schedule")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_arbitrary_schedule",
        ).withSchedulePermission().withBootstrapRoot(rootDir)
        val repository = RecordingCronRepository()
        val scheduler = RecordingCronScheduler()
        val loader = newLoader(
            executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
                bootstrapSessions = listOf(
                    BootstrappingSession(
                        registrations = 0,
                        scheduledRegistrations = listOf(
                            scheduleRegistration(
                                conversationId = "conversation-1",
                                platformAdapterType = "onebot",
                            ),
                        ),
                    ),
                ),
            ),
            scheduledHandlerLifecycle = PluginV2ScheduledHandlerLifecycle(
                repository = repository,
                scheduler = scheduler,
            ),
        )

        val result = loader.load(record)

        assertEquals(PluginV2RuntimeLoadStatus.Failed, result.status)
        assertTrue(result.reason.contains("host-authorized schedule target"))
        assertTrue(repository.created.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun load_keeps_bootstrap_session_alive_until_runtime_is_unloaded() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-bootstrap-lifecycle")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_bootstrap_lifecycle",
        ).withBootstrapRoot(rootDir)
        val bootstrapSession = BootstrappingSession(registrations = 1)
        val loader = newLoader(
            RecordingPluginV2RuntimeLoaderScriptExecutor(
                bootstrapSessions = listOf(bootstrapSession),
            ),
        )

        val loadResult = loader.load(record)

        assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)
        assertFalse(bootstrapSession.disposed)

        loader.unload(record.pluginId)

        assertTrue(bootstrapSession.disposed)
    }

    @Test
    fun runtime_loader_publishes_task4_lifecycle_codes_and_broadcasts_loaded_unloaded_hooks() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-logs")
        createBootstrapFile(rootDir)
        val calls = mutableListOf<String>()
        val recordAlpha = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_logs_alpha",
        ).withBootstrapRoot(rootDir)
        val recordBeta = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_logs_beta",
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
            pluginId = "com.elymbot.samples.loader_conflict_alpha",
        ).withBootstrapRoot(rootDir)
        val beta = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_conflict_beta",
        ).withBootstrapRoot(rootDir)
        val executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
            bootstrapSessions = listOf(
                BootstrappingSession(
                    commandRegistrations = listOf(
                        commandRegistration(
                            registrationKey = "alpha.list",
                            command = "list",
                            aliases = listOf("elymbot plugin ls"),
                            groupPath = listOf("elymbot", "plugin"),
                        ),
                    ),
                ),
                BootstrappingSession(
                    commandRegistrations = listOf(
                        commandRegistration(
                            registrationKey = "beta.install",
                            command = "install",
                            aliases = listOf("elymbot plugin ls"),
                            groupPath = listOf("elymbot", "plugin"),
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
    fun initial_runtime_sync_emits_elymbot_and_platform_loaded_once_through_container_wiring() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-ready")
        createBootstrapFile(rootDir)
        val calls = mutableListOf<String>()
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_ready",
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
                                    hook = PluginLifecycleHookSurface.OnElymBotLoaded,
                                    registrationKey = "ready.elymbot",
                                    handle = PluginV2CallbackHandle { calls += "elymbot" },
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

        assertEquals(listOf("elymbot", "platform"), calls)
    }

    @Test
    fun runtime_loader_agent_run_invokes_registered_plugin_tool_handler() = runTest {
        val rootDir = createTempDir("plugin-v2-loader-agent-tool")
        createBootstrapFile(rootDir)
        val record = samplePluginV2InstallRecord(
            pluginId = "com.elymbot.samples.loader_agent_tool",
        ).withAgentPermission().withBootstrapRoot(rootDir)
        var toolHandlerInvocations = 0
        val llmPort = object : PluginV2HostLlmPort {
            val requests = mutableListOf<PluginV2HostLlmPortRequest>()

            override suspend fun generate(request: PluginV2HostLlmPortRequest): PluginV2HostLlmPortResult {
                requests += request
                return if (requests.size == 1) {
                    PluginV2HostLlmPortResult(
                        text = "",
                        finishReason = "tool_calls",
                        providerId = request.providerId,
                        modelId = request.modelId,
                        toolCalls = listOf(
                            PluginLlmToolCall(
                                toolCallId = "call-lookup",
                                toolName = "lookup",
                                arguments = linkedMapOf("query" to request.messages.single().text),
                            ),
                        ),
                    )
                } else {
                    PluginV2HostLlmPortResult(
                        text = "final",
                        finishReason = "stop",
                        providerId = request.providerId,
                        modelId = request.modelId,
                    )
                }
            }
        }
        val loader = newLoader(
            executor = RecordingPluginV2RuntimeLoaderScriptExecutor(
                bootstrapSessions = listOf(
                    BootstrappingSession(
                        registrations = 0,
                        bootstrapActions = { hostApi ->
                            hostApi.registerTool(
                                descriptor = PluginToolDescriptor(
                                    pluginId = record.pluginId,
                                    name = "lookup",
                                    description = "Lookup from plugin runtime.",
                                    visibility = PluginToolVisibility.LLM_VISIBLE,
                                    sourceKind = PluginToolSourceKind.PLUGIN_V2,
                                    inputSchema = linkedMapOf("type" to "object"),
                                ),
                                handler = PluginV2CallbackHandle {
                                    toolHandlerInvocations++
                                },
                            )
                            hostApi.registerAgent(
                                AgentRegistrationInput(
                                    key = "tool-agent",
                                    systemPrompt = "Use lookup.",
                                    tools = listOf("lookup"),
                                    model = AgentModelSelection(
                                        providerId = "provider-a",
                                        modelId = "model-a-1",
                                    ),
                                    handler = object : PluginV2AgentCallbackHandle {
                                        override fun invoke() = Unit

                                        override suspend fun handleAgent(event: PluginV2AgentInvocationEvent): Any? {
                                            return event.agent.run(event.input)
                                        }
                                    },
                                ),
                            )
                            hostApi.registerCommandHandler(
                                CommandHandlerRegistrationInput(
                                    command = "agenttool",
                                    handler = object : PluginV2EventAwareCallbackHandle {
                                        override fun invoke() = Unit

                                        override suspend fun handleEvent(event: PluginErrorEventPayload) {
                                            val result = hostApi.agentRun(
                                                PluginV2AgentRunHostApiRequest(
                                                    key = "tool-agent",
                                                    input = "agent tool question",
                                                ),
                                            ) as PluginV2HostApiResult.Success
                                            (event as PluginCommandEvent).replyText(
                                                "${(result.value as AgentRunResult).toolCallCount}:$toolHandlerInvocations",
                                            )
                                        }
                                    },
                                ),
                            )
                        },
                    ),
                ),
            ),
            hostLlmPort = llmPort,
        )

        val loadResult = loader.load(record)
        assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)

        val dispatchResult = PluginV2DispatchEngine(
            store = loaderStore(loader),
            clock = { 1L },
        ).dispatchMessage(
            event = PluginMessageEvent(
                eventId = "evt-agent-tool-loader",
                platformAdapterType = "app_chat",
                messageType = com.elymbot.android.model.chat.MessageType.FriendMessage,
                conversationId = "conversation-current",
                senderId = "user-1",
                timestampEpochMillis = 1L,
                rawText = "/agenttool",
                initialWorkingText = "/agenttool",
                rawMentions = emptyList(),
                normalizedMentions = emptyList(),
            ),
        )

        assertEquals("1:1", checkNotNull(dispatchResult.commandResponse).text)
        assertEquals(1, toolHandlerInvocations)
        assertEquals(2, llmPort.requests.size)
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
        scheduledHandlerLifecycle: PluginV2ScheduledHandlerLifecycle? = null,
        messageStreamApi: PluginV2MessageStreamApi? = null,
        hostLlmPort: PluginV2HostLlmPort? = null,
    ): PluginV2RuntimeLoader {
        return PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(scriptExecutor = executor),
            store = store,
            compiler = PluginV2RegistryCompiler(),
            logBus = logBus,
            lifecycleManager = lifecycleManager,
            scheduledHandlerLifecycle = scheduledHandlerLifecycle,
            messageStreamApi = messageStreamApi,
            hostLlmPort = hostLlmPort,
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

    private fun PluginInstallRecord.withSchedulePermission(): PluginInstallRecord {
        if (permissionSnapshot.any { it.permissionId == PluginV2HostApiPermissions.SCHEDULE_MANAGE }) {
            return this
        }
        val schedulePermission = PluginPermissionDeclaration(
            permissionId = PluginV2HostApiPermissions.SCHEDULE_MANAGE,
            title = "Schedule",
            description = "Allows registering plugin schedules",
            riskLevel = PluginRiskLevel.MEDIUM,
            required = true,
        )
        val permissions = permissionSnapshot + schedulePermission
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifestSnapshot.copy(permissions = manifestSnapshot.permissions + schedulePermission),
            source = source,
            packageContractSnapshot = packageContractSnapshot,
            permissionSnapshot = permissions,
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

    private fun PluginInstallRecord.withAgentPermission(): PluginInstallRecord {
        if (permissionSnapshot.any { it.permissionId == PluginV2HostApiPermissions.AGENT_RUN }) {
            return this
        }
        val agentPermission = PluginPermissionDeclaration(
            permissionId = PluginV2HostApiPermissions.AGENT_RUN,
            title = "Agent run",
            description = "Allows running plugin agents",
            riskLevel = PluginRiskLevel.MEDIUM,
            required = true,
        )
        val permissions = permissionSnapshot + agentPermission
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifestSnapshot.copy(permissions = manifestSnapshot.permissions + agentPermission),
            source = source,
            packageContractSnapshot = packageContractSnapshot,
            permissionSnapshot = permissions,
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

    private fun pluginScheduleJob(pluginId: String): CronJob {
        return CronJob(
            jobId = PluginV2ScheduledHandlerLifecycle.scheduleJobId(pluginId, "daily-summary"),
            jobType = PluginV2ScheduledHandlerLifecycle.PLUGIN_V2_SCHEDULE_JOB_TYPE,
            payloadJson = PluginV2SchedulePayload(
                pluginId = pluginId,
                pluginVersion = "1.0.0",
                handlerKey = "daily-summary",
                conversationId = "conversation-1",
                platformAdapterType = "onebot",
                triggerSource = PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE,
            ).toJsonString(),
            enabled = true,
            conversationId = "conversation-1",
            platform = "onebot",
        )
    }

    private fun streamApi(port: PluginV2MessageStreamPort): PluginV2MessageStreamApi {
        return PluginV2MessageStreamApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(
                    logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
                    clock = { 1L },
                ),
                clock = { 1L },
            ),
            streamPort = port,
            clock = { 1L },
        )
    }

    private fun streamContext(pluginId: String): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            requestId = "request-unload-stream",
            conversationId = "conversation-1",
            platformAdapterType = "onebot",
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.MESSAGE_STREAM),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.MESSAGE_STREAM,
                    title = "Stream",
                    granted = true,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.MESSAGE_STREAM),
        )
    }
}

private class RecordingCronRepository(
    initialJobs: List<CronJob> = emptyList(),
) : CronJobRepositoryPort {
    private val state = MutableStateFlow(initialJobs.toMutableList())
    val created = mutableListOf<CronJob>()
    val updated = mutableListOf<CronJob>()
    val deleted = mutableListOf<String>()
    override val jobs: StateFlow<List<CronJob>> = state

    override suspend fun create(job: CronJob): CronJob {
        created += job
        state.value = (state.value + job).toMutableList()
        return job
    }

    override suspend fun update(job: CronJob): CronJob {
        updated += job
        state.value = state.value.map { current -> if (current.jobId == job.jobId) job else current }.toMutableList()
        return job
    }

    override suspend fun delete(jobId: String) {
        deleted += jobId
        state.value = state.value.filterNot { it.jobId == jobId }.toMutableList()
    }

    override suspend fun getByJobId(jobId: String): CronJob? = state.value.firstOrNull { it.jobId == jobId }
    override suspend fun listAll(): List<CronJob> = state.value
    override suspend fun listEnabled(): List<CronJob> = state.value.filter(CronJob::enabled)
    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit
    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord = record
    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord = record
    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> = emptyList()
    override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
}

private class RecordingCronScheduler : CronSchedulerPort {
    val scheduled = mutableListOf<CronJob>()
    val cancelled = mutableListOf<String>()

    override fun schedule(job: CronJob) {
        scheduled += job
    }

    override fun cancel(jobId: String) {
        cancelled += jobId
    }

    override fun cancelAll() = Unit
}

private class RecordingMessageStreamPort : PluginV2MessageStreamPort {
    val opened = mutableListOf<PluginV2MessageStreamPortOpenRequest>()
    val failed = mutableListOf<PluginV2MessageStreamPortFailRequest>()

    override suspend fun open(request: PluginV2MessageStreamPortOpenRequest): PluginV2MessageStreamPortOpenResult {
        opened += request
        return PluginV2MessageStreamPortOpenResult(
            streamId = "stream-1",
            platformMode = PluginV2MessageStreamPlatformMode.Editable,
        )
    }

    override suspend fun append(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult =
        PluginV2MessageStreamPortMutationResult(success = true)

    override suspend fun replace(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult =
        PluginV2MessageStreamPortMutationResult(success = true)

    override suspend fun close(request: PluginV2MessageStreamPortCloseRequest): PluginV2MessageStreamPortMutationResult =
        PluginV2MessageStreamPortMutationResult(success = true)

    override suspend fun fail(request: PluginV2MessageStreamPortFailRequest): PluginV2MessageStreamPortMutationResult {
        failed += request
        return PluginV2MessageStreamPortMutationResult(success = true)
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
    private val scheduledRegistrations: List<ScheduledHandlerRegistrationInput> = emptyList(),
    private val bootstrapActions: (PluginV2BootstrapHostApi) -> Unit = {},
    private val executeFailure: Exception? = null,
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
        executeFailure?.let { throw it }
        val hostApi = globals["__elymbotBootstrapHostApi"] as? PluginV2BootstrapHostApi
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
        scheduledRegistrations.forEach { descriptor ->
            hostApi.registerScheduledHandler(descriptor)
            handleCounter.incrementAndGet()
        }
        bootstrapActions(hostApi)
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

private fun scheduleRegistration(
    conversationId: String = "",
    platformAdapterType: String = "",
): ScheduledHandlerRegistrationInput {
    return ScheduledHandlerRegistrationInput(
        key = "daily-summary",
        cron = "0 9 * * *",
        conversationId = conversationId,
        platformAdapterType = platformAdapterType,
        handler = PluginV2CallbackHandle {},
    )
}
