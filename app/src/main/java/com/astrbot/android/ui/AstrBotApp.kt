package com.astrbot.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.R
import com.astrbot.android.ui.screen.BotScreen
import com.astrbot.android.ui.screen.ChatScreen
import com.astrbot.android.ui.screen.ConfigScreen
import com.astrbot.android.ui.screen.LogScreen
import com.astrbot.android.ui.screen.MeScreen
import com.astrbot.android.ui.screen.PersonaScreen
import com.astrbot.android.ui.screen.ProviderScreen
import com.astrbot.android.ui.screen.QQAccountCenterScreen
import com.astrbot.android.ui.screen.QQLoginScreen
import com.astrbot.android.ui.screen.SettingsHubScreen
import com.astrbot.android.ui.screen.SettingsScreen
import com.astrbot.android.ui.screen.SubPageScaffold
import com.astrbot.android.ui.viewmodel.BridgeViewModel
import kotlin.math.roundToInt

@Composable
fun AstrBotApp(bridgeViewModel: BridgeViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var botModelsSelected by remember { mutableStateOf(false) }
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
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        val activeMainDestination = destinations.firstOrNull { (destination, _) ->
            currentDestination?.hierarchy?.any { it.route == destination.route } == true
        }
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

                        AppDestination.Chat -> Unit
                        AppDestination.Config -> MainTopBar(title = stringResource(R.string.nav_config), titleAlignment = TopBarTitleAlignment.Center)
                        AppDestination.Me -> MainTopBar(title = stringResource(R.string.nav_me), titleAlignment = TopBarTitleAlignment.Center)
                        else -> MainTopBar(title = label)
                    }
                }
            },
            bottomBar = {
                if (destinations.any { (destination, _) ->
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    } && !(activeMainDestination?.first == AppDestination.Chat && imeVisible)
                ) {
                    NavigationBar(
                        modifier = Modifier.navigationBarsPadding(),
                        containerColor = MonochromeUi.navBarBackground,
                        tonalElevation = 0.dp,
                    ) {
                        destinations.forEach { (destination, label) ->
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MonochromeUi.textPrimary,
                                    selectedTextColor = MonochromeUi.textPrimary,
                                    unselectedIconColor = MonochromeUi.textSecondary,
                                    unselectedTextColor = MonochromeUi.textSecondary,
                                    indicatorColor = MonochromeUi.activeIndicator,
                                ),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.Chat.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(AppDestination.Bots.route) {
                    BotScreen(showModels = botModelsSelected, onShowBots = { botModelsSelected = false })
                }
                composable(AppDestination.Personas.route) { PersonaScreen() }
                composable(AppDestination.Chat.route) { ChatScreen() }
                composable(AppDestination.Config.route) { ConfigScreen() }
                composable(AppDestination.Logs.route) {
                    SubPageScaffold(title = stringResource(R.string.nav_logs), onBack = { navController.popBackStack() }) { inner ->
                        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                            LogScreen(showContext = false)
                        }
                    }
                }
                composable(AppDestination.Me.route) {
                    MeScreen(
                        onOpenQqAccount = { navController.navigate(AppDestination.QQAccount.route) },
                        onOpenSettings = { navController.navigate(AppDestination.SettingsHub.route) },
                        onOpenLogs = { navController.navigate(AppDestination.Logs.route) },
                    )
                }
                composable(AppDestination.QQAccount.route) {
                    QQAccountCenterScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLogin = { navController.navigate(AppDestination.QQLogin.route) },
                    )
                }
                composable(AppDestination.QQLogin.route) { QQLoginScreen(onBack = { navController.popBackStack() }) }
                composable(AppDestination.SettingsHub.route) {
                    SettingsHubScreen(
                        onBack = { navController.popBackStack() },
                        onOpenRuntime = { navController.navigate(AppDestination.Runtime.route) },
                    )
                }
                composable(AppDestination.Models.route) { ProviderScreen(onBack = { navController.popBackStack() }) }
                composable(AppDestination.Runtime.route) { SettingsScreen(onBack = { navController.popBackStack() }) }
            }
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

@Composable
private fun MainTopBar(
    title: String,
    titleAlignment: TopBarTitleAlignment = TopBarTitleAlignment.Center,
    leftContent: @Composable (() -> Unit)? = null,
) {
    Surface(color = MonochromeUi.topBarSurface, shadowElevation = 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (titleAlignment == TopBarTitleAlignment.Center) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TopBarTitlePill(title)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    TopBarTitlePill(title)
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leftContent?.invoke()
            }
        }
    }
}

