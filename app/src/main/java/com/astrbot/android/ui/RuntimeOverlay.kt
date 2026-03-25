package com.astrbot.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import kotlin.math.roundToInt

@Composable
internal fun RuntimeOverlay(
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
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(12.dp),
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
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(16.dp),
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
