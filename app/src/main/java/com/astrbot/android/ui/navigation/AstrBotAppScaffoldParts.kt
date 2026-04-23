package com.astrbot.android.ui.navigation

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.astrbot.android.R
import com.astrbot.android.feature.config.data.FeatureConfigRepository as ConfigRepository
import com.astrbot.android.feature.resource.presentation.ResourceCenterViewModel
import com.astrbot.android.core.db.backup.AppBackupModuleKind
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.ui.navigation.AppDestination
import com.astrbot.android.ui.navigation.MainSwipePage
import com.astrbot.android.ui.navigation.MainSwipeRail
import com.astrbot.android.ui.navigation.mainSwipeEnabledForPage
import com.astrbot.android.ui.navigation.shouldRenderChatDrawer
import com.astrbot.android.ui.navigation.AppNavigationTransitions
import com.astrbot.android.ui.navigation.AppNavigator
import com.astrbot.android.ui.app.ChatTopBar
import com.astrbot.android.ui.app.ChatTopBarSelectorMenu
import com.astrbot.android.ui.app.MainTopBar
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.SelectionModeTopBar
import com.astrbot.android.ui.app.TopBarSegmentedToggle
import com.astrbot.android.ui.app.TopBarTitleAlignment
import com.astrbot.android.ui.app.topLevelContentTopPadding
import com.astrbot.android.ui.settings.AssetDetailScreen
import com.astrbot.android.ui.settings.AssetManagementScreen
import com.astrbot.android.ui.bot.BotScreen
import com.astrbot.android.ui.plugin.PluginConfigScreenRoute
import com.astrbot.android.ui.bot.BotWorkspaceTab
import com.astrbot.android.ui.chat.ChatScreen
import com.astrbot.android.ui.config.ConfigDetailScreen
import com.astrbot.android.ui.config.ConfigScreen
import com.astrbot.android.ui.settings.ConversationBackupScreen
import com.astrbot.android.ui.settings.DataBackupHubScreen
import com.astrbot.android.ui.settings.FullBackupScreen
import com.astrbot.android.ui.settings.LogScreen
import com.astrbot.android.ui.settings.CronJobsScreen
import com.astrbot.android.ui.settings.MeScreen
import com.astrbot.android.ui.settings.ModuleBackupScreen
import com.astrbot.android.ui.settings.ResourceCenterScreen
import com.astrbot.android.ui.settings.ResourceKind
import com.astrbot.android.ui.settings.ResourceListScreen
import com.astrbot.android.ui.settings.buildResourceCards
import com.astrbot.android.ui.persona.PersonaScreen
import com.astrbot.android.ui.plugin.PluginDetailScreenRoute
import com.astrbot.android.ui.plugin.PluginManagerScreenRoute
import com.astrbot.android.ui.plugin.PluginMarketDetailScreenRoute
import com.astrbot.android.ui.plugin.PluginRuntimeLogScreenRoute
import com.astrbot.android.ui.plugin.PluginTriggerManagementScreenRoute
import com.astrbot.android.ui.plugin.PluginWorkspaceScreenRoute
import com.astrbot.android.ui.plugin.PluginScreen
import com.astrbot.android.ui.plugin.PluginWorkspaceTab
import com.astrbot.android.ui.provider.ProviderScreen
import com.astrbot.android.ui.qqlogin.QQAccountCenterScreen
import com.astrbot.android.ui.qqlogin.QQLoginScreen
import com.astrbot.android.ui.settings.SettingsHubScreen
import com.astrbot.android.ui.settings.SettingsScreen
import com.astrbot.android.ui.common.SubPageScaffold
import com.astrbot.android.ui.viewmodel.ChatViewModel
import com.astrbot.android.ui.viewmodel.QQLoginViewModel

