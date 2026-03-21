package com.astrbot.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.displayLabel
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors

@Composable
internal fun VoiceAssetClonedVoicesHeader() {
    AssetSectionHeader(
        title = stringResource(R.string.voice_asset_cloned_library_title),
        description = stringResource(R.string.voice_asset_cloned_library_desc),
    )
}

@Composable
internal fun VoiceAssetBindingCard(
    asset: TtsVoiceReferenceAsset,
    binding: ClonedVoiceBinding,
    renamingBindingId: String,
    renamingBindingName: String,
    onRenamingBindingNameChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onDelete: () -> Unit,
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
            if (renamingBindingId == binding.id) {
                OutlinedTextField(
                    value = renamingBindingName,
                    onValueChange = onRenamingBindingNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.voice_asset_clone_name_field)) },
                    colors = monochromeOutlinedTextFieldColors(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSaveRename,
                        modifier = Modifier.weight(1f),
                        colors = voiceAssetButtonColors(),
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                    OutlinedButton(
                        onClick = onCancelRename,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            } else {
                Text(binding.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${binding.providerType.displayLabel()} / ${binding.model}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
                Text(
                    text = stringResource(R.string.voice_asset_origin_value, asset.name),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onStartRename,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.voice_asset_rename_action))
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                }
            }
        }
    }
}
