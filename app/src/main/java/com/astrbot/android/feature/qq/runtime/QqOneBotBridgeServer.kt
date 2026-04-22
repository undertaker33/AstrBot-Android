package com.astrbot.android.feature.qq.runtime

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginRuntimePlugin
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

internal data class OneBotSendResult(
    val success: Boolean,
    val receiptIds: List<String> = emptyList(),
    val errorSummary: String = "",
) {
    companion object {
        fun success(receiptIds: List<String> = emptyList()): OneBotSendResult {
            return OneBotSendResult(
                success = true,
                receiptIds = receiptIds.filter(String::isNotBlank),
            )
        }

        fun failure(errorSummary: String): OneBotSendResult {
            return OneBotSendResult(
                success = false,
                errorSummary = errorSummary,
            )
        }
    }
}

internal data class QqOneBotRuntimeDependencies(
    val botPort: BotRepositoryPort,
    val configPort: ConfigRepositoryPort,
    val personaPort: PersonaRepositoryPort,
    val providerPort: ProviderRepositoryPort,
    val conversationPort: QqConversationPort,
    val platformConfigPort: QqPlatformConfigPort,
    val orchestrator: RuntimeLlmOrchestratorPort,
    val runtimeContextResolverPort: RuntimeContextResolverPort,
    val appChatPluginRuntime: AppChatLlmPipelineRuntime,
    val pluginCatalog: () -> List<PluginRuntimePlugin>,
    val pluginV2DispatchEngine: PluginV2DispatchEngine,
    val failureStateStore: PluginFailureStateStore,
    val scopedFailureStateStore: PluginScopedFailureStateStore,
    val providerInvoker: QqProviderInvoker,
    val gatewayFactory: PluginHostCapabilityGatewayFactory,
    val hostCapabilityGateway: PluginHostCapabilityGateway,
    val hostActionExecutor: ExternalPluginHostActionExecutor,
    val pluginExecutionService: QqPluginExecutionService,
    val llmProviderProbePort: LlmProviderProbePort,
    val logBus: PluginRuntimeLogBus,
    val executeLegacyPluginsDuringLlmDispatch: Boolean = false,
)

internal interface QqScheduledMessageSender {
    fun sendScheduledMessage(
        conversationId: String,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
        botId: String = "",
    ): OneBotSendResult
}

internal interface QqBridgeRuntime : QqScheduledMessageSender {
    fun initialize(context: Context)
    fun start()
}

internal abstract class BaseQqOneBotBridgeRuntime : QqBridgeRuntime {
    private companion object {
        const val PORT = 6199
        const val PATH = "/ws"
        const val AUTH_TOKEN = "astrbot_android_bridge"
        const val MAX_RECENT_MESSAGE_IDS = 512
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val rateLimiter = QqRateLimiter()
    private val stashReplayGuards = ConcurrentHashMap<String, AtomicBoolean>()
    private val recentMessageIds = object : LinkedHashMap<String, Unit>(MAX_RECENT_MESSAGE_IDS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > MAX_RECENT_MESSAGE_IDS
        }
    }

    @Volatile
    private var transport: OneBotReverseWebSocketTransport? = null

    @Volatile
    private var appContext: Context? = null

    protected abstract fun requireRuntimeDependencies(): QqOneBotRuntimeDependencies

    protected open fun currentAppChatPluginRuntime(): AppChatLlmPipelineRuntime {
        return requireRuntimeDependencies().appChatPluginRuntime
    }

