package com.astrbot.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.astrbot.android.R
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.RegisterSecondaryTopBar
import com.astrbot.android.ui.SecondaryTopBarPlaceholder
import com.astrbot.android.ui.SecondaryTopBarSpec

@Composable
internal fun SubPageScaffold(
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

@Composable
internal fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Surface(
        color = MonochromeUi.pageBackground,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(com.astrbot.android.ui.AppTopBarHeight)
                .padding(start = 12.dp, end = 12.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MonochromeUi.iconButtonSurface,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(MonochromeUi.iconButtonSurface, CircleShape)
                        .border(1.dp, MonochromeUi.border, CircleShape)
                        .padding(9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MonochromeUi.textPrimary,
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MonochromeUi.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
