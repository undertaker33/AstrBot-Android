package com.astrbot.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.astrbot.android.ui.screen.ConfigDetailTopBar
import com.astrbot.android.ui.screen.SubPageHeader

internal sealed interface SecondaryTopBarSpec {
    data class SubPage(
        val title: String,
        val onBack: () -> Unit,
    ) : SecondaryTopBarSpec

    data class ConfigDetail(
        val profileName: String,
        val currentSectionTitle: String,
        val onBack: () -> Unit,
        val onOpenSections: () -> Unit,
    ) : SecondaryTopBarSpec
}

internal enum class GlobalTopBarLayer {
    MAIN,
    SECONDARY,
    NONE,
}

internal data class RegisteredSecondaryTopBar(
    val route: String,
    val spec: SecondaryTopBarSpec,
)

internal data class ConfigDetailChromeBinding(
    val currentSectionTitle: String,
    val onOpenSections: () -> Unit,
)

internal data class SecondaryTopBarStrings(
    val config: String,
    val logs: String,
    val qqAccount: String,
    val qqLogin: String,
    val settings: String,
    val assetManagement: String,
    val pluginDetail: String,
    val pluginConfig: String,
    val models: String,
    val runtime: String,
    val dataBackup: String,
    val configDetailDefaultSection: String,
)

internal val LocalSecondaryTopBarRegistrar =
    staticCompositionLocalOf<(String, SecondaryTopBarSpec?) -> Unit> { { _, _ -> } }
internal val LocalConfigDetailChromeRegistrar =
    staticCompositionLocalOf<(ConfigDetailChromeBinding?) -> Unit> { { _ -> } }

internal fun resolveGlobalTopBarLayer(
    activeMainRoute: String?,
    hasSecondaryTopBar: Boolean,
): GlobalTopBarLayer = when {
    activeMainRoute != null -> GlobalTopBarLayer.MAIN
    hasSecondaryTopBar -> GlobalTopBarLayer.SECONDARY
    else -> GlobalTopBarLayer.NONE
}

@Composable
internal fun RegisterSecondaryTopBar(
    route: String,
    spec: SecondaryTopBarSpec,
) {
    val registrar = LocalSecondaryTopBarRegistrar.current
    DisposableEffect(route, spec, registrar) {
        registrar(route, spec)
        onDispose {
            registrar(route, null)
        }
    }
}

@Composable
internal fun RegisterSecondaryTopBar(spec: SecondaryTopBarSpec) {
    RegisterSecondaryTopBar(route = "", spec = spec)
}

@Composable
internal fun RegisterConfigDetailChromeBinding(binding: ConfigDetailChromeBinding) {
    val registrar = LocalConfigDetailChromeRegistrar.current
    DisposableEffect(binding, registrar) {
        registrar(binding)
        onDispose {
            registrar(null)
        }
    }
}

internal fun resolveEffectiveSecondaryTopBarSpec(
    currentRoute: String?,
    registered: RegisteredSecondaryTopBar?,
    fallback: SecondaryTopBarSpec?,
): SecondaryTopBarSpec? = when {
    matchesConfigDetailRoute(currentRoute) && fallback is SecondaryTopBarSpec.ConfigDetail -> fallback
    registered != null && registered.route == currentRoute -> registered.spec
    else -> fallback
}

internal fun fallbackSecondaryTopBarSpecForRoute(
    route: String?,
    strings: SecondaryTopBarStrings,
    configDetailProfileName: String? = null,
    configDetailCurrentSectionTitle: String? = null,
    configDetailOnOpenSections: () -> Unit = {},
): SecondaryTopBarSpec? {
    if (matchesConfigDetailRoute(route)) {
        return SecondaryTopBarSpec.ConfigDetail(
            profileName = configDetailProfileName ?: strings.config,
            currentSectionTitle = configDetailCurrentSectionTitle ?: strings.configDetailDefaultSection,
            onBack = {},
            onOpenSections = configDetailOnOpenSections,
        )
    }

    val title = when (route) {
        AppDestination.Logs.route -> strings.logs
        AppDestination.QQAccount.route -> strings.qqAccount
        AppDestination.QQLogin.route -> strings.qqLogin
        AppDestination.SettingsHub.route -> strings.settings
        AppDestination.Assets.route -> strings.assetManagement
        AppDestination.Models.route -> strings.models
        AppDestination.Runtime.route -> strings.runtime
        AppDestination.BackupHub.route,
        AppDestination.BotBackup.route,
        AppDestination.ModelBackup.route,
        AppDestination.PersonaBackup.route,
        AppDestination.ConversationBackup.route,
        AppDestination.ConfigBackup.route,
        AppDestination.TtsBackup.route,
        AppDestination.FullBackup.route,
        -> strings.dataBackup
        else -> null
    } ?: if (matchesAssetDetailRoute(route)) {
        strings.assetManagement
    } else if (matchesPluginConfigRoute(route)) {
        strings.pluginConfig
    } else if (matchesPluginDetailRoute(route)) {
        strings.pluginDetail
    } else {
        null
    } ?: return null

    return SecondaryTopBarSpec.SubPage(
        title = title,
        onBack = {},
    )
}

private fun matchesConfigDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.ConfigDetail.route ||
        route.startsWith("config/detail/")
}

private fun matchesAssetDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.AssetDetail.route ||
        (route.startsWith("asset-management/") && route != AppDestination.Assets.route)
}

private fun matchesPluginDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.PluginDetail.route ||
        (route.startsWith("plugins/detail/") && route != AppDestination.Plugins.route)
}

private fun matchesPluginConfigRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.PluginConfig.route || route.endsWith("/config")
}

@Composable
internal fun SecondaryTopBarPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(AppTopBarHeight),
    )
}

@Composable
internal fun SecondaryTopBarHost(spec: SecondaryTopBarSpec) {
    when (spec) {
        is SecondaryTopBarSpec.SubPage -> {
            SubPageHeader(
                title = spec.title,
                onBack = spec.onBack,
            )
        }

        is SecondaryTopBarSpec.ConfigDetail -> {
            ConfigDetailTopBar(
                profileName = spec.profileName,
                currentSectionTitle = spec.currentSectionTitle,
                onBack = spec.onBack,
                onOpenSections = spec.onOpenSections,
            )
        }
    }
}
