package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginCardAction
import com.astrbot.android.model.plugin.PluginCardField
import com.astrbot.android.model.plugin.PluginCardSchema
import com.astrbot.android.model.plugin.PluginSelectOption
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSettingsSection
import com.astrbot.android.model.plugin.PluginUiStatus
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.ui.screen.plugin.schema.buildPluginCardRenderModel
import com.astrbot.android.ui.screen.plugin.schema.buildPluginSettingsRenderModel
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
}
