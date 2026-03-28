package com.astrbot.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.rememberDrawerState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.R
import com.astrbot.android.ui.screen.BotScreen
import com.astrbot.android.ui.screen.ChatScreen
import com.astrbot.android.ui.screen.ConfigScreen
import com.astrbot.android.ui.screen.ConfigDetailScreen
import com.astrbot.android.ui.screen.LogScreen
import com.astrbot.android.ui.screen.MeScreen
import com.astrbot.android.ui.screen.AssetManagementScreen
import com.astrbot.android.ui.screen.AssetDetailScreen
import com.astrbot.android.ui.screen.PersonaScreen
import com.astrbot.android.ui.screen.ProviderScreen
import com.astrbot.android.ui.screen.QQAccountCenterScreen
import com.astrbot.android.ui.screen.QQLoginScreen
import com.astrbot.android.ui.screen.DataBackupHubScreen
import com.astrbot.android.ui.screen.ConversationBackupScreen
import com.astrbot.android.ui.screen.FullBackupScreen
import com.astrbot.android.ui.screen.ModuleBackupScreen
import com.astrbot.android.ui.screen.SettingsHubScreen
import com.astrbot.android.ui.screen.SettingsScreen
import com.astrbot.android.ui.screen.SubPageScaffold
import com.astrbot.android.ui.viewmodel.ChatViewModel
import com.astrbot.android.ui.viewmodel.BridgeViewModel
import com.astrbot.android.data.backup.AppBackupModuleKind
import kotlinx.coroutines.launch

