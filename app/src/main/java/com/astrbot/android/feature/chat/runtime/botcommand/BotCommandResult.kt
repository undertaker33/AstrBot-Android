package com.astrbot.android.feature.chat.runtime.botcommand

data class BotCommandResult(
    val handled: Boolean,
    val replyText: String? = null,
    val stopModelDispatch: Boolean = handled,
) {
    companion object {
        fun unhandled(): BotCommandResult = BotCommandResult(handled = false, stopModelDispatch = false)
    }
}
