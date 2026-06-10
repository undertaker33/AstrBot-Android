package com.elymbot.android.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.elymbot.android.ui.app.MonochromeUi
import com.elymbot.android.ui.app.RegisterSecondaryTopBar
import com.elymbot.android.ui.app.SecondaryTopBarPlaceholder
import com.elymbot.android.ui.app.SecondaryTopBarSpec

@Composable
internal fun GeofenceSubPageScaffold(
    route: String = "",
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    RegisterSecondaryTopBar(
        route = route,
        spec = SecondaryTopBarSpec.SubPage(
            title = title,
            onBack = onBack,
        ),
    )
    Scaffold(
        topBar = { SecondaryTopBarPlaceholder() },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MonochromeUi.pageBackground,
        content = content,
    )
}
