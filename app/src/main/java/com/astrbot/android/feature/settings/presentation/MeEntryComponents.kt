package com.astrbot.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.ui.app.MonochromeUi

@Composable
internal fun EntryListPage(
    entries: List<EntryCardState>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .background(MonochromeUi.pageBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Surface(
                shape = MonochromeUi.radiusGrouped,
                color = MonochromeUi.cardBackground,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    entries.forEachIndexed { index, entry ->
                        AccountEntryCard(entry)
                        if (index < entries.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp)
                                    .background(MonochromeUi.divider)
                                    .padding(top = 0.5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccountEntryCard(entry: EntryCardState) {
    Surface(
        onClick = entry.onClick,
        shape = MonochromeUi.radiusListItem,
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, MonochromeUi.radiusIcon)
                    .padding(8.dp),
            ) {
                Icon(entry.icon, contentDescription = null, tint = MonochromeUi.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MonochromeUi.textSecondary,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MonochromeUi.textSecondary,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
    }
}

internal data class EntryCardState(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
