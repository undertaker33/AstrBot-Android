package com.astrbot.android.ui.chat

object SessionSwipeMotion {
    fun applyDrag(
        currentOffset: Float,
        dragAmount: Float,
        revealWidth: Float,
    ): Float {
        return (currentOffset + dragAmount).coerceIn(0f, revealWidth.coerceAtLeast(0f))
    }

    fun settleOffset(
        currentOffset: Float,
        revealWidth: Float,
    ): Float {
        val width = revealWidth.coerceAtLeast(0f)
        if (width == 0f) return 0f
        return if (currentOffset >= width / 3f) width else 0f
    }
}
