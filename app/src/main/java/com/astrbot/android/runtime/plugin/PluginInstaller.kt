package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginInstallStore
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginSource
import java.io.File
import java.util.zip.ZipInputStream

class PluginInstaller(
    private val validator: PluginPackageValidator,
    private val storagePaths: PluginStoragePaths,
    private val installStore: PluginInstallStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun installFromLocalPackage(packageFile: File): PluginInstallRecord {
        val validation = validator.validate(packageFile)
        check(validation.compatibilityState.isCompatible()) {
            "Plugin package is incompatible with the current host."
        }

        val existing = installStore.findByPluginId(validation.manifest.pluginId)
        if (existing != null) {
            val versionComparison = compareVersions(validation.manifest.version, existing.installedVersion)
            when {
                versionComparison == 0 -> error("Plugin ${validation.manifest.pluginId} is already installed.")
                versionComparison < 0 -> error("Plugin ${validation.manifest.pluginId} version is not an upgrade.")
            }
        }

        storagePaths.ensureBaseDirectories()
        val now = clock()
        val storedPackage = storagePaths.packageFile(buildStoredPackageName(validation.manifest.pluginId, validation.manifest.version))
        storedPackage.parentFile?.mkdirs()
        val extractedDir = storagePaths.extractedDir(validation.manifest.pluginId)
        val stagingExtractedDir = File(
            extractedDir.parentFile ?: storagePaths.extractedRootDir,
            "${validation.manifest.pluginId}.staging",
        )
        cleanupPath(stagingExtractedDir)
        try {
            packageFile.copyTo(storedPackage, overwrite = true)
            stagingExtractedDir.mkdirs()
            extractPackage(storedPackage, stagingExtractedDir)
            cleanupPath(extractedDir)
            if (!stagingExtractedDir.renameTo(extractedDir)) {
                throw IllegalStateException("Failed to finalize extracted plugin resources.")
            }
        } catch (error: Throwable) {
            cleanupPath(stagingExtractedDir)
            if (!existingPackageMatches(storedPackage, existing?.localPackagePath)) {
                storedPackage.delete()
            }
            throw error
        }

        val record = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = validation.manifest,
            source = PluginSource(
                sourceType = validation.manifest.sourceType,
                location = packageFile.absolutePath,
                importedAt = now,
            ),
            permissionSnapshot = validation.manifest.permissions,
            compatibilityState = validation.compatibilityState,
            uninstallPolicy = existing?.uninstallPolicy ?: PluginInstallRecord.installFromManifest(
                manifestSnapshot = validation.manifest,
                source = PluginSource(
                    sourceType = validation.manifest.sourceType,
                    location = packageFile.absolutePath,
                    importedAt = now,
                ),
            ).uninstallPolicy,
            enabled = existing?.enabled ?: false,
            installedAt = existing?.installedAt ?: now,
            lastUpdatedAt = now,
            localPackagePath = storedPackage.absolutePath,
            extractedDir = extractedDir.absolutePath,
        )
        installStore.upsert(record)
        return record
    }

    private fun buildStoredPackageName(pluginId: String, version: String): String {
        val sanitizedPluginId = pluginId.replace(Regex("[^A-Za-z0-9._-]+"), "_")
        val sanitizedVersion = version.replace(Regex("[^A-Za-z0-9._-]+"), "_")
        return "$sanitizedPluginId-$sanitizedVersion.zip"
    }

    private fun extractPackage(packageFile: File, outputDir: File) {
        val basePath = outputDir.canonicalPath + File.separator
        ZipInputStream(packageFile.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val normalizedName = normalizeArchiveEntryName(entry.name)
                if (normalizedName.isNotBlank()) {
                    val target = File(outputDir, normalizedName)
                    val targetPath = target.canonicalPath
                    check(targetPath == outputDir.canonicalPath || targetPath.startsWith(basePath)) {
                        "Blocked unsafe plugin archive entry: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                entry = input.nextEntry
            }
        }
    }

    private fun cleanupPath(path: File) {
        if (path.exists()) {
            path.deleteRecursively()
        }
    }

    private fun existingPackageMatches(candidate: File, existingPath: String?): Boolean {
        return existingPath?.let { File(it).absolutePath == candidate.absolutePath } == true
    }
}
