package com.astrbot.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun MainSwipeRail(
    currentPage: MainSwipePage,
    onPageSettled: (MainSwipePage) -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    pages: Map<MainSwipePage, @Composable () -> Unit>,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val displayPagePosition = remember { Animatable(currentPage.ordinal.toFloat()) }

    LaunchedEffect(currentPage) {
        if (!isDragging) {
            displayPagePosition.animateTo(
                targetValue = currentPage.ordinal.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val dragState = rememberDraggableState { dragAmount ->
        if (containerWidthPx <= 0f) return@rememberDraggableState
        val currentOffsetFraction = currentPage.ordinal - displayPagePosition.value
        val dragDeltaFraction = dragAmount / containerWidthPx
        val nextOffsetFraction = applyMainSwipeRailDrag(
            current = currentPage,
            currentOffsetFraction = currentOffsetFraction,
            dragDeltaFraction = dragDeltaFraction,
        )
        scope.launch {
            displayPagePosition.snapTo(currentPage.ordinal - nextOffsetFraction)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .draggable(
                enabled = swipeEnabled,
                orientation = Orientation.Horizontal,
                state = dragState,
                onDragStarted = { isDragging = true },
                onDragStopped = { velocity ->
                    isDragging = false
                    if (containerWidthPx <= 0f) return@draggable
                    val deltaFraction = currentPage.ordinal - displayPagePosition.value
                    val targetPage = settleMainSwipePage(
                        current = currentPage,
                        deltaFraction = deltaFraction,
                        velocity = velocity,
                    )
                    scope.launch {
                        displayPagePosition.animateTo(
                            targetValue = targetPage.ordinal.toFloat(),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                        if (targetPage != currentPage) {
                            onPageSettled(targetPage)
                        } else if (abs(deltaFraction) > 0f) {
                            displayPagePosition.snapTo(currentPage.ordinal.toFloat())
                        }
                    }
                },
            ),
    ) {
        containerWidthPx = with(density) { maxWidth.toPx() }
        val width = containerWidthPx
        if (width <= 0f) return@BoxWithConstraints

        MainSwipePage.entries.forEach { page ->
            RailPage(
                pageIndex = page.ordinal,
                displayPagePosition = displayPagePosition.value,
            ) {
                pages.getValue(page).invoke()
            }
        }
    }
}

@Composable
private fun RailPage(
    pageIndex: Int,
    displayPagePosition: Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = constraints.maxWidth
                val offsetFraction = pageIndex - displayPagePosition
                val xOffset = (offsetFraction * width).roundToInt()
                layout(width, constraints.maxHeight) {
                    placeable.placeRelative(x = xOffset, y = 0)
                }
            },
    ) {
        content()
    }
}
