package com.astrbot.android.model.plugin

object PluginTriggerContracts {
    val onlineHostTriggers = setOf(
        PluginTriggerSource.OnPluginEntryClick,
        PluginTriggerSource.OnCommand,
        PluginTriggerSource.BeforeSendMessage,
        PluginTriggerSource.AfterModelResponse,
    )

    val residualCompatOnlyTriggers = setOf(
        PluginTriggerSource.OnMessageReceived,
        PluginTriggerSource.OnSchedule,
        PluginTriggerSource.OnConversationEnter,
    )

    fun isOnlineHostTrigger(trigger: PluginTriggerSource): Boolean = trigger in onlineHostTriggers

    fun isResidualCompatOnlyTrigger(trigger: PluginTriggerSource): Boolean = trigger in residualCompatOnlyTriggers
}

enum class ExternalPluginHostActionAvailability {
    OPEN_V1,
    DECLARED_ONLY,
}

object ExternalPluginHostActionPolicy {
    private val openActions = setOf(
        PluginHostAction.SendMessage,
        PluginHostAction.SendNotification,
        PluginHostAction.OpenHostPage,
    )

    private val requiredPermissions = mapOf(
        PluginHostAction.SendMessage to "send_message",
        PluginHostAction.SendNotification to "send_notification",
        PluginHostAction.OpenHostPage to "open_host_page",
    )

    fun availability(action: PluginHostAction): ExternalPluginHostActionAvailability {
        return if (action in openActions) {
            ExternalPluginHostActionAvailability.OPEN_V1
        } else {
            ExternalPluginHostActionAvailability.DECLARED_ONLY
        }
    }

    fun isOpen(action: PluginHostAction): Boolean {
        return availability(action) == ExternalPluginHostActionAvailability.OPEN_V1
    }

    fun openActions(): List<PluginHostAction> {
        return openActions.toList()
    }

    fun requiredPermissionId(action: PluginHostAction): String? {
        return requiredPermissions[action]
    }
}

data class ResolvedExternalPluginMediaItem(
    val source: String,
    val resolvedSource: String,
    val mimeType: String,
    val altText: String,
)

object ExternalPluginMediaSourceResolver {
    private const val PackagePrefix = "plugin://package/"
    private const val WorkspacePrefix = "plugin://workspace/"

    fun resolve(
        item: PluginMediaItem,
        extractedDir: String,
        privateRootPath: String = "",
    ): ResolvedExternalPluginMediaItem {
        val source = item.source.trim()
        require(source.isNotBlank()) { "Media source must not be blank." }
        return when {
            source.startsWith("https://") || source.startsWith("http://") -> ResolvedExternalPluginMediaItem(
                source = source,
                resolvedSource = source,
                mimeType = item.mimeType,
                altText = item.altText,
            )

            source.startsWith(PackagePrefix) -> {
                val relativePath = source.removePrefix(PackagePrefix).trim()
                require(relativePath.isNotBlank()) { "Package media source must declare a relative path." }
                val pluginRoot = extractedDir.trim().takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Package media source requires plugin extractedDir.")
                val rootPath = java.io.File(pluginRoot).canonicalFile.toPath()
                val candidate = java.io.File(pluginRoot, relativePath).canonicalFile.toPath()
                require(candidate.startsWith(rootPath)) {
                    "Package media source escapes plugin root: $source"
                }
                require(candidate.toFile().isFile) {
                    "Package media source does not exist: $source"
                }
                ResolvedExternalPluginMediaItem(
                    source = source,
                    resolvedSource = candidate.toFile().absolutePath,
                    mimeType = item.mimeType,
                    altText = item.altText,
                )
            }

            source.startsWith(WorkspacePrefix) -> {
                val location = source.removePrefix(WorkspacePrefix).trim()
                val area = location.substringBefore('/').trim()
                require(area in setOf("imports", "runtime", "exports", "cache")) {
                    "Workspace media source must use imports, runtime, exports, or cache."
                }
                val relativePath = location.substringAfter('/', "").trim()
                require(relativePath.isNotBlank()) { "Workspace media source must declare a relative path." }
                val workspaceRoot = privateRootPath.trim().takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Workspace media source requires plugin privateRootPath.")
                val candidate = ExternalPluginWorkspacePolicy.resolveWorkspaceFile(
                    privateRootPath = workspaceRoot,
                    relativePath = "$area/$relativePath",
                )
                require(candidate.isFile) {
                    "Workspace media source does not exist: $source"
                }
                ResolvedExternalPluginMediaItem(
                    source = source,
                    resolvedSource = candidate.absolutePath,
                    mimeType = item.mimeType,
                    altText = item.altText,
                )
            }

            else -> throw IllegalArgumentException(
                "Unsupported media source: $source. Use http(s)://, plugin://package/..., or plugin://workspace/...",
            )
        }
    }
}
