package com.astrbot.android.ui.screen

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
import com.astrbot.android.ui.MonochromeUi

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEach { entry ->
                    AccountEntryCard(entry)
                }
            }
        }
    }
}

@Composable
internal fun AccountEntryCard(entry: EntryCardState) {
    Surface(
        onClick = entry.onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Icon(entry.icon, contentDescription = null, tint = MonochromeUi.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MonochromeUi.textPrimary,
                )
            }
        }
    }
}

internal data class EntryCardState(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
