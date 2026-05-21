package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.core.runtime.network.RuntimeNetworkException
import com.elymbot.android.core.runtime.network.RuntimeNetworkFailure
import com.elymbot.android.core.runtime.network.RuntimeNetworkRequest
import com.elymbot.android.core.runtime.network.RuntimeNetworkResponse
import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import com.elymbot.android.core.runtime.network.SseEvent
import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginCompatibilityState
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginManifest
import com.elymbot.android.model.plugin.PluginNetworkAccessPolicySnapshot
import com.elymbot.android.model.plugin.PluginPackageContractSnapshot
import com.elymbot.android.model.plugin.PluginPermissionDeclaration
import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.elymbot.android.model.plugin.PluginSource
import com.elymbot.android.model.plugin.PluginSourceType
import com.whl.quickjs.wrapper.QuickJSContext
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2HostNetworkApiTest {

    @Test
    fun fetch_successful_get_returns_status_headers_and_body() = runTest {
        val transport = RecordingTransport(
            response = RuntimeNetworkResponse(
                statusCode = 200,
                headers = mapOf("content-type" to listOf("text/plain"), "x-host" to listOf("elymbot")),
                bodyBytes = "ok".toByteArray(),
                traceId = "trace-network-get",
                durationMs = 12L,
            ),
        )
        val api = hostNetworkApi(transport, allowedDomains = setOf("api.example.com"))

        val result = api.fetch(
            context = requestContext(),
            request = PluginV2HostNetworkRequest(url = "https://api.example.com/ping"),
        )

        val success = result as PluginV2HostApiResult.Success
        val response = success.value as PluginV2HostNetworkResponse
        assertEquals(200, response.status)
        assertEquals(listOf("text/plain"), response.headers["content-type"])
        assertEquals("ok", response.bodyText)
        assertEquals("text/plain", response.contentType)
        assertEquals(12L, response.elapsedMs)
        assertEquals("GET", transport.requests.single().method)
        assertFalse(transport.requests.single().followRedirects)
    }

    @Test
    fun fetch_post_body_is_passed_to_host_transport() = runTest {
        val transport = RecordingTransport()
        val api = hostNetworkApi(transport, allowedDomains = setOf("api.example.com"))

        val result = api.fetch(
            context = requestContext(),
            request = PluginV2HostNetworkRequest(
                url = "https://api.example.com/submit",
                method = "POST",
                headers = mapOf("content-type" to "application/json"),
                bodyText = """{"hello":"world"}""",
            ),
        )

        assertTrue(result is PluginV2HostApiResult.Success)
        val runtimeRequest = transport.requests.single()
        assertEquals("POST", runtimeRequest.method)
        assertArrayEquals("""{"hello":"world"}""".toByteArray(), runtimeRequest.body)
        assertEquals("application/json", runtimeRequest.contentType)
    }

    @Test
    fun fetch_rejects_non_http_scheme() = runTest {
        val result = hostNetworkApi(RecordingTransport()).fetch(
            context = requestContext(),
            request = PluginV2HostNetworkRequest(url = "file:///etc/passwd"),
        )

        assertFailureCode(result, "network_url_blocked")
    }

    @Test
    fun fetch_rejects_localhost_and_private_networks() = runTest {
        val api = hostNetworkApi(RecordingTransport(), allowedDomains = setOf("localhost", "10.0.0.5"))

        assertFailureCode(
            api.fetch(
                context = requestContext(),
                request = PluginV2HostNetworkRequest(url = "http://localhost:8080/"),
            ),
            "network_url_blocked",
        )
        assertFailureCode(
            api.fetch(
                context = requestContext(),
                request = PluginV2HostNetworkRequest(url = "http://10.0.0.5/"),
            ),
            "network_url_blocked",
        )
    }

    @Test
    fun fetch_rejects_domain_outside_manifest_allowlist() = runTest {
        val result = hostNetworkApi(
            RecordingTransport(),
            allowedDomains = setOf("api.example.com"),
        ).fetch(
            context = requestContext(),
            request = PluginV2HostNetworkRequest(url = "https://other.example.com/ping"),
        )

        assertFailureCode(result, "network_domain_not_allowed")
    }

    @Test
    fun fetch_rejects_response_body_over_one_megabyte() = runTest {
        val result = hostNetworkApi(
            RecordingTransport(
                response = RuntimeNetworkResponse(
                    statusCode = 200,
                    headers = emptyMap(),
                    bodyBytes = ByteArray(PluginV2HostNetworkApi.DEFAULT_RESPONSE_BODY_LIMIT_BYTES + 1),
                    traceId = "trace-too-large",
                    durationMs = 1L,
                ),
            ),
            allowedDomains = setOf("api.example.com"),
        ).fetch(
            context = requestContext(),
            request = PluginV2HostNetworkRequest(url = "https://api.example.com/big"),
        )

        assertFailureCode(result, "network_response_too_large")
    }

    @Test
    fun fetch_maps_runtime_timeout_to_structured_error() = runTest {
        val result = hostNetworkApi(
            RecordingTransport(
                failure = RuntimeNetworkException(
                    RuntimeNetworkFailure.ReadTimeout("https://api.example.com/slow"),
                ),
            ),
            allowedDomains = setOf("api.example.com"),
        ).fetch(
            context = requestContext(),
            request = PluginV2HostNetworkRequest(url = "https://api.example.com/slow"),
        )

        assertFailureCode(result, PluginV2HostApiErrorCodes.TIMEOUT)
    }

    @Test
    fun quickjs_can_await_host_fetch_and_continue_before_dispatch_returns() = runTest {
        assumeTrue(runCatching { QuickJSContext.create().use { } }.isSuccess)
        val workingRoot = Files.createTempDirectory("plugin-v2-host-network").toFile()
        try {
            File(workingRoot, "runtime").mkdirs()
            File(workingRoot, "runtime/bootstrap.js").writeText(
                """
                    export default async function bootstrap(hostApi) {
                      const response = await hostApi.fetch({ url: "https://api.example.com/ping" });
                      const second = await hostApi.network.request({ url: "https://api.example.com/pong" });
                      hostApi.registerCommandHandler({
                        command: "net",
                        handler: (event) => {
                          event.replyText(response.bodyText + ":" + second.status);
                        }
                      });
                    }
                """.trimIndent(),
                Charsets.UTF_8,
            )
            val transport = RecordingTransport(
                response = RuntimeNetworkResponse(
                    statusCode = 200,
                    headers = mapOf("content-type" to listOf("text/plain")),
                    bodyBytes = "network-ok".toByteArray(),
                    traceId = "trace-quickjs-network",
                    durationMs = 3L,
                ),
            )
            val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
            val store = PluginV2ActiveRuntimeStore(logBus = logBus, clock = { 1L })
            val loader = PluginV2RuntimeLoader(
                sessionFactory = PluginV2RuntimeSessionFactory(
                    scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
                ),
                compiler = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L }),
                clock = { 1L },
                logBus = logBus,
                store = store,
                lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
                hostNetworkApi = hostNetworkApi(transport, allowedDomains = setOf("api.example.com"), logBus = logBus),
            )

            val loadResult = loader.load(networkPluginRecord(workingRoot))
            assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)

            val dispatchResult = PluginV2DispatchEngine(
                store = store,
                logBus = logBus,
                clock = { 1L },
            ).dispatchMessage(
                event = PluginMessageEvent(
                    eventId = "evt-network",
                    platformAdapterType = "app_chat",
                    messageType = MessageType.FriendMessage,
                    conversationId = "conversation-1",
                    senderId = "user-1",
                    timestampEpochMillis = 1L,
                    rawText = "/net",
                    initialWorkingText = "/net",
                    rawMentions = emptyList(),
                    normalizedMentions = emptyList(),
                ),
            )

            assertEquals("network-ok:200", checkNotNull(dispatchResult.commandResponse).text)
            assertEquals(2, transport.requests.size)
        } finally {
            workingRoot.deleteRecursively()
        }
    }

    private fun hostNetworkApi(
        transport: RuntimeNetworkTransport,
        allowedDomains: Set<String> = emptySet(),
        logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
    ): PluginV2HostNetworkApi {
        return PluginV2HostNetworkApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(logBus = logBus, clock = { 10L }),
                clock = { 10L },
            ),
            transport = transport,
            domainAllowlistProvider = { context ->
                if (context.pluginId == "plugin.network") allowedDomains else emptySet()
            },
            clock = { 10L },
        )
    }

    private fun requestContext(): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.network",
            pluginVersion = "1.0.0",
            requestId = "request-network",
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
                    title = "Network",
                    granted = true,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
        )
    }

    private fun networkPluginRecord(workingRoot: File): PluginInstallRecord {
        val permissions = listOf(
            PluginPermissionDeclaration(
                permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
                title = "Network",
                description = "Allows outgoing requests.",
                riskLevel = PluginRiskLevel.MEDIUM,
                required = true,
            ),
        )
        val manifest = PluginManifest(
            pluginId = "plugin.network",
            version = "1.0.0",
            protocolVersion = 2,
            author = "ElymBot",
            title = "Network Plugin",
            description = "Network host API test plugin.",
            permissions = permissions,
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "runtime/bootstrap.js",
        )
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = workingRoot.absolutePath,
                importedAt = 1L,
            ),
            packageContractSnapshot = PluginPackageContractSnapshot(
                protocolVersion = 2,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/bootstrap.js",
                    apiVersion = 1,
                ),
                network = PluginNetworkAccessPolicySnapshot(
                    allowedDomains = listOf("api.example.com"),
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
            localPackagePath = File(workingRoot, "plugin.zip").absolutePath,
            extractedDir = workingRoot.absolutePath,
        )
    }

    private fun assertFailureCode(
        result: PluginV2HostApiResult,
        code: String,
    ) {
        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(code, failure.error.code)
    }

    private class RecordingTransport(
        private val response: RuntimeNetworkResponse = RuntimeNetworkResponse(
            statusCode = 204,
            headers = emptyMap(),
            bodyBytes = ByteArray(0),
            traceId = "trace-default",
            durationMs = 1L,
        ),
        private val failure: RuntimeNetworkException? = null,
    ) : RuntimeNetworkTransport {
        val requests = mutableListOf<RuntimeNetworkRequest>()

        override suspend fun execute(request: RuntimeNetworkRequest): RuntimeNetworkResponse {
            requests += request
            failure?.let { throw it }
            return response
        }

        override fun openStream(request: RuntimeNetworkRequest): Flow<String> = emptyFlow()

        override fun openSse(request: RuntimeNetworkRequest): Flow<SseEvent> = emptyFlow()
    }
}
