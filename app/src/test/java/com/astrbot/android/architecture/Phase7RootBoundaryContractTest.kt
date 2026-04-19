package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-7 root-boundary contracts.
 *
 * Phase 7 is treated as complete-state verification rather than a snapshot cap:
 * root `ui`, `model`, `data`, and `runtime` trees may only keep a tiny number
 * of explicit shells or infrastructure seams. Feature-scoped presentation,
 * models, repositories, and runtime orchestration must already live behind
 * feature/core ownership, so these tests intentionally fail while that work is
 * still incomplete.
 */
class Phase7RootBoundaryContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun feature_code_must_not_reference_root_data_repository_objects_outside_explicit_compat_files() {
        val violations = findScopedTokenViolations(
            relativeRoot = "feature",
            allowedTokenScopes = allowedFeatureRootRepositoryScopes,
            forbiddenTokens = rootRepositoryTokens,
        )

        assertTrue(
            "Feature code still references root repository singletons outside explicit compat files: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun scheduled_task_runtime_executor_must_not_reference_feature_repository_singletons() {
        val source = readSource("feature/cron/runtime/ScheduledTaskRuntimeExecutor.kt")
        val forbiddenTokens = listOf(
            "FeatureBotRepository",
            "FeatureConversationRepository",
        )

        assertTrue(
            "ScheduledTaskRuntimeExecutor must consume bot/conversation state through ports instead of feature repositories",
            referencesNoneOf(source, forbiddenTokens),
        )
    }

    @Test
    fun qq_bridge_server_must_not_reference_feature_repository_singletons() {
        val source = readSource("feature/qq/runtime/QqOneBotBridgeServer.kt")
        val forbiddenTokens = listOf(
            "FeatureBotRepository",
            "FeatureConfigRepository",
            "FeatureConversationRepository",
            "FeaturePersonaRepository",
            "FeaturePluginRepository",
            "FeatureProviderRepository",
        )

        assertTrue(
            "QqOneBotBridgeServer must route repository access through runtime dependency ports instead of feature repositories",
            referencesNoneOf(source, forbiddenTokens),
        )
    }

