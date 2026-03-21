package com.astrbot.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.ui.MonochromeUi

@Composable
internal fun VoiceAssetOverviewCard(
    referenceAssetCount: Int,
    totalReferenceClipCount: Int,
    clonedVoiceCount: Int,
    totalReferenceDurationMs: Long,
    lastVoiceCloneMessage: String,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.asset_tts_voice_assets_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.voice_asset_overview_desc),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VoiceAssetMetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.voice_asset_metric_reference_sets),
                    value = referenceAssetCount.toString(),
                )
                VoiceAssetMetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.voice_asset_metric_clips),
                    value = totalReferenceClipCount.toString(),
                )
                VoiceAssetMetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.voice_asset_metric_cloned_voices),
                    value = clonedVoiceCount.toString(),
                )
            }
            if (totalReferenceDurationMs > 0L) {
                Text(
                    text = stringResource(
                        R.string.voice_asset_total_duration_value,
                        formatDuration(totalReferenceDurationMs),
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
            }
            if (lastVoiceCloneMessage.isNotBlank()) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = MonochromeUi.inputBackground,
                ) {
                    Text(
                        text = lastVoiceCloneMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        color = MonochromeUi.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
