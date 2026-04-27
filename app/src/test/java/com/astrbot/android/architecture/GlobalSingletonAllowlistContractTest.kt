package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSingletonAllowlistContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val allowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/global-singleton-allowlist.txt")

    private val allowlist: SingletonAllowlist = loadAllowlist()

    private val allowedTypes = setOf("object", "companion")

    private val allowedCategories = setOf(
        "temporary",
        "permanent",
        "permanent-candidate",
    )

    private val ownerPattern =
        Regex("""^(feature-[a-z0-9-]+|core-[a-z0-9-]+|download|app-shell|app-integration)$""")

    private val issuePattern =
        Regex("""^(ARCH-DEBT-[A-Za-z0-9_-]+|#[0-9]+|GH-[0-9]+)$""")

    private val vagueTargetPattern =
        Regex("""^(remove[-\s]?later|cleanup|legacy|temp(?:orary)?|todo|tbd|none|n/?a)$""", RegexOption.IGNORE_CASE)

    private val suspiciousCompanionTokens = listOf(
        "@Volatile",
        "MutableStateFlow",
        "MutableSharedFlow",
        "CoroutineScope",
        "SupervisorJob",
        "Context",
        "Database",
        "Dao",
        "delegate",
        "graphInstance",
        "instance",
        "initialize(",
        "installDelegateIfAbsent",
        "OverrideForTests",
        "setLoaderOverrideForTests",
    )

    private val businessSingletonNameSuffixes = listOf(
        "Repository",
        "Manager",
        "Controller",
        "Provider",
        "Runner",
        "Store",
        "Service",
        "Registry",
        "Bus",
        "Gateway",
        "Installer",
        "Factory",
    )

    @Test
    fun allowlist_file_must_exist_and_be_well_formed() {
        assertTrue(
            "Global singleton allowlist file must exist: $allowlistFile",
            allowlistFile.exists(),
        )

        val malformedEntries = allowlist.entries.filter { entry ->
            entry.path.isBlank() ||
                entry.type !in allowedTypes ||
                entry.category !in allowedCategories ||
                entry.owner.isBlank() ||
                !ownerPattern.matches(entry.owner) ||
                entry.target.isBlank() ||
                vagueTargetPattern.matches(entry.target.trim()) ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                !issuePattern.matches(entry.issue) ||
                entry.path.contains("*") ||
                entry.path.endsWith("/") ||
                entry.path.startsWith("/") ||
                entry.path.startsWith("\\") ||
                Regex("""^[A-Za-z]:[\\/].*""").matches(entry.path) ||
                !entry.path.endsWith(".kt")
        }

        assertTrue(
            "Global singleton allowlist contains malformed entries: $malformedEntries",
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
            "Global singleton allowlist points to missing production files: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun temporary_allowlist_entries_must_not_claim_permanent_expiry() {
        val violations = allowlist.entries.filter { entry ->
            entry.category == "temporary" &&
                entry.expires.equals("permanent", ignoreCase = true)
        }

        assertTrue(
            "Temporary singleton allowlist entries must have a real phase/review expiry: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun permanent_entries_must_explain_why_they_are_stateless() {
        val requiredReasonHints = listOf(
            "stateless",
            "pure",
            "constant",
            "constants",
            "parser",
            "formatter",
            "mapper",
            "sealed",
        )

        val violations = allowlist.entries.filter { entry ->
            entry.category == "permanent" &&
                requiredReasonHints.none { hint ->
                    entry.reason.contains(hint, ignoreCase = true)
                }
        }

        assertTrue(
            "Permanent singleton allowlist entries must explain why they are stateless/pure: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_business_objects_must_be_explicitly_allowlisted() {
        val violations = kotlinFilesUnder(mainRoot)
            .filter { file ->
                val text = file.readText(UTF_8)
                containsBusinessObjectDeclaration(text)
            }
            .map(::relativePath)
            .filterNot { path -> allowlist.isAllowed(path, "object") }

        assertTrue(
            "Business Kotlin object singletons must be explicitly allowlisted. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_suspicious_companion_objects_must_be_explicitly_allowlisted() {
        val violations = kotlinFilesUnder(mainRoot)
            .filter { file ->
                val text = file.readText(UTF_8)
                containsSuspiciousCompanionObject(text)
            }
            .map(::relativePath)
            .filterNot { path -> allowlist.isAllowed(path, "companion") }

        assertTrue(
            "Suspicious companion object global state must be explicitly allowlisted. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_code_must_not_add_new_graph_instance_singletons() {
        val violations = kotlinFilesUnder(mainRoot)
            .filter { file -> file.readText(UTF_8).contains("graphInstance") }
            .map(::relativePath)
            .filterNot { path -> allowlist.containsPath(path) }

        assertTrue(
            "Do not add new graphInstance singletons. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_code_must_not_add_new_delegate_singletons() {
        val violations = kotlinFilesUnder(mainRoot)
            .filter { file ->
                val text = file.readText(UTF_8)
                containsVolatileDelegate(text)
            }
            .map(::relativePath)
            .filterNot { path -> allowlist.containsPath(path) }

        assertTrue(
            "Do not add new @Volatile delegate singletons. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_code_must_not_add_new_manual_database_singleton_access() {
        val violations = kotlinFilesUnder(mainRoot)
            .filter { file -> file.readText(UTF_8).contains("AstrBotDatabase.get(") }
            .map(::relativePath)
            .filterNot { path -> path == "data/db/AstrBotDatabase.kt" }

        assertTrue(
            "Production code must use injected AstrBotDatabase instead of AstrBotDatabase.get(...). Found: $violations",
            violations.isEmpty(),
        )
    }

    private fun containsBusinessObjectDeclaration(text: String): Boolean {
        val objectRegex = Regex("""(?m)^\s*(?:(?:internal|private|public)\s+)?(data\s+)?object\s+([A-Za-z0-9_]+)""")
        return objectRegex.findAll(text).any { match ->
            if (match.groupValues[1].isNotBlank()) return@any false
            val objectName = match.groupValues[2]
            businessSingletonNameSuffixes.any { suffix -> objectName.endsWith(suffix) }
        }
    }

    private fun containsSuspiciousCompanionObject(text: String): Boolean {
        return companionObjectBlocks(text).any { block ->
            suspiciousCompanionTokens
                .filterNot { token -> token in weakCompanionTokens }
                .any(block::contains) ||
                companionOwnsWeakGlobalState(block)
        }
    }

    private fun companionObjectBlocks(text: String): List<String> {
        val companionRegex = Regex("""companion\s+object(?:\s+[A-Za-z0-9_]+)?\s*\{""")
        return companionRegex.findAll(text).mapNotNull { match ->
            val openingBrace = text.indexOf('{', startIndex = match.range.first)
            if (openingBrace < 0) return@mapNotNull null
            var depth = 0
            for (index in openingBrace until text.length) {
                when (text[index]) {
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) {
                            return@mapNotNull text.substring(match.range.first, index + 1)
                        }
                    }
                }
            }
            null
        }.toList()
    }

    private fun companionOwnsWeakGlobalState(block: String): Boolean {
        val weakStateRegexes = listOf(
            Regex("""(?m)^\s*(?:(?:private|internal|public)\s+)?(?:lateinit\s+)?var\s+[A-Za-z0-9_]+\s*:\s*.*\b(?:Context|Database|Dao|instance)\b"""),
            Regex("""(?m)^\s*(?:(?:private|internal|public)\s+)?(?:val|var)\s+[A-Za-z0-9_]*instance[A-Za-z0-9_]*\b""", RegexOption.IGNORE_CASE),
        )
        return weakStateRegexes.any { regex -> regex.containsMatchIn(block) }
    }

    private fun containsVolatileDelegate(text: String): Boolean {
        return Regex("""@Volatile\s+(?:(?:private|internal|public)\s+)?(?:var|val)\s+[A-Za-z0-9_]*delegate[A-Za-z0-9_]*\b""")
            .containsMatchIn(text)
    }

    private fun loadAllowlist(): SingletonAllowlist {
        if (!allowlistFile.exists()) {
            return SingletonAllowlist(emptyList())
        }

        val entries = allowlistFile.readLines(UTF_8)
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                require(parts.size == 8) {
                    "Invalid singleton allowlist entry at line ${index + 1}: $line"
                }
                SingletonAllowlistEntry(
                    path = parts[0],
                    type = parts[1],
                    category = parts[2],
                    owner = parts[3],
                    target = parts[4],
                    reason = parts[5],
                    expires = parts[6],
                    issue = parts[7],
                )
            }
            .toList()

        return SingletonAllowlist(entries)
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

    private data class SingletonAllowlist(
        val entries: List<SingletonAllowlistEntry>,
    ) {
        fun isAllowed(path: String, type: String): Boolean {
            return entries.any { entry -> entry.path == path && entry.type == type }
        }

        fun containsPath(path: String): Boolean {
            return entries.any { entry -> entry.path == path }
        }
    }

    private data class SingletonAllowlistEntry(
        val path: String,
        val type: String,
        val category: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )

    private companion object {
        private val weakCompanionTokens = setOf(
            "Context",
            "Database",
            "Dao",
            "instance",
        )
    }
}
