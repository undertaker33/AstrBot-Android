package com.astrbot.android.ui.voiceasset
import com.astrbot.android.ui.settings.formatDuration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.ui.app.MonochromeUi

@Composable
internal fun VoiceAssetReferenceLibraryHeader() {
    AssetSectionHeader(
        title = stringResource(R.string.voice_asset_reference_library_title),
        description = stringResource(R.string.voice_asset_reference_library_desc),
    )
}

@Composable
internal fun VoiceAssetReferenceAssetCard(
    asset: TtsVoiceReferenceAsset,
    expanded: Boolean,
    isImportingReferenceAudio: Boolean,
    onToggleExpanded: () -> Unit,
    onAddClip: () -> Unit,
    onClearReferenceAudio: () -> Unit,
    onDeleteReferenceClip: (String) -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(asset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.voice_asset_clip_count_value, asset.clips.size),
                        color = MonochromeUi.textSecondary,
                    )
                    if (asset.providerBindings.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.voice_asset_binding_count_value, asset.providerBindings.size),
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
                TextButton(onClick = onToggleExpanded, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary)) {
                    Text(
                        stringResource(
                            if (expanded) R.string.voice_asset_hide_details_action else R.string.voice_asset_show_details_action,
                        ),
                    )
                }
            }
            if (asset.clips.isEmpty() && asset.localPath.isBlank()) {
                Text(
                    text = stringResource(R.string.voice_asset_reference_removed),
                    color = MonochromeUi.textSecondary,
                )
            } else {
                val previewClip = asset.clips.firstOrNull()
                if (previewClip != null) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        color = MonochromeUi.inputBackground,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.voice_asset_latest_clip_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MonochromeUi.textSecondary,
                            )
                            if (previewClip.durationMs > 0L) {
                                Text(
                                    text = stringResource(
                                        R.string.voice_asset_duration_value,
                                        formatDuration(previewClip.durationMs),
                                    ),
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                            if (previewClip.sampleRateHz > 0) {
                                Text(
                                    text = stringResource(R.string.voice_asset_sample_rate_value, previewClip.sampleRateHz),
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
            if (expanded) {
                asset.clips.forEachIndexed { index, clip ->
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        color = MonochromeUi.inputBackground,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.voice_asset_clip_title, index + 1),
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                stringResource(R.string.voice_asset_local_path_value, clip.localPath),
                                color = MonochromeUi.textSecondary,
                            )
                            if (clip.durationMs > 0L) {
                                Text(
                                    stringResource(R.string.voice_asset_duration_value, formatDuration(clip.durationMs)),
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                            if (clip.sampleRateHz > 0) {
                                Text(
                                    stringResource(R.string.voice_asset_sample_rate_value, clip.sampleRateHz),
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                            TextButton(
                                onClick = { onDeleteReferenceClip(clip.id) },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
                            ) {
                                Text(stringResource(R.string.voice_asset_remove_clip_action))
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = onAddClip,
                enabled = !isImportingReferenceAudio,
                modifier = Modifier.fillMaxWidth(),
                colors = voiceAssetOutlinedButtonColors(),
                border = BorderStroke(1.dp, MonochromeUi.border),
            ) {
                Text(stringResource(R.string.voice_asset_add_clip_action))
            }
            OutlinedButton(
                onClick = onClearReferenceAudio,
                modifier = Modifier.fillMaxWidth(),
                colors = voiceAssetOutlinedButtonColors(),
                border = BorderStroke(1.dp, MonochromeUi.border),
            ) {
                Text(stringResource(R.string.voice_asset_clear_reference_action))
            }
        }
    }
}
