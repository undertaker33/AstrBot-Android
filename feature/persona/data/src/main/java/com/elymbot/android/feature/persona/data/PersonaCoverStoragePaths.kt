package com.elymbot.android.feature.persona.data

import java.io.File
import java.nio.file.Files
import java.util.UUID

class PersonaCoverStoragePaths(val root: File) {
    private val canonicalRoot: File get() = root.canonicalFile

    init { root.mkdirs() }

    fun newDraftFile(personaId: String): File = controlled(File(root, ".staging/${safeId(personaId)}/${UUID.randomUUID()}.png"))
    fun newAssetFile(personaId: String): File = controlled(File(root, "${safeId(personaId)}/${UUID.randomUUID()}.png"))
    fun personaDirectory(personaId: String): File = controlled(File(root, safeId(personaId)))
    fun resolveAsset(assetRef: String): File {
        require(assetRef.isNotBlank() && !File(assetRef).isAbsolute) { "Asset reference must be relative" }
        return controlled(File(root, assetRef))
    }
    fun relative(file: File): String = controlled(file).relativeTo(canonicalRoot).invariantSeparatorsPath
    fun isSymlink(file: File): Boolean = Files.isSymbolicLink(file.toPath())

    private fun controlled(file: File): File {
        val candidate = file.canonicalFile
        val prefix = canonicalRoot.path + File.separator
        require(candidate.path.startsWith(prefix)) { "Asset path escapes controlled root" }
        var cursor: File? = file.absoluteFile
        while (cursor != null && cursor.absolutePath != root.absoluteFile.absolutePath) {
            require(!Files.isSymbolicLink(cursor.toPath())) { "Symbolic links are not allowed" }
            cursor = cursor.parentFile
        }
        return candidate
    }

    private fun safeId(value: String): String {
        require(value.isNotBlank() && value.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid persona id" }
        return value
    }
}
