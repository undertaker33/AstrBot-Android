package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDbDaoOwnerContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val allowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/dao-owner-allowlist.txt")
    private val allowlist: DaoOwnerAllowlist = loadAllowlist()

    @Test
    fun allowlist_file_must_exist_and_be_well_formed() {
        assertTrue(
            "DAO owner allowlist file must exist: $allowlistFile",
            allowlistFile.exists(),
        )

        val malformedEntries = allowlist.entries.filter { entry ->
            entry.path.isBlank() ||
                entry.dao.isBlank() ||
                entry.owner.isBlank() ||
                entry.target.isBlank() ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                entry.path.contains("*") ||
                entry.path.endsWith("/") ||
                entry.path.startsWith("/") ||
                entry.dao.contains("*")
        }

        assertTrue(
            "DAO owner allowlist contains malformed entries: $malformedEntries",
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
            "DAO owner allowlist points to missing production files: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun feature_data_must_only_import_daos_owned_by_that_feature() {
        val violations = featureDataFiles()
            .flatMap { file ->
                val path = relativePath(file)
                val owner = featureOwner(path)
                daoImports(file).mapNotNull { dao ->
                    if (isDaoOwnedByFeature(owner, dao) || allowlist.isAllowed(path, dao)) {
                        null
                    } else {
                        "$path imports $dao outside $owner DAO ownership"
                    }
                }
            }

        assertTrue(
            "core-db can stay physically centralized, but feature/*/data must only import DAOs owned by that feature. " +
                "Add precise allowlist entries for existing cross-owner debt: $violations",
            violations.isEmpty(),
        )
    }

    private fun featureDataFiles(): List<Path> {
        val featureRoot = mainRoot.resolve("feature")
        if (!featureRoot.exists()) {
            return emptyList()
        }

        val roots = Files.list(featureRoot).use { stream ->
            stream
                .filter { path -> Files.isDirectory(path) }
                .map { path -> path.resolve("data") }
                .filter { path -> path.exists() }
                .toList()
        }

        return roots.flatMap { root ->
            Files.walk(root).use { stream ->
                stream
                    .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                    .toList()
            }
        }
    }

    private fun daoImports(file: Path): List<String> {
        return file.readLines()
            .asSequence()
            .map { line -> importRegex.matchEntire(line.trim())?.groupValues?.get(1) }
            .filterNotNull()
            .filter { importName -> importName.startsWith("com.astrbot.android.data.db.") }
            .map { importName -> importName.substringAfterLast('.') }
            .filter { importedName -> importedName.endsWith("Dao") }
            .toList()
    }

    private fun featureOwner(path: String): String {
        val parts = path.split("/")
        require(parts.size >= 3 && parts[0] == "feature") {
            "Expected feature data path relative to mainRoot, got $path"
        }
        return parts[1]
    }

    private fun isDaoOwnedByFeature(owner: String, dao: String): Boolean {
        if (dao == "AppPreferenceDao") {
            return true
        }

        val allowedPrefixes = ownerDaoPrefixes.getValue(owner)
        return allowedPrefixes.any { prefix -> dao.startsWith(prefix) }
    }

    private fun loadAllowlist(): DaoOwnerAllowlist {
        if (!allowlistFile.exists()) {
            return DaoOwnerAllowlist(emptyList())
        }

        val entries = allowlistFile.readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                require(parts.size == 7) {
                    "Invalid DAO owner allowlist entry at line ${index + 1}: $line"
                }
                DaoOwnerAllowlistEntry(
                    path = parts[0],
                    dao = parts[1],
                    owner = parts[2],
                    target = parts[3],
                    reason = parts[4],
                    expires = parts[5],
                    issue = parts[6],
                )
            }
            .toList()

        return DaoOwnerAllowlist(entries)
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

    private data class DaoOwnerAllowlist(
        val entries: List<DaoOwnerAllowlistEntry>,
    ) {
        fun isAllowed(path: String, dao: String): Boolean {
            return entries.any { entry -> entry.path == path && entry.dao == dao }
        }
    }

    private data class DaoOwnerAllowlistEntry(
        val path: String,
        val dao: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )

    private companion object {
        private val importRegex = Regex("""^import\s+([A-Za-z0-9_.*]+)$""")
        private val ownerDaoPrefixes = mapOf(
            "bot" to setOf("Bot"),
            "chat" to setOf("Conversation"),
            "config" to setOf("Config"),
            "cron" to setOf("Cron"),
            "persona" to setOf("Persona"),
            "plugin" to setOf("Plugin"),
            "provider" to setOf("Provider"),
            "qq" to setOf("Qq"),
            "resource" to setOf("ResourceCenter"),
        )
    }
}
