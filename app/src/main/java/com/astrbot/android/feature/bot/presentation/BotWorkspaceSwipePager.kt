package com.astrbot.android.ui.bot

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.roundToInt

private const val WorkspaceSwipeSettleThresholdFraction = 0.33f
private const val WorkspaceSwipeVelocityThreshold = 1200f

internal data class BotWorkspaceSwipePagerPreviewState(
    val currentTab: BotWorkspaceTab,
    val adjacentTab: BotWorkspaceTab?,
    val currentTabVisibleFraction: Float,
    val adjacentTabVisibleFraction: Float,
) {
    companion object {
        fun drag(
            from: BotWorkspaceTab,
            deltaFraction: Float,
        ): BotWorkspaceSwipePagerPreviewState {
            val adjacentTab = adjacentBotWorkspaceTab(from, deltaFraction)
            val revealedFraction = abs(deltaFraction).coerceIn(0f, 1f)
            return BotWorkspaceSwipePagerPreviewState(
                currentTab = from,
                adjacentTab = adjacentTab,
                currentTabVisibleFraction = 1f - revealedFraction,
                adjacentTabVisibleFraction = if (adjacentTab == null) 0f else revealedFraction,
            )
        }
    }
}

internal fun settleBotWorkspaceTab(
    current: BotWorkspaceTab,
    deltaFraction: Float,
    velocity: Float,
): BotWorkspaceTab {
    val adjacent = adjacentBotWorkspaceTab(current, deltaFraction) ?: return current
    val crossedThreshold = abs(deltaFraction) >= WorkspaceSwipeSettleThresholdFraction
    val crossedVelocity = abs(velocity) >= WorkspaceSwipeVelocityThreshold
    return if (crossedThreshold || crossedVelocity) adjacent else current
}

private fun adjacentBotWorkspaceTab(
    current: BotWorkspaceTab,
    deltaFraction: Float,
): BotWorkspaceTab? {
    return when {
        deltaFraction < 0f -> when (current) {
            BotWorkspaceTab.BOTS -> BotWorkspaceTab.MODELS
            BotWorkspaceTab.MODELS -> BotWorkspaceTab.PERSONAS
            BotWorkspaceTab.PERSONAS -> null
        }

        deltaFraction > 0f -> when (current) {
            BotWorkspaceTab.BOTS -> null
            BotWorkspaceTab.MODELS -> BotWorkspaceTab.BOTS
            BotWorkspaceTab.PERSONAS -> BotWorkspaceTab.MODELS
        }

        else -> null
    }
}

@Composable
internal fun BotWorkspaceSwipePager(
    currentTab: BotWorkspaceTab,
    onTabChange: (BotWorkspaceTab) -> Unit,
    modifier: Modifier = Modifier,
    botsPage: @Composable () -> Unit,
    modelsPage: @Composable () -> Unit,
    personasPage: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember(currentTab) { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(currentTab, containerWidthPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (containerWidthPx <= 0f) return@detectHorizontalDragGestures
                        val maxDrag = containerWidthPx
                        val proposed = dragOffsetPx + dragAmount
                        val bounded = when {
                            currentTab == BotWorkspaceTab.BOTS -> proposed.coerceIn(-maxDrag, 0f)
                            currentTab == BotWorkspaceTab.PERSONAS -> proposed.coerceIn(0f, maxDrag)
                            else -> proposed.coerceIn(-maxDrag, maxDrag)
                        }
                        val adjacent = adjacentBotWorkspaceTab(
                            current = currentTab,
                            deltaFraction = bounded / containerWidthPx,
                        )
                        dragOffsetPx = if (adjacent == null) 0f else bounded
                    },
                    onDragEnd = {
                        if (containerWidthPx <= 0f) return@detectHorizontalDragGestures
                        val target = settleBotWorkspaceTab(
                            current = currentTab,
                            deltaFraction = dragOffsetPx / containerWidthPx,
                            velocity = 0f,
                        )
                        dragOffsetPx = 0f
                        onTabChange(target)
                    },
                    onDragCancel = {
                        dragOffsetPx = 0f
                    },
                )
            },
    ) {
        containerWidthPx = with(density) { maxWidth.toPx() }
        val width = containerWidthPx
        if (width <= 0f) {
            return@BoxWithConstraints
        }
        val currentIndex = currentTab.ordinal
        val dragFraction = (dragOffsetPx / width).coerceIn(-1f, 1f)

        WorkspacePage(
            pageIndex = 0,
            currentIndex = currentIndex,
            dragFraction = dragFraction,
        ) {
            botsPage()
        }
        WorkspacePage(
            pageIndex = 1,
            currentIndex = currentIndex,
            dragFraction = dragFraction,
        ) {
            modelsPage()
        }
        WorkspacePage(
            pageIndex = 2,
            currentIndex = currentIndex,
            dragFraction = dragFraction,
        ) {
            personasPage()
        }
    }
}

@Composable
private fun WorkspacePage(
    pageIndex: Int,
    currentIndex: Int,
    dragFraction: Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = constraints.maxWidth
                val offsetFraction = (pageIndex - currentIndex) + dragFraction
                val xOffset = (offsetFraction * width).roundToInt()
                layout(width, constraints.maxHeight) {
                    placeable.placeRelative(x = xOffset, y = 0)
                }
            },
    ) {
        content()
    }
}
