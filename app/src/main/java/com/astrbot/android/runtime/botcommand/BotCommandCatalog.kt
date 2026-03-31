package com.astrbot.android.runtime.botcommand

import androidx.annotation.StringRes
import com.astrbot.android.R

internal data class BotCommandCatalogEntry(
    val command: String,
    @StringRes val descriptionResId: Int,
)

internal data class BotCommandCatalogSection(
    @StringRes val titleResId: Int,
    val entries: List<BotCommandCatalogEntry>,
)

internal object BotCommandCatalog {
    val sections: List<BotCommandCatalogSection> = listOf(
        BotCommandCatalogSection(
            titleResId = R.string.bot_command_section_builtin,
            entries = listOf(
                BotCommandCatalogEntry("/help", R.string.bot_command_desc_help),
                BotCommandCatalogEntry("/stop", R.string.bot_command_desc_stop_current),
                BotCommandCatalogEntry("/stop <agent_name>", R.string.bot_command_desc_stop_named),
                BotCommandCatalogEntry("/start <agent_name>", R.string.bot_command_desc_start_named),
                BotCommandCatalogEntry("/agent", R.string.bot_command_desc_agent),
            ),
        ),
        BotCommandCatalogSection(
            titleResId = R.string.bot_command_section_conversation,
            entries = listOf(
                BotCommandCatalogEntry("/ls", R.string.bot_command_desc_ls),
                BotCommandCatalogEntry("/switch", R.string.bot_command_desc_switch),
                BotCommandCatalogEntry("/new", R.string.bot_command_desc_new),
                BotCommandCatalogEntry("/del", R.string.bot_command_desc_del),
                BotCommandCatalogEntry("/rename", R.string.bot_command_desc_rename),
                BotCommandCatalogEntry("/reset", R.string.bot_command_desc_reset),
                BotCommandCatalogEntry("/sid", R.string.bot_command_desc_sid),
            ),
        ),
        BotCommandCatalogSection(
            titleResId = R.string.bot_command_section_admin,
            entries = listOf(
                BotCommandCatalogEntry("/deop <UID>", R.string.bot_command_desc_deop),
                BotCommandCatalogEntry("/op <UID>", R.string.bot_command_desc_op),
            ),
        ),
        BotCommandCatalogSection(
            titleResId = R.string.bot_command_section_whitelist,
            entries = listOf(
                BotCommandCatalogEntry("/wl <UMO>", R.string.bot_command_desc_wl),
                BotCommandCatalogEntry("/dwl <UMO>", R.string.bot_command_desc_dwl),
            ),
        ),
        BotCommandCatalogSection(
            titleResId = R.string.bot_command_section_provider,
            entries = listOf(
                BotCommandCatalogEntry("/provider", R.string.bot_command_desc_provider),
                BotCommandCatalogEntry("/model", R.string.bot_command_desc_model),
                BotCommandCatalogEntry("/llm", R.string.bot_command_desc_llm),
            ),
        ),
        BotCommandCatalogSection(
            titleResId = R.string.bot_command_section_persona,
            entries = listOf(
                BotCommandCatalogEntry("/persona", R.string.bot_command_desc_persona),
            ),
        ),
        BotCommandCatalogSection(
            titleResId = R.string.bot_command_section_voice,
            entries = listOf(
                BotCommandCatalogEntry("/tts", R.string.bot_command_desc_tts),
                BotCommandCatalogEntry("/stt", R.string.bot_command_desc_stt),
            ),
        ),
    )
}
