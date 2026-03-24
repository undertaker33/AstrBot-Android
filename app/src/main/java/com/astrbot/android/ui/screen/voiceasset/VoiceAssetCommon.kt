package com.astrbot.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astrbot.android.ui.MonochromeUi

@Composable
internal fun VoiceAssetMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MonochromeUi.inputBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
internal fun VoiceAssetFaceChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = if (selected) MonochromeUi.strong else MonochromeUi.inputBackground,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MonochromeUi.strongText else MonochromeUi.textPrimary,
        )
    }
}

@Composable
internal fun AssetSectionHeader(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            color = MonochromeUi.textSecondary,
        )
    }
}

@Composable
internal fun VoiceAssetEmptyCard(
    title: String,
    description: String,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
internal fun voiceAssetButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MonochromeUi.strong,
    contentColor = MonochromeUi.strongText,
    disabledContainerColor = MonochromeUi.border,
    disabledContentColor = MonochromeUi.textSecondary,
)

@Composable
internal fun voiceAssetOutlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    containerColor = Color.Transparent,
    contentColor = MonochromeUi.textPrimary,
    disabledContentColor = MonochromeUi.textSecondary,
)
