package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSingletonDebtReportTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val allowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/global-singleton-allowlist.txt")
    private val reportFile: Path =
        projectRoot.resolve("app/build/reports/architecture/global-singleton-debt.txt")

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
    fun generate_global_singleton_debt_report() {
        val allowlist = loadAllowlist()
        val productionFiles = kotlinFilesUnder(mainRoot)

        val businessObjects = productionFiles
            .filter { file -> containsBusinessObjectDeclaration(file.readText(UTF_8)) }
            .map(::relativePath)
            .sorted()

        val suspiciousCompanions = productionFiles
            .filter { file -> containsSuspiciousCompanionObject(file.readText(UTF_8)) }
            .map(::relativePath)
            .sorted()

        val graphInstanceFiles = productionFiles
            .filter { file -> file.readText(UTF_8).contains("graphInstance") }
            .map(::relativePath)
            .sorted()

        val volatileDelegateFiles = productionFiles
            .filter { file -> containsVolatileDelegate(file.readText(UTF_8)) }
            .map(::relativePath)
            .sorted()

        val manualDatabaseGetCallers = productionFiles
            .filter { file -> file.readText(UTF_8).contains("AstrBotDatabase.get(") }
            .map(::relativePath)
            .filterNot { path -> path == "data/db/AstrBotDatabase.kt" }
            .sorted()

        val missingProductionFiles = allowlist.entries
            .map { entry -> entry.path }
            .distinct()
            .filterNot { path -> mainRoot.resolve(path).exists() }
            .sorted()

        val report = buildString {
            appendLine("Global Singleton Debt Report")
            appendLine()
            appendLine("total: ${allowlist.entries.size}")
            appendLine("temporary: ${allowlist.countByCategory("temporary")}")
            appendLine("permanent-candidate: ${allowlist.countByCategory("permanent-candidate")}")
            appendLine("permanent: ${allowlist.countByCategory("permanent")}")
            appendLine()
            appendSection("by owner", allowlist.countsBy { it.owner })
            appendLine()
            appendSection("by expires", allowlist.countsBy { it.expires })
            appendLine()
            appendSection("missing production file", missingProductionFiles)
            appendLine()
            appendSection("business object before allowlist", businessObjects)
            appendLine()
            appendSection("business object after allowlist", businessObjects.filterNot { path ->
                allowlist.isAllowed(path, "object")
            })
            appendLine()
            appendSection("suspicious companion before allowlist", suspiciousCompanions)
            appendLine()
            appendSection("suspicious companion after allowlist", suspiciousCompanions.filterNot { path ->
                allowlist.isAllowed(path, "companion")
            })
            appendLine()
            appendSection("graphInstance files", graphInstanceFiles)
            appendLine()
            appendSection("volatile delegate files", volatileDelegateFiles)
            appendLine()
            appendSection("manual AstrBotDatabase.get callers", manualDatabaseGetCallers)
        }

        Files.createDirectories(reportFile.parent)
        Files.write(reportFile, report.toByteArray(UTF_8))

        assertTrue("Debt report must be generated: $reportFile", reportFile.exists())
        assertTrue(
            "Debt report must include required singleton governance sections.",
            listOf(
                "total:",
                "temporary:",
                "permanent-candidate:",
                "permanent:",
                "by owner",
                "by expires",
                "missing production file",
                "business object before allowlist",
                "suspicious companion before allowlist",
                "graphInstance files",
                "volatile delegate files",
                "manual AstrBotDatabase.get callers",
            ).all(report::contains),
        )
    }

    private fun StringBuilder.appendSection(title: String, items: Map<String, Int>) {
        appendLine("$title:")
        if (items.isEmpty()) {
            appendLine("- none")
            return
        }
        items.toSortedMap().forEach { (key, count) ->
            appendLine("- $key: $count")
        }
    }

    private fun StringBuilder.appendSection(title: String, items: List<String>) {
        appendLine("$title:")
        if (items.isEmpty()) {
            appendLine("- none")
            return
        }
        items.forEach { item ->
            appendLine("- $item")
        }
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
        fun countByCategory(category: String): Int {
            return entries.count { entry -> entry.category == category }
        }

        fun countsBy(selector: (SingletonAllowlistEntry) -> String): Map<String, Int> {
            return entries.groupingBy(selector).eachCount()
        }

        fun isAllowed(path: String, type: String): Boolean {
            return entries.any { entry -> entry.path == path && entry.type == type }
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
