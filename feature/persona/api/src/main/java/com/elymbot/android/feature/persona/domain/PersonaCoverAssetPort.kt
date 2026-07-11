package com.elymbot.android.feature.persona.domain

import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import com.elymbot.android.feature.persona.domain.model.PersonaCoverMetadata

data class PersonaCoverDraft(
    val draftId: String,
    val previewAssetRef: String,
    val pixelWidth: Int,
    val pixelHeight: Int,
)

interface PersonaCoverAssetPort {
    fun stageImport(personaId: String, sourceUriString: String): PersonaCoverDraft
    fun commit(draftId: String, portraitCrop: PersonaCropSpec, squareCrop: PersonaCropSpec): PersonaCoverMetadata
    fun discard(draftId: String)
    fun deleteForPersona(personaId: String)
    fun resolveReadableFile(assetRef: String): String?
    fun cleanupOrphans(gracePeriodMillis: Long): Int
}
