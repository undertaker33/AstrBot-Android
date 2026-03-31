package com.astrbot.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.astrbot.android.R
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.ui.navigation.AstrBotAppNavGraph
import com.astrbot.android.ui.navigation.AstrBotAppTopBar
import com.astrbot.android.ui.navigation.activeMainDestination
import com.astrbot.android.ui.screen.BotWorkspaceTab
import com.astrbot.android.ui.screen.ChatDrawerContent
import com.astrbot.android.ui.viewmodel.BridgeViewModel
import com.astrbot.android.ui.viewmodel.ChatViewModel
import com.astrbot.android.ui.viewmodel.QQLoginViewModel
import kotlinx.coroutines.launch

@Composable
fun AstrBotApp(bridgeViewModel: BridgeViewModel = astrBotViewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val chatViewModel: ChatViewModel = astrBotViewModel()
    val qqLoginViewModel: QQLoginViewModel = astrBotViewModel()
    val chatDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var botWorkspaceTab by remember { mutableStateOf(BotWorkspaceTab.BOTS) }
    var configSelectedIds by remember { mutableStateOf(setOf<String>()) }
    val destinations = mainNavigationDestinations(
        botsLabel = stringResource(R.string.nav_bots),
        pluginsLabel = stringResource(R.string.nav_plugins),
        chatLabel = stringResource(R.string.nav_chat),
        configLabel = stringResource(R.string.nav_config),
        meLabel = stringResource(R.string.nav_me),
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
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val activeMainDestination = activeMainDestination(currentDestination, destinations)
    val activeMainRoute = activeMainDestination?.first?.route
    val contentTopPadding = navGraphContentTopPadding(
        activeMainRoute = activeMainRoute,
        safeDrawingTopPadding = safeDrawingPadding.calculateTopPadding(),
    )
    val currentMainSwipePage = currentMainSwipePage(
        activeMainRoute = activeMainRoute,
        botWorkspaceTab = botWorkspaceTab,
    )
    val hostGlobalChatDrawer = shouldHostGlobalChatDrawer(
        activeMainRoute = activeMainRoute,
        chatDrawerOpen = chatDrawerState.isOpen,
    )
    val showFloatingBottomNav = shouldShowFloatingBottomNav(
        activeMainRoute = activeMainRoute,
        imeVisible = imeVisible,
        chatDrawerOpen = chatDrawerState.isOpen,
    )

    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.route != AppDestination.Chat.route) {
            chatSelectorExpanded = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        ModalNavigationDrawer(
            drawerState = chatDrawerState,
            gesturesEnabled = hostGlobalChatDrawer,
            drawerContent = {
                if (hostGlobalChatDrawer) {
                    ChatDrawerContent(
                        chatViewModel = chatViewModel,
                        drawerState = chatDrawerState,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.material3.Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    AstrBotAppNavGraph(
                        navController = navController,
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(top = contentTopPadding)
                            .padding(
                                bottom = floatingBottomNavContentPadding(
                                    activeMainRoute = activeMainRoute,
                                    visible = showFloatingBottomNav,
                                ),
                            ),
                        currentMainSwipePage = currentMainSwipePage,
                        onMainSwipePageSettled = { page ->
                            botWorkspaceTabForMainSwipePage(page)?.let { tab ->
                                botWorkspaceTab = tab
                                if (activeMainRoute != AppDestination.Bots.route) {
                                    AppNavigator.openTopLevel(navController, AppDestination.Bots)
                                }
                            } ?: run {
                                when (page) {
                                    MainSwipePage.PLUGINS -> AppNavigator.openTopLevel(navController, AppDestination.Plugins)
                                    MainSwipePage.CHAT -> AppNavigator.openTopLevel(navController, AppDestination.Chat)
                                    MainSwipePage.CONFIG -> AppNavigator.openTopLevel(navController, AppDestination.Config)
                                    MainSwipePage.ME -> AppNavigator.openTopLevel(navController, AppDestination.Me)
                                    else -> Unit
                                }
                            }
                        },
                        botWorkspaceTab = botWorkspaceTab,
                        onBotWorkspaceTabChange = { botWorkspaceTab = it },
                        chatViewModel = chatViewModel,
                        qqLoginViewModel = qqLoginViewModel,
                        chatDrawerState = chatDrawerState,
                        floatingBottomNavPadding = chatBottomBarPadding(showFloatingBottomNav),
                        configSelectedIds = configSelectedIds,
                        onConfigSelectedIdsChange = { configSelectedIds = it },
                    )
                }

                if (showFloatingBottomNav) {
                    FloatingBottomNavBar(
                        destinations = destinations,
                        selectedRoute = activeMainRoute ?: AppDestination.Chat.route,
                        onSelect = { destination -> AppNavigator.openTopLevel(navController, destination) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }

        AstrBotAppTopBar(
            activeMainDestination = activeMainDestination,
            botWorkspaceTab = botWorkspaceTab,
            configSelectedIds = configSelectedIds,
            currentChatBot = currentChatBot,
            chatBots = chatBots,
            chatPersonas = chatPersonas,
            currentPersonaId = currentChatPersona?.id,
            chatSelectorExpanded = chatSelectorExpanded,
            onBotWorkspaceTabChange = { botWorkspaceTab = it },
            onConfigSelectionClear = { configSelectedIds = emptySet() },
            onOpenHistory = { scope.launch { chatDrawerState.open() } },
            onBotSelectorExpandedChange = { chatSelectorExpanded = it },
            onSelectBot = { chatViewModel.selectBot(it) },
            onSelectPersona = { chatViewModel.selectPersona(it) },
        )

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

private fun mainNavigationDestinations(
    botsLabel: String,
    pluginsLabel: String,
    chatLabel: String,
    configLabel: String,
    meLabel: String,
): List<Pair<AppDestination, String>> = listOf(
    AppDestination.Bots to botsLabel,
    AppDestination.Plugins to pluginsLabel,
    AppDestination.Chat to chatLabel,
    AppDestination.Config to configLabel,
    AppDestination.Me to meLabel,
)

internal fun mainNavigationDestinationsForTest(): List<AppDestination> = listOf(
    AppDestination.Bots,
    AppDestination.Plugins,
    AppDestination.Chat,
    AppDestination.Config,
    AppDestination.Me,
)
