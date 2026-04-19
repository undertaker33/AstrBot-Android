package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryPortSourceContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun domain_ports_exist() {
        val required = listOf(
            "feature/provider/domain/ProviderRepositoryPort.kt",
            "feature/config/domain/ConfigRepositoryPort.kt",
            "feature/bot/domain/BotRepositoryPort.kt",
            "feature/persona/domain/PersonaRepositoryPort.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing domain port files: $missing", missing.isEmpty())
    }

    @Test
    fun legacy_adapters_exist() {
        val required = listOf(
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing legacy adapter files: $missing", missing.isEmpty())
    }

    @Test
    fun domain_ports_do_not_import_legacy_repositories() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val forbiddenImports = listOf(
            "import com.astrbot.android.data.ProviderRepository",
            "import com.astrbot.android.data.ConfigRepository",
            "import com.astrbot.android.data.BotRepository",
            "import com.astrbot.android.data.PersonaRepository",
        )

        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        }

        assertTrue(
            "Domain ports must not import legacy repository singletons: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun domain_ports_do_not_expose_mutable_flows() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("MutableStateFlow") || text.contains("MutableSharedFlow")) {
                    listOf("$relative exposes mutable flow")
                } else {
                    emptyList()
                }
            }
        }

        assertTrue(
            "Domain ports must not expose mutable flows: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun new_feature_domain_files_do_not_import_android_or_legacy_packages() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val forbiddenPrefixes = listOf(
            "import android.",
            "import androidx.compose.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.runtime.",
        )

        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenPrefixes.mapNotNull { prefix ->
                    if (text.contains(prefix)) "$relative imports $prefix" else null
                }
            }
        }

        assertTrue(
            "Domain files must not import Android framework, Compose, or legacy packages: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun legacy_adapters_do_not_add_runBlocking() {
        val adapterFiles = listOf(
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
        )

        val violations = adapterFiles.mapNotNull { path ->
            val file = mainRoot.resolve(path)
            if (!file.exists()) return@mapNotNull null
            val text = file.readText()
            if (text.contains("runBlocking")) "$path uses runBlocking" else null
        }

        assertTrue(
            "Legacy adapters must not add runBlocking: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun initialization_coordinator_exists() {
        val required = listOf(
            "core/di/AppInitializer.kt",
            "core/di/InitializationCoordinator.kt",
            "feature/provider/data/ProviderRepositoryInitializer.kt",
            "feature/config/data/ConfigRepositoryInitializer.kt",
            "feature/persona/data/PersonaRepositoryInitializer.kt",
            "feature/bot/data/BotRepositoryInitializer.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing initialization coordinator files: $missing", missing.isEmpty())
    }

    @Test
    fun app_container_bootstrap_uses_coordinator_for_configuration_domains() {
        val source = mainRoot.resolve("di/ElymBotAppContainer.kt").readText()
        val bootstrapBody = functionBody(source, "bootstrap")

        assertTrue(
            "ElymBotAppContainer.bootstrap must delegate configuration-domain startup to InitializationCoordinator",
            bootstrapBody.contains("InitializationCoordinator("),
        )
        val requiredInitializers = listOf(
            "ProviderRepositoryInitializer()",
            "ConfigRepositoryInitializer()",
            "PersonaRepositoryInitializer()",
            "BotRepositoryInitializer()",
        )
        val missingInitializers = requiredInitializers.filterNot(bootstrapBody::contains)
        assertTrue(
            "Bootstrap coordinator wiring is missing initializers: $missingInitializers",
            missingInitializers.isEmpty(),
        )
        assertTrue(
            "Bootstrap must execute the configuration-domain coordinator",
            bootstrapBody.contains(".initializeAll(application)"),
        )

        val forbiddenDirectCalls = listOf(
            "ProviderRepository.initialize(application)",
            "ConfigRepository.initialize(application)",
            "PersonaRepository.initialize(application)",
            "BotRepository.initialize(application)",
        )
        val remainingDirectCalls = forbiddenDirectCalls.filter(bootstrapBody::contains)
        assertTrue(
            "Bootstrap must not directly initialize repositories now owned by InitializationCoordinator: $remainingDirectCalls",
            remainingDirectCalls.isEmpty(),
        )
    }

    @Test
    fun initialization_coordinator_wiring_does_not_include_out_of_scope_initializers() {
        val source = mainRoot.resolve("di/ElymBotAppContainer.kt").readText()
        val bootstrapBody = functionBody(source, "bootstrap")

        val forbiddenInitializerTokens = listOf(
            "ConversationRepositoryInitializer",
            "RuntimeInitializer",
            "PlatformBridgeInitializer",
            "QQRepositoryInitializer",
            "QqRepositoryInitializer",
            "OneBotInitializer",
            "PluginRepositoryInitializer",
            "ToolSourceInitializer",
        )
        val violations = forbiddenInitializerTokens.filter(bootstrapBody::contains)
        assertTrue(
            "Fourth-phase coordinator must only wire Provider/Config/Persona/Bot initializers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_repository_imports_are_limited_to_legacy_adapters_and_initializers() {
        val featureDirs = listOf(
            "feature/provider",
            "feature/config",
            "feature/bot",
            "feature/persona",
        )
        val allowed = setOf(
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/provider/data/ProviderRepositoryInitializer.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/config/data/ConfigRepositoryInitializer.kt",
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/bot/data/BotRepositoryInitializer.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
            "feature/persona/data/PersonaRepositoryInitializer.kt",
        )
        val forbiddenRepositoryImports = listOf(
            "import com.astrbot.android.data.ProviderRepository",
            "import com.astrbot.android.data.ConfigRepository",
            "import com.astrbot.android.data.BotRepository",
            "import com.astrbot.android.data.PersonaRepository",
        )

        val violations = featureDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (relative in allowed) {
                    emptyList()
                } else {
                    forbiddenRepositoryImports.mapNotNull { forbidden ->
                        if (text.contains(forbidden)) "$relative imports $forbidden" else null
                    }
                }
            }
        }

        assertTrue(
            "Only legacy adapters and initializers may import legacy repository singletons: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun persona_port_exposes_designed_tool_enablement_and_explicit_enabled_semantics() {
        val port = mainRoot.resolve("feature/persona/domain/PersonaRepositoryPort.kt").readText()
        val adapter = mainRoot.resolve("feature/persona/data/LegacyPersonaRepositoryAdapter.kt").readText()

        assertTrue(
            "PersonaRepositoryPort must expose all persona tool enablement snapshots",
            port.contains("fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot>"),
        )
        assertTrue(
            "PersonaRepositoryPort must preserve lookup by persona id for existing callers",
            port.contains("fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot?"),
        )
        assertTrue(
            "PersonaRepositoryPort must expose explicit enabled state updates",
            port.contains("suspend fun toggleEnabled(id: String, enabled: Boolean)"),
        )
        assertTrue(
            "PersonaRepositoryPort must preserve existing toggle semantics for existing callers",
            port.contains("suspend fun toggleEnabled(id: String)"),
        )
        assertTrue(
            "LegacyPersonaRepositoryAdapter must implement the all-snapshot port API",
            adapter.contains("override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot>"),
        )
        assertTrue(
            "LegacyPersonaRepositoryAdapter must implement explicit enabled updates",
            adapter.contains("override suspend fun toggleEnabled(id: String, enabled: Boolean)"),
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
        val signatureIndex = source.indexOf("fun $functionName(")
        assertNotEquals("Missing function: $functionName", -1, signatureIndex)
        val bodyStart = source.indexOf('{', signatureIndex)
        assertNotEquals("Missing body for function: $functionName", -1, bodyStart)
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun matchingBraceIndex(source: String, openIndex: Int): Int {
        return matchingIndex(source, openIndex, '{', '}')
    }

    private fun matchingIndex(source: String, openIndex: Int, open: Char, close: Char): Int {
        var depth = 0
        for (index in openIndex until source.length) {
            when (source[index]) {
                open -> depth += 1
                close -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        throw AssertionError("No matching '$close' found")
    }
}
