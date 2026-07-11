package com.elymbot.android.feature.persona.presentation

import com.elymbot.android.feature.persona.domain.PersonaCoverDraft
import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import org.junit.Assert.*
import org.junit.Test

class PersonaEditorStateTest {
    @Test fun `panel defaults expanded and snaps by distance or velocity`() {
        assertEquals(PersonaEditorPanelState.EXPANDED, PersonaEditorState().panelState)
        assertEquals(PersonaEditorPanelState.COLLAPSED, settlePersonaPanel(PersonaEditorPanelState.EXPANDED, .6f, 0f))
        assertEquals(PersonaEditorPanelState.EXPANDED, settlePersonaPanel(PersonaEditorPanelState.EXPANDED, .2f, 0f))
        assertEquals(PersonaEditorPanelState.COLLAPSED, settlePersonaPanel(PersonaEditorPanelState.EXPANDED, .1f, 1400f))
        assertEquals(PersonaEditorPanelState.EXPANDED, settlePersonaPanel(PersonaEditorPanelState.COLLAPSED, .9f, -1400f))
    }

    @Test fun `form exposes five fields and normalizes at most three tags`() {
        val result = validatePersonaEditor(PersonaEditorForm(" Elym ", "温柔，可靠, 日常, 多余", "prompt", 12, true))
        assertEquals(listOf("温柔", "可靠", "日常"), result.getOrThrow().tags)
        assertTrue(validatePersonaEditor(PersonaEditorForm(" ", "", "", 12, true)).isFailure)
        assertTrue(validatePersonaEditor(PersonaEditorForm("E", "", "", 0, true)).isFailure)
    }

    @Test fun `crop steps share draft but retain independent specs`() {
        val draft = PersonaCoverDraft("d", "preview", 1000, 1600)
        val portrait = PersonaCropSpec(.4f, .3f, 1.2f)
        val square = PersonaCropSpec(.7f, .6f, 2f)
        val p = PersonaCoverCropFlow.Portrait(draft, portrait)
        val s = p.confirmPortrait().updateSquare(square)
        assertEquals(draft, s.draft)
        assertEquals(portrait, s.portraitCrop)
        assertEquals(square, s.squareCrop)
    }

    @Test fun `pinch and pan produce finite bounded crop for portrait and square`() {
        val start = PersonaCropSpec(.5f, .5f, 1f)
        val portrait = applyCropGesture(start, panX = 80f, panY = -40f, viewportWidth = 400f, viewportHeight = 600f, zoomChange = 1.5f)
        val square = applyCropGesture(start, panX = -100f, panY = 60f, viewportWidth = 400f, viewportHeight = 400f, zoomChange = 2f)
        assertEquals(1.5f, portrait.zoom)
        assertEquals(2f, square.zoom)
        listOf(portrait, square).forEach { assertTrue(it.centerX.isFinite()); assertTrue(it.centerY.isFinite()); assertTrue(it.centerX in 0f..1f); assertTrue(it.centerY in 0f..1f) }
        assertEquals(start, applyCropGesture(start, Float.NaN, Float.POSITIVE_INFINITY, 0f, 0f, Float.NaN))
    }

    @Test fun `editor portrait render consumes persisted center and zoom`() {
        val render = personaCoverRenderSpec(PersonaCropSpec(.2f, .8f, 2.4f))
        assertEquals(-.6f, render.biasX, .0001f)
        assertEquals(.6f, render.biasY, .0001f)
        assertEquals(2.4f, render.zoom, .0001f)
    }

    @Test fun `commit failure retains draft crops for retry and discard`() {
        val draft = PersonaCoverDraft("draft-9", "preview", 800, 1200)
        val portrait = PersonaCropSpec(.3f, .4f, 1.5f)
        val square = PersonaCropSpec(.7f, .6f, 2f)
        val failure = PersonaCoverCropFlow.Failure(draft, portrait, square, "disk")
        assertEquals(PersonaCoverCropFlow.Square(draft, portrait, square), failure.retry())
        assertEquals("draft-9", failure.draftIdToDiscard)
    }
}
