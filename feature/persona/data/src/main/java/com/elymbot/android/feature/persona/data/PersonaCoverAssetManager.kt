package com.elymbot.android.feature.persona.data

import com.elymbot.android.feature.persona.domain.PersonaCoverAssetPort
import com.elymbot.android.feature.persona.domain.PersonaCoverDraft
import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import com.elymbot.android.feature.persona.domain.model.PersonaCoverMetadata
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedPersonaCover(val pixelWidth: Int, val pixelHeight: Int, val contentSha256: String)

interface PersonaCoverSourceImporter {
    fun importReadOnly(sourceUriString: String, destination: File): ImportedPersonaCover
}

interface PersonaCoverMetadataStore {
    fun coverForPersona(personaId: String): PersonaCoverMetadata?
    fun updateCover(personaId: String, metadata: PersonaCoverMetadata?)
    fun validAssetRefs(): Set<String>
    fun onOldAssetDeleted() = Unit
}

@Singleton
class PersonaCoverAssetManager @Inject constructor(
    private val paths: PersonaCoverStoragePaths,
    private val importer: PersonaCoverSourceImporter,
    private val metadataStore: PersonaCoverMetadataStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : PersonaCoverAssetPort {
    private data class Pending(val personaId: String, val file: File, val imported: ImportedPersonaCover)
    private val pending = mutableMapOf<String, Pending>()

    @Synchronized override fun stageImport(personaId: String, sourceUriString: String): PersonaCoverDraft {
        require(sourceUriString.isNotBlank())
        val file = paths.newDraftFile(personaId)
        return try {
            val imported = importer.importReadOnly(sourceUriString, file)
            require(file.isFile && file.length() > 0) { "Import produced no file" }
            require(imported.pixelWidth > 0 && imported.pixelHeight > 0) { "Image is not decodable" }
            require(maxOf(imported.pixelWidth, imported.pixelHeight) <= MAX_EDGE) { "Normalized image exceeds limit" }
            val id = UUID.randomUUID().toString()
            pending[id] = Pending(personaId, file, imported)
            PersonaCoverDraft(id, paths.relative(file), imported.pixelWidth, imported.pixelHeight)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    @Synchronized override fun commit(draftId: String, portraitCrop: PersonaCropSpec, squareCrop: PersonaCropSpec): PersonaCoverMetadata {
        val draft = pending[draftId] ?: error("Unknown cover draft")
        val old = metadataStore.coverForPersona(draft.personaId)
        val published = paths.newAssetFile(draft.personaId)
        requireNotNull(published.parentFile).mkdirs()
        draft.file.copyTo(published, overwrite = false)
        val metadata = PersonaCoverMetadata(paths.relative(published), draft.imported.contentSha256,
            draft.imported.pixelWidth, draft.imported.pixelHeight, portraitCrop, squareCrop, clock())
        try {
            metadataStore.updateCover(draft.personaId, metadata)
        } catch (error: Throwable) {
            published.delete()
            throw error
        }
        pending.remove(draftId)
        draft.file.delete()
        old?.assetRef?.takeIf { it != metadata.assetRef }?.let { oldRef ->
            runCatching { paths.resolveAsset(oldRef) }.getOrNull()?.takeIf(File::isFile)?.let {
                if (it.delete()) metadataStore.onOldAssetDeleted()
            }
        }
        return metadata
    }

    @Synchronized override fun discard(draftId: String) { pending.remove(draftId)?.file?.delete() }

    override fun deleteForPersona(personaId: String) {
        synchronized(this) {
            pending.entries.removeAll { (_, draft) ->
                if (draft.personaId == personaId) draft.file.delete()
                draft.personaId == personaId
            }
        }
        paths.personaDirectory(personaId).deleteRecursively()
    }

    override fun resolveReadableFile(assetRef: String): String? = runCatching { paths.resolveAsset(assetRef) }
        .getOrNull()?.takeIf { it.isFile && !paths.isSymlink(it) }?.absolutePath

    override fun cleanupOrphans(gracePeriodMillis: Long): Int {
        require(gracePeriodMillis >= 0)
        val valid = metadataStore.validAssetRefs()
        val cutoff = clock() - gracePeriodMillis
        var removed = 0
        paths.root.walkTopDown().filter(File::isFile).forEach { file ->
            val ref = runCatching { paths.relative(file) }.getOrNull() ?: return@forEach
            if (!paths.isSymlink(file) && ref !in valid && file.lastModified() < cutoff && file.delete()) removed++
        }
        return removed
    }

    private companion object { const val MAX_EDGE = 2048 }
}
