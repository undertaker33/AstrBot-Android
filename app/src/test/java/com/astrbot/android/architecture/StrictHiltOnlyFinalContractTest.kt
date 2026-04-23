package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictHiltOnlyFinalContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun startup_and_runtime_hotspots_must_not_retain_out_of_graph_bypass_tokens() {
        val forbiddenTokensByFile = mapOf(
            "di/startup/RepositoryInitializationStartupChain.kt" to listOf(
                "InitializationCoordinator(",
                "ConfigRepositoryInitializer()",
                "BotRepositoryInitializer()",
                "PersonaRepositoryInitializer()",
                ".initialize(application)",
                "providerRepositoryWarmup.warmUp()",
            ),
            "feature/bot/data/BotRepositoryInitializer.kt" to listOf("FeatureBotRepository.initialize("),
            "feature/config/data/ConfigRepositoryInitializer.kt" to listOf("FeatureConfigRepository.initialize("),
            "feature/persona/data/PersonaRepositoryInitializer.kt" to listOf("FeaturePersonaRepository.initialize("),
            "feature/provider/data/ProviderRepositoryWarmup.kt" to listOf(
                "@ApplicationContext",
                "FeatureProviderRepository.initialize(",
            ),
            "di/hilt/DatabaseModule.kt" to listOf("AstrBotDatabase.get("),
            "core/runtime/container/ContainerRuntimeEntryPoint.kt" to listOf(
                "EntryPointAccessors.fromApplication(",
                "EntryPoints.get(",
            ),
            "feature/qq/runtime/QqOneBotBridgeServer.kt" to listOf(
                "installRuntimeDependencies(",
                "installRuntimeDependencies =",
                "RuntimeDependenciesTestInstaller",
                "updateRuntimeDependenciesForTests(",
            ),
            "data/http/AstrBotHttpClient.kt" to listOf(
                "SharedRuntimeNetworkTransport.get(",
                "RuntimeNetworkTransportRegistry",
            ),
            "feature/plugin/runtime/toolsource/WebSearchToolSourceProvider.kt" to listOf(
                "SharedRuntimeNetworkTransport.get(",
                "RuntimeNetworkTransportRegistry",
                "resolveProductionRuntimeNetworkTransport()",
            ),
            "di/hilt/RuntimeNetworkModule.kt" to listOf(
                "SharedRuntimeNetworkTransport.get(",
                "RuntimeNetworkTransportRegistry",
                "installFromHilt(",
            ),
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt" to listOf(
                "QqPluginDispatchService(",
                "QqMessageRuntimeService(",
                "QqBotCommandRuntimeService(",
                "QqStreamingReplyService(",
            ),
        )

        val violations = buildList {
            forbiddenTokensByFile.forEach { (relativePath, forbiddenTokens) ->
                val file = mainRoot.resolve(relativePath)
                assertTrue("Expected strict-Hilt hotspot to exist: ${file.toAbsolutePath()}", file.exists())
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Strict Hilt-only final hotspots must not retain manual bypass tokens: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun closed_mainlines_must_not_reference_legacy_adapters_or_transition_helpers() {
        val hotspotFiles = listOf(
            "feature/chat/presentation/ChatViewModel.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
            "feature/plugin/presentation/PluginViewModel.kt",
            "feature/plugin/runtime/PluginRuntimeFacade.kt",
            "feature/qq/runtime/QqOneBotBridgeServer.kt",
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt",
            "feature/qq/runtime/QqPluginDispatchService.kt",
        )
        val forbiddenTokens = listOf(
            "LegacyBotRepositoryAdapter",
            "LegacyConfigRepositoryAdapter",
            "LegacyPersonaRepositoryAdapter",
            "LegacyProviderRepositoryAdapter",
            "LegacyConversationRepositoryAdapter",
            "LegacyQqConversationAdapter",
            "LegacyQqPlatformConfigAdapter",
            "LegacyCronJobRepositoryAdapter",
            "LegacyCronSchedulerAdapter",
            "LegacyResourceCenterRepositoryAdapter",
            "LegacyChatCompletionServiceAdapter",
            "LegacyLlmProviderProbeAdapter",
            "LegacyRuntimeOrchestratorAdapter",
            "PluginRuntimeDependencyBridge.",
            "createCompatPluginHostCapabilityGatewayFactory(",
            "createCompatPluginHostCapabilityGateway(",
        )

        val violations = buildList {
            hotspotFiles.forEach { relativePath ->
                val file = mainRoot.resolve(relativePath)
                assertTrue("Expected closed mainline to exist: ${file.toAbsolutePath()}", file.exists())
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Closed mainlines must remain free of legacy adapters and transition helpers: $violations",
            violations.isEmpty(),
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
