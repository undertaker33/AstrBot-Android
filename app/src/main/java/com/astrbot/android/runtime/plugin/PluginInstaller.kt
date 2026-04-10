package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginInstallStore
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.toSnapshot
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

fun interface RemotePluginPackageDownloader {
    suspend fun download(
        packageUrl: String,
        destinationFile: File,
        onProgress: (PluginDownloadProgress) -> Unit,
    )
}

class UrlConnectionRemotePluginPackageDownloader : RemotePluginPackageDownloader {
    override suspend fun download(
        packageUrl: String,
        destinationFile: File,
        onProgress: (PluginDownloadProgress) -> Unit,
    ) {
        val connection = (URL(packageUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            doInput = true
        }
        destinationFile.parentFile?.mkdirs()
        val totalBytes = connection.contentLengthLong
        val startedAtNanos = System.nanoTime()
        var bytesDownloaded = 0L
        onProgress(
            PluginDownloadProgress.downloading(
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                bytesPerSecond = 0L,
            ),
        )
        connection.inputStream.use { input ->
            destinationFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    bytesDownloaded += read
                    onProgress(
                        PluginDownloadProgress.downloading(
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            bytesPerSecond = calculateBytesPerSecond(
                                bytesDownloaded = bytesDownloaded,
                                startedAtNanos = startedAtNanos,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun calculateBytesPerSecond(bytesDownloaded: Long, startedAtNanos: Long): Long {
        val elapsedNanos = (System.nanoTime() - startedAtNanos).coerceAtLeast(1L)
        return (bytesDownloaded * 1_000_000_000L) / elapsedNanos
    }
}

class PluginInstaller(
    private val validator: PluginPackageValidator,
    private val storagePaths: PluginStoragePaths,
    private val installStore: PluginInstallStore,
    private val remotePackageDownloader: RemotePluginPackageDownloader = UrlConnectionRemotePluginPackageDownloader(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun installFromLocalPackage(packageFile: File): PluginInstallRecord {
        return installPackage(
            packageFile = packageFile,
            sourceType = PluginSourceType.LOCAL_FILE,
            sourceLocation = packageFile.absolutePath,
            installedPackageUrl = "",
            catalogSourceId = null,
            lastCatalogCheckAtEpochMillis = null,
        )
    }

    suspend fun install(
        intent: PluginInstallIntent.DirectPackageUrl,
        onProgress: (PluginDownloadProgress) -> Unit = {},
    ): PluginInstallRecord {
        return installFromRemotePackage(
            sourceType = PluginSourceType.DIRECT_LINK,
            packageUrl = intent.url,
            catalogSourceId = null,
            lastCatalogCheckAtEpochMillis = null,
            onProgress = onProgress,
        )
    }

    suspend fun install(
        intent: PluginInstallIntent.CatalogVersion,
        onProgress: (PluginDownloadProgress) -> Unit = {},
    ): PluginInstallRecord {
        return installFromRemotePackage(
            sourceType = PluginSourceType.REPOSITORY,
            packageUrl = intent.packageUrl,
            catalogSourceId = intent.catalogSourceId,
            lastCatalogCheckAtEpochMillis = clock(),
            onProgress = onProgress,
        )
    }

    suspend fun upgrade(availability: PluginUpdateAvailability): PluginInstallRecord {
        check(availability.updateAvailable) {
            "Plugin ${availability.pluginId} does not have an available update."
        }
        check(availability.canUpgrade) {
            availability.incompatibilityReason.ifBlank {
                "Plugin ${availability.pluginId} cannot be upgraded."
            }
        }
        val sourceId = availability.catalogSourceId?.takeIf { it.isNotBlank() }
            ?: error("Repository upgrades require a catalog source id.")
        return install(
            PluginInstallIntent.catalogVersion(
                pluginId = availability.pluginId,
                version = availability.latestVersion,
                packageUrl = availability.packageUrl,
                catalogSourceId = sourceId,
            ),
        )
    }

    private suspend fun installFromRemotePackage(
        sourceType: PluginSourceType,
        packageUrl: String,
        catalogSourceId: String?,
        lastCatalogCheckAtEpochMillis: Long? = null,
        onProgress: (PluginDownloadProgress) -> Unit,
    ): PluginInstallRecord {
        storagePaths.ensureBaseDirectories()
        val tempFile = remoteTempFile(packageUrl)
        try {
            remotePackageDownloader.download(packageUrl, tempFile, onProgress)
            onProgress(
                PluginDownloadProgress.installing(
                    bytesDownloaded = tempFile.length(),
                    totalBytes = tempFile.length(),
                ),
            )
            return installPackage(
                packageFile = tempFile,
                sourceType = sourceType,
                sourceLocation = packageUrl,
                installedPackageUrl = packageUrl,
                catalogSourceId = catalogSourceId,
                lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun remoteTempFile(packageUrl: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(packageUrl.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(storagePaths.rootDir, "tmp/$digest.zip")
    }

    private fun installPackage(
        packageFile: File,
        sourceType: PluginSourceType,
        sourceLocation: String,
        installedPackageUrl: String,
        catalogSourceId: String?,
        lastCatalogCheckAtEpochMillis: Long?,
    ): PluginInstallRecord {
        val validation = validator.validate(packageFile)
        check(validation.installable) {
            if (!validation.compatibilityState.isCompatible()) {
                "Plugin package is incompatible with the current host."
            } else {
                "Plugin package is not installable."
            }
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
        val storedPackage = storagePaths.packageFile(
            buildStoredPackageName(validation.manifest.pluginId, validation.manifest.version),
        )
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

        val manifestSnapshot = validation.manifest.copy(sourceType = sourceType)
        val packageContractSnapshot = requireNotNull(validation.packageContract) {
            "Installable plugin package is missing package contract."
        }.toSnapshot()
        val record = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifestSnapshot,
            source = PluginSource(
                sourceType = sourceType,
                location = sourceLocation,
                importedAt = now,
            ),
            packageContractSnapshot = packageContractSnapshot,
            permissionSnapshot = validation.manifest.permissions,
            compatibilityState = validation.compatibilityState,
            uninstallPolicy = existing?.uninstallPolicy ?: PluginInstallRecord.installFromManifest(
                manifestSnapshot = manifestSnapshot,
                source = PluginSource(
                    sourceType = sourceType,
                    location = sourceLocation,
                    importedAt = now,
                ),
            ).uninstallPolicy,
            enabled = existing?.enabled ?: false,
            catalogSourceId = catalogSourceId,
            installedPackageUrl = installedPackageUrl,
            lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis ?: existing?.lastCatalogCheckAtEpochMillis,
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
