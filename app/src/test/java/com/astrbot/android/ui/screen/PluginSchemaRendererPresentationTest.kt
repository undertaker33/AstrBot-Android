package com.astrbot.android.ui.screen

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
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSettingsSection
import com.astrbot.android.model.plugin.PluginUiStatus
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.ui.screen.plugin.schema.buildPluginCardRenderModel
import com.astrbot.android.ui.screen.plugin.schema.buildPluginSettingsRenderModel
import com.astrbot.android.ui.screen.plugin.schema.buildPluginStaticConfigRenderModel
import com.astrbot.android.ui.screen.plugin.schema.dispatchSchemaCardAction
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginSettingDraftValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSchemaRendererPresentationTest {

    @Test
    fun `card render model keeps title body status fields actions and feedback`() {
        val schema = PluginCardSchema(
            title = "Host Card",
            body = "Host body",
            status = PluginUiStatus.Warning,
            fields = listOf(
                PluginCardField(label = "Version", value = "1.2.0"),
                PluginCardField(label = "Mode", value = "Safe"),
            ),
            actions = listOf(
                PluginCardAction(
                    actionId = "refresh",
                    label = "Refresh",
                    payload = mapOf("source" to "card"),
                ),
            ),
        )

        val model = buildPluginCardRenderModel(
            schema = schema,
            feedback = PluginActionFeedback.Text("Done"),
        )

        assertEquals("Host Card", model.title)
        assertEquals("Host body", model.body)
        assertEquals(PluginUiStatus.Warning, model.status)
        assertEquals(2, model.fields.size)
        assertEquals("Version", model.fields.first().label)
        assertEquals("refresh", model.actions.single().actionId)
        assertEquals(
            PluginActionFeedback.Text("Done"),
            model.feedback,
        )
    }

    @Test
    fun `settings render model keeps sections and resolves drafts for toggle text and select`() {
        val schema = PluginSettingsSchema(
            title = "Plugin Settings",
            sections = listOf(
                PluginSettingsSection(
                    sectionId = "general",
                    title = "General",
                    fields = listOf(
                        ToggleSettingField(
                            fieldId = "enabled",
                            label = "Enabled",
                            defaultValue = false,
                        ),
                        TextInputSettingField(
                            fieldId = "nickname",
                            label = "Nickname",
                            placeholder = "Type your nickname",
                            defaultValue = "",
                        ),
                        SelectSettingField(
                            fieldId = "mode",
                            label = "Mode",
                            defaultValue = "safe",
                            options = listOf(
                                PluginSelectOption("safe", "Safe"),
                                PluginSelectOption("full", "Full"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = PluginSchemaUiState.Settings(
            schema = schema,
            draftValues = mapOf(
                "enabled" to PluginSettingDraftValue.Toggle(true),
                "nickname" to PluginSettingDraftValue.Text("AstrBot"),
                "mode" to PluginSettingDraftValue.Text("full"),
            ),
        )

        val model = buildPluginSettingsRenderModel(state)

        assertEquals("Plugin Settings", model.title)
        assertEquals(1, model.sections.size)
        val fields = model.sections.single().fields
        assertEquals(3, fields.size)
        assertTrue(fields[0] is com.astrbot.android.ui.screen.plugin.schema.SettingsFieldRenderModel.Toggle)
        assertTrue(fields[1] is com.astrbot.android.ui.screen.plugin.schema.SettingsFieldRenderModel.TextInput)
        assertTrue(fields[2] is com.astrbot.android.ui.screen.plugin.schema.SettingsFieldRenderModel.Select)

        val toggle = fields[0] as com.astrbot.android.ui.screen.plugin.schema.SettingsFieldRenderModel.Toggle
        val text = fields[1] as com.astrbot.android.ui.screen.plugin.schema.SettingsFieldRenderModel.TextInput
        val select = fields[2] as com.astrbot.android.ui.screen.plugin.schema.SettingsFieldRenderModel.Select
        assertTrue(toggle.value)
        assertEquals("AstrBot", text.value)
        assertEquals("full", select.value)
    }

    @Test
    fun `card action dispatcher forwards actionId and payload`() {
        val action = PluginCardAction(
            actionId = "retry",
            label = "Retry",
            payload = mapOf("sessionId" to "s-1"),
        )
        var actualId = ""
        var actualPayload: Map<String, String> = emptyMap()

        dispatchSchemaCardAction(action) { actionId, payload ->
            actualId = actionId
            actualPayload = payload
        }

        assertEquals("retry", actualId)
        assertEquals(mapOf("sessionId" to "s-1"), actualPayload)
    }

    @Test
    fun `detail workspace schema visibility follows schema state`() {
        assertFalse(shouldRenderSchemaWorkspace(PluginSchemaUiState.None))
        assertTrue(
            shouldRenderSchemaWorkspace(
                PluginSchemaUiState.Card(
                    schema = PluginCardSchema(title = "Card"),
                ),
            ),
        )
        assertTrue(
            shouldRenderSchemaWorkspace(
                PluginSchemaUiState.Settings(
                    schema = PluginSettingsSchema(
                        title = "Settings",
                        sections = emptyList(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `static config render model groups fields by section and resolves drafts for core schema subset`() {
        val schema = PluginStaticConfigSchema(
            fields = listOf(
                PluginStaticConfigField(
                    fieldKey = "token",
                    fieldType = PluginStaticConfigFieldType.StringField,
                    description = "Bot token",
                    hint = "Required for provider auth.",
                    defaultValue = PluginStaticConfigValue.StringValue("sk-demo"),
                    section = "credentials",
                ),
                PluginStaticConfigField(
                    fieldKey = "prompt_template",
                    fieldType = PluginStaticConfigFieldType.TextField,
                    description = "Prompt template",
                    defaultValue = PluginStaticConfigValue.StringValue("You are AstrBot."),
                    section = "content",
                ),
                PluginStaticConfigField(
                    fieldKey = "max_tokens",
                    fieldType = PluginStaticConfigFieldType.IntField,
                    description = "Maximum number of generated tokens.",
                    defaultValue = PluginStaticConfigValue.IntValue(8192),
                    section = "tuning",
                ),
                PluginStaticConfigField(
                    fieldKey = "temperature",
                    fieldType = PluginStaticConfigFieldType.FloatField,
                    description = "Sampling temperature.",
                    defaultValue = PluginStaticConfigValue.FloatValue(0.7),
                    section = "tuning",
                ),
                PluginStaticConfigField(
                    fieldKey = "enabled",
                    fieldType = PluginStaticConfigFieldType.BoolField,
                    description = "Whether the plugin is enabled.",
                    defaultValue = PluginStaticConfigValue.BoolValue(false),
                ),
                PluginStaticConfigField(
                    fieldKey = "provider",
                    fieldType = PluginStaticConfigFieldType.StringField,
                    description = "Provider id",
                    options = listOf(
                        PluginStaticConfigOption.Plain("openai"),
                        PluginStaticConfigOption.Labeled("gemini", "Gemini"),
                    ),
                    specialType = PluginStaticConfigSpecialType.SelectProvider,
                    section = "credentials",
                ),
                PluginStaticConfigField(
                    fieldKey = "internal_note",
                    fieldType = PluginStaticConfigFieldType.StringField,
                    description = "Internal note",
                    invisible = true,
                    section = "advanced",
                ),
            ),
        )

        val model = buildPluginStaticConfigRenderModel(
            schema = schema,
            draftValues = mapOf(
                "token" to PluginSettingDraftValue.Text("sk-live"),
                "max_tokens" to PluginSettingDraftValue.Text("4096"),
                "temperature" to PluginSettingDraftValue.Text("0.9"),
                "enabled" to PluginSettingDraftValue.Toggle(true),
                "provider" to PluginSettingDraftValue.Text("gemini"),
            ),
        )

        assertEquals(5, model.sections.size)
        assertEquals("General", model.sections[0].title)
        assertEquals("Credentials", model.sections[1].title)
        assertEquals("Content", model.sections[2].title)
        assertEquals("Tuning", model.sections[3].title)
        assertEquals("Advanced", model.sections[4].title)

        val generalField = model.sections[0].fields.single()
        assertTrue(generalField is com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.Toggle)
        val credentialsFields = model.sections[1].fields
        assertTrue(credentialsFields[0] is com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.TextInput)
        assertTrue(credentialsFields[1] is com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.Select)
        val contentField = model.sections[2].fields.single()
        val tuningFields = model.sections[3].fields
        val advancedField = model.sections[4].fields.single()

        val tokenField =
            credentialsFields[0] as com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.TextInput
        val providerField =
            credentialsFields[1] as com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.Select
        val promptField =
            contentField as com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.TextInput
        val maxTokensField =
            tuningFields[0] as com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.TextInput
        val temperatureField =
            tuningFields[1] as com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.TextInput
        val enabledField =
            generalField as com.astrbot.android.ui.screen.plugin.schema.StaticConfigFieldRenderModel.Toggle

        assertEquals("Token", tokenField.label)
        assertEquals("sk-live", tokenField.value)
        assertEquals(
            com.astrbot.android.ui.screen.plugin.schema.StaticConfigTextInputMode.SingleLine,
            tokenField.inputMode,
        )
        assertEquals("Prompt Template", promptField.label)
        assertEquals(
            com.astrbot.android.ui.screen.plugin.schema.StaticConfigTextInputMode.MultiLine,
            promptField.inputMode,
        )
        assertEquals(
            com.astrbot.android.ui.screen.plugin.schema.StaticConfigTextInputMode.Integer,
            maxTokensField.inputMode,
        )
        assertEquals("4096", maxTokensField.value)
        assertEquals(
            com.astrbot.android.ui.screen.plugin.schema.StaticConfigTextInputMode.Decimal,
            temperatureField.inputMode,
        )
        assertEquals("0.9", temperatureField.value)
        assertTrue(enabledField.value)
        assertEquals(PluginStaticConfigSpecialType.SelectProvider, providerField.specialType)
        assertEquals("gemini", providerField.value)
        assertEquals("Gemini", providerField.options[1].label)
        assertFalse(advancedField.isVisible)
    }
}
