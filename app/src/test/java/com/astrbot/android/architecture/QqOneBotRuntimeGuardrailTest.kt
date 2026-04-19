package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class QqOneBotRuntimeGuardrailTest {
    private val projectRoot: Path = generateSequence(Path.of("").toAbsolutePath()) { current ->
        current.parent
    }.firstOrNull { candidate ->
        Files.exists(candidate.resolve("app/src/main/java/com/astrbot/android"))
    } ?: error("Could not locate project root from ${Path.of("").toAbsolutePath()}")
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val testRoot: Path = projectRoot.resolve("app/src/test/java/com/astrbot/android")

    @Test
    fun qq_onebot_bridge_server_must_stay_thin_and_glue_only() {
        val source = mainRoot.resolve("feature/qq/runtime/QqOneBotBridgeServer.kt").readText()
        val lineCount = Files.readAllLines(
            mainRoot.resolve("feature/qq/runtime/QqOneBotBridgeServer.kt"),
        ).size
        val forbiddenTokens = listOf(
            "legacyProcessMessageEventDoNotUse",
            "executeQqPlugins",
            "executeLegacyQqPlugins",
            "dispatchQqV2MessageIngress",
            "handlePluginCommand",
            "consumeQqPluginOutcome",
            "handleBotCommand",
            "sendReplyWithOutcome",
            "sendPseudoStreamingReply",
            "sendStreamingVoiceReply",
            "ConversationSessionLockManager",
            "class OneBotWebSocketServer",
            "class OneBotWebSocket",
        )

        assertTrue(
            "QqOneBotBridgeServer must stay <= 500 lines after task 9 phase 2, actual=$lineCount",
            lineCount <= 500,
        )
        assertTrue(
            "QqOneBotBridgeServer must stay glue-only without legacy business functions",
            forbiddenTokens.none(source::contains),
        )
    }

    @Test
    fun qq_message_runtime_service_must_delegate_high_complexity_roles() {
        val source = mainRoot.resolve("feature/qq/runtime/QqMessageRuntimeService.kt").readText()
        val requiredCollaborators = listOf(
            "QqPluginDispatchService",
            "QqStreamingReplyService",
            "QqBotCommandRuntimeService",
            "QqRuntimeProfileResolver",
        )
        val forbiddenLocalFunctions = listOf(
            "private fun handlePluginCommand(",
            "private fun executeLegacyQqPlugins(",
            "private fun dispatchQqV2MessageIngress(",
            "private fun consumeQqPluginOutcome(",
            "private fun handleBotCommand(",
            "private fun resolveProvider(",
            "private fun resolveSttProvider(",
            "private fun resolveTtsProvider(",
            "private fun resolvePersona(",
        )

        assertTrue(
            "QqMessageRuntimeService must depend on dedicated QQ runtime sub-services",
            requiredCollaborators.all(source::contains),
        )
        assertTrue(
            "QqMessageRuntimeService must not keep plugin/command/profile heavy methods inline",
            forbiddenLocalFunctions.none(source::contains),
        )
    }

    @Test
    fun qq_runtime_tests_must_not_reflect_bridge_private_methods() {
        val bridgeTestSource = testRoot.resolve("runtime/OneBotBridgeServerTest.kt").readText()
        val pluginIngressSource = testRoot.resolve("runtime/plugin/PluginV2HostIngressTest.kt").readText()
        val reflectionToken = "QqOneBotBridgeServer::class.java.getDeclaredMethod"

        assertTrue(
            "QqOneBot runtime tests must not reflect QqOneBotBridgeServer private methods",
            !bridgeTestSource.contains(reflectionToken) && !pluginIngressSource.contains(reflectionToken),
        )
    }
}
