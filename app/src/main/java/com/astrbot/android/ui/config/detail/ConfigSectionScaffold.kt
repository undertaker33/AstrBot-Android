package com.astrbot.android.ui.config.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.ui.app.MonochromeUi

@Composable
internal fun ConfigFieldGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MonochromeUi.inputBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun ConfigSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MonochromeUi.cardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            if (shouldRenderConfigDescription(subtitle)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
            }
            content()
        }
    }
}

@Composable
internal fun PlaceholderSectionCard(
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MonochromeUi.cardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            if (shouldRenderConfigDescription(subtitle)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
            }
        }
    }
}

@Composable
internal fun InlineConfigNotice(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MonochromeUi.inputBackground,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MonochromeUi.textSecondary,
        )
    }
}

@Composable
internal fun LabeledField(
    title: String,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            if (shouldRenderConfigDescription(subtitle)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
            }
            content()
        }
    }
}

internal fun shouldRenderConfigDescription(text: String): Boolean = text.isNotBlank()
