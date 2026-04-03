package com.astrbot.android.ui.screen.plugin.schema

import com.astrbot.android.model.plugin.PluginCardAction
import com.astrbot.android.model.plugin.PluginCardField
import com.astrbot.android.model.plugin.PluginCardSchema
import com.astrbot.android.model.plugin.PluginSelectOption
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

private fun PluginSettingDraftValue?.asToggleOrDefault(defaultValue: Boolean): Boolean {
    return (this as? PluginSettingDraftValue.Toggle)?.value ?: defaultValue
}

private fun PluginSettingDraftValue?.asTextOrDefault(defaultValue: String): String {
    return (this as? PluginSettingDraftValue.Text)?.value ?: defaultValue
}
