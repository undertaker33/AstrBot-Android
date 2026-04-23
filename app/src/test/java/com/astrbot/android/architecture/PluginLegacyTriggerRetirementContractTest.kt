package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginLegacyTriggerRetirementContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun v2_online_mainlines_must_not_use_external_plugin_trigger_policy() {
        val hotspotFiles = listOf(
            "feature/chat/presentation/ChatViewModel.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
            "feature/plugin/runtime/DefaultRuntimeLlmOrchestrator.kt",
            "feature/plugin/runtime/PluginEntryExecutionService.kt",
            "feature/qq/runtime/QqPluginDispatchService.kt",
        )
        val violations = hotspotFiles.filter { relativePath ->
            mainRoot.resolve(relativePath).readText().contains("ExternalPluginTriggerPolicy")
        }

        assertTrue(
            "V2 online behaviors must not stay coupled to ExternalPluginTriggerPolicy: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun legacy_dispatch_residue_must_be_confined_to_plugin_v1_dispatch_adapter() {
        val allowlist = setOf("feature/plugin/runtime/PluginV1DispatchAdapter.kt")
        val tokens = listOf("dispatchLegacy(", "executeLegacyBatch(")
        val violations = buildList {
            kotlinFilesUnder(mainRoot).forEach { file ->
                val relativePath = mainRoot.relativize(file).toString().replace('\\', '/')
                val text = file.readText()
                tokens.forEach { token ->
                    if (text.contains(token) && relativePath !in allowlist) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Legacy dispatch/batch residue must be compressed to the compat-only V1 adapter boundary: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun residual_v1_triggers_must_not_leak_back_into_production_mainline() {
        val allowlistByToken = mapOf(
            "PluginTriggerSource.OnMessageReceived" to setOf(
                "feature/plugin/model/PluginExecutionProtocol.kt",
                "feature/plugin/model/ExternalPluginExecutionPolicy.kt",
            ),
            "PluginTriggerSource.OnConversationEnter" to setOf(
                "feature/plugin/model/PluginExecutionProtocol.kt",
                "feature/plugin/model/ExternalPluginExecutionPolicy.kt",
            ),
            "PluginTriggerSource.OnSchedule" to setOf(
                "feature/plugin/model/PluginExecutionProtocol.kt",
                "feature/plugin/model/ExternalPluginExecutionPolicy.kt",
                "feature/plugin/runtime/PluginRuntimeScheduler.kt",
            ),
        )

        val violations = buildList {
            allowlistByToken.forEach { (token, allowlist) ->
                kotlinFilesUnder(mainRoot).forEach { file ->
                    val relativePath = mainRoot.relativize(file).toString().replace('\\', '/')
                    if (file.readText().contains(token) && relativePath !in allowlist) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Residual V1 triggers must stay retired from production mainline: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
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
