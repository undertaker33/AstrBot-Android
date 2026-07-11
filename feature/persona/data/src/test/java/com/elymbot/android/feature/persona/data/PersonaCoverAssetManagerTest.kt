package com.elymbot.android.feature.persona.data

import com.elymbot.android.feature.persona.domain.model.PersonaCoverMetadata
import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaCoverAssetManagerTest {
    private fun fixture(store: FakeStore = FakeStore()): Triple<PersonaCoverAssetManager, FakeImporter, File> {
        val files = Files.createTempDirectory("persona-files").toFile()
        val importer = FakeImporter()
        return Triple(PersonaCoverAssetManager(PersonaCoverStoragePaths(files), importer, store), importer, files)
    }

    @Test fun `source is read only and failed import leaves no draft`() {
        val (manager, importer, root) = fixture()
        importer.failure = true
        assertThrows(IllegalStateException::class.java) { manager.stageImport("p", "content://cloud/image") }
        assertEquals(listOf("content://cloud/image"), importer.readUris)
        assertTrue(root.walkTopDown().filter(File::isFile).none())
    }

    @Test fun `import rejects undecodable and oversized normalized output`() {
        val (manager, importer) = fixture()
        importer.result = ImportedPersonaCover(0, 10, "x")
        assertThrows(IllegalArgumentException::class.java) { manager.stageImport("p", "content://bad") }
        importer.result = ImportedPersonaCover(2049, 100, "x")
        assertThrows(IllegalArgumentException::class.java) { manager.stageImport("p", "content://large") }
    }

    @Test fun `commit publishes then updates room then removes old private asset`() {
        val old = "p/old.webp"
        val store = FakeStore(old)
        val (manager, _, root) = fixture(store)
        File(root, old).apply { requireNotNull(parentFile).mkdirs(); writeText("old") }
        val draft = manager.stageImport("p", "content://image")
        val metadata = manager.commit(draft.draftId, PersonaCropSpec(), PersonaCropSpec())
        assertEquals(listOf("update", "delete-old"), store.events)
        assertFalse(File(root, old).exists())
        assertTrue(manager.resolveReadableFile(metadata.assetRef) != null)
    }

    @Test fun `room failure preserves readable draft for retry then successful commit cleans draft`() {
        val old = "p/old.webp"
        val store = FakeStore(old).apply { remainingUpdateFailures = 1 }
        val (manager, _, root) = fixture(store)
        File(root, old).apply { requireNotNull(parentFile).mkdirs(); writeText("old") }
        val draft = manager.stageImport("p", "content://image")
        val draftFile = File(root, draft.previewAssetRef)
        assertThrows(IllegalStateException::class.java) { manager.commit(draft.draftId, PersonaCropSpec(), PersonaCropSpec()) }
        assertTrue(File(root, old).exists())
        assertEquals(old, store.current)
        assertTrue(draftFile.isFile)
        assertEquals("normalized", draftFile.readText())
        assertEquals(2, root.walkTopDown().count(File::isFile))

        val committed = manager.commit(draft.draftId, PersonaCropSpec(), PersonaCropSpec())

        assertFalse(draftFile.exists())
        assertFalse(File(root, old).exists())
        assertEquals(committed.assetRef, store.current)
        assertEquals("normalized", File(root, committed.assetRef).readText())
    }

    @Test fun `discard after room failure removes preserved draft`() {
        val old = "p/old.webp"
        val store = FakeStore(old).apply { remainingUpdateFailures = 1 }
        val (manager, _, root) = fixture(store)
        File(root, old).apply { requireNotNull(parentFile).mkdirs(); writeText("old") }
        val draft = manager.stageImport("p", "content://image")
        val draftFile = File(root, draft.previewAssetRef)
        assertThrows(IllegalStateException::class.java) { manager.commit(draft.draftId, PersonaCropSpec(), PersonaCropSpec()) }

        manager.discard(draft.draftId)

        assertFalse(draftFile.exists())
        assertTrue(File(root, old).exists())
        assertEquals(old, store.current)
        assertEquals(1, root.walkTopDown().count(File::isFile))
    }

    @Test fun `discard and persona delete touch only managed copies`() {
        val store = FakeStore()
        val (manager, importer, root) = fixture(store)
        val draft = manager.stageImport("p", "content://user/original")
        manager.discard(draft.draftId)
        assertNull(manager.resolveReadableFile(draft.previewAssetRef))
        val committed = manager.commit(manager.stageImport("p", "content://user/original").draftId, PersonaCropSpec(), PersonaCropSpec())
        manager.deleteForPersona("p")
        assertNull(manager.resolveReadableFile(committed.assetRef))
        assertEquals(2, importer.readUris.size)
        assertTrue(root.exists())
    }

    @Test fun `orphan cleanup honors grace and valid refs`() {
        var now = 10_000L
        val store = FakeStore("p/valid.webp")
        val (manager0, importer, root) = fixture(store)
        val manager = PersonaCoverAssetManager(PersonaCoverStoragePaths(root), importer, store) { now }
        val valid = File(root, "p/valid.webp").apply { requireNotNull(parentFile).mkdirs(); writeText("v"); setLastModified(1) }
        val old = File(root, "p/old.webp").apply { writeText("o"); setLastModified(1) }
        val fresh = File(root, "p/fresh.webp").apply { writeText("f"); setLastModified(now) }
        assertEquals(1, manager.cleanupOrphans(1_000L))
        assertTrue(valid.exists()); assertFalse(old.exists()); assertTrue(fresh.exists())
    }
}

private class FakeImporter : PersonaCoverSourceImporter {
    val readUris = mutableListOf<String>()
    var failure = false
    var result = ImportedPersonaCover(1200, 2048, "abc")
    override fun importReadOnly(sourceUriString: String, destination: File): ImportedPersonaCover {
        readUris += sourceUriString
        if (failure) throw IllegalStateException("read failed")
        requireNotNull(destination.parentFile).mkdirs(); destination.writeText("normalized")
        return result
    }
}

private class FakeStore(initial: String? = null) : PersonaCoverMetadataStore {
    var current = initial
    var remainingUpdateFailures = 0
    val events = mutableListOf<String>()
    override fun coverForPersona(personaId: String): PersonaCoverMetadata? = current?.let(::metadata)
    override fun updateCover(personaId: String, metadata: PersonaCoverMetadata?) {
        events += "update"
        if (remainingUpdateFailures > 0) {
            remainingUpdateFailures--
            throw IllegalStateException("room failed")
        }
        current = metadata?.assetRef
    }
    override fun validAssetRefs(): Set<String> = setOfNotNull(current)
    override fun onOldAssetDeleted() { events += "delete-old" }
    private fun metadata(ref: String) = PersonaCoverMetadata(ref, "x", 1, 1, PersonaCropSpec(), PersonaCropSpec(), 1)
}
