package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryPortSourceContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun repository_port_module_binds_only_semantic_feature_port_adapters() {
        val source = mainRoot.resolve("di/hilt/RepositoryPortModule.kt").readText()

        val requiredTokens = listOf(
            "FeatureBotRepositoryPortAdapter",
            "FeatureConfigRepositoryPortAdapter",
            "FeatureConversationRepositoryPortAdapter",
            "FeaturePersonaRepositoryPortAdapter",
            "FeatureProviderRepositoryPortAdapter",
            "FeatureQqConversationPortAdapter",
            "FeatureQqPlatformConfigPortAdapter",
        )
        val forbiddenTokens = listOf(
            "LegacyBotRepositoryAdapter",
            "LegacyConfigRepositoryAdapter",
            "LegacyConversationRepositoryAdapter",
            "LegacyPersonaRepositoryAdapter",
            "LegacyProviderRepositoryAdapter",
            "LegacyQqConversationAdapter",
            "LegacyQqPlatformConfigAdapter",
        )

        assertTrue(
            "RepositoryPortModule must bind semantic feature port adapters",
            requiredTokens.all(source::contains),
        )
        assertTrue(
            "RepositoryPortModule must not bind legacy adapter names",
            forbiddenTokens.none(source::contains),
        )
    }

    @Test
    fun semantic_adapter_files_exist_and_legacy_adapter_files_are_removed() {
        val semanticFiles = listOf(
            "feature/bot/data/FeatureBotRepositoryPortAdapter.kt",
            "feature/chat/data/FeatureConversationRepositoryPortAdapter.kt",
            "feature/config/data/FeatureConfigRepositoryPortAdapter.kt",
            "feature/cron/data/FeatureCronJobRepositoryPortAdapter.kt",
            "feature/persona/data/FeaturePersonaRepositoryPortAdapter.kt",
            "feature/provider/data/FeatureProviderRepositoryPortAdapter.kt",
            "feature/qq/data/FeatureQqConversationPortAdapter.kt",
            "feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt",
            "feature/resource/data/FeatureResourceCenterPortAdapter.kt",
        )
        val legacyFiles = listOf(
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/chat/data/LegacyConversationRepositoryAdapter.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/cron/data/LegacyCronJobRepositoryAdapter.kt",
            "feature/cron/data/LegacyCronSchedulerAdapter.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/qq/data/LegacyQqConversationAdapter.kt",
            "feature/qq/data/LegacyQqPlatformConfigAdapter.kt",
            "feature/resource/data/LegacyResourceCenterRepositoryAdapter.kt",
        )

        val missingSemantic = semanticFiles.filterNot { relativePath -> mainRoot.resolve(relativePath).exists() }
        val remainingLegacy = legacyFiles.filter { relativePath -> mainRoot.resolve(relativePath).exists() }

        assertTrue("Missing semantic adapter files: $missingSemantic", missingSemantic.isEmpty())
        assertTrue("Legacy adapter files must be removed from production: $remainingLegacy", remainingLegacy.isEmpty())
    }

    @Test
    fun feature_repository_singleton_usage_is_limited_to_semantic_adapters_and_initializers() {
        val tokenToAllowedPaths = mapOf(
            "FeatureBotRepository." to setOf(
                "feature/bot/data/BotRepositoryInitializer.kt",
                "feature/bot/data/FeatureBotRepository.kt",
                "feature/bot/data/FeatureBotRepositoryPortAdapter.kt",
                "feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt",
            ),
            "FeatureConversationRepository." to setOf(
                "feature/bot/data/FeatureBotRepository.kt",
                "feature/chat/data/FeatureConversationRepository.kt",
                "feature/chat/data/FeatureConversationRepositoryPortAdapter.kt",
                "feature/qq/data/FeatureQqConversationPortAdapter.kt",
            ),
            "FeatureConfigRepository." to setOf(
                "feature/bot/data/FeatureBotRepository.kt",
                "feature/config/data/ConfigRepositoryInitializer.kt",
                "feature/config/data/FeatureConfigRepository.kt",
                "feature/config/data/FeatureConfigRepositoryPortAdapter.kt",
                "feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt",
            ),
            "FeatureCronJobRepository." to setOf(
                "feature/cron/data/FeatureCronJobRepository.kt",
                "feature/cron/data/FeatureCronJobRepositoryPortAdapter.kt",
            ),
            "FeaturePersonaRepository." to setOf(
                "feature/persona/data/FeaturePersonaRepository.kt",
                "feature/persona/data/PersonaRepositoryInitializer.kt",
                "feature/persona/data/FeaturePersonaRepositoryPortAdapter.kt",
            ),
            "FeatureProviderRepository." to setOf(
                "feature/provider/data/FeatureProviderRepository.kt",
                "feature/provider/data/ProviderRepositoryWarmup.kt",
                "feature/provider/data/FeatureProviderRepositoryPortAdapter.kt",
            ),
            "FeatureResourceCenterRepository." to setOf(
                "feature/resource/data/FeatureResourceCenterRepository.kt",
                "feature/resource/data/FeatureResourceCenterPortAdapter.kt",
            ),
        )

        val violations = buildList {
            tokenToAllowedPaths.forEach { (token, allowedPaths) ->
                kotlinFilesUnder("feature").forEach { file ->
                    val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                    if (file.readText().contains(token) && relative !in allowedPaths) {
                        add("$relative contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Feature singleton repository usage must stay confined to semantic adapters/initializers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun qq_semantic_adapters_must_depend_on_semantic_ports_not_repository_store_types() {
        val qqAdapterFiles = listOf(
            "feature/qq/data/FeatureQqConversationPortAdapter.kt",
            "feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt",
        )
        val forbiddenStoreTypes = listOf(
            "FeatureConversationRepositoryStore",
            "FeatureBotRepositoryStore",
            "FeatureConfigRepositoryStore",
        )

        val violations = qqAdapterFiles.flatMap { relativePath ->
            val source = mainRoot.resolve(relativePath).readText()
            forbiddenStoreTypes.filter(source::contains).map { token ->
                "$relativePath contains $token"
            }
        }

        assertTrue(
            "QQ semantic adapters must wire through semantic ports instead of repository store types: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_services_module_must_not_bind_legacy_llm_adapters() {
        val source = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()
        val forbiddenTokens = listOf(
            "LegacyChatCompletionServiceAdapter",
            "LegacyLlmProviderProbeAdapter",
            "LegacyRuntimeOrchestratorAdapter",
        )
        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "RuntimeServicesModule must not bind legacy llm adapters: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(relativeRoot: String): List<Path> {
        val root = mainRoot.resolve(relativeRoot)
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }
}
