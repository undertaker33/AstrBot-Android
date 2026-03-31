package com.astrbot.android.runtime.botcommand

internal data class BotCommandCatalogEntry(
    val command: String,
    val zhDescription: String,
    val enDescription: String,
)

internal data class BotCommandCatalogSection(
    val zhTitle: String,
    val enTitle: String,
    val entries: List<BotCommandCatalogEntry>,
)

internal object BotCommandCatalog {
    val sections: List<BotCommandCatalogSection> = listOf(
        BotCommandCatalogSection(
            zhTitle = "内置指令",
            enTitle = "Built-in commands",
            entries = listOf(
                BotCommandCatalogEntry("/help", "查看帮助", "View help"),
                BotCommandCatalogEntry("/stop", "停用当前会话中正在运行的 Agent", "Stop the running agent in this conversation"),
                BotCommandCatalogEntry("/stop <agent_name>", "停用指定名称的 Agent", "Stop the named agent"),
                BotCommandCatalogEntry("/start <agent_name>", "启用指定名称的 Agent", "Start the named agent"),
                BotCommandCatalogEntry("/agent", "列出当前 agent 列表", "List current agents"),
            ),
        ),
        BotCommandCatalogSection(
            zhTitle = "会话管理",
            enTitle = "Conversation management",
            entries = listOf(
                BotCommandCatalogEntry("/ls", "查看对话列表", "Show conversation list"),
                BotCommandCatalogEntry("/switch", "通过 /ls 前面的序号切换对话", "Switch conversations by the /ls index"),
                BotCommandCatalogEntry("/new", "创建新对话", "Create a new conversation"),
                BotCommandCatalogEntry("/groupnew", "创建新群聊对话", "Create a new group conversation"),
                BotCommandCatalogEntry("/del", "删除当前对话", "Delete current conversation"),
                BotCommandCatalogEntry("/rename", "重命名对话", "Rename current conversation"),
                BotCommandCatalogEntry("/reset", "重置 LLM 会话", "Reset the current LLM conversation"),
                BotCommandCatalogEntry("/sid", "获取会话 ID 和管理员 ID", "Show session ID and admin UID"),
            ),
        ),
        BotCommandCatalogSection(
            zhTitle = "管理员管理（需要管理员权限）",
            enTitle = "Admin management (admin only)",
            entries = listOf(
                BotCommandCatalogEntry("/deop <UID>", "取消授权管理员", "Remove admin authorization"),
                BotCommandCatalogEntry("/op <UID>", "授权管理员", "Grant admin authorization"),
            ),
        ),
        BotCommandCatalogSection(
            zhTitle = "白名单管理",
            enTitle = "Whitelist management",
            entries = listOf(
                BotCommandCatalogEntry("/wl <UMO>", "添加白名单", "Add whitelist entry"),
                BotCommandCatalogEntry("/dwl <UMO>", "删除白名单", "Remove whitelist entry"),
            ),
        ),
        BotCommandCatalogSection(
            zhTitle = "提供商管理",
            enTitle = "Provider management",
            entries = listOf(
                BotCommandCatalogEntry("/provider", "查看或切换 LLM Provider", "Show or switch the LLM provider"),
                BotCommandCatalogEntry("/model", "查看或切换模型", "Show or switch the model"),
                BotCommandCatalogEntry("/llm", "开启或关闭 LLM", "Enable or disable the LLM"),
                BotCommandCatalogEntry("/key", "查看或切换 Key", "Show or switch the key"),
            ),
        ),
        BotCommandCatalogSection(
            zhTitle = "人格管理",
            enTitle = "Persona management",
            entries = listOf(
                BotCommandCatalogEntry("/persona", "查看或切换 Persona", "Show or switch personas"),
            ),
        ),
        BotCommandCatalogSection(
            zhTitle = "语音管理",
            enTitle = "Voice management",
            entries = listOf(
                BotCommandCatalogEntry("/tts", "开关文本转语音（会话级别）", "Toggle text to speech for this conversation"),
                BotCommandCatalogEntry("/stt", "开关语音转文本（会话级别）", "Toggle speech to text for this conversation"),
            ),
        ),
    )
}
