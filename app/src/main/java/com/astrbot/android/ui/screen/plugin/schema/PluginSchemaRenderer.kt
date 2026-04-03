package com.astrbot.android.ui.screen.plugin.schema

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginSettingDraftValue

@Composable
fun PluginSchemaRenderer(
    schemaUiState: PluginSchemaUiState,
    onCardActionClick: (actionId: String, payload: Map<String, String>) -> Unit,
    onSettingsDraftChange: (fieldId: String, draftValue: PluginSettingDraftValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (schemaUiState) {
        PluginSchemaUiState.None -> Unit
        is PluginSchemaUiState.Card -> {
            PluginSchemaCard(
                model = buildPluginCardRenderModel(
                    schema = schemaUiState.schema,
                    feedback = schemaUiState.lastActionFeedback,
                ),
                onCardActionClick = onCardActionClick,
                modifier = modifier,
            )
        }

        is PluginSchemaUiState.Settings -> {
            PluginSchemaSettings(
                model = buildPluginSettingsRenderModel(schemaUiState),
                onSettingsDraftChange = onSettingsDraftChange,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PluginSchemaCard(
    model: PluginCardRenderModel,
    onCardActionClick: (actionId: String, payload: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.SchemaCardTag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(PluginUiSpec.SchemaContainerPadding),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
        ) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            if (model.body.isNotBlank()) {
                Text(
                    text = model.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonochromeUi.textSecondary,
                )
            }
            PluginSchemaStatusChip(status = model.status)

            if (model.fields.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldGroupSpacing)) {
                    model.fields.forEach { field ->
                        Surface(
                            shape = PluginUiSpec.SectionShape,
                            color = MonochromeUi.cardAltBackground,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = PluginUiSpec.SchemaRowHorizontalPadding,
                                        vertical = PluginUiSpec.SchemaRowVerticalPadding,
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = field.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MonochromeUi.textSecondary,
                                )
                                Text(
                                    text = field.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MonochromeUi.textPrimary,
                                )
                            }
                        }
                    }
                }
            }

            if (model.actions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaActionSpacing)) {
                    model.actions.forEach { action ->
                        val palette = PluginUiSpec.schemaActionPalette(action.style)
                        OutlinedButton(
                            onClick = {
                                dispatchSchemaCardAction(
                                    actionId = action.actionId,
                                    payload = action.payload,
                                ) { actionId, payload ->
                                    onCardActionClick(actionId, payload)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(PluginUiSpec.schemaCardActionTag(action.actionId)),
                            border = BorderStroke(PluginUiSpec.SchemaActionBorderWidth, palette.borderColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = palette.containerColor,
                                contentColor = palette.contentColor,
                            ),
                        ) {
                            Text(text = action.label)
                        }
                    }
                }
            }

            model.feedback?.let { feedback ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PluginUiSpec.SchemaCardFeedbackTag),
                    shape = PluginUiSpec.SectionShape,
                    color = MonochromeUi.cardAltBackground,
                ) {
                    Text(
                        text = feedback.asText(),
                        modifier = Modifier.padding(
                            horizontal = PluginUiSpec.SchemaRowHorizontalPadding,
                            vertical = PluginUiSpec.SchemaRowVerticalPadding,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginSchemaSettings(
    model: PluginSettingsRenderModel,
    onSettingsDraftChange: (fieldId: String, draftValue: PluginSettingDraftValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.SchemaSettingsTag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(PluginUiSpec.SchemaContainerPadding),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
        ) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            model.sections.forEach { section ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PluginUiSpec.schemaSettingsSectionTag(section.sectionId)),
                    shape = PluginUiSpec.SectionShape,
                    color = MonochromeUi.cardAltBackground,
                ) {
                    Column(
                        modifier = Modifier.padding(PluginUiSpec.SchemaSectionPadding),
                        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaSectionInnerSpacing),
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MonochromeUi.textPrimary,
                        )
                        section.fields.forEach { field ->
                            when (field) {
                                is SettingsFieldRenderModel.Toggle -> PluginToggleSettingField(
                                    field = field,
                                    onChange = { checked ->
                                        onSettingsDraftChange(
                                            field.fieldId,
                                            PluginSettingDraftValue.Toggle(checked),
                                        )
                                    },
                                )

                                is SettingsFieldRenderModel.TextInput -> PluginTextInputSettingField(
                                    field = field,
                                    onChange = { value ->
                                        onSettingsDraftChange(
                                            field.fieldId,
                                            PluginSettingDraftValue.Text(value),
                                        )
                                    },
                                )

                                is SettingsFieldRenderModel.Select -> PluginSelectSettingField(
                                    field = field,
                                    onChange = { value ->
                                        onSettingsDraftChange(
                                            field.fieldId,
                                            PluginSettingDraftValue.Text(value),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginSchemaStatusChip(status: com.astrbot.android.model.plugin.PluginUiStatus) {
    val palette = PluginUiSpec.schemaStatusPalette(status)
    Surface(
        modifier = Modifier.testTag(PluginUiSpec.SchemaCardStatusTag),
        shape = PluginUiSpec.BadgeShape,
        color = palette.containerColor,
    ) {
        Text(
            text = pluginUiStatusLabel(status),
            modifier = Modifier.padding(
                horizontal = PluginUiSpec.SchemaStatusChipHorizontalPadding,
                vertical = PluginUiSpec.SchemaStatusChipVerticalPadding,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = palette.contentColor,
        )
    }
}

@Composable
private fun PluginToggleSettingField(
    field: SettingsFieldRenderModel.Toggle,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textPrimary,
        )
        Switch(
            checked = field.value,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(PluginUiSpec.schemaSettingsToggleTag(field.fieldId)),
        )
    }
}

@Composable
private fun PluginTextInputSettingField(
    field: SettingsFieldRenderModel.TextInput,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = field.value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.schemaSettingsTextInputTag(field.fieldId)),
        label = {
            Text(
                text = field.label,
                color = MonochromeUi.textSecondary,
            )
        },
        placeholder = if (field.placeholder.isNotBlank()) {
            {
                Text(
                    text = field.placeholder,
                    color = MonochromeUi.textSecondary,
                )
            }
        } else {
            null
        },
        singleLine = true,
    )
}

@Composable
private fun PluginSelectSettingField(
    field: SettingsFieldRenderModel.Select,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.schemaSettingsSelectTag(field.fieldId)),
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelMedium,
            color = MonochromeUi.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing)) {
            field.options.forEach { option ->
                val selected = option.value == field.value
                OutlinedButton(
                    onClick = { onChange(option.value) },
                    modifier = Modifier.testTag(
                        PluginUiSpec.schemaSettingsSelectOptionTag(field.fieldId, option.value),
                    ),
                    border = if (selected) {
                        BorderStroke(PluginUiSpec.SchemaSelectedBorderWidth, MonochromeUi.textPrimary)
                    } else {
                        PluginUiSpec.CardBorder
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MonochromeUi.cardBackground else MonochromeUi.cardAltBackground,
                        contentColor = MonochromeUi.textPrimary,
                    ),
                ) {
                    Text(option.label)
                }
            }
        }
    }
}

@Composable
private fun PluginActionFeedback.asText(): String {
    return when (this) {
        is PluginActionFeedback.Resource -> stringResource(resId, *formatArgs.toTypedArray())
        is PluginActionFeedback.Text -> value
    }
}

private fun pluginUiStatusLabel(status: com.astrbot.android.model.plugin.PluginUiStatus): String {
    return when (status) {
        com.astrbot.android.model.plugin.PluginUiStatus.Info -> "Info"
        com.astrbot.android.model.plugin.PluginUiStatus.Success -> "Success"
        com.astrbot.android.model.plugin.PluginUiStatus.Warning -> "Warning"
        com.astrbot.android.model.plugin.PluginUiStatus.Error -> "Error"
    }
}
