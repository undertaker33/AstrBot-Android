package com.astrbot.android.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppUiTransitionState {
    private val _transitionEvents = MutableStateFlow(0L)
    val transitionEvents = _transitionEvents.asStateFlow()

    @Volatile
    private var activeColor: Int? = null

    fun requestTransition(colorArgb: Int) {
        activeColor = colorArgb
        _transitionEvents.value += 1
    }

    fun currentColor(): Int? = activeColor

    fun clearColor(colorArgb: Int?) {
        if (colorArgb == null) return
        if (activeColor == colorArgb) {
            activeColor = null
        }
    }
}
