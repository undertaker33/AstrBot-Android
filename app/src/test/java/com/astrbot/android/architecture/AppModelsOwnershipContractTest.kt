package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelsOwnershipContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val appModelsFile: Path =
        projectRoot.resolve("app/src/main/java/com/astrbot/android/model/AppModels.kt")
    private val ownerMapFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/app-models-owner-map.txt")

    @Test
    fun owner_map_file_must_exist_and_be_well_formed() {
        assertTrue(
            "AppModels owner map file must exist: $ownerMapFile",
            ownerMapFile.exists(),
        )

        val sourceSymbols = readAppModelSymbols()
        val ownerMap = readOwnerMap()

        val duplicateSymbols = ownerMap.entries
            .groupBy { entry -> entry.symbol }
            .filterValues { entries -> entries.size > 1 }
            .keys

        assertTrue(
            "AppModels owner map must not contain duplicate symbols: $duplicateSymbols",
            duplicateSymbols.isEmpty(),
        )

        val malformedEntries = ownerMap.entries.filter { entry ->
            entry.fields.any(String::isBlank) ||
                entry.target.startsWith("/") ||
                entry.target.contains("*") ||
                !entry.target.endsWith(".kt") ||
                sourceSymbols[entry.symbol]?.kind != entry.kind
        }

        assertTrue(
            "AppModels owner map contains malformed entries: $malformedEntries",
            malformedEntries.isEmpty(),
        )
    }

    @Test
    fun every_top_level_app_model_symbol_must_have_an_owner() {
        val sourceSymbols = readAppModelSymbols()
        val ownerMap = readOwnerMap()
        val mappedSymbols = ownerMap.entries.map { entry -> entry.symbol }.toSet()

        val missing = sourceSymbols.keys - mappedSymbols

        assertTrue(
            "Every top-level AppModels.kt symbol must have an owner map entry. Missing: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun owner_map_must_not_keep_deleted_app_model_symbols() {
        val sourceSymbols = readAppModelSymbols()
        val ownerMap = readOwnerMap()

        val stale = ownerMap.entries
            .map { entry -> entry.symbol }
            .filterNot(sourceSymbols::containsKey)

        assertTrue(
            "AppModels owner map contains stale symbols no longer present in AppModels.kt: $stale",
            stale.isEmpty(),
        )
    }

    private fun readAppModelSymbols(): Map<String, AppModelSymbol> {
        val text = appModelsFile.readText()
        val lines = text.lines()
        val symbols = linkedMapOf<String, AppModelSymbol>()
        var braceDepth = 0

        lines.forEach { line ->
            if (braceDepth == 0) {
                declarationRegex.find(line)?.let { match ->
                    val qualifier = match.groupValues[1]
                    val baseKind = match.groupValues[2]
                    val kind = when {
                        qualifier == "data" && baseKind == "class" -> "data class"
                        qualifier == "sealed" && baseKind == "class" -> "sealed class"
                        baseKind == "enum class" -> "enum class"
                        baseKind == "interface" -> "interface"
                        else -> "class"
                    }
                    val name = match.groupValues[3]
                    symbols[name] = AppModelSymbol(symbol = name, kind = kind)
                }
            }

            braceDepth += line.count { char -> char == '{' }
            braceDepth -= line.count { char -> char == '}' }
            if (braceDepth < 0) {
                braceDepth = 0
            }
        }

        return symbols
    }

    private fun readOwnerMap(): OwnerMap {
        if (!ownerMapFile.exists()) {
            return OwnerMap(emptyList())
        }

        val entries = ownerMapFile.readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                require(parts.size == OWNER_MAP_FIELD_COUNT) {
                    "Invalid AppModels owner map entry at line ${index + 1}: $line"
                }
                OwnerMapEntry(
                    symbol = parts[0],
                    kind = parts[1],
                    owner = parts[2],
                    target = parts[3],
                    reason = parts[4],
                    expires = parts[5],
                    issue = parts[6],
                )
            }
            .toList()

        return OwnerMap(entries)
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private data class AppModelSymbol(
        val symbol: String,
        val kind: String,
    )

    private data class OwnerMap(
        val entries: List<OwnerMapEntry>,
    )

    private data class OwnerMapEntry(
        val symbol: String,
        val kind: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    ) {
        val fields: List<String>
            get() = listOf(symbol, kind, owner, target, reason, expires, issue)
    }

    private companion object {
        private const val OWNER_MAP_FIELD_COUNT = 7

        private val declarationRegex = Regex(
            """^\s*(?:(?:public|internal|private)\s+)?(?:(data|sealed)\s+)?(enum\s+class|class|interface)\s+([A-Za-z_][A-Za-z0-9_]*)\b""",
        )
    }
}
