package com.elymbot.android.feature.persona.presentation

import com.elymbot.android.feature.persona.domain.PersonaCoverDraft
import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import com.elymbot.android.feature.persona.domain.model.normalizePersonaTags

enum class PersonaEditorPanelState { EXPANDED, COLLAPSED }

data class PersonaEditorState(val panelState: PersonaEditorPanelState = PersonaEditorPanelState.EXPANDED)

fun settlePersonaPanel(current: PersonaEditorPanelState, fractionTowardCollapsed: Float, velocityY: Float): PersonaEditorPanelState = when {
    velocityY > 900f -> PersonaEditorPanelState.COLLAPSED
    velocityY < -900f -> PersonaEditorPanelState.EXPANDED
    fractionTowardCollapsed >= .5f -> PersonaEditorPanelState.COLLAPSED
    fractionTowardCollapsed <= .5f -> PersonaEditorPanelState.EXPANDED
    else -> current
}

data class PersonaEditorForm(val name: String, val tagsInput: String, val systemPrompt: String, val maxContextMessages: Int, val enabled: Boolean)
data class ValidPersonaEditorForm(val name: String, val tags: List<String>, val systemPrompt: String, val maxContextMessages: Int, val enabled: Boolean)

fun validatePersonaEditor(form: PersonaEditorForm): Result<ValidPersonaEditorForm> = runCatching {
    require(form.name.isNotBlank()) { "人格名称不能为空" }
    require(form.maxContextMessages in 1..200) { "上下文消息数须为 1–200" }
    ValidPersonaEditorForm(form.name.trim(), normalizePersonaTags(form.tagsInput), form.systemPrompt, form.maxContextMessages, form.enabled)
}

fun applyCropGesture(
    current: PersonaCropSpec,
    panX: Float,
    panY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    zoomChange: Float,
): PersonaCropSpec {
    if (!panX.isFinite() || !panY.isFinite() || !viewportWidth.isFinite() || !viewportHeight.isFinite() ||
        viewportWidth <= 0f || viewportHeight <= 0f || !zoomChange.isFinite() || zoomChange <= 0f
    ) return current
    return PersonaCropSpec(
        centerX = (current.centerX - panX / viewportWidth / current.zoom).coerceIn(0f, 1f),
        centerY = (current.centerY - panY / viewportHeight / current.zoom).coerceIn(0f, 1f),
        zoom = (current.zoom * zoomChange).coerceIn(0.1f, 10f),
    )
}

data class PersonaCoverRenderSpec(val biasX: Float, val biasY: Float, val zoom: Float)

fun personaCoverRenderSpec(crop: PersonaCropSpec?) = (crop ?: PersonaCropSpec()).let {
    PersonaCoverRenderSpec(it.centerX * 2f - 1f, it.centerY * 2f - 1f, it.zoom)
}

sealed interface PersonaCoverCropFlow {
    data object Idle : PersonaCoverCropFlow
    data class Portrait(val draft: PersonaCoverDraft, val cropSpec: PersonaCropSpec = PersonaCropSpec()) : PersonaCoverCropFlow {
        fun confirmPortrait() = Square(draft, cropSpec)
    }
    data class Square(val draft: PersonaCoverDraft, val portraitCrop: PersonaCropSpec, val squareCrop: PersonaCropSpec = PersonaCropSpec()) : PersonaCoverCropFlow {
        fun updateSquare(value: PersonaCropSpec) = copy(squareCrop = value)
    }
    data class Committing(val draftId: String) : PersonaCoverCropFlow
    data class Failure(
        val draft: PersonaCoverDraft,
        val portraitCrop: PersonaCropSpec,
        val squareCrop: PersonaCropSpec,
        val message: String,
    ) : PersonaCoverCropFlow {
        fun retry() = Square(draft, portraitCrop, squareCrop)
        val draftIdToDiscard: String get() = draft.draftId
    }
}
