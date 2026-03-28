package com.astrbot.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy

// 统一维护全局导航 route，避免在页面和动画层重复写字符串。
internal sealed class AppDestination(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Bots : AppDestination("bots", Icons.Outlined.SmartToy)
    data object Personas : AppDestination("personas", Icons.Outlined.Face)
    data object Chat : AppDestination("chat", Icons.Outlined.ChatBubbleOutline)
    data object Config : AppDestination("config", Icons.Outlined.Settings)
    data object ConfigDetail : AppDestination("config/detail/{configId}", Icons.Outlined.Settings) {
        fun routeFor(configId: String): String = "config/detail/$configId"
    }

    data object Logs : AppDestination("logs", Icons.AutoMirrored.Outlined.List)
    data object Me : AppDestination("me", Icons.Outlined.PersonOutline)
    data object QQAccount : AppDestination("qq-account", Icons.Outlined.PersonOutline)
    data object QQLogin : AppDestination("qq-login", Icons.Outlined.PersonOutline)
    data object SettingsHub : AppDestination("settings-hub", Icons.Outlined.Settings)
    data object Assets : AppDestination("asset-management", Icons.Outlined.Memory)
    data object AssetDetail : AppDestination("asset-management/{assetId}", Icons.Outlined.Memory) {
        fun routeFor(assetId: String): String = "asset-management/$assetId"
    }

    data object BackupHub : AppDestination("backup-hub", Icons.Outlined.Memory)
    data object BotBackup : AppDestination("backup/bots", Icons.Outlined.SmartToy)
    data object ModelBackup : AppDestination("backup/models", Icons.Outlined.Memory)
    data object PersonaBackup : AppDestination("backup/personas", Icons.Outlined.Face)
    data object ConversationBackup : AppDestination("backup/conversations", Icons.Outlined.ChatBubbleOutline)
    data object ConfigBackup : AppDestination("backup/configs", Icons.Outlined.Settings)
    data object TtsBackup : AppDestination("backup/tts", Icons.Outlined.Memory)
    data object FullBackup : AppDestination("backup/full", Icons.Outlined.Memory)
    data object Models : AppDestination("models", Icons.Outlined.Memory)
    data object Runtime : AppDestination("runtime", Icons.Outlined.Settings)
}