@Composable
fun AstrBotApp(bridgeViewModel: BridgeViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val chatViewModel: ChatViewModel = viewModel()
    val chatDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var botModelsSelected by remember { mutableStateOf(false) }
    var configSelectedIds by remember { mutableStateOf(setOf<String>()) }
    val botsLabel = stringResource(R.string.nav_bots)
    val modelsLabel = stringResource(R.string.nav_models)
    val destinations = listOf(
        AppDestination.Bots to stringResource(R.string.nav_bots),
        AppDestination.Personas to stringResource(R.string.nav_personas),
        AppDestination.Chat to stringResource(R.string.nav_chat),
        AppDestination.Config to stringResource(R.string.nav_config),
        AppDestination.Me to stringResource(R.string.nav_me),
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val runtimeState by bridgeViewModel.runtimeState.collectAsState()
    val chatBots by chatViewModel.bots.collectAsState()
    val chatPersonas by chatViewModel.personas.collectAsState()
    val chatUiState by chatViewModel.uiState.collectAsState()
    val currentChatBot = chatViewModel.selectedBot()
    val currentChatPersona = chatViewModel.currentPersona()
    var chatSelectorExpanded by remember(
        chatUiState.selectedSessionId,
        chatUiState.selectedBotId,
        chatUiState.selectedProviderId,
    ) { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.hierarchy?.any { it.route == AppDestination.Chat.route } != true) {
            chatSelectorExpanded = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        val activeMainDestination = destinations.firstOrNull { (destination, _) ->
            currentDestination?.hierarchy?.any { it.route == destination.route } == true
        }
        val activeMainRoute = activeMainDestination?.first?.route
        val showFloatingBottomNav = shouldShowFloatingBottomNav(
            activeMainRoute = activeMainRoute,
            imeVisible = imeVisible,
        )
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                activeMainDestination?.let { (destination, label) ->
                    when (destination) {
                        AppDestination.Bots -> MainTopBar(
                            title = if (botModelsSelected) modelsLabel else botsLabel,
                            titleAlignment = TopBarTitleAlignment.End,
                            leftContent = {
                                TopBarToggle(
                                    leftLabel = botsLabel,
                                    rightLabel = modelsLabel,
                                    leftSelected = !botModelsSelected,
                                    onSelectLeft = { botModelsSelected = false },
                                    onSelectRight = { botModelsSelected = true },
                                )
                            },
                        )

                        AppDestination.Personas -> MainTopBar(
                            title = stringResource(R.string.nav_personas),
                            titleAlignment = TopBarTitleAlignment.End,
                            leftContent = {
                                Text(
                                    stringResource(R.string.nav_personas),
                                    color = MonochromeUi.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                        )

                        AppDestination.Chat -> ChatTopBar(
                            selectedBotName = currentChatBot?.displayName ?: stringResource(R.string.chat_selector_bots),
                            onOpenHistory = { scope.launch { chatDrawerState.open() } },
                            onOpenBotSelector = { chatSelectorExpanded = true },
                            botSelectorDropdown = {
                                ChatTopBarSelectorMenu(
                                    expanded = chatSelectorExpanded,
                                    bots = chatBots,
                                    personas = chatPersonas,
                                    currentPersonaId = currentChatPersona?.id,
                                    onDismissRequest = { chatSelectorExpanded = false },
                                    onSelectBot = { botId ->
                                        chatViewModel.selectBot(botId)
                                        chatSelectorExpanded = false
                                    },
                                    onSelectPersona = { personaId ->
                                        chatViewModel.selectPersona(personaId)
                                        chatSelectorExpanded = false
                                    },
                                )
                            },
                        )
                        AppDestination.Config -> {
                            if (configSelectedIds.isNotEmpty()) {
                                SelectionModeTopBar(
                                    count = configSelectedIds.size,
                                    onCancel = { configSelectedIds = emptySet() },
                                )
                            } else {
                                MainTopBar(title = stringResource(R.string.nav_config), titleAlignment = TopBarTitleAlignment.Center)
                            }
                        }
                        AppDestination.Me -> MainTopBar(title = stringResource(R.string.nav_me), titleAlignment = TopBarTitleAlignment.Center)
                        else -> MainTopBar(title = label)
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.Chat.route,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(
                        bottom = floatingBottomNavContentPadding(
                            activeMainRoute = activeMainRoute,
                            visible = showFloatingBottomNav,
                        ),
                    ),
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
                    BotScreen(showModels = botModelsSelected, onShowBots = { botModelsSelected = false })
                }
                composable(AppDestination.Personas.route) { PersonaScreen() }
                composable(AppDestination.Chat.route) {
                    ChatScreen(
                        chatViewModel = chatViewModel,
                        drawerState = chatDrawerState,
                        floatingBottomNavPadding = chatBottomBarPadding(showFloatingBottomNav),
                    )
                }
                composable(AppDestination.Config.route) {
                    ConfigScreen(
                        selectedConfigIds = configSelectedIds,
                        onSelectedConfigIdsChange = { configSelectedIds = it },
                        onOpenProfile = { profileId ->
                            AppNavigator.open(navController, AppDestination.ConfigDetail.routeFor(profileId))
                        },
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
                    MeScreen(
                        onOpenQqAccount = { AppNavigator.open(navController, AppDestination.QQAccount.route) },
                        onOpenSettings = { AppNavigator.open(navController, AppDestination.SettingsHub.route) },
                        onOpenLogs = { AppNavigator.open(navController, AppDestination.Logs.route) },
                        onOpenAssets = { AppNavigator.open(navController, AppDestination.Assets.route) },
                        onOpenBackup = { AppNavigator.open(navController, AppDestination.BackupHub.route) },
                    )
                }
                composable(AppDestination.QQAccount.route) {
                    QQAccountCenterScreen(
                        onBack = { AppNavigator.back(navController) },
                        onOpenLogin = { AppNavigator.open(navController, AppDestination.QQLogin.route) },
                    )
                }
                composable(AppDestination.QQLogin.route) { QQLoginScreen(onBack = { AppNavigator.back(navController) }) }
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

        if (showFloatingBottomNav) {
            FloatingBottomNavBar(
                destinations = destinations,
                selectedRoute = activeMainRoute,
                onSelect = { destination -> AppNavigator.openTopLevel(navController, destination) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }

        RuntimeOverlay(
            status = runtimeState.status,
            details = runtimeState.details,
            progressLabel = runtimeState.progressLabel,
            progressPercent = runtimeState.progressPercent,
            installerCached = runtimeState.installerCached,
            onStart = { ContainerBridgeController.start(context) },
            onStop = { ContainerBridgeController.stop(context) },
        )
    }
}
