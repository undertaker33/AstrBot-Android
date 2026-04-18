package com.astrbot.android.ui.chat

object SessionSwipeSizing {
    const val actionWidthDp: Int = 66
    const val actionSpacingDp: Int = 6
    const val peekWidthDp: Int = 10

    fun revealWidthDp(actionCount: Int): Int {
        if (actionCount <= 0) return 0
        return actionCount * actionWidthDp + (actionCount - 1) * actionSpacingDp + peekWidthDp
    }
}
