package com.astrbot.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.data.RuntimeCacheCleanupState
import com.astrbot.android.ui.app.MonochromeUi

@Composable
internal fun RuntimeCacheCleanupCard(
    state: RuntimeCacheCleanupState,
    onClearClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(MonochromeUi.mutedSurface, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = MonochromeUi.textPrimary)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.me_cache_cleanup_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MonochromeUi.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.me_cache_cleanup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
            }
            if (state.lastSummary.isNotBlank()) {
                Text(
                    text = stringResource(R.string.me_cache_cleanup_last_result, state.lastSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
            }
            Button(
                onClick = onClearClick,
                enabled = !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MonochromeUi.strong,
                    contentColor = MonochromeUi.strongText,
                    disabledContainerColor = MonochromeUi.border,
                    disabledContentColor = MonochromeUi.textSecondary,
                ),
            ) {
                if (state.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = if (MonochromeUi.isDarkTheme) Color(0xFF111318) else Color.White,
                    )
                }
                Text(
                    text = if (state.isRunning) {
                        stringResource(R.string.me_cache_cleanup_running)
                    } else {
                        stringResource(R.string.me_cache_cleanup_action)
                    },
                )
            }
        }
    }
}
