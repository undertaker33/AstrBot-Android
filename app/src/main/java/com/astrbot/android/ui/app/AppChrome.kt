package com.astrbot.android.ui.app

import androidx.compose.ui.unit.dp
import com.astrbot.android.ui.navigation.AppDestination

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.astrbot.android.R
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.PersonaProfile

/** 浮动底部导航栏占位高度，用于页面内容底部预留空间，避免被导航栏遮挡。 */
internal val FloatingBottomNavReservedPadding = 96.dp

/** 浮动底部导航栏中 FAB 按钮的底部内边距，确保 FAB 不会贴边。 */
internal val FloatingBottomNavFabBottomPadding = 88.dp
internal val AppTopBarHeight = 58.dp
internal val ChatDrawerTopSpacing = 8.dp

@Suppress("UNUSED_PARAMETER")
internal fun shouldShowFloatingBottomNav(
    activeMainRoute: String?,
    imeVisible: Boolean,
    chatDrawerOpen: Boolean = false,
): Boolean = activeMainRoute != null &&
    !(activeMainRoute == AppDestination.Chat.route && imeVisible)

internal fun shouldHostGlobalChatDrawer(
    activeMainRoute: String?,
    chatDrawerOpen: Boolean,
): Boolean = activeMainRoute == AppDestination.Chat.route || chatDrawerOpen

internal fun topLevelContentTopPadding(safeDrawingTopPadding: Dp): Dp =
    safeDrawingTopPadding + AppTopBarHeight

@Suppress("UNUSED_PARAMETER")
internal fun navGraphContentTopPadding(
    activeMainRoute: String?,
    safeDrawingTopPadding: Dp,
): Dp = 0.dp

internal fun secondaryPageHeaderTotalHeight(safeDrawingTopPadding: Dp): Dp =
    topLevelContentTopPadding(safeDrawingTopPadding)

internal fun chatDrawerContentTopPadding(safeDrawingTopPadding: Dp): Dp =
    topLevelContentTopPadding(safeDrawingTopPadding) + ChatDrawerTopSpacing

@Suppress("UNUSED_PARAMETER")
internal fun floatingBottomNavContentPadding(
    activeMainRoute: String?,
    visible: Boolean,
) = 0.dp

internal fun chatBottomBarPadding(visible: Boolean) = if (visible) FloatingBottomNavReservedPadding else 0.dp

@Composable
internal fun SelectionModeTopBar(
    count: Int,
    onCancel: () -> Unit,
) {
    Surface(color = MonochromeUi.topBarSurface, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(AppTopBarHeight)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_cancel), tint = MonochromeUi.textPrimary)
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = MonochromeUi.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.config_selected_count, count),
                    color = MonochromeUi.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun MainTopBar(
    title: String,
    titleAlignment: TopBarTitleAlignment = TopBarTitleAlignment.Center,
    leftContent: @Composable (() -> Unit)? = null,
) {
    Surface(color = MonochromeUi.topBarSurface, shadowElevation = 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(AppTopBarHeight)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (titleAlignment == TopBarTitleAlignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 64.dp),
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
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leftContent?.invoke()
            }
        }
    }
}

@Composable
internal fun TopBarTitlePill(title: String) {
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
internal fun TopBarToggle(
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
internal fun TopBarSegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { index, label ->
            TextButton(onClick = { onSelect(index) }) {
                Text(
                    text = label,
                    color = if (selectedIndex == index) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                )
            }
            if (index != options.lastIndex) {
                Text("|", color = MonochromeUi.textSecondary)
            }
        }
    }
}

@Composable
internal fun ChatTopBar(
    selectedBotName: String,
    onOpenHistory: () -> Unit,
    onOpenBotSelector: () -> Unit,
    botSelectorDropdown: @Composable (BoxScope.() -> Unit) = {},
) {
    Surface(color = MonochromeUi.topBarSurface, shadowElevation = 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(AppTopBarHeight)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Surface(
                        onClick = onOpenHistory,
                        shape = CircleShape,
                        color = MonochromeUi.elevatedSurface,
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = stringResource(R.string.chat_history),
                                tint = MonochromeUi.textPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MonochromeUi.strong,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_title),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            color = MonochromeUi.strongText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        onClick = onOpenBotSelector,
                        shape = RoundedCornerShape(18.dp),
                        color = MonochromeUi.elevatedSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = selectedBotName,
                                color = MonochromeUi.textPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                tint = MonochromeUi.textPrimary,
                            )
                        }
                    }
                    botSelectorDropdown()
                }
            }
        }
    }
}

/** 聊天顶部栏的 Bot / Persona 选择菜单，提供切换当前对话角色的 UI 入口。 */
@Composable
internal fun BoxScope.ChatTopBarSelectorMenu(
    expanded: Boolean,
    bots: List<BotProfile>,
    personas: List<PersonaProfile>,
    currentPersonaId: String?,
    onDismissRequest: () -> Unit,
    onSelectBot: (String) -> Unit,
    onSelectPersona: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.align(Alignment.TopEnd),
        containerColor = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        Text(
            text = stringResource(R.string.chat_selector_bots),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MonochromeUi.textSecondary,
        )
        bots.forEach { bot ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = bot.displayName,
                        color = MonochromeUi.textPrimary,
                    )
                },
                onClick = { onSelectBot(bot.id) },
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(1.dp)
                .background(MonochromeUi.border.copy(alpha = 0.45f)),
        )
        Text(
            text = stringResource(R.string.chat_selector_personas),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MonochromeUi.textSecondary,
        )
        personas
            .filter { it.enabled }
            .forEach { persona ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = persona.name,
                            color = MonochromeUi.textPrimary,
                        )
                    },
                    trailingIcon = {
                        if (persona.id == currentPersonaId) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MonochromeUi.textPrimary,
                            )
                        }
                    },
                    onClick = { onSelectPersona(persona.id) },
                )
            }
    }
}

/** 浮动底部导航栏组件，提供主页面之间的切换入口。 */
@Composable
internal fun FloatingBottomNavBar(
    destinations: List<Pair<AppDestination, String>>,
    selectedRoute: String?,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = MonochromeUi.navBarBackground.copy(alpha = if (MonochromeUi.isDarkTheme) 0.9f else 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, MonochromeUi.border.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { (destination, label) ->
                val selected = selectedRoute == destination.route
                val itemColor = if (selected) {
                    MonochromeUi.activeIndicator.copy(alpha = if (MonochromeUi.isDarkTheme) 0.72f else 0.92f)
                } else {
                    Color.Transparent
                }
                val contentColor = if (selected) MonochromeUi.textPrimary else MonochromeUi.textSecondary
                Surface(
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = itemColor,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = label,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor,
                        )
                        Text(
                            text = label,
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

internal enum class TopBarTitleAlignment {
    Center,
    End,
}
