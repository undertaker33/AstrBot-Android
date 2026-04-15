package com.astrbot.android.ui.plugin.schema

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginSettingDraftValue

@Composable
fun PluginSchemaRenderer(
    schemaUiState: PluginSchemaUiState,
    onCardActionClick: (actionId: String, payload: Map<String, String>) -> Unit,
    onSettingsDraftChange: (fieldId: String, draftValue: PluginSettingDraftValue) -> Unit,
    modifier: Modifier = Modifier,
    embeddedInSection: Boolean = false,
) {
    when (schemaUiState) {
        PluginSchemaUiState.None -> Unit
        is PluginSchemaUiState.Text -> {
            PluginSchemaText(
                title = schemaUiState.title,
                text = schemaUiState.text,
                modifier = modifier,
            )
        }

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
                embeddedInSection = embeddedInSection,
            )
        }

        is PluginSchemaUiState.Media -> {
            PluginSchemaMedia(
                items = schemaUiState.items,
                modifier = modifier,
            )
        }

        is PluginSchemaUiState.Error -> {
            PluginSchemaError(
                message = schemaUiState.message,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PluginSchemaText(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.SchemaTextTag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(PluginUiSpec.SchemaContainerPadding),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textPrimary,
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
                PluginAdaptiveOutlinedButtonGroup(
                    items = model.actions.map { action ->
                        val palette = PluginUiSpec.schemaActionPalette(action.style)
                        AdaptiveOutlinedButtonItem(
                            label = action.label,
                            testTag = PluginUiSpec.schemaCardActionTag(action.actionId),
                            borderColor = palette.borderColor,
                            containerColor = palette.containerColor,
                            contentColor = palette.contentColor,
                            onClick = {
                                dispatchSchemaCardAction(
                                    actionId = action.actionId,
                                    payload = action.payload,
                                ) { actionId, payload ->
                                    onCardActionClick(actionId, payload)
                                }
                            },
                        )
                    },
                    maxColumns = 1,
                )
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
    embeddedInSection: Boolean = false,
) {
    if (embeddedInSection) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag(PluginUiSpec.SchemaSettingsTag),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
        ) {
            PluginSchemaSettingsContent(
                model = model,
                onSettingsDraftChange = onSettingsDraftChange,
                showTitle = false,
            )
        }
    } else {
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
                PluginSchemaSettingsContent(
                    model = model,
                    onSettingsDraftChange = onSettingsDraftChange,
                    showTitle = true,
                )
            }
        }
    }
}

@Composable
private fun PluginSchemaSettingsContent(
    model: PluginSettingsRenderModel,
    onSettingsDraftChange: (fieldId: String, draftValue: PluginSettingDraftValue) -> Unit,
    showTitle: Boolean,
) {
    if (showTitle) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MonochromeUi.textPrimary,
        )
    }
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

