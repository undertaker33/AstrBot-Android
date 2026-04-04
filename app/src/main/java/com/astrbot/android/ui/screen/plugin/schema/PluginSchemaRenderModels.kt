package com.astrbot.android.ui.screen.plugin.schema

import com.astrbot.android.model.plugin.PluginCardAction
import com.astrbot.android.model.plugin.PluginCardField
import com.astrbot.android.model.plugin.PluginCardSchema
import com.astrbot.android.model.plugin.PluginSelectOption
import com.astrbot.android.model.plugin.PluginStaticConfigField
import com.astrbot.android.model.plugin.PluginStaticConfigFieldType
import com.astrbot.android.model.plugin.PluginStaticConfigOption
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigSpecialType
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUiActionStyle
import com.astrbot.android.model.plugin.PluginUiStatus
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginSettingDraftValue

data class PluginCardRenderModel(
    val title: String,
    val body: String,
    val status: PluginUiStatus,
    val fields: List<PluginCardFieldRenderModel>,
    val actions: List<PluginCardActionRenderModel>,
    val feedback: PluginActionFeedback?,
)

data class PluginCardFieldRenderModel(
    val label: String,
    val value: String,
)

data class PluginCardActionRenderModel(
    val actionId: String,
    val label: String,
    val style: PluginUiActionStyle,
    val payload: Map<String, String>,
)

data class PluginSettingsRenderModel(
    val title: String,
    val sections: List<PluginSettingsSectionRenderModel>,
)

data class PluginSettingsSectionRenderModel(
    val sectionId: String,
    val title: String,
    val fields: List<SettingsFieldRenderModel>,
)

sealed interface SettingsFieldRenderModel {
    val fieldId: String
    val label: String

    data class Toggle(
        override val fieldId: String,
        override val label: String,
        val value: Boolean,
    ) : SettingsFieldRenderModel

    data class TextInput(
        override val fieldId: String,
        override val label: String,
        val placeholder: String,
        val value: String,
    ) : SettingsFieldRenderModel

    data class Select(
        override val fieldId: String,
        override val label: String,
        val value: String,
        val options: List<SelectOptionRenderModel>,
    ) : SettingsFieldRenderModel
}

data class SelectOptionRenderModel(
    val value: String,
    val label: String,
)

data class PluginStaticConfigRenderModel(
    val sections: List<PluginStaticConfigSectionRenderModel>,
)

data class PluginStaticConfigSectionRenderModel(
    val sectionId: String,
    val title: String,
    val fields: List<StaticConfigFieldRenderModel>,
)

enum class StaticConfigTextInputMode {
    SingleLine,
    MultiLine,
    Integer,
    Decimal,
}

sealed interface StaticConfigFieldRenderModel {
    val fieldKey: String
    val label: String
    val description: String
    val hint: String
    val obviousHint: Boolean
    val defaultValueText: String
    val isVisible: Boolean

    data class Toggle(
        override val fieldKey: String,
        override val label: String,
        override val description: String,
        override val hint: String,
        override val obviousHint: Boolean,
        override val defaultValueText: String,
        override val isVisible: Boolean,
        val value: Boolean,
    ) : StaticConfigFieldRenderModel

    data class TextInput(
        override val fieldKey: String,
        override val label: String,
        override val description: String,
        override val hint: String,
        override val obviousHint: Boolean,
        override val defaultValueText: String,
        override val isVisible: Boolean,
        val value: String,
        val inputMode: StaticConfigTextInputMode,
    ) : StaticConfigFieldRenderModel

    data class Select(
        override val fieldKey: String,
        override val label: String,
        override val description: String,
        override val hint: String,
        override val obviousHint: Boolean,
        override val defaultValueText: String,
        override val isVisible: Boolean,
        val value: String,
        val options: List<SelectOptionRenderModel>,
        val specialType: PluginStaticConfigSpecialType?,
    ) : StaticConfigFieldRenderModel
}

