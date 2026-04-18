package com.astrbot.android.feature.chat.runtime.botcommand

data class ParsedBotCommand(
    val name: String,
    val arguments: List<String>,
    val rawArguments: String,
)

object BotCommandParser {
    fun parse(input: String): ParsedBotCommand? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) return null
        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val commandName = parts.firstOrNull()?.removePrefix("/")?.lowercase().orEmpty()
        if (commandName.isBlank()) return null
        return ParsedBotCommand(
            name = commandName,
            arguments = parts.drop(1),
            rawArguments = trimmed.substringAfter(' ', "").trim(),
        )
    }
}
