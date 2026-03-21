package com.astrbot.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors

@Composable
internal fun VoiceAssetImportCard(
    selectedImportFileName: String,
    referenceName: String,
    onReferenceNameChange: (String) -> Unit,
    canImport: Boolean,
    isImportingReferenceAudio: Boolean,
    onLaunchImport: () -> Unit,
    onImportReference: () -> Unit,
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
            Text(
                text = stringResource(R.string.voice_asset_import_step_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.voice_asset_import_desc),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            OutlinedButton(
                onClick = onLaunchImport,
                enabled = !isImportingReferenceAudio,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
            ) {
                Text(stringResource(R.string.voice_asset_pick_audio_action))
            }
            if (selectedImportFileName.isNotBlank()) {
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
                            text = stringResource(R.string.voice_asset_selected_audio_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        )
                        Text(
                            text = selectedImportFileName,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = referenceName,
                onValueChange = onReferenceNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.voice_asset_name_field)) },
                colors = monochromeOutlinedTextFieldColors(),
            )
            Button(
                onClick = onImportReference,
                enabled = canImport,
                modifier = Modifier.fillMaxWidth(),
                colors = voiceAssetButtonColors(),
            ) {
                Text(stringResource(R.string.voice_asset_import_action))
            }
            if (isImportingReferenceAudio) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = MonochromeUi.textPrimary)
                }
            }
        }
    }
}