@Composable
internal fun AstrBotAppTopBar(
    activeMainDestination: Pair<AppDestination, String>?,
    botWorkspaceTab: BotWorkspaceTab,
    pluginWorkspaceTab: PluginWorkspaceTab,
    configSelectedIds: Set<String>,
    currentChatBot: BotProfile?,
    chatBots: List<BotProfile>,
    chatPersonas: List<PersonaProfile>,
    currentPersonaId: String?,
    chatSelectorExpanded: Boolean,
    onBotWorkspaceTabChange: (BotWorkspaceTab) -> Unit,
    onPluginWorkspaceTabChange: (PluginWorkspaceTab) -> Unit,
    onConfigSelectionClear: () -> Unit,
    onOpenHistory: () -> Unit,
    onBotSelectorExpandedChange: (Boolean) -> Unit,
    onSelectBot: (String) -> Unit,
    onSelectPersona: (String) -> Unit,
) {
    activeMainDestination?.let { (destination, label) ->
        when (destination) {
            AppDestination.Bots -> MainTopBar(
                title = when (botWorkspaceTab) {
                    BotWorkspaceTab.BOTS -> stringResource(R.string.nav_bots)
                    BotWorkspaceTab.MODELS -> stringResource(R.string.nav_models)
                    BotWorkspaceTab.PERSONAS -> stringResource(R.string.nav_personas)
                },
                titleAlignment = TopBarTitleAlignment.End,
                leftContent = {
                    TopBarSegmentedToggle(
                        options = listOf(
                            stringResource(R.string.nav_bots),
                            stringResource(R.string.nav_models),
                            stringResource(R.string.nav_personas),
                        ),
                        selectedIndex = botWorkspaceTab.ordinal,
                        onSelect = { onBotWorkspaceTabChange(BotWorkspaceTab.entries[it]) },
                    )
                },
            )

            AppDestination.Plugins -> MainTopBar(
                title = stringResource(R.string.nav_plugins),
                titleAlignment = TopBarTitleAlignment.End,
                leftContent = {
                    TopBarSegmentedToggle(
                        options = listOf(
                            stringResource(R.string.plugin_workspace_tab_local),
                            stringResource(R.string.plugin_workspace_tab_market),
                        ),
                        selectedIndex = pluginWorkspaceToggleSelectionIndex(pluginWorkspaceTab),
                        onSelect = { index ->
                            onPluginWorkspaceTabChange(
                                if (index == 0) PluginWorkspaceTab.LOCAL else PluginWorkspaceTab.MARKET,
                            )
                        },
                    )
                },
            )

            AppDestination.Chat -> ChatTopBar(
                selectedBotName = currentChatBot?.displayName ?: stringResource(R.string.chat_selector_bots),
                onOpenHistory = onOpenHistory,
                onOpenBotSelector = { onBotSelectorExpandedChange(true) },
                botSelectorDropdown = {
                    ChatTopBarSelectorMenu(
                        expanded = chatSelectorExpanded,
                        bots = chatBots,
                        personas = chatPersonas,
                        currentPersonaId = currentPersonaId,
                        onDismissRequest = { onBotSelectorExpandedChange(false) },
                        onSelectBot = { botId ->
                            onSelectBot(botId)
                            onBotSelectorExpandedChange(false)
                        },
                        onSelectPersona = { personaId ->
                            onSelectPersona(personaId)
                            onBotSelectorExpandedChange(false)
                        },
                    )
                },
            )

            AppDestination.Config -> {
                if (configSelectedIds.isNotEmpty()) {
                    SelectionModeTopBar(
                        count = configSelectedIds.size,
                        onCancel = onConfigSelectionClear,
                    )
                } else {
                    MainTopBar(title = stringResource(R.string.nav_config), titleAlignment = TopBarTitleAlignment.Center)
                }
            }

            AppDestination.Me -> MainTopBar(title = stringResource(R.string.nav_me), titleAlignment = TopBarTitleAlignment.Center)
            else -> MainTopBar(title = label)
        }
    }
}

