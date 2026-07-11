package com.elymbot.android.feature.persona.presentation

import com.elymbot.android.feature.persona.domain.model.PersonaCoverMetadata
import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PersonaCoverRenderPolicyTest {
    private val metadata = PersonaCoverMetadata(
        assetRef = "assets/persona-covers/p/a.png", contentSha256 = "sha",
        pixelWidth = 1200, pixelHeight = 1800,
        portraitCrop = PersonaCropSpec(.25f, .4f, 1.35f),
        squareCrop = PersonaCropSpec(.7f, .6f, 1.8f), updatedAt = 1L,
    )

    @Test fun `portrait and square render policies select independent crop specs including zoom`() {
        val portrait = personaCoverRenderPolicy(metadata, PersonaCoverViewport.PORTRAIT)
        val square = personaCoverRenderPolicy(metadata, PersonaCoverViewport.SQUARE)
        assertEquals(.25f, portrait.centerX)
        assertEquals(.4f, portrait.centerY)
        assertEquals(1.35f, portrait.scale)
        assertEquals(.7f, square.centerX)
        assertEquals(.6f, square.centerY)
        assertEquals(1.8f, square.scale)
        assertNotEquals(portrait.scale, square.scale)
    }

    @Test fun `square card remains one to one at different calculated widths`() {
        assertEquals(144f, squareCardHeight(144f))
        assertEquals(208f, squareCardHeight(208f))
    }
}
