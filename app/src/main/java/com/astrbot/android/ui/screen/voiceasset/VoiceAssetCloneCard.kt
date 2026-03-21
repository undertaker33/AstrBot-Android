package com.astrbot.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors

@Composable
internal fun VoiceAssetCloneCard(
    selectedReferenceAsset: TtsVoiceReferenceAsset?,
    selectedCloneProvider: ProviderProfile?,
    referenceOptions: List<Pair<String, String>>,
    selectedReferenceAssetId: String,
    onSelectReferenceAsset: (String) -> Unit,
    providerOptions: List<Pair<String, String>>,
    selectedCloneProviderId: String,
    onSelectCloneProvider: (String) -> Unit,
    cloneDisplayName: String,
    onCloneDisplayNameChange: (String) -> Unit,
    canClone: Boolean,
    isCloningVoice: Boolean,
    onCloneVoice: () -> Unit,
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
                text = stringResource(R.string.voice_asset_clone_step_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.voice_asset_clone_desc),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
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
                        text = stringResource(R.string.voice_asset_clone_ready_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = selectedReferenceAsset?.let {
                            stringResource(R.string.voice_asset_clone_ready_reference, it.name, it.clips.size)
                        } ?: stringResource(R.string.voice_asset_clone_reference_hint),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                    Text(
                        text = selectedCloneProvider?.let {
                            stringResource(R.string.voice_asset_clone_ready_provider, it.name, it.model)
                        } ?: stringResource(R.string.voice_asset_clone_provider_hint),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
            }
            SelectionField(
                title = stringResource(R.string.voice_asset_reference_audio_field),
                options = referenceOptions,
                selectedId = selectedReferenceAssetId,
                onSelect = onSelectReferenceAsset,
            )
            SelectionField(
                title = stringResource(R.string.voice_asset_provider_model_field),
                options = providerOptions,
                selectedId = selectedCloneProviderId,
                onSelect = onSelectCloneProvider,
            )
            OutlinedTextField(
                value = cloneDisplayName,
                onValueChange = onCloneDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.voice_asset_clone_name_field)) },
                colors = monochromeOutlinedTextFieldColors(),
            )
            Button(
                onClick = onCloneVoice,
                enabled = canClone,
                modifier = Modifier.fillMaxWidth(),
                colors = voiceAssetButtonColors(),
            ) {
                Text(stringResource(R.string.voice_asset_clone_action))
            }
            if (isCloningVoice) {
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