@Composable
private fun PluginSchemaMedia(
    items: List<com.astrbot.android.ui.viewmodel.PluginSchemaMediaItem>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.SchemaMediaTag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(PluginUiSpec.SchemaContainerPadding),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
        ) {
            Text(
                text = stringResource(com.astrbot.android.R.string.plugin_media_result_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            items.forEachIndexed { index, item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PluginUiSpec.schemaMediaItemTag(index)),
                    shape = PluginUiSpec.SectionShape,
                    color = MonochromeUi.cardAltBackground,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = PluginUiSpec.SchemaRowHorizontalPadding,
                            vertical = PluginUiSpec.SchemaRowVerticalPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MonochromeUi.textPrimary,
                        )
                        Text(
                            text = item.mimeType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MonochromeUi.textSecondary,
                        )
                        Text(
                            text = item.resolvedSource,
                            style = MaterialTheme.typography.bodySmall,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginSchemaError(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.SchemaErrorTag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(PluginUiSpec.SchemaContainerPadding),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
        ) {
            Text(
                text = stringResource(com.astrbot.android.R.string.plugin_runtime_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
fun PluginStaticConfigRenderer(
    model: PluginStaticConfigRenderModel,
    onDraftChange: (fieldKey: String, draftValue: PluginSettingDraftValue) -> Unit,
    modifier: Modifier = Modifier,
    embeddedInSection: Boolean = false,
) {
    val visibleSections = model.sections.mapNotNull { section ->
        val visibleFields = section.fields.filter(StaticConfigFieldRenderModel::isVisible)
        if (visibleFields.isEmpty()) {
            null
        } else {
            section.copy(fields = visibleFields)
        }
    }
    if (embeddedInSection) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag(PluginUiSpec.SchemaStaticConfigTag),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
        ) {
            PluginStaticConfigContent(
                visibleSections = visibleSections,
                onDraftChange = onDraftChange,
            )
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag(PluginUiSpec.SchemaStaticConfigTag),
            shape = PluginUiSpec.SectionShape,
            color = MonochromeUi.cardBackground,
            border = PluginUiSpec.CardBorder,
        ) {
            Column(
                modifier = Modifier.padding(PluginUiSpec.SchemaContainerPadding),
                verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
            ) {
                PluginStaticConfigContent(
                    visibleSections = visibleSections,
                    onDraftChange = onDraftChange,
                )
            }
        }
    }
}

@Composable
private fun PluginStaticConfigContent(
    visibleSections: List<PluginStaticConfigSectionRenderModel>,
    onDraftChange: (fieldKey: String, draftValue: PluginSettingDraftValue) -> Unit,
) {
    visibleSections.forEach { section ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PluginUiSpec.schemaStaticConfigSectionTag(section.sectionId)),
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
                        is StaticConfigFieldRenderModel.Toggle -> PluginStaticConfigToggleField(
                            field = field,
                            onChange = { checked ->
                                onDraftChange(field.fieldKey, PluginSettingDraftValue.Toggle(checked))
                            },
                        )

                        is StaticConfigFieldRenderModel.TextInput -> PluginStaticConfigTextInputField(
                            field = field,
                            onChange = { value ->
                                onDraftChange(field.fieldKey, PluginSettingDraftValue.Text(value))
                            },
                        )

                        is StaticConfigFieldRenderModel.Select -> PluginStaticConfigSelectField(
                            field = field,
                            onChange = { value ->
                                onDraftChange(field.fieldKey, PluginSettingDraftValue.Text(value))
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class AdaptiveOutlinedButtonItem(
    val label: String,
    val testTag: String,
    val borderColor: Color,
    val borderWidth: Dp = PluginUiSpec.SchemaActionBorderWidth,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
)

@Composable
private fun PluginAdaptiveOutlinedButtonGroup(
    items: List<AdaptiveOutlinedButtonItem>,
    modifier: Modifier = Modifier,
    maxColumns: Int = 3,
    maxVisibleLines: Int = 2,
) {
    if (items.isEmpty()) return

    val textStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val itemSpacingPx = with(density) { PluginUiSpec.SchemaButtonGroupSpacing.roundToPx() }
        val buttonTextHorizontalPaddingPx = with(density) { 28.dp.roundToPx() }

        fun lineCountsFor(columns: Int): List<Int> {
            val itemWidthPx = ((maxWidthPx - itemSpacingPx * (columns - 1)) / columns)
                .coerceAtLeast(1)
            val textWidthPx = (itemWidthPx - buttonTextHorizontalPaddingPx)
                .coerceAtLeast(1)
            return items.map { item ->
                textMeasurer.measure(
                    text = item.label,
                    style = textStyle,
                    constraints = Constraints(maxWidth = textWidthPx),
                ).lineCount
            }
        }

        val columns = resolveButtonGroupColumns(
            itemCount = items.size,
            maxColumns = maxColumns.coerceAtLeast(1),
            maxVisibleLines = maxVisibleLines,
            lineCountsForColumns = ::lineCountsFor,
        )
        val rowLineCounts = normalizeButtonRowLineCounts(
            lineCounts = lineCountsFor(columns),
            columns = columns,
            maxVisibleLines = maxVisibleLines,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaButtonGroupSpacing),
        ) {
            items.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                val rowLines = rowLineCounts.getOrElse(rowIndex) { 1 }
                val minHeight = if (rowLines > 1) {
                    PluginUiSpec.SchemaButtonTwoLineMinHeight
                } else {
                    PluginUiSpec.SchemaButtonSingleLineMinHeight
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaButtonGroupSpacing),
                ) {
                    rowItems.forEach { item ->
                        OutlinedButton(
                            onClick = item.onClick,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = minHeight)
                                .testTag(item.testTag),
                            border = BorderStroke(item.borderWidth, item.borderColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = item.containerColor,
                                contentColor = item.contentColor,
                            ),
                        ) {
                            Text(
                                text = item.label,
                                minLines = rowLines,
                                maxLines = rowLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
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
        PluginAdaptiveOutlinedButtonGroup(
            items = field.options.map { option ->
                val selected = option.value == field.value
                AdaptiveOutlinedButtonItem(
                    label = option.label,
                    testTag = PluginUiSpec.schemaSettingsSelectOptionTag(field.fieldId, option.value),
                    borderColor = if (selected) MonochromeUi.textPrimary else MonochromeUi.border,
                    borderWidth = if (selected) {
                        PluginUiSpec.SchemaSelectedBorderWidth
                    } else {
                        PluginUiSpec.SchemaActionBorderWidth
                    },
                    containerColor = if (selected) MonochromeUi.cardBackground else MonochromeUi.cardAltBackground,
                    contentColor = MonochromeUi.textPrimary,
                    onClick = { onChange(option.value) },
                )
            },
        )
    }
}

@Composable
private fun StaticConfigFieldMeta(
    fieldKey: String,
    description: String,
    hint: String,
    obviousHint: Boolean,
    defaultValueText: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.schemaStaticConfigFieldTag(fieldKey)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (description.isNotBlank()) {
            Text(
                text = description,
                modifier = Modifier.testTag(PluginUiSpec.schemaStaticConfigDescriptionTag(fieldKey)),
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
        }
        if (hint.isNotBlank()) {
            Text(
                text = if (obviousHint) hint else "Hint: $hint",
                modifier = Modifier.testTag(PluginUiSpec.schemaStaticConfigHintTag(fieldKey)),
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
        }
        if (defaultValueText.isNotBlank()) {
            Text(
                text = "Default: $defaultValueText",
                modifier = Modifier.testTag(PluginUiSpec.schemaStaticConfigDefaultTag(fieldKey)),
                style = MaterialTheme.typography.labelSmall,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
private fun PluginStaticConfigToggleField(
    field: StaticConfigFieldRenderModel.Toggle,
    onChange: (Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
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
                modifier = Modifier.testTag(PluginUiSpec.schemaStaticConfigToggleTag(field.fieldKey)),
            )
        }
        StaticConfigFieldMeta(
            fieldKey = field.fieldKey,
            description = field.description,
            hint = field.hint,
            obviousHint = field.obviousHint,
            defaultValueText = field.defaultValueText,
        )
    }
}

@Composable
private fun PluginStaticConfigTextInputField(
    field: StaticConfigFieldRenderModel.TextInput,
    onChange: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
    ) {
        OutlinedTextField(
            value = field.value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PluginUiSpec.schemaStaticConfigTextInputTag(field.fieldKey)),
            label = {
                Text(
                    text = field.label,
                    color = MonochromeUi.textSecondary,
                )
            },
            singleLine = field.inputMode == StaticConfigTextInputMode.SingleLine ||
                field.inputMode == StaticConfigTextInputMode.Integer ||
                field.inputMode == StaticConfigTextInputMode.Decimal,
        )
        StaticConfigFieldMeta(
            fieldKey = field.fieldKey,
            description = field.description,
            hint = field.hint,
            obviousHint = field.obviousHint,
            defaultValueText = field.defaultValueText,
        )
    }
}

@Composable
private fun PluginStaticConfigSelectField(
    field: StaticConfigFieldRenderModel.Select,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.schemaStaticConfigSelectTag(field.fieldKey)),
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SchemaFieldSpacing),
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelMedium,
            color = MonochromeUi.textSecondary,
        )
        PluginAdaptiveOutlinedButtonGroup(
            items = field.options.map { option ->
                val selected = option.value == field.value
                AdaptiveOutlinedButtonItem(
                    label = option.label,
                    testTag = PluginUiSpec.schemaStaticConfigSelectOptionTag(field.fieldKey, option.value),
                    borderColor = if (selected) MonochromeUi.textPrimary else MonochromeUi.border,
                    borderWidth = if (selected) {
                        PluginUiSpec.SchemaSelectedBorderWidth
                    } else {
                        PluginUiSpec.SchemaActionBorderWidth
                    },
                    containerColor = if (selected) MonochromeUi.cardBackground else MonochromeUi.cardAltBackground,
                    contentColor = MonochromeUi.textPrimary,
                    onClick = { onChange(option.value) },
                )
            },
        )
        StaticConfigFieldMeta(
            fieldKey = field.fieldKey,
            description = field.description,
            hint = field.hint,
            obviousHint = field.obviousHint,
            defaultValueText = field.defaultValueText,
        )
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
