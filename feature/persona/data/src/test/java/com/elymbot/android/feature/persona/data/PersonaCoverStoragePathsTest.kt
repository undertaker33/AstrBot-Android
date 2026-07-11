package com.elymbot.android.feature.persona.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaCoverStoragePathsTest {
    @Test fun `resolved assets cannot escape the controlled root`() {
        val base = Files.createTempDirectory("covers").toFile()
        val paths = PersonaCoverStoragePaths(base)
        assertThrows(IllegalArgumentException::class.java) { paths.resolveAsset("../outside.webp") }
        assertThrows(IllegalArgumentException::class.java) { paths.resolveAsset("persona/../../outside.webp") }
    }

    @Test fun `symlink beneath root is rejected`() {
        val base = Files.createTempDirectory("covers").toFile()
        val outside = Files.createTempDirectory("outside").toFile()
        val link = File(base, "link").toPath()
        runCatching { Files.createSymbolicLink(link, outside.toPath()) }.getOrElse { return }
        val paths = PersonaCoverStoragePaths(base)
        assertThrows(IllegalArgumentException::class.java) { paths.resolveAsset("link/file.webp") }
    }

    @Test fun `draft and asset names stay opaque and controlled`() {
        val root = Files.createTempDirectory("covers").toFile()
        val paths = PersonaCoverStoragePaths(root)
        assertTrue(paths.newDraftFile("persona").canonicalPath.startsWith(root.canonicalPath))
        assertTrue(paths.newAssetFile("persona").canonicalPath.startsWith(root.canonicalPath))
        assertFalse(paths.newAssetFile("persona").name.contains("persona"))
    }
}
