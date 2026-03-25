package com.astrbot.android.ui

// 统一维护全局动画节奏，避免页面里散落魔法数字。
internal object AppMotionTokens {
    // 页面间切换：主导航之间更轻，层级推进更明显。
    // 底部导航切页要更干脆，只做无位移淡切，所以时长压短一些。
    const val bottomNavSwitchMillis = 120
    const val routeCrossfadeMillis = 180
    const val routePushMillis = 260
    const val routePopMillis = 220
    const val routeFadeMillis = 140
    const val routeSlideDivisor = 6

    // 页面内反馈：录音提示和语音按钮呼吸动画。
    const val recordingPulseMillis = 900
    const val voiceButtonPulseMillis = 620

    // 列表类交互统一使用同一套平滑滚动节奏。
    const val listScrollMillis = 280
}