fun buildPluginCardRenderModel(
    schema: PluginCardSchema,
    feedback: PluginActionFeedback?,
): PluginCardRenderModel {
    return PluginCardRenderModel(
        title = schema.title,
        body = schema.body,
        status = schema.status,
        fields = schema.fields.map(PluginCardField::toRenderModel),
        actions = schema.actions.map(PluginCardAction::toRenderModel),
        feedback = feedback,
    )
}

fun buildPluginSettingsRenderModel(
    state: PluginSchemaUiState.Settings,
): PluginSettingsRenderModel {
    return PluginSettingsRenderModel(
        title = state.schema.title,
        sections = state.schema.sections.map { section ->
            PluginSettingsSectionRenderModel(
                sectionId = section.sectionId,
                title = section.title,
                fields = section.fields.map { field ->
                    when (field) {
                        is ToggleSettingField -> SettingsFieldRenderModel.Toggle(
                            fieldId = field.fieldId,
                            label = field.label,
                            value = state.draftValues[field.fieldId].asToggleOrDefault(field.defaultValue),
                        )

                        is TextInputSettingField -> SettingsFieldRenderModel.TextInput(
                            fieldId = field.fieldId,
                            label = field.label,
                            placeholder = field.placeholder,
                            value = state.draftValues[field.fieldId].asTextOrDefault(field.defaultValue),
                        )

                        is SelectSettingField -> SettingsFieldRenderModel.Select(
                            fieldId = field.fieldId,
                            label = field.label,
                            value = state.draftValues[field.fieldId].asTextOrDefault(field.defaultValue),
                            options = field.options.map(PluginSelectOption::toRenderModel),
                        )
                    }
                },
            )
        },
    )
}

fun buildPluginStaticConfigRenderModel(
    schema: PluginStaticConfigSchema,
    draftValues: Map<String, PluginSettingDraftValue>,
): PluginStaticConfigRenderModel {
    val fieldsBySection = linkedMapOf<String, MutableList<PluginStaticConfigField>>()
    schema.fields.forEach { field ->
        val sectionId = field.section.trim().ifBlank { "general" }
        fieldsBySection.getOrPut(sectionId) { mutableListOf() }.add(field)
    }
    val orderedSectionIds = buildList {
        if (fieldsBySection.containsKey("general")) {
            add("general")
        }
        addAll(fieldsBySection.keys.filterNot { it == "general" })
    }
    return PluginStaticConfigRenderModel(
        sections = orderedSectionIds.map { sectionId ->
            val fields = fieldsBySection.getValue(sectionId)
            PluginStaticConfigSectionRenderModel(
                sectionId = sectionId,
                title = sectionId.toDisplayLabel(),
                fields = fields.map { field ->
                    field.toStaticConfigFieldRenderModel(draftValues[field.fieldKey])
                },
            )
        },
    )
}

fun dispatchSchemaCardAction(
    action: PluginCardAction,
    onAction: (actionId: String, payload: Map<String, String>) -> Unit,
) {
    dispatchSchemaCardAction(
        actionId = action.actionId,
        payload = action.payload,
        onAction = onAction,
    )
}

fun dispatchSchemaCardAction(
    actionId: String,
    payload: Map<String, String>,
    onAction: (actionId: String, payload: Map<String, String>) -> Unit,
) {
    onAction(actionId, payload)
}

private fun PluginCardField.toRenderModel(): PluginCardFieldRenderModel {
    return PluginCardFieldRenderModel(
        label = label,
        value = value,
    )
}

private fun PluginCardAction.toRenderModel(): PluginCardActionRenderModel {
    return PluginCardActionRenderModel(
        actionId = actionId,
        label = label,
        style = style,
        payload = payload,
    )
}

private fun PluginSelectOption.toRenderModel(): SelectOptionRenderModel {
    return SelectOptionRenderModel(
        value = value,
        label = label,
    )
}

