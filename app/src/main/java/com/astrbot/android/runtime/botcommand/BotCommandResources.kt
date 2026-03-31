package com.astrbot.android.runtime.botcommand

import androidx.annotation.StringRes
import com.astrbot.android.AppStrings
import com.astrbot.android.R
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile

object BotCommandResources {
    internal enum class CommandLanguage {
        ZH,
        EN,
    }

    fun help(languageTag: String): String {
        return buildString {
            appendLine(text(languageTag, R.string.bot_command_help_version))
            BotCommandCatalog.sections.forEachIndexed { index, section ->
                appendLine("${text(languageTag, section.titleResId)}:")
                section.entries.forEach { entry ->
                    appendLine("${entry.command} - ${text(languageTag, entry.descriptionResId)}")
                }
                if (index != BotCommandCatalog.sections.lastIndex) {
                    appendLine()
                }
            }
        }.trim()
    }

    fun sttToggled(enabled: Boolean, languageTag: String): String {
        return text(
            languageTag,
            if (enabled) R.string.bot_command_stt_enabled else R.string.bot_command_stt_disabled,
        )
    }

    fun ttsToggled(enabled: Boolean, languageTag: String): String {
        return text(
            languageTag,
            if (enabled) R.string.bot_command_tts_enabled else R.string.bot_command_tts_disabled,
        )
    }

    fun reset(languageTag: String): String = text(languageTag, R.string.bot_command_reset)

    fun emptyAgentList(languageTag: String): String = text(languageTag, R.string.bot_command_empty_agent_list)

    fun agentSummary(
        bots: List<BotProfile>,
        currentBot: BotProfile,
        languageTag: String,
    ): String {
        if (bots.isEmpty()) {
            return emptyAgentList(languageTag)
        }
        return buildString {
            appendLine(text(languageTag, R.string.bot_command_agent_summary_count, bots.size))
            bots.forEachIndexed { index, bot ->
                appendLine(text(languageTag, R.string.bot_command_agent_list_item, index + 1, bot.displayName))
            }
            append(text(languageTag, R.string.bot_command_agent_summary_current, currentBot.displayName))
        }
    }

    fun agentStopped(agentName: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_agent_stopped, agentName)
    }

    fun agentStarted(agentName: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_agent_started, agentName)
    }

    fun llmToggled(enabled: Boolean, languageTag: String): String {
        return text(
            languageTag,
            if (enabled) R.string.bot_command_llm_enabled else R.string.bot_command_llm_disabled,
        )
    }

    fun unsupportedCommand(
        commandName: String,
        languageTag: String,
    ): String = text(languageTag, R.string.bot_command_unsupported, commandName)

    fun adminGranted(uid: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_admin_granted, uid)
    }

    fun adminRevoked(uid: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_admin_revoked, uid)
    }

    fun whitelistAdded(umo: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_whitelist_added, umo)
    }

    fun whitelistRemoved(umo: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_whitelist_removed, umo)
    }

    fun sessionSwitched(sessionId: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_session_switched, sessionId)
    }

    fun sessionCreated(sessionId: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_session_created, sessionId)
    }

    fun sessionDeleted(languageTag: String): String = text(languageTag, R.string.bot_command_session_deleted)

    fun sessionRenamed(title: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_session_renamed, title)
    }

    fun sessionIndexNotFound(index: Int, languageTag: String): String {
        return text(languageTag, R.string.bot_command_session_index_not_found, index)
    }

    fun providerSummary(
        currentProvider: ProviderProfile?,
        providers: List<ProviderProfile>,
        languageTag: String,
    ): String {
        val enabledProviders = providers.filter { it.enabled }
        return buildString {
            appendLine(
                text(
                    languageTag,
                    R.string.bot_command_provider_summary_current,
                    currentProvider?.name ?: text(languageTag, R.string.bot_command_provider_summary_none),
                ),
            )
            appendLine(text(languageTag, R.string.bot_command_provider_summary_available))
            enabledProviders.forEach { provider ->
                val suffix = if (provider.id == currentProvider?.id) {
                    text(languageTag, R.string.bot_command_provider_summary_current_suffix)
                } else {
                    ""
                }
                appendLine("- ${provider.name} / ${provider.model}$suffix")
            }
        }.trim()
    }

    fun providerNotFound(argument: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_provider_not_found, argument)
    }

    fun providerSwitched(providerName: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_provider_switched, providerName)
    }

    fun noActiveProvider(languageTag: String): String {
        return text(languageTag, R.string.bot_command_no_active_provider)
    }

    fun modelUpdated(providerName: String, model: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_model_updated, providerName, model)
    }

    fun apiKeyUpdated(providerName: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_api_key_updated, providerName)
    }

    fun personaHelp(languageTag: String): String {
        return listOf(
            text(languageTag, R.string.bot_command_persona_help_title),
            text(languageTag, R.string.bot_command_persona_help_list),
            text(languageTag, R.string.bot_command_persona_help_switch),
            text(languageTag, R.string.bot_command_persona_help_view),
        ).joinToString("\n")
    }

    fun personaList(
        personas: List<PersonaProfile>,
        currentPersonaId: String?,
        languageTag: String,
    ): String {
        if (personas.isEmpty()) {
            return text(languageTag, R.string.bot_command_persona_none)
        }
        return buildString {
            appendLine(text(languageTag, R.string.bot_command_persona_available))
            personas.forEach { persona ->
                val suffix = if (persona.id == currentPersonaId) {
                    text(languageTag, R.string.bot_command_persona_current_suffix)
                } else {
                    ""
                }
                appendLine("- ${persona.name}$suffix")
            }
        }.trim()
    }

    fun personaNotFound(languageTag: String): String {
        return text(languageTag, R.string.bot_command_persona_not_found)
    }

    fun personaView(persona: PersonaProfile, languageTag: String): String {
        return text(languageTag, R.string.bot_command_persona_view, persona.name, persona.systemPrompt)
    }

    fun personaSwitched(personaName: String, languageTag: String): String {
        return text(languageTag, R.string.bot_command_persona_switched, personaName)
    }

    fun sid(context: BotCommandContext): String {
        val uid = context.sourceUid.ifBlank { text(context.languageTag, R.string.bot_command_sid_uid_na) }
        return buildString {
            appendLine("UMO: `${context.bot.id}:${context.messageType.name}:${umoSessionToken(context.session)}`")
            appendLine("UID: `$uid`")
            appendLine(text(context.languageTag, R.string.bot_command_sid_session_source_info))
            appendLine(text(context.languageTag, R.string.bot_command_sid_bot_id, context.bot.id))
            appendLine(text(context.languageTag, R.string.bot_command_sid_message_type, sessionTypeLabel(context.session, context.languageTag)))
            appendLine(text(context.languageTag, R.string.bot_command_sid_session_id, context.sessionId))
            append(text(context.languageTag, R.string.bot_command_sid_footer))
        }
    }

    internal fun resolveLanguage(languageTag: String): CommandLanguage {
        return if (languageTag.startsWith("en", ignoreCase = true)) CommandLanguage.EN else CommandLanguage.ZH
    }

    private fun text(
        languageTag: String,
        @StringRes resId: Int,
        vararg formatArgs: Any,
    ): String {
        return AppStrings.getForLanguageTag(languageTag, resId, *formatArgs)
    }
}