    protected open fun currentReplySenderOverride():
        ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)? = null

    override fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    override fun start() {
        if (started.get()) return
        synchronized(this) {
            if (started.get()) return
            if (ensureTransport().start()) {
                started.set(true)
            }
        }
    }

    override fun sendScheduledMessage(
        conversationId: String,
        text: String,
        attachments: List<ConversationAttachment>,
        botId: String,
    ): OneBotSendResult {
        val dependencies = requireRuntimeDependencies()
        return buildRuntimeGraph(dependencies).outboundGateway().sendScheduledMessage(
            conversationId = conversationId,
            text = text,
            attachments = attachments,
            botId = botId,
        )
    }

    internal suspend fun handlePayload(payload: String) {
        val json = runCatching { JSONObject(payload) }
            .getOrElse { error ->
                AppLogger.append(
                    "OneBot payload parse failed: ${error.message ?: error.javaClass.simpleName}",
                )
                return
            }

        if (json.has("retcode") || json.has("status")) {
            val echo = json.opt("echo")?.toString().orEmpty()
            val status = json.optString("status").ifBlank { "unknown" }
            val retcode = json.opt("retcode")?.toString().orEmpty()
            AppLogger.append(
                "OneBot action response: status=$status retcode=${retcode.ifBlank { "-" }} echo=${echo.ifBlank { "-" }}",
            )
            return
        }

        when (val result = buildServerAdapter().handlePayload(payload)) {
            is OneBotServerAdapterResult.Handled -> Unit
            is OneBotServerAdapterResult.Ignored -> AppLogger.append("OneBot payload ignored: ${result.reason}")
            is OneBotServerAdapterResult.Invalid -> AppLogger.append("OneBot payload invalid: ${result.reason}")
        }
    }

    internal fun runtimeGraphForTests(): QqOneBotRuntimeGraph {
        return buildRuntimeGraph(requireRuntimeDependencies())
    }

    private fun buildServerAdapter(): OneBotServerAdapter {
        return buildRuntimeGraph(requireRuntimeDependencies()).adapter()
    }

    private fun buildRuntimeGraph(
        dependencies: QqOneBotRuntimeDependencies,
    ): QqOneBotRuntimeGraph {
        return QqOneBotRuntimeGraph(
            dependencies = dependencies,
            transport = ensureTransport(),
            appChatPluginRuntime = currentAppChatPluginRuntime(),
            replyOverrideProvider = { currentReplySenderOverride() },
            filesDirProvider = { appContext?.filesDir },
            rateLimiter = rateLimiter,
            markMessageId = ::markMessageId,
            scheduleStashReplay = ::scheduleStashReplay,
            currentLanguageTag = ::currentLanguageTag,
            transcribeAudio = dependencies.llmProviderProbePort::transcribeAudio,
            resolvePluginPrivateRootPath = ::resolvePluginPrivateRootPath,
            gatewayFactory = dependencies.gatewayFactory,
            log = AppLogger::append,
        )
    }

    private fun ensureTransport(): OneBotReverseWebSocketTransport {
        transport?.let { return it }
        return synchronized(this) {
            transport ?: OneBotReverseWebSocketTransport(
                port = PORT,
                path = PATH,
                authToken = AUTH_TOKEN,
                onPayload = { payload ->
                    scope.launch {
                        handlePayload(payload)
                    }
                },
                log = AppLogger::append,
            ).also { transport = it }
        }
    }

    private fun scheduleStashReplay(
        bot: BotProfile,
        config: com.astrbot.android.model.ConfigProfile,
        sourceKey: String,
    ) {
        if (config.rateLimitStrategy != "stash" || config.rateLimitWindowSeconds <= 0) return
        val guard = stashReplayGuards.getOrPut(sourceKey) { AtomicBoolean(false) }
        if (!guard.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    delay(config.rateLimitWindowSeconds * 1000L)
                    val replayEvents = rateLimiter.releaseReady(
                        sourceKey = sourceKey,
                        windowSeconds = config.rateLimitWindowSeconds,
                        maxCount = config.rateLimitMaxCount,
                    )
                    if (replayEvents.isEmpty()) {
                        if (rateLimiter.drainReady(sourceKey).isEmpty()) {
                            return@launch
                        }
                        continue
                    }
                    replayEvents.forEach { payload ->
                        val message = payload as? com.astrbot.android.feature.qq.domain.IncomingQqMessage ?: return@forEach
                        buildRuntimeGraph(requireRuntimeDependencies()).runtimeService().handleIncomingMessage(message)
                    }
                    if (rateLimiter.drainReady(sourceKey).isEmpty()) {
                        return@launch
                    }
                }
            } finally {
                guard.set(false)
                if (rateLimiter.drainReady(sourceKey).isNotEmpty()) {
                    scheduleStashReplay(
                        bot = bot,
                        config = requireRuntimeDependencies().configPort.resolve(bot.configProfileId),
                        sourceKey = sourceKey,
                    )
                }
            }
        }
    }

    private fun resolvePluginPrivateRootPath(pluginId: String): String {
        val context = appContext ?: return ""
        return runCatching {
            PluginStoragePaths.fromFilesDir(context.filesDir).privateDir(pluginId).absolutePath
        }.getOrDefault("")
    }

    private fun currentLanguageTag(): String {
        return AppCompatDelegate.getApplicationLocales()[0]
            ?.toLanguageTag()
            .orEmpty()
            .ifBlank { "zh" }
    }

    private fun markMessageId(messageId: String): Boolean {
        synchronized(recentMessageIds) {
            val existed = recentMessageIds.containsKey(messageId)
            recentMessageIds[messageId] = Unit
            return !existed
        }
    }
}

internal object QqOneBotBridgeServer : BaseQqOneBotBridgeRuntime() {
    @Volatile
    private var appChatPluginRuntimeOverrideForTests: AppChatLlmPipelineRuntime? = null

    @Volatile
    private var runtimeDependencies: QqOneBotRuntimeDependencies? = null

    @Volatile
    private var replySenderOverrideForTests: ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)? =
        null

    internal fun setAppChatPluginRuntimeOverrideForTests(runtime: AppChatLlmPipelineRuntime?) {
        appChatPluginRuntimeOverrideForTests = runtime
    }

    internal fun setReplySenderOverrideForTests(
        sender: ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)?,
    ) {
        replySenderOverrideForTests = sender
    }

    internal fun installRuntimeDependencies(dependencies: QqOneBotRuntimeDependencies) {
        runtimeDependencies = dependencies
    }

    internal fun updateRuntimeDependenciesForTests(
        transform: (QqOneBotRuntimeDependencies) -> QqOneBotRuntimeDependencies,
    ) {
        val current = runtimeDependencies
            ?: error("QqOneBotBridgeServer requires installed runtime dependencies before test updates.")
        runtimeDependencies = transform(current)
    }

    override fun requireRuntimeDependencies(): QqOneBotRuntimeDependencies {
        return runtimeDependencies
            ?: error("QqOneBotBridgeServer requires runtime dependencies from the Hilt runtime graph.")
    }

    override fun currentAppChatPluginRuntime(): AppChatLlmPipelineRuntime =
        appChatPluginRuntimeOverrideForTests ?: requireRuntimeDependencies().appChatPluginRuntime

    override fun currentReplySenderOverride():
        ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)? = replySenderOverrideForTests
}

@Singleton
internal class HiltQqOneBotBridgeRuntime @Inject constructor(
    private val runtimeDependencies: QqOneBotRuntimeDependencies,
) : BaseQqOneBotBridgeRuntime() {
    override fun requireRuntimeDependencies(): QqOneBotRuntimeDependencies = runtimeDependencies
}