private fun PluginStaticConfigField.toStaticConfigFieldRenderModel(
    draftValue: PluginSettingDraftValue?,
): StaticConfigFieldRenderModel {
    val baseLabel = fieldKey.toDisplayLabel()
    val isVisible = !invisible
    val defaultValueText = defaultValue.toDisplayValue()
    return when {
        fieldType == PluginStaticConfigFieldType.BoolField -> StaticConfigFieldRenderModel.Toggle(
            fieldKey = fieldKey,
            label = baseLabel,
            description = description,
            hint = hint,
            obviousHint = obviousHint,
            defaultValueText = defaultValueText,
            isVisible = isVisible,
            value = draftValue.asToggleOrDefault(defaultValue.asBooleanOrDefault(false)),
        )

        options.isNotEmpty() || specialType != null -> StaticConfigFieldRenderModel.Select(
            fieldKey = fieldKey,
            label = baseLabel,
            description = description,
            hint = hint,
            obviousHint = obviousHint,
            defaultValueText = defaultValueText,
            isVisible = isVisible,
            value = draftValue.asTextOrDefault(defaultValue.asStringOrDefault("")),
            options = options.map(PluginStaticConfigOption::toRenderModel),
            specialType = specialType,
        )

        else -> StaticConfigFieldRenderModel.TextInput(
            fieldKey = fieldKey,
            label = baseLabel,
            description = description,
            hint = hint,
            obviousHint = obviousHint,
            defaultValueText = defaultValueText,
            isVisible = isVisible,
            value = draftValue.asTextOrDefault(defaultValue.toDisplayValue()),
            inputMode = fieldType.toStaticConfigTextInputMode(),
        )
    }
}

private fun PluginStaticConfigOption.toRenderModel(): SelectOptionRenderModel {
    return SelectOptionRenderModel(
        value = value,
        label = label,
    )
}

private fun PluginStaticConfigFieldType.toStaticConfigTextInputMode(): StaticConfigTextInputMode {
    return when (this) {
        PluginStaticConfigFieldType.StringField -> StaticConfigTextInputMode.SingleLine
        PluginStaticConfigFieldType.TextField -> StaticConfigTextInputMode.MultiLine
        PluginStaticConfigFieldType.IntField -> StaticConfigTextInputMode.Integer
        PluginStaticConfigFieldType.FloatField -> StaticConfigTextInputMode.Decimal
        PluginStaticConfigFieldType.BoolField -> StaticConfigTextInputMode.SingleLine
    }
}

private fun PluginStaticConfigValue?.asBooleanOrDefault(defaultValue: Boolean): Boolean {
    return (this as? PluginStaticConfigValue.BoolValue)?.value ?: defaultValue
}

private fun PluginStaticConfigValue?.asStringOrDefault(defaultValue: String): String {
    return when (this) {
        is PluginStaticConfigValue.StringValue -> value
        is PluginStaticConfigValue.IntValue -> value.toString()
        is PluginStaticConfigValue.FloatValue -> value.toString()
        is PluginStaticConfigValue.BoolValue -> value.toString()
        null -> defaultValue
    }
}

private fun PluginStaticConfigValue?.toDisplayValue(): String {
    return asStringOrDefault(defaultValue = "")
}

private fun PluginSettingDraftValue?.asToggleOrDefault(defaultValue: Boolean): Boolean {
    return (this as? PluginSettingDraftValue.Toggle)?.value ?: defaultValue
}

private fun PluginSettingDraftValue?.asTextOrDefault(defaultValue: String): String {
    return (this as? PluginSettingDraftValue.Text)?.value ?: defaultValue
}

private fun String.toDisplayLabel(): String {
    return trim()
        .ifBlank { "general" }
        .split('_', '-', ' ')
        .filter(String::isNotBlank)
        .joinToString(separator = " ") { token ->
            token.lowercase().replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
}
