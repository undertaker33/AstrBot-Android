package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticRepositoryUsageContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val allowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/static-repository-usage-allowlist.txt")

    private val allowlist: StaticRepositoryAllowlist = loadAllowlist()

    private val forbiddenSymbols = setOf(
        "FeatureBotRepository",
        "FeatureConfigRepository",
        "FeatureProviderRepository",
        "FeaturePersonaRepository",
        "FeatureConversationRepository",
        "FeatureCronJobRepository",
        "FeatureResourceCenterRepository",
        "FeaturePluginRepository",
        "NapCatLoginRepository",
        "RuntimeCacheRepository",
        "AppBackupRepository",
        "ConversationBackupRepository",
    )

    @Test
    fun allowlist_file_must_exist_and_be_well_formed() {
        assertTrue(
            "Static repository usage allowlist file must exist: $allowlistFile",
            allowlistFile.exists(),
        )

        val malformedEntries = allowlist.entries.filter { entry ->
            entry.path.isBlank() ||
                entry.symbol !in forbiddenSymbols ||
                entry.owner.isBlank() ||
                entry.target.isBlank() ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                entry.path.contains("*") ||
                entry.path.contains("\\") ||
                entry.path.endsWith("/") ||
                entry.path.startsWith("/") ||
                !entry.path.endsWith(".kt")
        }

        assertTrue(
            "Static repository usage allowlist contains malformed entries: $malformedEntries",
            malformedEntries.isEmpty(),
        )
    }

    @Test
    fun allowlist_entries_must_point_to_existing_production_files() {
        val missing = allowlist.entries
            .map { entry -> entry.path }
            .distinct()
            .filterNot { path -> mainRoot.resolve(path).exists() }

        assertTrue(
            "Static repository usage allowlist points to missing production files: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun allowlist_entries_must_still_match_current_static_repository_debt() {
        val currentDebt = findStaticRepositoryUsages()
            .map { usage -> usage.path to usage.symbol }
            .toSet()
        val staleEntries = allowlist.entries.filterNot { entry -> entry.path to entry.symbol in currentDebt }

        assertTrue(
            "Static repository usage allowlist has stale entries with no matching current usage: $staleEntries",
            staleEntries.isEmpty(),
        )
    }

    @Test
    fun production_code_must_not_add_new_static_repository_facade_usages() {
        val violations = findStaticRepositoryUsages()
            .filterNot { usage -> allowlist.isAllowed(usage.path, usage.symbol) }

        assertTrue(
            "Production code must not add new static repository facade imports or calls. Found: $violations",
            violations.isEmpty(),
        )
    }

    private fun findStaticRepositoryUsages(): List<StaticRepositoryUsage> {
        return kotlinFilesUnder(mainRoot).flatMap { file ->
            val relative = relativePath(file)
            val text = file.readText()

            forbiddenSymbols.mapNotNull { symbol ->
                if (declaresForbiddenSymbol(text, symbol)) {
                    return@mapNotNull null
                }

                val importRegex = Regex(
                    """(?m)^\s*import\s+com\.astrbot\.android(?:\.[A-Za-z0-9_]+)*\.${Regex.escape(symbol)}\s*$""",
                )
                val callRegex = Regex("""\b${Regex.escape(symbol)}\s*\.""")
                val kinds = buildList {
                    if (importRegex.containsMatchIn(text)) add("import")
                    if (callRegex.containsMatchIn(text)) add("call")
                }

                if (kinds.isEmpty()) {
                    null
                } else {
                    StaticRepositoryUsage(
                        path = relative,
                        symbol = symbol,
                        kinds = kinds,
                    )
                }
            }
        }
    }

    private fun declaresForbiddenSymbol(text: String, symbol: String): Boolean {
        val declarationRegex = Regex(
            """(?m)^\s*(?:(?:internal|private|public)\s+)?(?:class|object|interface|typealias)\s+${Regex.escape(symbol)}\b""",
        )
        return declarationRegex.containsMatchIn(text)
    }

    private fun loadAllowlist(): StaticRepositoryAllowlist {
        if (!allowlistFile.exists()) {
            return StaticRepositoryAllowlist(emptyList())
        }

        val entries = allowlistFile.readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                require(parts.size == 7) {
                    "Invalid static repository usage allowlist entry at line ${index + 1}: $line"
                }
                StaticRepositoryAllowlistEntry(
                    path = parts[0],
                    symbol = parts[1],
                    owner = parts[2],
                    target = parts[3],
                    reason = parts[4],
                    expires = parts[5],
                    issue = parts[6],
                )
            }
            .toList()

        return StaticRepositoryAllowlist(entries)
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePath(file: Path): String {
        return mainRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private data class StaticRepositoryAllowlist(
        val entries: List<StaticRepositoryAllowlistEntry>,
    ) {
        fun isAllowed(path: String, symbol: String): Boolean {
            return entries.any { entry -> entry.path == path && entry.symbol == symbol }
        }
    }

    private data class StaticRepositoryAllowlistEntry(
        val path: String,
        val symbol: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )

    private data class StaticRepositoryUsage(
        val path: String,
        val symbol: String,
        val kinds: List<String>,
    )
}