@Composable
private fun TopBarTitlePill(title: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MonochromeUi.strong, tonalElevation = 1.dp) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MonochromeUi.strongText,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TopBarToggle(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelectLeft) {
            Text(
                leftLabel,
                color = if (leftSelected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                fontWeight = if (leftSelected) FontWeight.Bold else FontWeight.Medium,
            )
        }
        Text("|", color = MonochromeUi.textSecondary)
        TextButton(onClick = onSelectRight) {
            Text(
                rightLabel,
                color = if (leftSelected) MonochromeUi.textSecondary else MonochromeUi.textPrimary,
                fontWeight = if (leftSelected) FontWeight.Medium else FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun ChatTopBar(
    onOpenHistory: () -> Unit,
    onOpenBotSelector: () -> Unit,
    botSelectorDropdown: @Composable (BoxScope.() -> Unit) = {},
) {
    Surface(color = MonochromeUi.topBarSurface, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Surface(onClick = onOpenHistory, shape = CircleShape, color = MonochromeUi.iconButtonSurface) {
                    Box(
                        modifier = Modifier.border(1.dp, MonochromeUi.border, CircleShape).padding(9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.chat_history), tint = MonochromeUi.textPrimary)
                    }
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.chat_title), color = MonochromeUi.textPrimary, fontWeight = FontWeight.SemiBold)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Surface(onClick = onOpenBotSelector, shape = RoundedCornerShape(16.dp), color = MonochromeUi.iconButtonSurface) {
                    Row(
                        modifier = Modifier.border(1.dp, MonochromeUi.border, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = stringResource(R.string.chat_selector_bots), color = MonochromeUi.textPrimary, maxLines = 1)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MonochromeUi.textPrimary)
                    }
                }
                botSelectorDropdown()
            }
        }
    }
}

private enum class TopBarTitleAlignment {
    Center,
    End,
}

@Composable
private fun RuntimeOverlay(
    status: String,
    details: String,
    progressLabel: String,
    progressPercent: Int,
    installerCached: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val runtimeTitle = stringResource(R.string.runtime_title)
    val runtimeMinimize = stringResource(R.string.runtime_minimize)
    val runtimeExpand = stringResource(R.string.runtime_expand)
    val runtimeStart = stringResource(R.string.runtime_start)
    val runtimeStop = stringResource(R.string.runtime_stop)
    var expanded by rememberSaveable { mutableStateOf(true) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(120f) }
    val progress = progressPercent.coerceIn(0, 100) / 100f

    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(12.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(expanded) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
            shape = if (expanded) RoundedCornerShape(24.dp) else CircleShape,
            tonalElevation = 10.dp,
            shadowElevation = 8.dp,
            color = Color(0xFF111827),
        ) {
            if (expanded) {
                Column(
                    modifier = Modifier.widthIn(max = 280.dp).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(runtimeTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(status, color = Color(0xFFD1D5DB))
                        }
                        IconButton(onClick = { expanded = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = runtimeMinimize,
                                tint = Color.White,
                            )
                        }
                    }
                    RuntimeProgressBar(
                        label = progressLabel.ifBlank {
                            if (installerCached) stringResource(R.string.runtime_installer_ready)
                            else stringResource(R.string.runtime_waiting)
                        },
                        progress = progress,
                        installerCached = installerCached,
                    )
                    Text(text = details, color = Color.White.copy(alpha = 0.74f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = onStart, shape = RoundedCornerShape(16.dp), color = Color(0xFF1F1F1F)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = runtimeStart, tint = Color.White)
                                Text(runtimeStart, color = Color.White)
                            }
                        }
                        Surface(onClick = onStop, shape = RoundedCornerShape(16.dp), color = Color(0xFF3B3B3B)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = runtimeStop, tint = Color.White)
                                Text(runtimeStop, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                Surface(onClick = { expanded = true }, shape = CircleShape, color = Color(0xFF111827)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(Icons.Outlined.Memory, contentDescription = runtimeExpand, tint = Color.White)
                        Text(status.take(1), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeProgressBar(
    label: String,
    progress: Float,
    installerCached: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.White)
        Box(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(Color(0xFFE5E7EB), RoundedCornerShape(999.dp))
                    .padding(vertical = 4.dp),
            )
        }
        Text(
            text = if (installerCached) {
                stringResource(R.string.runtime_installer_ready_details)
            } else {
                stringResource(R.string.runtime_downloading_details)
            },
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

private sealed class AppDestination(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Bots : AppDestination("bots", Icons.Outlined.SmartToy)
    data object Personas : AppDestination("personas", Icons.Outlined.Face)
    data object Chat : AppDestination("chat", Icons.Outlined.ChatBubbleOutline)
    data object Config : AppDestination("config", Icons.Outlined.Settings)
    data object Logs : AppDestination("logs", Icons.AutoMirrored.Outlined.List)
    data object Me : AppDestination("me", Icons.Outlined.PersonOutline)
    data object QQAccount : AppDestination("qq-account", Icons.Outlined.PersonOutline)
    data object QQLogin : AppDestination("qq-login", Icons.Outlined.PersonOutline)
    data object SettingsHub : AppDestination("settings-hub", Icons.Outlined.Settings)
    data object Models : AppDestination("models", Icons.Outlined.Memory)
    data object Runtime : AppDestination("runtime", Icons.Outlined.Settings)
}