    @Test
    fun feature_compat_files_must_not_absorb_extra_root_repository_dependencies() {
        val violations = findScopedTokenViolations(
            relativeRoot = "feature",
            allowedTokenScopes = allowedFeatureRootRepositoryScopes,
            forbiddenTokens = rootRepositoryTokens,
            onlyAllowedFiles = true,
        )

        assertTrue(
            "Legacy adapters/initializers absorbed extra root repository dependencies: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_compat_files_must_not_absorb_root_runtime_business_dependencies() {
        val violations = findScopedTokenViolations(
            relativeRoot = "feature",
            allowedTokenScopes = allowedFeatureRootRepositoryScopes,
            forbiddenTokens = rootRuntimeTokens,
            onlyAllowedFiles = true,
        )

        assertTrue(
            "Legacy adapters/initializers must stay repository-only seams, not root runtime bridges: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_code_must_not_reference_root_runtime_business_objects() {
        val violations = findScopedTokenViolations(
            relativeRoot = "feature",
            allowedTokenScopes = emptyMap(),
            forbiddenTokens = rootRuntimeTokens,
        )

        assertTrue(
            "Feature code still references root runtime business objects instead of feature/core ports: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun chat_completion_service_must_not_be_referenced_from_feature_code() {
        val violations = findScopedTokenViolations(
            relativeRoot = "feature",
            allowedTokenScopes = emptyMap(),
            forbiddenTokens = listOf("ChatCompletionService"),
        )

        assertTrue(
            "Feature code must not keep root ChatCompletionService ownership after phase 7: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun streaming_response_segmenter_must_not_be_referenced_from_feature_code() {
        val violations = findScopedTokenViolations(
            relativeRoot = "feature",
            allowedTokenScopes = emptyMap(),
            forbiddenTokens = listOf("StreamingResponseSegmenter"),
        )

        assertTrue(
            "Feature code must not keep root StreamingResponseSegmenter ownership after phase 7: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun one_bot_payload_codec_must_not_be_referenced_from_feature_code() {
        val violations = findScopedTokenViolations(
            relativeRoot = "feature",
            allowedTokenScopes = emptyMap(),
            forbiddenTokens = listOf("OneBotPayloadCodec"),
        )

        assertTrue(
            "Feature code must not keep root OneBotPayloadCodec ownership after phase 7: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun root_ui_tree_must_only_keep_app_shell_and_navigation_support_paths() {
        val violations = unexpectedKotlinPaths(
            relativeRoot = "ui",
            allowedExactPaths = allowedRootUiExactPaths,
            allowedPathPrefixes = allowedRootUiPathPrefixes,
        )

        assertTrue(
            "Root ui/ still contains feature-owned presentation paths after phase 7: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun bridge_view_model_must_remain_dependency_driven_bridge_shell() {
        val source = readSource("ui/viewmodel/BridgeViewModel.kt")
        val imports = importsOf(source)

        assertTrue(
            "BridgeViewModel must stay a DI-owned bridge shell with root-owned bridge models only",
            packageNameOf(source) == "com.astrbot.android.ui.viewmodel" &&
                declaresClass(source, "BridgeViewModel") &&
                source.contains(": ViewModel()") &&
                imports.contains("androidx.lifecycle.ViewModel") &&
                imports.contains("com.astrbot.android.di.BridgeViewModelDependencies") &&
                imports.any { it.startsWith("com.astrbot.android.model.NapCatBridgeConfig") } &&
                imports.any { it.startsWith("com.astrbot.android.model.NapCatRuntimeState") } &&
                declaresFunction(source, "saveConfig") &&
                source.contains("dependencies"),
        )
        assertTrue(
            "BridgeViewModel must not reach feature, root data, or root runtime implementations directly",
            importsNoneOf(source, disallowedImportPrefixesForShells) &&
                referencesNoneOf(
                    source,
                    rootRepositoryTokens + rootRuntimeTokens + listOf("ContainerBridgeController", "astrBotViewModel"),
                ),
        )
    }

    @Test
    fun runtime_asset_view_model_must_remain_dependency_driven_runtime_shell() {
        val source = readSource("ui/viewmodel/RuntimeAssetViewModel.kt")
        val imports = importsOf(source)

        assertTrue(
            "RuntimeAssetViewModel must stay a dependency-driven runtime asset shell",
            packageNameOf(source) == "com.astrbot.android.ui.viewmodel" &&
                declaresClass(source, "RuntimeAssetViewModel") &&
                source.contains(": ViewModel()") &&
                imports.contains("androidx.lifecycle.ViewModel") &&
                imports.contains("com.astrbot.android.di.RuntimeAssetViewModelDependencies") &&
                imports.any { it.startsWith("com.astrbot.android.model.RuntimeAssetState") } &&
                declaresFunction(source, "refresh") &&
                declaresFunction(source, "downloadAsset") &&
                declaresFunction(source, "clearAsset") &&
                declaresFunction(source, "downloadOnDeviceTtsModel") &&
                declaresFunction(source, "clearOnDeviceTtsModel"),
        )
        assertTrue(
            "RuntimeAssetViewModel must not reach root data/runtime/feature implementations directly",
            importsNoneOf(source, disallowedImportPrefixesForShells) &&
                referencesNoneOf(
                    source,
                    rootRepositoryTokens + rootRuntimeTokens + listOf("ContainerBridgeController", "astrBotViewModel"),
                ),
        )
    }

    @Test
    fun settings_hub_screen_must_remain_navigation_and_app_preferences_shell() {
        val source = readSource("ui/settings/SettingsHubScreen.kt")
        val imports = importsOf(source)

        assertTrue(
            "SettingsHubScreen must remain a settings shell that wires navigation and app-level preference/cache seams",
            packageNameOf(source) == "com.astrbot.android.ui.settings" &&
                declaresFunction(source, "SettingsHubScreen") &&
                source.contains("onBack: () -> Unit") &&
                source.contains("onOpenRuntime: () -> Unit") &&
                dataImports(source).all { it in allowedSettingsHubDataImports } &&
                imports.contains("com.astrbot.android.data.AppPreferencesRepository") &&
                imports.contains("com.astrbot.android.data.RuntimeCacheRepository"),
        )
        assertTrue(
            "SettingsHubScreen must not pull feature code, view models, or runtime controllers into the shell",
            importsNoneOf(source, forbiddenSettingsHubImportPrefixes) &&
                referencesNoneOf(
                    source,
                    rootRepositoryTokens +
                        rootRuntimeTokens +
                        listOf("BridgeViewModel", "RuntimeAssetViewModel", "astrBotViewModel", "Legacy"),
                ),
        )
    }

    @Test
    fun settings_screen_must_remain_runtime_shell_over_bridge_view_model() {
        val source = readSource("ui/settings/SettingsScreen.kt")
        val imports = importsOf(source)

        assertTrue(
            "SettingsScreen must remain the runtime shell over BridgeViewModel",
            packageNameOf(source) == "com.astrbot.android.ui.settings" &&
                declaresFunction(source, "SettingsScreen") &&
                imports.contains("com.astrbot.android.ui.viewmodel.BridgeViewModel") &&
                imports.contains("androidx.hilt.navigation.compose.hiltViewModel") &&
                imports.contains("com.astrbot.android.model.NapCatBridgeConfig"),
        )
        assertTrue(
            "SettingsScreen must not pull feature presentation packages or root data repositories",
            importsNoneOf(source, forbiddenSettingsScreenImportPrefixes) &&
                referencesNoneOf(source, rootRepositoryTokens + listOf("Legacy")),
        )
        assertTrue(
            "SettingsScreen may only reach the allowed runtime controller from the shell",
            imports.contains("com.astrbot.android.core.runtime.container.ContainerBridgeController") &&
                referencesNoneOf(
                    source,
                    rootRuntimeTokens.filter { it != "ContainerBridgeController" },
                ),
        )
    }

    @Test
    fun root_model_tree_must_only_keep_app_models_file() {
        val violations = unexpectedKotlinPaths(
            relativeRoot = "model",
            allowedExactPaths = setOf("model/AppModels.kt"),
            allowedPathPrefixes = emptySet(),
        )

        assertTrue(
            "Root model/ still contains feature-owned nested models after phase 7: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_models_file_must_only_hold_root_shared_models() {
        val source = readSource("model/AppModels.kt")

        assertTrue(
            "AppModels must remain the root shared-model seam",
            source.contains("package com.astrbot.android.model") &&
                declaresType(source, "enum class", "ProviderCapability") &&
                declaresType(source, "data class", "ProviderProfile") &&
                declaresType(source, "data class", "ConfigProfile") &&
                declaresType(source, "data class", "NapCatBridgeConfig") &&
                declaresType(source, "data class", "RuntimeAssetState"),
        )
        assertTrue(
            "AppModels must not import feature/data/runtime/ui implementations",
            importsNoneOf(
                source,
                listOf(
                "import com.astrbot.android.feature.",
                "import com.astrbot.android.data.",
                "import com.astrbot.android.runtime.",
                "import com.astrbot.android.ui.",
                ),
            ),
        )
    }

    @Test
    fun root_data_tree_must_only_keep_explicit_infrastructure_or_compat_paths() {
        val violations = unexpectedKotlinPaths(
            relativeRoot = "data",
            allowedExactPaths = allowedRootDataExactPaths,
            allowedPathPrefixes = allowedRootDataPathPrefixes,
        )

        assertTrue(
            "Root data/ still contains phase-7 business code outside explicit infrastructure/compat seams: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun allowed_root_data_paths_must_not_reference_phase7_business_objects() {
        val violations = findTokenViolationsInAllowedPaths(
            relativeRoot = "data",
            allowedExactPaths = allowedRootDataExactPaths,
            allowedPathPrefixes = allowedRootDataPathPrefixes,
            forbiddenTokens = rootRepositoryTokens + listOf("ChatCompletionService", "StreamingResponseSegmenter"),
        )

        assertTrue(
            "Allowed root data infrastructure/compat paths still reference phase-7 business objects: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun root_runtime_tree_must_only_keep_explicit_compat_paths() {
        val violations = unexpectedKotlinPaths(
            relativeRoot = "runtime",
            allowedExactPaths = allowedRootRuntimeExactPaths,
            allowedPathPrefixes = emptySet(),
        )

        assertTrue(
            "Root runtime/ still contains phase-7 business code outside explicit compat seams: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun explicit_root_runtime_compat_paths_must_not_reach_phase7_runtime_business_objects() {
        val violations = findTokenViolationsInAllowedPaths(
            relativeRoot = "runtime",
            allowedExactPaths = allowedRootRuntimeExactPaths,
            allowedPathPrefixes = emptySet(),
            forbiddenTokens = rootRuntimeTokens,
        )

        assertTrue(
            "Allowed root runtime compat seams still depend on root runtime business objects: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_code_must_not_instantiate_legacy_adapters() {
        val adapterTypes = listOf(
            "LegacyBotRepositoryAdapter",
            "LegacyConfigRepositoryAdapter",
            "LegacyPersonaRepositoryAdapter",
            "LegacyProviderRepositoryAdapter",
            "LegacyConversationRepositoryAdapter",
            "LegacyQqConversationAdapter",
            "LegacyQqPlatformConfigAdapter",
            "LegacyCronJobRepositoryAdapter",
            "LegacyResourceCenterRepositoryAdapter",
            "LegacyCronSchedulerAdapter",
            "LegacyChatCompletionServiceAdapter",
            "LegacyRuntimeOrchestratorAdapter",
        )

        val violations = kotlinFilesUnder("feature")
            .mapNotNull { file ->
                val relative = relativePathOf(file)
                if ("Legacy" in relative && "Adapter" in relative) {
                    return@mapNotNull null
                }
                val text = file.readText()
                val hits = adapterTypes.flatMap { type ->
                    findInstantiationHits(text, type).map { hit -> "$type ${hit.rendered()}" }
                }
                if (hits.isEmpty()) null else "$relative instantiates $hits"
            }

        assertTrue(
            "Feature code instantiates legacy adapters instead of receiving ports from composition root: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun core_code_must_not_import_feature_packages() {
        val violations = kotlinFilesUnder("core")
            .mapNotNull { file ->
                val relative = relativePathOf(file)
                val imports = importsOf(file.readText())
                    .filter { it.startsWith("com.astrbot.android.feature.") }
                if (imports.isEmpty()) null else "$relative imports $imports"
            }

        assertTrue(
            "Core code must not import feature-owned packages after phase 7 migration: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    @Test
    fun core_code_must_not_alias_feature_repositories_back_to_root_names() {
        val aliasPattern = Regex(
            """import\s+com\.astrbot\.android\.feature\..*?\s+as\s+(${rootRepositoryTokens.joinToString("|") { Regex.escape(it) }})\b""",
        )
        val violations = kotlinFilesUnder("core")
            .mapNotNull { file ->
                val relative = relativePathOf(file)
                val source = file.readText()
                val hits = aliasPattern.findAll(source)
                    .map { match -> lineHitAt(source, match.range.first).rendered() }
                    .toList()
                if (hits.isEmpty()) null else "$relative aliases feature repositories as root names $hits"
            }

        assertTrue(
            "Core code must not keep feature repository aliases that mimic removed root repositories: ${summarize(violations)}",
            violations.isEmpty(),
        )
    }

    private fun findScopedTokenViolations(
        relativeRoot: String,
        allowedTokenScopes: Map<String, Set<String>>,
        forbiddenTokens: List<String>,
        onlyAllowedFiles: Boolean = false,
    ): List<String> {
        return kotlinFilesUnder(relativeRoot).mapNotNull { file ->
            val relative = relativePathOf(file)
            val allowedTokens = allowedTokenScopes[relative]
            if (onlyAllowedFiles && allowedTokens == null) {
                return@mapNotNull null
            }
            val text = file.readText()
            val hits = forbiddenTokens.flatMap { token ->
                findTokenHits(text, token).map { hit -> "$token ${hit.rendered()}" }
            }
            when {
                hits.isEmpty() -> null
                allowedTokens == null -> "$relative references $hits"
                else -> {
                    val extraHits = hits.filter { hit ->
                        val token = hit.substringBefore(" at L")
                        token !in allowedTokens
                    }
                    if (extraHits.isEmpty()) null else "$relative references extra root tokens $extraHits"
                }
            }
        }
    }

    private fun unexpectedKotlinPaths(
        relativeRoot: String,
        allowedExactPaths: Set<String>,
        allowedPathPrefixes: Set<String>,
    ): List<String> {
        return kotlinFilesUnder(relativeRoot)
            .map(::relativePathOf)
            .filterNot { path -> isAllowedPath(path, allowedExactPaths, allowedPathPrefixes) }
            .sorted()
    }

    private fun findTokenViolationsInAllowedPaths(
        relativeRoot: String,
        allowedExactPaths: Set<String>,
        allowedPathPrefixes: Set<String>,
        forbiddenTokens: List<String>,
    ): List<String> {
        return kotlinFilesUnder(relativeRoot)
            .mapNotNull { file ->
                val relative = relativePathOf(file)
                if (!isAllowedPath(relative, allowedExactPaths, allowedPathPrefixes)) {
                    return@mapNotNull null
                }
                val text = file.readText()
                val hits = forbiddenTokens.flatMap { token ->
                    findTokenHits(text, token).map { hit -> "$token ${hit.rendered()}" }
                }
                if (hits.isEmpty()) null else "$relative references $hits"
            }
    }

    private fun isAllowedPath(path: String, allowedExactPaths: Set<String>, allowedPathPrefixes: Set<String>): Boolean {
        return path in allowedExactPaths || allowedPathPrefixes.any(path::startsWith)
    }

    private fun readSource(relativePath: String): String {
        val file = mainRoot.resolve(relativePath)
        assertTrue("$relativePath must exist", file.exists())
        return file.readText()
    }

    private fun importsNoneOf(source: String, forbiddenPrefixes: List<String>): Boolean =
        importsOf(source).none { importLine -> forbiddenPrefixes.any { prefix -> "import $importLine".startsWith(prefix) } }

    private fun referencesNoneOf(source: String, forbiddenTokens: List<String>): Boolean =
        forbiddenTokens.none { token -> findTokenHits(source, token).isNotEmpty() }

    private fun importsOf(source: String): List<String> =
        source
            .lineSequence()
            .map(String::trim)
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ") }
            .toList()

    private fun packageNameOf(source: String): String =
        source
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("package ") }
            ?.removePrefix("package ")
            .orEmpty()

    private fun declaresClass(source: String, className: String): Boolean =
        Regex("""\bclass\s+$className\b""").containsMatchIn(source)

    private fun declaresFunction(source: String, functionName: String): Boolean =
        Regex("""\bfun\s+$functionName\s*\(""").containsMatchIn(source)

    private fun declaresType(source: String, keyword: String, typeName: String): Boolean =
        Regex("""\b${Regex.escape(keyword)}\s+$typeName\b""").containsMatchIn(source)

    private fun dataImports(source: String): List<String> =
        importsOf(source)
            .asSequence()
            .filter { it.startsWith("com.astrbot.android.data.") }
            .map { it.removePrefix("com.astrbot.android.data.") }
            .toList()

    private fun findTokenHits(source: String, token: String): List<LineHit> {
        val cleanedSource = stripCommentsAndStrings(source)
        val regex = Regex("""\b${Regex.escape(token)}\b""")
        return regex.findAll(cleanedSource)
            .map { match -> lineHitAt(source, match.range.first) }
            .toList()
    }

    private fun findInstantiationHits(source: String, typeName: String): List<LineHit> {
        val cleanedSource = stripCommentsAndStrings(source)
        val regex = Regex("""\b${Regex.escape(typeName)}\s*\(""")
        return regex.findAll(cleanedSource)
            .map { match -> lineHitAt(source, match.range.first) }
            .toList()
    }

    private fun lineHitAt(source: String, offset: Int): LineHit {
        val safeOffset = offset.coerceIn(0, source.length)
        val lineNumber = source.take(safeOffset).count { it == '\n' } + 1
        val line = source.lineSequence().elementAt(lineNumber - 1).trim()
        return LineHit(lineNumber = lineNumber, snippet = line)
    }

    private fun stripCommentsAndStrings(source: String): String {
        val result = StringBuilder(source.length)
        var index = 0
        var blockCommentDepth = 0
        var inLineComment = false
        var inString = false
        var inTripleString = false
        var inChar = false
        var escaping = false

        fun appendMasked(char: Char) {
            result.append(if (char == '\n' || char == '\r') char else ' ')
        }

        while (index < source.length) {
            val current = source[index]
            val next = source.getOrNull(index + 1)
            val nextTwo = source.getOrNull(index + 2)

            when {
                inLineComment -> {
                    appendMasked(current)
                    if (current == '\n') {
                        inLineComment = false
                    }
                    index += 1
                }

                blockCommentDepth > 0 -> {
                    when {
                        current == '/' && next == '*' -> {
                            appendMasked(current)
                            appendMasked(next)
                            blockCommentDepth += 1
                            index += 2
                        }

                        current == '*' && next == '/' -> {
                            appendMasked(current)
                            appendMasked(next)
                            blockCommentDepth -= 1
                            index += 2
                        }

                        else -> {
                            appendMasked(current)
                            index += 1
                        }
                    }
                }

                inTripleString -> {
                    when {
                        current == '"' && next == '"' && nextTwo == '"' -> {
                            appendMasked(current)
                            appendMasked(next)
                            appendMasked(nextTwo)
                            inTripleString = false
                            index += 3
                        }

                        else -> {
                            appendMasked(current)
                            index += 1
                        }
                    }
                }

                inString -> {
                    appendMasked(current)
                    when {
                        escaping -> escaping = false
                        current == '\\' -> escaping = true
                        current == '"' -> inString = false
                    }
                    index += 1
                }

                inChar -> {
                    appendMasked(current)
                    when {
                        escaping -> escaping = false
                        current == '\\' -> escaping = true
                        current == '\'' -> inChar = false
                    }
                    index += 1
                }

                current == '/' && next == '/' -> {
                    appendMasked(current)
                    appendMasked(next)
                    inLineComment = true
                    index += 2
                }

                current == '/' && next == '*' -> {
                    appendMasked(current)
                    appendMasked(next)
                    blockCommentDepth = 1
                    index += 2
                }

                current == '"' && next == '"' && nextTwo == '"' -> {
                    appendMasked(current)
                    appendMasked(next)
                    appendMasked(nextTwo)
                    inTripleString = true
                    index += 3
                }

                current == '"' -> {
                    appendMasked(current)
                    inString = true
                    escaping = false
                    index += 1
                }

                current == '\'' -> {
                    appendMasked(current)
                    inChar = true
                    escaping = false
                    index += 1
                }

                else -> {
                    result.append(current)
                    index += 1
                }
            }
        }

        return result.toString()
    }

    private fun kotlinFilesUnder(relativeRoot: String): List<Path> {
        val root = mainRoot.resolve(relativeRoot)
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePathOf(file: Path): String = mainRoot.relativize(file).toString().replace('\\', '/')

    private fun summarize(items: List<String>, limit: Int = 20): String {
        if (items.isEmpty()) return "none"
        val head = items.sorted().take(limit)
        return if (items.size <= limit) {
            head.toString()
        } else {
            "${head} ... (+${items.size - limit} more)"
        }
    }

    private companion object {
        val rootRepositoryTokens = listOf(
            "BotRepository",
            "ConfigRepository",
            "PersonaRepository",
            "ProviderRepository",
            "ConversationRepository",
            "CronJobRepository",
            "PluginRepository",
            "ResourceCenterRepository",
        )

        val rootRuntimeTokens = listOf(
            "OneBotBridgeServer",
            "OneBotPayloadCodec",
            "ContainerBridgeController",
            "ContainerBridgeRuntimeSupport",
            "ContainerBridgeService",
            "ContainerRuntimeInstaller",
            "RuntimeLogRepository",
            "RuntimeLogCleanupRepository",
            "RuntimeSecretRepository",
            "TencentSilkEncoder",
        )

        val allowedFeatureRootRepositoryScopes = mapOf(
            "feature/bot/data/BotRepositoryInitializer.kt" to setOf("BotRepository"),
            "feature/bot/data/LegacyBotRepositoryAdapter.kt" to setOf("BotRepository"),
            "feature/chat/data/LegacyConversationRepositoryAdapter.kt" to setOf("ConversationRepository"),
            "feature/config/data/ConfigRepositoryInitializer.kt" to setOf("ConfigRepository"),
            "feature/config/data/LegacyConfigRepositoryAdapter.kt" to setOf("ConfigRepository"),
            "feature/cron/data/LegacyCronJobRepositoryAdapter.kt" to setOf("CronJobRepository"),
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt" to setOf("PersonaRepository"),
            "feature/persona/data/PersonaRepositoryInitializer.kt" to setOf("PersonaRepository"),
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt" to setOf("ProviderRepository"),
            "feature/provider/data/ProviderRepositoryInitializer.kt" to setOf("ProviderRepository"),
            "feature/qq/data/LegacyQqConversationAdapter.kt" to setOf("ConversationRepository"),
            "feature/qq/data/LegacyQqPlatformConfigAdapter.kt" to setOf("BotRepository", "ConfigRepository"),
            "feature/resource/data/LegacyResourceCenterRepositoryAdapter.kt" to setOf("ResourceCenterRepository"),
        )

        val allowedRootUiExactPaths = setOf(
            "ui/settings/SettingsHubScreen.kt",
            "ui/settings/SettingsScreen.kt",
            "ui/viewmodel/BridgeViewModel.kt",
            "ui/viewmodel/RuntimeAssetViewModel.kt",
        )

        val allowedRootUiPathPrefixes = setOf(
            "ui/app/",
            "ui/common/",
            "ui/navigation/",
            "ui/theme/",
        )

        val allowedRootDataExactPaths = setOf(
            "data/AppPreferencesRepository.kt",
            "data/LegacyProfileImport.kt",
            "data/LegacyRuntimeStateImport.kt",
            "data/LegacyStructuredStateImport.kt",
            "data/RuntimeAssetRepository.kt",
            "data/RuntimeCacheRepository.kt",
        )

        val allowedRootDataPathPrefixes = setOf(
            "data/db/",
            "data/http/",
        )

        val allowedRootRuntimeExactPaths = setOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            "runtime/llm/LegacyRuntimeOrchestratorAdapter.kt",
        )

        val disallowedImportPrefixesForShells = listOf(
            "import com.astrbot.android.data.",
            "import com.astrbot.android.runtime.",
            "import com.astrbot.android.feature.",
        )

        val allowedSettingsHubDataImports = setOf(
            "AppLanguage",
            "AppPreferencesRepository",
            "RuntimeCacheRepository",
            "ThemeMode",
        )

        val forbiddenSettingsHubImportPrefixes = listOf(
            "import com.astrbot.android.feature.",
            "import com.astrbot.android.ui.viewmodel.",
            "import com.astrbot.android.runtime.",
        )

        val forbiddenSettingsScreenImportPrefixes = listOf(
            "import com.astrbot.android.feature.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.ui.bot.",
            "import com.astrbot.android.ui.chat.",
            "import com.astrbot.android.ui.config.",
            "import com.astrbot.android.ui.persona.",
            "import com.astrbot.android.ui.plugin.",
            "import com.astrbot.android.ui.provider.",
            "import com.astrbot.android.ui.qqlogin.",
            "import com.astrbot.android.ui.voiceasset.",
        )
    }

    private data class LineHit(
        val lineNumber: Int,
        val snippet: String,
    ) {
        fun rendered(): String = "at L$lineNumber: $snippet"
    }
}