@Composable
internal fun AstrBotAppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    currentMainSwipePage: MainSwipePage,
    onMainSwipePageSettled: (MainSwipePage) -> Unit,
    botWorkspaceTab: BotWorkspaceTab,
    onBotWorkspaceTabChange: (BotWorkspaceTab) -> Unit,
    pluginWorkspaceTab: PluginWorkspaceTab,
    onPluginWorkspaceTabChange: (PluginWorkspaceTab) -> Unit,
    chatViewModel: ChatViewModel,
    qqLoginViewModel: QQLoginViewModel,
    chatDrawerState: androidx.compose.material3.DrawerState,
    floatingBottomNavPadding: androidx.compose.ui.unit.Dp,
    configSelectedIds: Set<String>,
    onConfigSelectedIdsChange: (Set<String>) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Chat.route,
        modifier = modifier,
        enterTransition = {
            AppNavigationTransitions.enterTransition(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route,
            )
        },
        exitTransition = {
            AppNavigationTransitions.exitTransition(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route,
            )
        },
        popEnterTransition = {
            AppNavigationTransitions.popEnterTransition(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route,
            )
        },
        popExitTransition = {
            AppNavigationTransitions.popExitTransition(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route,
            )
        },
    ) {
        composable(AppDestination.Bots.route) {
            MainTopLevelRail(
                currentMainSwipePage = currentMainSwipePage,
                onMainSwipePageSettled = onMainSwipePageSettled,
                navController = navController,
                botWorkspaceTab = botWorkspaceTab,
                onBotWorkspaceTabChange = onBotWorkspaceTabChange,
                pluginWorkspaceTab = pluginWorkspaceTab,
                onPluginWorkspaceTabChange = onPluginWorkspaceTabChange,
                chatViewModel = chatViewModel,
                drawerState = chatDrawerState,
                floatingBottomNavPadding = floatingBottomNavPadding,
                configSelectedIds = configSelectedIds,
                onConfigSelectedIdsChange = onConfigSelectedIdsChange,
                qqLoginViewModel = qqLoginViewModel,
            )
        }
        composable(AppDestination.Plugins.route) {
            MainTopLevelRail(
                currentMainSwipePage = currentMainSwipePage,
                onMainSwipePageSettled = onMainSwipePageSettled,
                navController = navController,
                onOpenPluginDetail = { pluginId ->
                    AppNavigator.open(navController, AppDestination.PluginDetail.routeFor(pluginId))
                },
                onOpenPluginManager = {
                    AppNavigator.open(navController, AppDestination.PluginManager.route)
                },
                onOpenMarketPluginDetail = { pluginId ->
                    AppNavigator.open(navController, AppDestination.PluginMarketDetail.routeFor(pluginId))
                },
                botWorkspaceTab = botWorkspaceTab,
                onBotWorkspaceTabChange = onBotWorkspaceTabChange,
                pluginWorkspaceTab = pluginWorkspaceTab,
                onPluginWorkspaceTabChange = onPluginWorkspaceTabChange,
                chatViewModel = chatViewModel,
                drawerState = chatDrawerState,
                floatingBottomNavPadding = floatingBottomNavPadding,
                configSelectedIds = configSelectedIds,
                onConfigSelectedIdsChange = onConfigSelectedIdsChange,
                qqLoginViewModel = qqLoginViewModel,
            )
        }
        composable(AppDestination.PluginDetail.route) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId").orEmpty()
            PluginDetailScreenRoute(
                pluginId = pluginId,
                onBack = { AppNavigator.back(navController) },
                onOpenLogs = {
                    AppNavigator.open(navController, AppDestination.PluginLogs.routeFor(pluginId))
                },
                onOpenConfig = {
                    AppNavigator.open(navController, AppDestination.PluginConfig.routeFor(pluginId))
                },
                onReturnToInstalledPlugins = {
                    onPluginWorkspaceTabChange(PluginWorkspaceTab.LOCAL)
                    val returnedToPluginRoot = navController.popBackStack(
                        route = AppDestination.Plugins.route,
                        inclusive = false,
                    )
                    if (!returnedToPluginRoot) {
                        AppNavigator.openTopLevelFresh(navController, AppDestination.Plugins)
                    }
                },
            )
        }
        composable(AppDestination.PluginMarketDetail.route) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId").orEmpty()
            PluginMarketDetailScreenRoute(
                pluginId = pluginId,
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.PluginManager.route) {
            PluginManagerScreenRoute(
                onBack = { AppNavigator.back(navController) },
                onOpenPluginDetail = { pluginId ->
                    AppNavigator.open(navController, AppDestination.PluginDetail.routeFor(pluginId))
                },
            )
        }
        composable(AppDestination.PluginWorkspace.route) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId").orEmpty()
            PluginWorkspaceScreenRoute(
                pluginId = pluginId,
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.PluginLogs.route) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId").orEmpty()
            PluginRuntimeLogScreenRoute(
                pluginId = pluginId,
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.PluginTriggers.route) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId").orEmpty()
            PluginTriggerManagementScreenRoute(
                pluginId = pluginId,
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.PluginConfig.route) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId").orEmpty()
            PluginConfigScreenRoute(
                pluginId = pluginId,
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.Chat.route) {
            MainTopLevelRail(
                currentMainSwipePage = currentMainSwipePage,
                onMainSwipePageSettled = onMainSwipePageSettled,
                navController = navController,
                botWorkspaceTab = botWorkspaceTab,
                onBotWorkspaceTabChange = onBotWorkspaceTabChange,
                pluginWorkspaceTab = pluginWorkspaceTab,
                onPluginWorkspaceTabChange = onPluginWorkspaceTabChange,
                chatViewModel = chatViewModel,
                drawerState = chatDrawerState,
                floatingBottomNavPadding = floatingBottomNavPadding,
                configSelectedIds = configSelectedIds,
                onConfigSelectedIdsChange = onConfigSelectedIdsChange,
                qqLoginViewModel = qqLoginViewModel,
            )
        }
        composable(AppDestination.Config.route) {
            MainTopLevelRail(
                currentMainSwipePage = currentMainSwipePage,
                onMainSwipePageSettled = onMainSwipePageSettled,
                navController = navController,
                botWorkspaceTab = botWorkspaceTab,
                onBotWorkspaceTabChange = onBotWorkspaceTabChange,
                pluginWorkspaceTab = pluginWorkspaceTab,
                onPluginWorkspaceTabChange = onPluginWorkspaceTabChange,
                chatViewModel = chatViewModel,
                drawerState = chatDrawerState,
                floatingBottomNavPadding = floatingBottomNavPadding,
                configSelectedIds = configSelectedIds,
                onConfigSelectedIdsChange = onConfigSelectedIdsChange,
                qqLoginViewModel = qqLoginViewModel,
            )
        }
        composable(AppDestination.ConfigDetail.route) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("configId").orEmpty()
            ConfigDetailScreen(
                profileId = profileId,
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.Logs.route) {
            SubPageScaffold(title = stringResource(R.string.nav_logs), onBack = { AppNavigator.back(navController) }) { inner ->
                Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                    LogScreen(showContext = false)
                }
            }
        }
        composable(AppDestination.Me.route) {
            MainTopLevelRail(
                currentMainSwipePage = currentMainSwipePage,
                onMainSwipePageSettled = onMainSwipePageSettled,
                navController = navController,
                botWorkspaceTab = botWorkspaceTab,
                onBotWorkspaceTabChange = onBotWorkspaceTabChange,
                pluginWorkspaceTab = pluginWorkspaceTab,
                onPluginWorkspaceTabChange = onPluginWorkspaceTabChange,
                chatViewModel = chatViewModel,
                drawerState = chatDrawerState,
                floatingBottomNavPadding = floatingBottomNavPadding,
                configSelectedIds = configSelectedIds,
                onConfigSelectedIdsChange = onConfigSelectedIdsChange,
                qqLoginViewModel = qqLoginViewModel,
            )
        }
        composable(AppDestination.ResourceCenter.route) {
            ResourceCenterScreen(
                onBack = { AppNavigator.back(navController) },
                onOpenResourceList = { kind ->
                    AppNavigator.open(navController, AppDestination.ResourceList.routeFor(kind))
                },
            )
        }
        composable(AppDestination.ResourceList.route) { backStackEntry ->
            val resourceType = backStackEntry.arguments?.getString("resourceType").orEmpty()
            val kind = ResourceKind.fromRouteSegment(resourceType)
            if (kind == ResourceKind.TOOL) {
                ResourceListScreen(
                    kind = kind,
                    onBack = { AppNavigator.back(navController) },
                )
            } else {
                val resourceCenterViewModel: ResourceCenterViewModel = hiltViewModel()
                val repositoryResources by resourceCenterViewModel.resources.collectAsState()
                val controller = resourceCenterViewModel.controller
                ResourceListScreen(
                    kind = kind,
                    resources = buildResourceCards(kind, repositoryResources, controller),
                    onBack = { AppNavigator.back(navController) },
                    onAddResource = { resource ->
                        val savedResource = controller.saveResource(resource)
                        val selectedConfigId = ConfigRepository.resolveExistingId(ConfigRepository.selectedProfileId.value)
                        val nextSortIndex = controller.projectionsForConfig(selectedConfigId)
                            .count { projection -> projection.kind == savedResource.kind }
                        controller.setProjection(
                            ConfigResourceProjection(
                                configId = selectedConfigId,
                                resourceId = savedResource.resourceId,
                                kind = savedResource.kind,
                                active = savedResource.enabled,
                                sortIndex = nextSortIndex,
                            ),
                        )
                    },
                )
            }
        }
        composable(AppDestination.QQAccount.route) {
            QQAccountCenterScreen(
                onBack = { AppNavigator.back(navController) },
                onOpenLogin = { AppNavigator.open(navController, AppDestination.QQLogin.route) },
                qqLoginViewModel = qqLoginViewModel,
            )
        }
        composable(AppDestination.QQLogin.route) {
            QQLoginScreen(
                onBack = { AppNavigator.back(navController) },
                qqLoginViewModel = qqLoginViewModel,
            )
        }
        composable(AppDestination.SettingsHub.route) {
            SettingsHubScreen(
                onBack = { AppNavigator.back(navController) },
                onOpenRuntime = { AppNavigator.open(navController, AppDestination.Runtime.route) },
            )
        }
        composable(AppDestination.Assets.route) {
            AssetManagementScreen(
                onBack = { AppNavigator.back(navController) },
                onOpenAsset = { assetId ->
                    AppNavigator.open(navController, AppDestination.AssetDetail.routeFor(assetId))
                },
            )
        }
        composable(AppDestination.AssetDetail.route) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getString("assetId").orEmpty()
            AssetDetailScreen(
                assetId = assetId,
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.Models.route) {
            ProviderScreen(
                onBack = { AppNavigator.back(navController) },
                onOpenOnDeviceTtsAssets = {
                    AppNavigator.open(
                        navController,
                        AppDestination.AssetDetail.routeFor(com.astrbot.android.model.RuntimeAssetId.ON_DEVICE_TTS.value),
                    )
                },
            )
        }
        composable(AppDestination.Runtime.route) { SettingsScreen(onBack = { AppNavigator.back(navController) }) }
        composable(AppDestination.CronJobs.route) {
            CronJobsScreen(onBack = { AppNavigator.back(navController) })
        }
        composable(AppDestination.BackupHub.route) {
            DataBackupHubScreen(
                onBack = { AppNavigator.back(navController) },
                onOpenBotBackup = { AppNavigator.open(navController, AppDestination.BotBackup.route) },
                onOpenModelBackup = { AppNavigator.open(navController, AppDestination.ModelBackup.route) },
                onOpenPersonaBackup = { AppNavigator.open(navController, AppDestination.PersonaBackup.route) },
                onOpenConversationBackup = { AppNavigator.open(navController, AppDestination.ConversationBackup.route) },
                onOpenConfigBackup = { AppNavigator.open(navController, AppDestination.ConfigBackup.route) },
                onOpenTtsBackup = { AppNavigator.open(navController, AppDestination.TtsBackup.route) },
                onOpenFullBackup = { AppNavigator.open(navController, AppDestination.FullBackup.route) },
            )
        }
        composable(AppDestination.BotBackup.route) {
            ModuleBackupScreen(
                module = AppBackupModuleKind.BOTS,
                title = stringResource(R.string.backup_module_bots_title),
                description = stringResource(R.string.backup_module_bots_desc),
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.ModelBackup.route) {
            ModuleBackupScreen(
                module = AppBackupModuleKind.PROVIDERS,
                title = stringResource(R.string.backup_module_models_title),
                description = stringResource(R.string.backup_module_models_desc),
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.PersonaBackup.route) {
            ModuleBackupScreen(
                module = AppBackupModuleKind.PERSONAS,
                title = stringResource(R.string.backup_module_personas_title),
                description = stringResource(R.string.backup_module_personas_desc),
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.ConversationBackup.route) {
            ConversationBackupScreen(onBack = { AppNavigator.back(navController) })
        }
        composable(AppDestination.ConfigBackup.route) {
            ModuleBackupScreen(
                module = AppBackupModuleKind.CONFIGS,
                title = stringResource(R.string.backup_module_configs_title),
                description = stringResource(R.string.backup_module_configs_desc),
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.TtsBackup.route) {
            ModuleBackupScreen(
                module = AppBackupModuleKind.TTS_ASSETS,
                title = stringResource(R.string.backup_module_tts_title),
                description = stringResource(R.string.backup_module_tts_desc),
                onBack = { AppNavigator.back(navController) },
            )
        }
        composable(AppDestination.FullBackup.route) {
            FullBackupScreen(onBack = { AppNavigator.back(navController) })
        }
    }
}

internal fun activeMainDestination(
    currentDestination: NavDestination?,
    destinations: List<Pair<AppDestination, String>>,
): Pair<AppDestination, String>? {
    return destinations.firstOrNull { (destination, _) ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }
}

internal fun mainRoute(activeMainDestination: Pair<AppDestination, String>?): String? {
    return activeMainDestination?.first?.route
}

internal fun pluginWorkspaceToggleSelectionIndex(pluginWorkspaceTab: PluginWorkspaceTab): Int {
    return when (pluginWorkspaceTab) {
        PluginWorkspaceTab.LOCAL -> 0
        PluginWorkspaceTab.MARKET -> 1
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun MainTopLevelRail(
    currentMainSwipePage: MainSwipePage,
    onMainSwipePageSettled: (MainSwipePage) -> Unit,
    navController: NavHostController,
    botWorkspaceTab: BotWorkspaceTab,
    onBotWorkspaceTabChange: (BotWorkspaceTab) -> Unit,
    pluginWorkspaceTab: PluginWorkspaceTab,
    onPluginWorkspaceTabChange: (PluginWorkspaceTab) -> Unit,
    chatViewModel: ChatViewModel,
    drawerState: androidx.compose.material3.DrawerState,
    floatingBottomNavPadding: androidx.compose.ui.unit.Dp,
    configSelectedIds: Set<String>,
    onConfigSelectedIdsChange: (Set<String>) -> Unit,
    qqLoginViewModel: QQLoginViewModel,
    onOpenPluginDetail: (String) -> Unit = {},
    onOpenPluginManager: () -> Unit = {},
    onOpenMarketPluginDetail: (String) -> Unit = {},
) {
    val safeDrawingTopPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier.padding(top = topLevelContentTopPadding(safeDrawingTopPadding)),
    ) {
        MainSwipeRail(
            currentPage = currentMainSwipePage,
            onPageSettled = onMainSwipePageSettled,
            swipeEnabled = mainSwipeEnabledForPage(currentMainSwipePage),
            pages = mapOf(
                MainSwipePage.BOTS to {
                    BotScreen(
                        workspaceTab = BotWorkspaceTab.BOTS,
                        onWorkspaceTabChange = onBotWorkspaceTabChange,
                    )
                },
                MainSwipePage.MODELS to {
                    BotScreen(
                        workspaceTab = BotWorkspaceTab.MODELS,
                        onWorkspaceTabChange = onBotWorkspaceTabChange,
                    )
                },
                MainSwipePage.PERSONAS to {
                    BotScreen(
                        workspaceTab = BotWorkspaceTab.PERSONAS,
                        onWorkspaceTabChange = onBotWorkspaceTabChange,
                    )
                },
                MainSwipePage.PLUGINS_LOCAL to {
                    PluginScreen(
                        workspaceTab = PluginWorkspaceTab.LOCAL,
                        onOpenPluginDetail = onOpenPluginDetail,
                        onOpenPluginManager = onOpenPluginManager,
                    )
                },
                MainSwipePage.PLUGINS_MARKET to {
                    PluginScreen(
                        workspaceTab = PluginWorkspaceTab.MARKET,
                        onOpenMarketPluginDetail = onOpenMarketPluginDetail,
                    )
                },
                MainSwipePage.CHAT to {
                    ChatScreen(
                        chatViewModel = chatViewModel,
                        floatingBottomNavPadding = floatingBottomNavPadding,
                        showDrawer = false,
                    )
                },
                MainSwipePage.CONFIG to {
                    ConfigScreen(
                        selectedConfigIds = configSelectedIds,
                        onSelectedConfigIdsChange = onConfigSelectedIdsChange,
                        onOpenProfile = { profileId ->
                            AppNavigator.open(navController, AppDestination.ConfigDetail.routeFor(profileId))
                        },
                    )
                },
                MainSwipePage.ME to {
                    MeScreen(
                        onOpenQqAccount = { AppNavigator.open(navController, AppDestination.QQAccount.route) },
                        onOpenSettings = { AppNavigator.open(navController, AppDestination.SettingsHub.route) },
                        onOpenLogs = { AppNavigator.open(navController, AppDestination.Logs.route) },
                        onOpenAssets = { AppNavigator.open(navController, AppDestination.Assets.route) },
                        onOpenBackup = { AppNavigator.open(navController, AppDestination.BackupHub.route) },
                        onOpenCronJobs = { AppNavigator.open(navController, AppDestination.CronJobs.route) },
                        onOpenResourceCenter = { AppNavigator.open(navController, AppDestination.ResourceCenter.route) },
                    )
                },
            ),
        )
    }
}

