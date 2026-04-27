package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureImportBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val featureRoot: Path = mainRoot.resolve("feature")
    private val allowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/feature-import-allowlist.txt")

    private val allowlist: FeatureImportAllowlist = loadAllowlist()
    private val scannedSegments = setOf("data", "runtime", "presentation", "domain")

    @Test
    fun allowlist_file_must_exist_and_be_well_formed() {
        assertTrue(
            "Feature import allowlist file must exist: $allowlistFile",
            allowlistFile.exists(),
        )

        val malformedEntries = allowlist.entries.filter { entry ->
            entry.path.isBlank() ||
                entry.forbiddenImport.isBlank() ||
                entry.owner.isBlank() ||
                entry.target.isBlank() ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                entry.path.contains("*") ||
                entry.path.contains("\\") ||
                entry.path.endsWith("/") ||
                entry.path.startsWith("/") ||
                !entry.path.endsWith(".kt") ||
                !entry.path.startsWith("feature/") ||
                !entry.forbiddenImport.startsWith("import com.astrbot.android.feature.") ||
                entry.forbiddenImport.contains("*")
        }

        assertTrue(
            "Feature import allowlist contains malformed entries: $malformedEntries",
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
            "Feature import allowlist points to missing production files: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun allowlist_entries_must_still_match_current_feature_import_debt() {
        val currentDebt = findForbiddenFeatureImports()
            .map { usage -> usage.path to usage.forbiddenImport }
            .toSet()
        val staleEntries = allowlist.entries.filterNot { entry ->
            entry.path to entry.forbiddenImport in currentDebt
        }

        assertTrue(
            "Feature import allowlist has stale entries with no matching current import: $staleEntries",
            staleEntries.isEmpty(),
        )
    }

    @Test
    fun feature_modules_must_not_add_unallowlisted_cross_feature_data_or_domain_imports() {
        val violations = findForbiddenFeatureImports()
            .filterNot { usage -> allowlist.isAllowed(usage.path, usage.forbiddenImport) }

        assertTrue(
            "Feature modules must not add unallowlisted cross-feature data imports or domain imports. Found: $violations",
            violations.isEmpty(),
        )
    }

    private fun findForbiddenFeatureImports(): List<ForbiddenFeatureImport> {
        return featureDirectories().flatMap { featureDirectory ->
            val sourceFeature = featureDirectory.fileName.toString()
            scannedSegments.flatMap { segment ->
                val segmentRoot = featureDirectory.resolve(segment)
                if (!segmentRoot.exists()) {
                    emptyList()
                } else {
                    kotlinFilesUnder(segmentRoot).flatMap { file ->
                        val relative = relativePath(file)
                        file.readLines().mapNotNull { line ->
                            forbiddenImportOrNull(
                                line = line,
                                path = relative,
                                sourceFeature = sourceFeature,
                                sourceSegment = segment,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun forbiddenImportOrNull(
        line: String,
        path: String,
        sourceFeature: String,
        sourceSegment: String,
    ): ForbiddenFeatureImport? {
        val trimmed = line.trim()
        val match = featureImportRegex.matchEntire(trimmed) ?: return null
        val targetFeature = match.groupValues[1]
        val targetRemainder = match.groupValues[2]

        if (targetFeature == sourceFeature) {
            return null
        }

        val forbidden = targetRemainder.startsWith("data.") || sourceSegment == "domain"
        return if (forbidden) {
            ForbiddenFeatureImport(
                path = path,
                forbiddenImport = trimmed,
                sourceFeature = sourceFeature,
                targetFeature = targetFeature,
            )
        } else {
            null
        }
    }

    private fun loadAllowlist(): FeatureImportAllowlist {
        if (!allowlistFile.exists()) {
            return FeatureImportAllowlist(emptyList())
        }

        val entries = allowlistFile.readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                require(parts.size == 7) {
                    "Invalid feature import allowlist entry at line ${index + 1}: $line"
                }
                FeatureImportAllowlistEntry(
                    path = parts[0],
                    forbiddenImport = parts[1],
                    owner = parts[2],
                    target = parts[3],
                    reason = parts[4],
                    expires = parts[5],
                    issue = parts[6],
                )
            }
            .toList()

        return FeatureImportAllowlist(entries)
    }

    private fun featureDirectories(): List<Path> {
        return Files.list(featureRoot).use { stream ->
            stream
                .filter { path -> path.isDirectory() }
                .toList()
        }
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

    private data class FeatureImportAllowlist(
        val entries: List<FeatureImportAllowlistEntry>,
    ) {
        fun isAllowed(path: String, forbiddenImport: String): Boolean {
            return entries.any { entry -> entry.path == path && entry.forbiddenImport == forbiddenImport }
        }
    }

    private data class FeatureImportAllowlistEntry(
        val path: String,
        val forbiddenImport: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )

    private data class ForbiddenFeatureImport(
        val path: String,
        val forbiddenImport: String,
        val sourceFeature: String,
        val targetFeature: String,
    )

    private companion object {
        private val featureImportRegex =
            Regex("""import\s+com\.astrbot\.android\.feature\.([A-Za-z0-9_]+)\.(.+)""")
    }
}
