package com.astrbot.android.runtime.botcommand

import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile

object BotCommandResources {
    internal enum class CommandLanguage {
        ZH,
        EN,
    }

    fun help(languageTag: String): String {
        val language = resolveLanguage(languageTag)
        return buildString {
            appendLine("AstrBot V0.3.5")
            BotCommandCatalog.sections.forEachIndexed { index, section ->
                appendLine(
                    when (language) {
                        CommandLanguage.ZH -> section.zhTitle
                        CommandLanguage.EN -> section.enTitle
                    } + ":",
                )
                section.entries.forEach { entry ->
                    appendLine(
                        when (language) {
                            CommandLanguage.ZH -> "${entry.command} - ${entry.zhDescription}"
                            CommandLanguage.EN -> "${entry.command} - ${entry.enDescription}"
                        },
                    )
                }
                if (index != BotCommandCatalog.sections.lastIndex) {
                    appendLine()
                }
            }
        }.trim()
    }

    fun sttToggled(enabled: Boolean, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> if (enabled) "STT 已开启（当前会话）" else "STT 已关闭（当前会话）"
            CommandLanguage.EN -> if (enabled) "STT enabled for this conversation." else "STT disabled for this conversation."
        }
    }

    fun ttsToggled(enabled: Boolean, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> if (enabled) "TTS 已开启（当前会话）" else "TTS 已关闭（当前会话）"
            CommandLanguage.EN -> if (enabled) "TTS enabled for this conversation." else "TTS disabled for this conversation."
        }
    }

    fun reset(languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "当前会话已重置"
            CommandLanguage.EN -> "Current conversation has been reset."
        }
    }

    fun emptyAgentList(languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "当前 agent 列表为空"
            CommandLanguage.EN -> "The current agent list is empty."
        }
    }

    fun unsupportedCommand(
        commandName: String,
        languageTag: String,
    ): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "指令 /$commandName 暂未实现"
            CommandLanguage.EN -> "Command /$commandName is not implemented yet."
        }
    }

    fun adminGranted(uid: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "已授权管理员$uid"
            CommandLanguage.EN -> "Admin authorization granted for $uid."
        }
    }

    fun adminRevoked(uid: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "已取消管理员$uid 的授权"
            CommandLanguage.EN -> "Admin authorization revoked for $uid."
        }
    }

    fun whitelistAdded(umo: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "已将 $umo 添加至白名单"
            CommandLanguage.EN -> "Added $umo to the whitelist."
        }
    }

    fun whitelistRemoved(umo: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "已将 $umo 移出白名单"
            CommandLanguage.EN -> "Removed $umo from the whitelist."
        }
    }

    fun sessionSwitched(sessionId: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "已切换至 $sessionId"
            CommandLanguage.EN -> "Switched to $sessionId."
        }
    }

    fun sessionCreated(sessionId: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "新会话已创建，会话ID：$sessionId"
            CommandLanguage.EN -> "A new conversation has been created. Session ID: $sessionId"
        }
    }

    fun sessionDeleted(languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "当前会话已删除"
            CommandLanguage.EN -> "The current conversation has been deleted."
        }
    }

    fun sessionRenamed(title: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "当前会话已重命名为 $title"
            CommandLanguage.EN -> "The current conversation has been renamed to $title."
        }
    }

    fun sessionIndexNotFound(index: Int, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "未找到序号为 $index 的会话"
            CommandLanguage.EN -> "No conversation found for index $index."
        }
    }

    fun providerSummary(
        currentProvider: ProviderProfile?,
        providers: List<ProviderProfile>,
        languageTag: String,
    ): String {
        val enabledProviders = providers.filter { it.enabled }
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> buildString {
                appendLine("褰撳墠 Provider: ${currentProvider?.name ?: "鏈厤缃?"}")
                appendLine("鍙敤 Provider:")
                enabledProviders.forEach { provider ->
                    val suffix = if (provider.id == currentProvider?.id) "锛堝綋鍓嶏級" else ""
                    appendLine("- ${provider.name} / ${provider.model}$suffix")
                }
            }.trim()

            CommandLanguage.EN -> buildString {
                appendLine("Current provider: ${currentProvider?.name ?: "Not configured"}")
                appendLine("Available providers:")
                enabledProviders.forEach { provider ->
                    val suffix = if (provider.id == currentProvider?.id) " (current)" else ""
                    appendLine("- ${provider.name} / ${provider.model}$suffix")
                }
            }.trim()
        }
    }

    fun providerNotFound(argument: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "鏈壘鍒?Provider锛?$argument"
            CommandLanguage.EN -> "Provider not found: $argument"
        }
    }

    fun providerSwitched(providerName: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "宸插垏鎹?Provider 涓?${providerName}"
            CommandLanguage.EN -> "Switched provider to $providerName."
        }
    }

    fun noActiveProvider(languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "褰撳墠浼氳瘽鏈厤缃?Provider"
            CommandLanguage.EN -> "No active provider is configured for this conversation."
        }
    }

    fun modelUpdated(providerName: String, model: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "宸插皢 ${providerName} 鐨勬ā鍨嬫洿鏂颁负 $model"
            CommandLanguage.EN -> "Model updated to $model for provider $providerName."
        }
    }

    fun apiKeyUpdated(providerName: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "宸插皢 ${providerName} 鐨?API Key 鏇存柊"
            CommandLanguage.EN -> "API key updated for provider $providerName."
        }
    }

    fun personaHelp(languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> listOf(
                "人格指令：",
                "/persona list - 查看所有人格并标注当前人格",
                "/persona <name> - 切换当前会话人格",
                "/persona view <name> - 查看人格系统提示词",
            ).joinToString("\n")

            CommandLanguage.EN -> listOf(
                "Persona commands:",
                "/persona list - List all personas and mark the current one",
                "/persona <name> - Switch the persona for this conversation",
                "/persona view <name> - Show the persona system prompt",
            ).joinToString("\n")
        }
    }

    fun personaList(
        personas: List<PersonaProfile>,
        currentPersonaId: String?,
        languageTag: String,
    ): String {
        val language = resolveLanguage(languageTag)
        if (personas.isEmpty()) {
            return when (language) {
                CommandLanguage.ZH -> "当前没有可用人格"
                CommandLanguage.EN -> "No personas available."
            }
        }
        return buildString {
            appendLine(
                when (language) {
                    CommandLanguage.ZH -> "可用人格："
                    CommandLanguage.EN -> "Available personas:"
                },
            )
            personas.forEach { persona ->
                val suffix = if (persona.id == currentPersonaId) {
                    when (language) {
                        CommandLanguage.ZH -> "（当前）"
                        CommandLanguage.EN -> " (current)"
                    }
                } else {
                    ""
                }
                appendLine("- ${persona.name}$suffix")
            }
        }.trim()
    }

    fun personaNotFound(languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "未找到对应人格，可先使用 /persona list 查看可用人格"
            CommandLanguage.EN -> "Persona not found. Use /persona list to check available personas."
        }
    }

    fun personaView(persona: PersonaProfile, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "人格 ${persona.name} 的系统提示词：\n${persona.systemPrompt}"
            CommandLanguage.EN -> "Persona ${persona.name} system prompt:\n${persona.systemPrompt}"
        }
    }

    fun personaSwitched(personaName: String, languageTag: String): String {
        return when (resolveLanguage(languageTag)) {
            CommandLanguage.ZH -> "人格已切换为 ${personaName}，如需清空旧上下文请继续输入 /reset"
            CommandLanguage.EN -> "Persona switched to $personaName. Use /reset to clear old context if needed."
        }
    }

    fun sid(context: BotCommandContext): String {
        val language = resolveLanguage(context.languageTag)
        val uid = context.sourceUid.ifBlank {
            when (language) {
                CommandLanguage.ZH -> "N/A"
                CommandLanguage.EN -> "N/A"
            }
        }
        return buildString {
            when (language) {
                CommandLanguage.ZH -> {
                    appendLine("UMO: `${context.bot.id}:${context.messageType.name}:${umoSessionToken(context.session)}`")
                    appendLine("UID: `$uid`")
                    appendLine("消息会话来源信息:")
                    appendLine("  机器人 ID: `${context.bot.id}`")
                    appendLine("  消息类型: `${sessionTypeLabel(context.session, context.languageTag)}`")
                    appendLine("  会话 ID: `${context.sessionId}`")
                    append("消息来源可用于配置机器人的配置文件路由。")
                }

                CommandLanguage.EN -> {
                    appendLine("UMO: `${context.bot.id}:${context.messageType.name}:${umoSessionToken(context.session)}`")
                    appendLine("UID: `$uid`")
                    appendLine("Session source info:")
                    appendLine("  Bot ID: `${context.bot.id}`")
                    appendLine("  Message type: `${sessionTypeLabel(context.session, context.languageTag)}`")
                    appendLine("  Session ID: `${context.sessionId}`")
                    append("This source info can be used for bot routing configuration.")
                }
            }
        }
    }

    internal fun resolveLanguage(languageTag: String): CommandLanguage {
        return if (languageTag.startsWith("en", ignoreCase = true)) CommandLanguage.EN else CommandLanguage.ZH
    }
}
