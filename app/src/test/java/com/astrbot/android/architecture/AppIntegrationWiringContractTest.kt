package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIntegrationWiringContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val diRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android/di")
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val allowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/app-integration-allowlist.txt")
    private val allowlist: WiringAllowlist = loadAllowlist()

    @Test
    fun allowlist_file_must_exist_and_be_well_formed() {
        assertTrue(
            "App integration allowlist file must exist: $allowlistFile",
            allowlistFile.exists(),
        )

        val malformedEntries = allowlist.entries.filter { entry ->
            entry.path.isBlank() ||
                entry.rule.isBlank() ||
                entry.owner.isBlank() ||
                entry.target.isBlank() ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                entry.path.contains("*") ||
                entry.path.endsWith("/") ||
                entry.path.startsWith("/") ||
                entry.rule.contains("*")
        }

        assertTrue(
            "App integration allowlist contains malformed entries: $malformedEntries",
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
            "App integration allowlist points to missing production files: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun di_files_must_stay_small_enough_to_review() {
        val violations = diFiles()
            .map { file -> relativePath(file) to file.readLines().size }
            .filter { (path, lineCount) ->
                lineCount > MAX_FILE_LINES && !allowlist.isAllowed(path, "file-lines")
            }
            .map { (path, lineCount) -> "$path has $lineCount lines" }

        assertTrue(
            "DI files over $MAX_FILE_LINES lines need precise allowlist and should be split: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun provider_methods_must_stay_lightweight() {
        val violations = diFiles()
            .flatMap { file ->
                val path = relativePath(file)
                providerBlocks(file).mapNotNull { block ->
                    val rule = "provider-lines:${block.name}"
                    if (block.lineCount > MAX_PROVIDER_LINES && !allowlist.isAllowed(path, rule)) {
                        "$path ${block.name} has ${block.lineCount} lines"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Hilt provider methods over $MAX_PROVIDER_LINES lines need precise allowlist and should be decomposed: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun di_wiring_must_not_own_runtime_processes_or_business_state() {
        val violations = diFiles()
            .flatMap { file ->
                val path = relativePath(file)
                forbiddenTokenRules(file).filterNot { rule -> allowlist.isAllowed(path, rule) }
                    .map { rule -> "$path violates $rule" }
            }

        assertTrue(
            "DI wiring must not own process launchers, long-running loops, JSON payload parsing, or mutable business state: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun di_wiring_must_not_call_repository_save_or_delete_directly() {
        val violations = diFiles()
            .flatMap { file ->
                val path = relativePath(file)
                repositoryMutationRules(file).filterNot { rule -> allowlist.isAllowed(path, rule) }
                    .map { rule -> "$path violates $rule" }
            }

        assertTrue(
            "DI wiring must not directly call repository save/delete APIs; bind ports/use cases instead: $violations",
            violations.isEmpty(),
        )
    }

    private fun diFiles(): List<Path> {
        if (!diRoot.exists()) {
            return emptyList()
        }
        return Files.walk(diRoot).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun providerBlocks(file: Path): List<ProviderBlock> {
        val lines = file.readLines()
        val providers = mutableListOf<ProviderBlock>()
        var index = 0
        while (index < lines.size) {
            if (!lines[index].contains("@Provides")) {
                index += 1
                continue
            }

            val annotationLine = index
            val funLine = (index until lines.size).firstOrNull { candidate ->
                Regex("""\bfun\s+[A-Za-z0-9_]+""").containsMatchIn(lines[candidate])
            } ?: break
            val name = Regex("""\bfun\s+([A-Za-z0-9_]+)""").find(lines[funLine])?.groupValues?.get(1)
                ?: "unknownProviderAt${funLine + 1}"

            val expressionLine = findExpressionBodyLine(lines, funLine)
            val openingLine = findBlockBodyOpeningLine(lines, funLine, expressionLine)
            if (expressionLine != null && openingLine == null) {
                providers += ProviderBlock(name = name, lineCount = expressionProviderLineCount(lines, annotationLine, funLine))
                index = funLine + 1
                continue
            }
            if (openingLine == null) {
                providers += ProviderBlock(name = name, lineCount = expressionProviderLineCount(lines, annotationLine, funLine))
                index = funLine + 1
                continue
            }

            val closingLine = findClosingBraceLine(lines, openingLine)
            if (closingLine == null) {
                index = funLine + 1
                continue
            }

            providers += ProviderBlock(
                name = name,
                lineCount = closingLine - annotationLine + 1,
            )
            index = closingLine + 1
        }
        return providers
    }

    private fun findExpressionBodyLine(lines: List<String>, funLine: Int): Int? {
        val searchEndExclusive = nextProviderBoundary(lines, funLine)
        return (funLine until searchEndExclusive).firstOrNull { candidate ->
            lines[candidate].contains("=")
        }
    }

    private fun findBlockBodyOpeningLine(lines: List<String>, funLine: Int, expressionLine: Int?): Int? {
        val searchEndExclusive = expressionLine ?: nextProviderBoundary(lines, funLine)
        return (funLine until searchEndExclusive).firstOrNull { candidate ->
            lines[candidate].contains("{")
        }
    }

    private fun expressionProviderLineCount(lines: List<String>, annotationLine: Int, funLine: Int): Int {
        val endLine = nextProviderBoundary(lines, funLine) - 1
        return endLine - annotationLine + 1
    }

    private fun nextProviderBoundary(lines: List<String>, fromLine: Int): Int {
        return ((fromLine + 1) until lines.size).firstOrNull { candidate ->
            lines[candidate].isBlank() || lines[candidate].contains("@Provides")
        } ?: lines.size
    }

    private fun findClosingBraceLine(lines: List<String>, openingLine: Int): Int? {
        var depth = 0
        for (lineIndex in openingLine until lines.size) {
            lines[lineIndex].forEach { char ->
                when (char) {
                    '{' -> depth += 1
                    '}' -> depth -= 1
                }
            }
            if (depth <= 0) {
                return lineIndex
            }
        }
        return null
    }

    private fun forbiddenTokenRules(file: Path): Set<String> {
        val text = file.readLines().joinToString("\n")
        return buildSet {
            forbiddenLiteralRules.forEach { (token, rule) ->
                if (text.contains(token)) {
                    add(rule)
                }
            }
            if (longRunningLoopRegex.containsMatchIn(text)) {
                add("long-running-loop")
            }
            if (jsonPayloadParsingRegexes.any { regex -> regex.containsMatchIn(text) }) {
                add("direct-payload-parsing")
            }
        }
    }

    private fun repositoryMutationRules(file: Path): Set<String> {
        return file.readLines()
            .asSequence()
            .mapNotNull { line ->
                repositoryMutationRegex.find(line)?.let { match ->
                    "repository-mutation:${match.groupValues[1]}.${match.groupValues[2]}"
                }
            }
            .toSet()
    }

    private fun loadAllowlist(): WiringAllowlist {
        if (!allowlistFile.exists()) {
            return WiringAllowlist(emptyList())
        }

        val entries = allowlistFile.readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                require(parts.size == 7) {
                    "Invalid app integration allowlist entry at line ${index + 1}: $line"
                }
                WiringAllowlistEntry(
                    path = parts[0],
                    rule = parts[1],
                    owner = parts[2],
                    target = parts[3],
                    reason = parts[4],
                    expires = parts[5],
                    issue = parts[6],
                )
            }
            .toList()

        return WiringAllowlist(entries)
    }

    private fun relativePath(file: Path): String = mainRoot.relativize(file).toString().replace('\\', '/')

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private data class ProviderBlock(
        val name: String,
        val lineCount: Int,
    )

    private data class WiringAllowlist(
        val entries: List<WiringAllowlistEntry>,
    ) {
        fun isAllowed(path: String, rule: String): Boolean {
            return entries.any { entry -> entry.path == path && entry.rule == rule }
        }
    }

    private data class WiringAllowlistEntry(
        val path: String,
        val rule: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )

    private companion object {
        private const val MAX_FILE_LINES = 500
        private const val MAX_PROVIDER_LINES = 40

        private val forbiddenLiteralRules = mapOf(
            "ProcessBuilder" to "process-builder",
            "MutableStateFlow" to "mutable-business-state",
            "MutableSharedFlow" to "mutable-business-state",
        )
        private val longRunningLoopRegex = Regex("""while\s*\(\s*(true|isActive)\s*\)""")
        private val jsonPayloadParsingRegexes = listOf(
            Regex("""\bJSONObject\s*\("""),
            Regex("""\bJSONArray\s*\("""),
            Regex("""\bJson\.decodeFromString\s*<"""),
            Regex("""\bJson\.decodeFromString\s*\("""),
            Regex("""\bdecodeFromString\s*<"""),
        )
        private val repositoryMutationRegex =
            Regex("""\b([A-Za-z0-9_]*(?:Repository|repository))\s*\.\s*(save[A-Za-z0-9_]*|delete[A-Za-z0-9_]*)\s*\(""")
    }
}
