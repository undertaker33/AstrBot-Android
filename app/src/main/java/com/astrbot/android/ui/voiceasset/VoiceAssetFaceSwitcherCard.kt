package com.astrbot.android.ui.voiceasset

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
import com.astrbot.android.ui.app.MonochromeUi

@Composable
internal fun VoiceAssetFaceSwitcherCard(
    currentFace: String,
    onFaceChange: (String) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.asset_tts_voice_assets_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VoiceAssetFaceChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.voice_asset_face_overview),
                    selected = currentFace == "overview",
                    onClick = { onFaceChange("overview") },
                )
                VoiceAssetFaceChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.voice_asset_face_import),
                    selected = currentFace == "import",
                    onClick = { onFaceChange("import") },
                )
                VoiceAssetFaceChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.voice_asset_face_clone),
                    selected = currentFace == "clone",
                    onClick = { onFaceChange("clone") },
                )
                VoiceAssetFaceChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.voice_asset_face_manage),
                    selected = currentFace == "manage",
                    onClick = { onFaceChange("manage") },
                )
            }
        }
    }
}
