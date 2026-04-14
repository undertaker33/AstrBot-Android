package com.astrbot.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.monochromeSwitchColors

internal fun formatTimedCleanupInterval(
    enabled: Boolean,
    intervalHours: Int,
    intervalMinutes: Int,
): String {
    if (!enabled) return "Off"
    val parts = buildList {
        if (intervalHours > 0) add("${intervalHours}h")
        if (intervalMinutes > 0) add("${intervalMinutes}m")
    }
    return if (parts.isEmpty()) "Off" else "Every ${parts.joinToString(separator = " ")}"
}

@Composable
internal fun TimedCleanupDialog(
    enabled: Boolean,
    intervalHours: Int,
    intervalMinutes: Int,
    titleResId: Int,
    hoursResId: Int,
    minutesResId: Int,
    enableResId: Int,
    invalidIntervalResId: Int,
    dialogTag: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int, Int) -> Unit,
) {
    var localEnabled by remember(enabled, intervalHours, intervalMinutes) { mutableStateOf(enabled) }
    var hours by remember(enabled, intervalHours, intervalMinutes) { mutableIntStateOf(intervalHours) }
    var minutes by remember(enabled, intervalHours, intervalMinutes) { mutableIntStateOf(intervalMinutes) }
    val intervalInvalid = localEnabled && hours == 0 && minutes == 0

    AlertDialog(
        modifier = Modifier.testTag(dialogTag),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleResId)) },
        containerColor = if (MonochromeUi.isDarkTheme) MonochromeUi.cardAltBackground else MonochromeUi.elevatedSurface,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textPrimary,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IntervalDropdownField(
                        label = stringResource(hoursResId),
                        value = hours,
                        range = 0..23,
                        suffix = "h",
                        modifier = Modifier.weight(1f),
                        onValueSelected = { hours = it },
                    )
                    IntervalDropdownField(
                        label = stringResource(minutesResId),
                        value = minutes,
                        range = 0..59,
                        suffix = "m",
                        modifier = Modifier.weight(1f),
                        onValueSelected = { minutes = it },
                    )
                }
                Text(
                    text = stringResource(
                        R.string.plugin_logs_auto_clear_summary,
                        formatTimedCleanupInterval(
                            enabled = localEnabled,
                            intervalHours = hours,
                            intervalMinutes = minutes,
                        ),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MonochromeUi.textPrimary,
                )
                if (intervalInvalid) {
                    Text(
                        text = stringResource(invalidIntervalResId),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Switch(
                        checked = localEnabled,
                        onCheckedChange = { localEnabled = it },
                        modifier = Modifier.scale(0.85f),
                        colors = monochromeSwitchColors(),
                    )
                    Text(
                        text = stringResource(enableResId),
                        style = MaterialTheme.typography.labelMedium,
                        color = MonochromeUi.textPrimary,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    onClick = { onConfirm(localEnabled, hours, minutes) },
                    enabled = !intervalInvalid,
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun IntervalDropdownField(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    modifier: Modifier = Modifier,
    onValueSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MonochromeUi.textPrimary,
        )
        MonochromeSecondaryActionButton(
            label = "$value$suffix",
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            range.forEach { option ->
                DropdownMenuItem(
                    text = { Text("$option$suffix") },
                    onClick = {
                        onValueSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun timedCleanupDialogUsesUnifiedMonochromeStyle(): Boolean = true
