package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFirstBoundaryContractTest {
    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun feature_first_anchor_directories_exist() {
        val required = listOf(
            "core/common",
            "core/db",
            "core/di",
            "core/network",
            "core/runtime",
            "feature/chat/presentation",
            "feature/chat/domain",
            "feature/chat/data",
            "feature/chat/runtime",
            "feature/qq/presentation",
            "feature/qq/domain",
            "feature/qq/data",
            "feature/qq/runtime",
            "feature/plugin/presentation",
            "feature/plugin/domain",
            "feature/plugin/data",
            "feature/plugin/runtime",
            "feature/resource/presentation",
            "feature/resource/domain",
            "feature/resource/data",
            "feature/resource/runtime",
            "feature/cron/presentation",
            "feature/cron/domain",
            "feature/cron/data",
            "feature/cron/runtime",
            "feature/provider/presentation",
            "feature/provider/domain",
            "feature/provider/data",
            "feature/provider/runtime",
            "feature/config/presentation",
            "feature/config/domain",
            "feature/config/data",
            "feature/config/runtime",
            "feature/bot/presentation",
            "feature/bot/domain",
            "feature/bot/data",
            "feature/bot/runtime",
            "feature/persona/presentation",
            "feature/persona/domain",
            "feature/persona/data",
            "feature/persona/runtime",
        )

        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing architecture anchors: $missing", missing.isEmpty())
    }

    @Test
    fun migrated_presentation_does_not_import_legacy_data_or_runtime() {
        val violations = kotlinFilesUnder("feature")
            .filter { it.toString().replace('\\', '/').contains("/presentation/") }
            .flatMap { file ->
                val text = file.readText()
                forbiddenPresentationImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "${mainRoot.relativize(file)} imports $forbidden" else null
                }
            }

        assertTrue("Presentation boundary violations: $violations", violations.isEmpty())
    }

    @Test
    fun migrated_domain_does_not_import_android_ui_or_legacy_singletons() {
        val violations = kotlinFilesUnder("feature")
            .filter { it.toString().replace('\\', '/').contains("/domain/") }
            .flatMap { file ->
                val text = file.readText()
                forbiddenDomainImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "${mainRoot.relativize(file)} imports $forbidden" else null
                }
            }

        assertTrue("Domain boundary violations: $violations", violations.isEmpty())
    }

    @Test
    fun app_chat_presentation_exposes_real_send_handler_bound_to_domain_use_case() {
        val handlerFile = mainRoot.resolve("feature/chat/presentation/AppChatSendHandler.kt")
        assertTrue("AppChatSendHandler.kt must exist", handlerFile.exists())
        val text = handlerFile.readText()
        assertTrue(
            "AppChatSendHandler must delegate to SendAppMessageUseCase",
            text.contains("SendAppMessageUseCase"),
        )
        assertTrue(
            "AppChatSendHandler must not depend on legacy data singletons",
            !text.contains("import com.astrbot.android.data."),
        )
        assertTrue(
            "AppChatSendHandler must not depend on runtime/plugin objects",
            !text.contains("import com.astrbot.android.runtime."),
        )
    }

    @Test
    fun chat_view_model_send_path_delegates_to_feature_chat_without_direct_runtime_send() {
        val viewModelFile = mainRoot.resolve("feature/chat/presentation/ChatViewModel.kt")
        assertTrue("ChatViewModel.kt must exist", viewModelFile.exists())
        val text = viewModelFile.readText()
        assertTrue(
            "ChatViewModel must delegate app chat sending through presentation handler",
            text.contains("AppChatSendHandler"),
        )
        assertTrue(
            "ChatViewModel must not keep deliverViaRuntimePort after use case migration",
            !text.contains("deliverViaRuntimePort"),
        )
        assertTrue(
            "ChatViewModel must not directly invoke AppChatRuntimePort.send",
            !text.contains(".send(\n            AppChatRequest(") &&
                !text.contains("localRuntimePort.send"),
        )
        assertTrue(
            "ChatViewModel must not instantiate AppChatRuntimeService directly after phase 3 wiring migration",
            !Regex("""\bAppChatRuntimeService\(""").containsMatchIn(text),
        )
        assertTrue(
            "ChatViewModel must not instantiate SendAppMessageUseCase directly after phase 3 wiring migration",
            !Regex("""\bSendAppMessageUseCase\(""").containsMatchIn(text),
        )
        assertTrue(
            "ChatViewModel must not instantiate AppChatSendHandler directly after phase 3 wiring migration",
            !Regex("""\bAppChatSendHandler\(""").containsMatchIn(text),
        )
        assertTrue(
            "ChatViewModel must route session operations through ChatSessionController after phase 3 wiring migration",
            text.contains("ChatSessionController"),
        )
        assertTrue(
            "ChatViewModel must not import PluginV2DispatchEngineProvider directly after phase 3 plugin extraction",
            !text.contains("import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngineProvider"),
        )
        assertTrue(
            "ChatViewModel must not import ExternalPluginMediaSourceResolver directly after phase 3 plugin extraction",
            !text.contains("import com.astrbot.android.model.plugin.ExternalPluginMediaSourceResolver"),
        )
        assertTrue(
            "ChatViewModel must not import ExternalPluginHostActionExecutor directly after phase 3 plugin extraction",
            !text.contains("import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor"),
        )
    }

    @Test
    fun feature_qq_domain_must_not_import_legacy_data_or_chat_completion_service() {
        val violations = kotlinFilesUnder("feature/qq/domain")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                listOf(
                    "import com.astrbot.android.data.",
                    "import com.astrbot.android.feature.plugin.runtime.",
                    "ChatCompletionService",
                ).mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        assertTrue(
            "feature/qq/domain boundary violations (phase 5): $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_qq_runtime_must_not_import_legacy_repository_objects() {
        val forbiddenLegacyImports = listOf(
            "import com.astrbot.android.data.BotRepository",
            "import com.astrbot.android.data.ConfigRepository",
            "import com.astrbot.android.data.ProviderRepository",
            "import com.astrbot.android.data.PersonaRepository",
            "import com.astrbot.android.data.ConversationRepository",
            "import com.astrbot.android.data.PluginRepository",
        )
        val violations = kotlinFilesUnder("feature/qq/runtime")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenLegacyImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        assertTrue(
            "feature/qq/runtime must not import legacy repository objects (phase 5): $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_qq_runtime_must_not_call_chat_completion_service_directly() {
        val violations = kotlinFilesUnder("feature/qq/runtime")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                listOf(
                    "sendConfiguredChatWithTools",
                    "sendConfiguredChatStreamWithTools",
                ).mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative calls $forbidden" else null
                }
            }
        assertTrue(
            "feature/qq/runtime must not call ChatCompletionService directly (phase 5): $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun one_bot_bridge_server_uses_phase5_feature_qq_adapter_path() {
        val source = mainRoot.resolve("feature/qq/runtime/QqOneBotBridgeServer.kt").readText()
        assertTrue(
            "QqOneBotBridgeServer must delegate QQ ingress to OneBotServerAdapter during phase 5",
            source.contains("OneBotServerAdapter"),
        )
        assertTrue(
            "QqOneBotBridgeServer should not keep the old QQ processMessageEvent chain after phase 5 migration",
            !source.contains("private suspend fun processMessageEvent("),
        )
        assertTrue(
            "QqOneBotBridgeServer should not keep invokeProviderForPipeline after phase 5 migration",
            !source.contains("private suspend fun invokeProviderForPipeline("),
        )
    }

    @Test
    fun feature_plugin_domain_must_not_import_legacy_data_or_runtime() {
        val violations = kotlinFilesUnder("feature/plugin/domain")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenDomainImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        assertTrue(
            "feature/plugin/domain must not import legacy data/runtime (phase 6): $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_view_model_routes_management_actions_through_plugin_presentation_controller() {
        val file = mainRoot.resolve("feature/plugin/presentation/PluginViewModel.kt")
        assertTrue("PluginViewModel.kt must exist", file.exists())
        val text = file.readText()
        val enableBody = functionBody(text, "updateSelectedPluginEnabled")
        val recoverBody = functionBody(text, "recoverSelectedPluginFromSuspension")
        assertTrue(
            "PluginViewModel must wire PluginPresentationController for phase 6 management paths",
            text.contains("PluginPresentationController"),
        )
        assertTrue(
            "PluginViewModel enable/disable path must delegate through PluginPresentationController",
            enableBody.contains("pluginPresentationController.enablePlugin(") ||
                enableBody.contains("pluginPresentationController.disablePlugin("),
        )
        assertTrue(
            "PluginViewModel recover path must delegate through PluginPresentationController",
            recoverBody.contains("pluginPresentationController.recoverPlugin("),
        )
        assertTrue(
            "PluginViewModel enable/disable path must not call dependencies.setPluginEnabled directly",
            !enableBody.contains("dependencies.setPluginEnabled("),
        )
        assertTrue(
            "PluginViewModel recover path must not call dependencies.recoverPluginFailureState directly",
            !recoverBody.contains("dependencies.recoverPluginFailureState("),
        )
    }

    @Test
    fun feature_cron_domain_must_not_import_legacy_repository_objects() {
        val forbiddenLegacyImports = listOf(
            "import com.astrbot.android.data.CronJobRepository",
            "import com.astrbot.android.data.BotRepository",
            "import com.astrbot.android.data.ConfigRepository",
        )
        val violations = kotlinFilesUnder("feature/cron/domain")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenLegacyImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        assertTrue(
            "feature/cron/domain must not import legacy repository objects (phase 6): $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun cron_jobs_view_model_routes_create_update_delete_schedule_through_controller() {
        val file = mainRoot.resolve("feature/cron/presentation/CronJobsViewModel.kt")
        assertTrue("CronJobsViewModel.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "CronJobsViewModel must wire CronJobsPresentationController",
            text.contains("CronJobsPresentationController"),
        )
        val forbiddenTokens = listOf(
            "import com.astrbot.android.data.CronJobRepository",
            "import com.astrbot.android.feature.cron.runtime.CronJobScheduler",
            "ActiveCapabilityRuntimeFacade",
            "createFacade(",
        ).filter(text::contains)
        assertTrue(
            "CronJobsViewModel must not keep direct cron runtime orchestration after phase 6 wiring: $forbiddenTokens",
            forbiddenTokens.isEmpty(),
        )
    }

    @Test
    fun feature_resource_domain_must_not_import_legacy_data_or_android() {
        val violations = kotlinFilesUnder("feature/resource/domain")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenDomainImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        assertTrue(
            "feature/resource/domain must not import legacy data/runtime (phase 6): $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun resource_center_ui_routes_presentation_through_feature_controller() {
        val screenFile = mainRoot.resolve("feature/resource/presentation/ResourceCenterScreen.kt")
        val presentationFile = mainRoot.resolve("feature/resource/presentation/ResourceCenterPresentation.kt")
        assertTrue("ResourceCenterScreen.kt must exist", screenFile.exists())
        assertTrue("ResourceCenterPresentation.kt must exist", presentationFile.exists())
        val screenText = screenFile.readText()
        val presentationText = presentationFile.readText()
        assertTrue(
            "ResourceCenter screen/presentation must wire ResourceCenterPresentationController",
            screenText.contains("ResourceCenterPresentationController") ||
                presentationText.contains("ResourceCenterPresentationController"),
        )
        assertTrue(
            "ResourceCenter presentation must delegate compatibility snapshots through the controller path",
            presentationText.contains("compatibilitySnapshotForConfig"),
        )
    }

    @Test
    fun core_runtime_tool_must_not_import_feature_or_legacy() {
        val forbiddenImports = listOf(
            "import com.astrbot.android.feature.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.ui.",
        )
        val violations = kotlinFilesUnder("core/runtime/tool")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        assertTrue(
            "core/runtime/tool must not import feature or legacy packages: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun core_sources_must_not_import_feature_packages_or_legacy_repository_aliases() {
        val legacyRepositoryAliases = listOf(
            "as BotRepository",
            "as ConfigRepository",
            "as PersonaRepository",
            "as ProviderRepository",
            "as ConversationRepository",
            "as CronJobRepository",
            "as PluginRepository",
            "as ResourceCenterRepository",
            "as NapCatBridgeRepository",
        )
        val violations = kotlinFilesUnder("core")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                buildList {
                    text.lineSequence()
                        .filter { it.trim().startsWith("import com.astrbot.android.feature.") }
                        .forEach { line -> add("$relative imports ${line.trim()}") }
                    legacyRepositoryAliases
                        .filter(text::contains)
                        .forEach { alias -> add("$relative uses legacy root repository alias $alias") }
                }
            }

        assertTrue(
            "core/** must not depend on feature packages or old root repository aliases: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun core_runtime_llm_must_not_expose_feature_plugin_contracts() {
        val violations = kotlinFilesUnder("core/runtime/llm")
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                listOf("com.astrbot.android.feature.plugin.runtime").mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative references $forbidden" else null
                }
            }

        assertTrue(
            "core/runtime/llm contracts must stay core-owned and must not expose feature plugin types: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_v1_runtime_boundary_is_frozen_behind_explicit_legacy_adapter() {
        val adapterFile = mainRoot.resolve("feature/plugin/runtime/PluginV1LegacyAdapter.kt")
        val facadeFile = mainRoot.resolve("feature/plugin/runtime/PluginRuntimeFacade.kt")
        assertTrue("PluginV1LegacyAdapter.kt must exist for the phase 6 V1 freeze boundary", adapterFile.exists())
        assertTrue("PluginRuntimeFacade.kt must exist", facadeFile.exists())

        val adapterText = adapterFile.readText()
        val facadeText = facadeFile.readText()

        assertTrue(
            "PluginV1LegacyAdapter must document the V1 legacy/frozen boundary explicitly",
            adapterText.contains("legacy") && adapterText.contains("freeze"),
        )
        assertTrue(
            "PluginRuntimeFacade must depend on PluginV1LegacyAdapter instead of keeping V1 logic implicit",
            facadeText.contains("PluginV1LegacyAdapter"),
        )
    }

    @Test
    fun future_tool_source_registry_is_compatibility_shell_over_core_runtime_contract() {
        val contractsFile = mainRoot.resolve("feature/plugin/runtime/toolsource/FutureToolSourceContracts.kt")
        val registryFile = mainRoot.resolve("feature/plugin/runtime/toolsource/FutureToolSourceRegistry.kt")
        assertTrue("FutureToolSourceContracts.kt must exist", contractsFile.exists())
        assertTrue("FutureToolSourceRegistry.kt must exist", registryFile.exists())

        val contractsText = contractsFile.readText()
        val registryText = registryFile.readText()
        val collectBody = functionBody(registryText, "collectToolDescriptors")

        assertTrue(
            "Future toolsource contracts must bridge through core/runtime/tool ToolSourceProviderPort",
            contractsText.contains("ToolSourceProviderPort") && contractsText.contains("ToolDescriptor"),
        )
        assertTrue(
            "FutureToolSourceRegistry must import the core runtime tool contract for phase 6 ownership",
            registryText.contains("import com.astrbot.android.core.runtime.tool."),
        )
        assertTrue(
            "FutureToolSourceRegistry must expose a contract-first collection path",
            registryText.contains("fun collectContractDescriptors("),
        )
        assertTrue(
            "Legacy collectToolDescriptors path must be a compatibility shell over collectContractDescriptors",
            collectBody.contains("collectContractDescriptors("),
        )
    }

    private fun kotlinFilesUnder(relative: String): List<Path> {
        val root = mainRoot.resolve(relative)
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun functionBody(source: String, functionName: String): String {
        val start = source.indexOf("fun $functionName(")
        if (start < 0) return ""
        val braceStart = source.indexOf('{', start)
        if (braceStart < 0) return ""
        var depth = 0
        for (index in braceStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(braceStart, index + 1)
                    }
                }
            }
        }
        return source.substring(braceStart)
    }

    private companion object {
        val forbiddenPresentationImports = listOf(
            "import com.astrbot.android.data.",
            "import com.astrbot.android.runtime.",
        )

        val forbiddenDomainImports = listOf(
            "import android.",
            "import androidx.compose.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.runtime.",
        )
    }
}
