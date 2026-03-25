package com.astrbot.android.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay

// 录音中红点、按钮呼吸等“往返脉冲”统一走这里，页面只关心取值。
@Composable
internal fun rememberPulsingAlpha(
    initialValue: Float = 0.35f,
    targetValue: Float = 1f,
    durationMillis: Int = AppMotionTokens.recordingPulseMillis,
    label: String,
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = label)
    val value by infiniteTransition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}Value",
    )
    return value
}

// 缩放脉冲是透明度脉冲的一个特化，统一复用同一套节奏配置。
@Composable
internal fun rememberPulsingScale(
    initialValue: Float = 1f,
    targetValue: Float = 1.14f,
    durationMillis: Int = AppMotionTokens.voiceButtonPulseMillis,
    label: String,
): Float {
    return rememberPulsingAlpha(
        initialValue = initialValue,
        targetValue = targetValue,
        durationMillis = durationMillis,
        label = label,
    )
}

// 主题/语言切换前统一触发一次全局过渡，避免每个页面重复写请求逻辑。
suspend internal fun runWithAppUiTransition(
    colorArgb: Int,
    action: suspend () -> Unit,
) {
    AppUiTransitionState.requestTransition(colorArgb)
    delay(50)
    action()
}

// 列表滚动动画统一收口，后面如果要改时长或切换策略，只改这一处。
suspend internal fun LazyListState.animateToItemWithAppMotion(index: Int) {
    animateScrollToItem(index)
}
