package com.astrbot.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.astrbot.android.model.plugin.PluginCardAction
import com.astrbot.android.model.plugin.PluginCardField
import com.astrbot.android.model.plugin.PluginCardSchema
import com.astrbot.android.model.plugin.PluginSelectOption
import com.astrbot.android.model.plugin.PluginStaticConfigField
import com.astrbot.android.model.plugin.PluginStaticConfigFieldType
import com.astrbot.android.model.plugin.PluginStaticConfigOption
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSettingsSection
import com.astrbot.android.model.plugin.PluginUiActionStyle
import com.astrbot.android.model.plugin.PluginUiStatus
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.screen.plugin.schema.PluginStaticConfigRenderer
import com.astrbot.android.ui.screen.plugin.schema.PluginSchemaRenderer
import com.astrbot.android.ui.screen.plugin.schema.buildPluginStaticConfigRenderModel
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginSettingDraftValue
import org.junit.Rule
import org.junit.Test

class PluginSchemaSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun schemaCardRendersAndActionFeedbackIsVisibleAfterClick() {
        val cardSchema = PluginCardSchema(
            title = "Plugin Host Card",
            body = "Schema card smoke body",
            status = PluginUiStatus.Info,
            fields = listOf(
                PluginCardField(label = "Runtime", value = "ready"),
            ),
            actions = listOf(
                PluginCardAction(
                    actionId = "refresh-card",
                    label = "Refresh card",
                    style = PluginUiActionStyle.Primary,
                    payload = mapOf("source" to "smoke"),
                ),
            ),
        )

        var schemaState by mutableStateOf<PluginSchemaUiState>(
            PluginSchemaUiState.Card(schema = cardSchema),
        )
        var clickedActionId = ""
        var clickedPayload: Map<String, String> = emptyMap()

        composeRule.setContent {
            MaterialTheme {
                PluginSchemaRenderer(
                    schemaUiState = schemaState,
                    onCardActionClick = { actionId, payload ->
                        clickedActionId = actionId
                        clickedPayload = payload
                        val current = schemaState as PluginSchemaUiState.Card
                        schemaState = current.copy(
                            lastActionFeedback = PluginActionFeedback.Text("Action: $actionId"),
                        )
                    },
                    onSettingsDraftChange = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.SchemaCardTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.SchemaCardStatusTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.schemaCardActionTag("refresh-card")).performClick()

        composeRule.onNodeWithTag(PluginUiSpec.SchemaCardFeedbackTag).assertIsDisplayed()
        composeRule.onNodeWithText("Action: refresh-card", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle {
            check(clickedActionId == "refresh-card")
            check(clickedPayload == mapOf("source" to "smoke"))
        }
    }

    @Test
    fun schemaSettingsRenderAndInteractionsUpdateDraftState() {
        val settingsSchema = PluginSettingsSchema(
            title = "Plugin Settings",
            sections = listOf(
                PluginSettingsSection(
                    sectionId = "general",
                    title = "General",
                    fields = listOf(
                        ToggleSettingField(
                            fieldId = "feature-enabled",
                            label = "Enable feature",
                            defaultValue = false,
                        ),
                        TextInputSettingField(
                            fieldId = "display-name",
                            label = "Display name",
                            placeholder = "Type display name",
                            defaultValue = "",
                        ),
                        SelectSettingField(
                            fieldId = "mode",
                            label = "Mode",
                            defaultValue = "safe",
                            options = listOf(
                                PluginSelectOption(value = "safe", label = "Safe"),
                                PluginSelectOption(value = "full", label = "Full"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        var settingsState by mutableStateOf(
            PluginSchemaUiState.Settings(
                schema = settingsSchema,
                draftValues = emptyMap(),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                PluginSchemaRenderer(
                    schemaUiState = settingsState,
                    onCardActionClick = { _, _ -> },
                    onSettingsDraftChange = { fieldId, draftValue ->
                        settingsState = settingsState.copy(
                            draftValues = settingsState.draftValues + (fieldId to draftValue),
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.SchemaSettingsTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.schemaSettingsToggleTag("feature-enabled")).performClick()
        composeRule.onNodeWithTag(PluginUiSpec.schemaSettingsTextInputTag("display-name"))
            .performTextInput("AstrBot")
        composeRule.onNodeWithTag(
            PluginUiSpec.schemaSettingsSelectOptionTag("mode", "full"),
        ).performClick()

        composeRule.runOnIdle {
            check(
                settingsState.draftValues["feature-enabled"] ==
                    PluginSettingDraftValue.Toggle(true),
            )
            check(
                settingsState.draftValues["display-name"] ==
                    PluginSettingDraftValue.Text("AstrBot"),
            )
            check(
                settingsState.draftValues["mode"] ==
                    PluginSettingDraftValue.Text("full"),
            )
        }
    }

    @Test
    fun staticConfigRendererShowsSectionMetaAndAppliesDraftUpdates() {
        val staticSchema = PluginStaticConfigSchema(
            fields = listOf(
                PluginStaticConfigField(
                    fieldKey = "enabled",
                    fieldType = PluginStaticConfigFieldType.BoolField,
                    description = "Whether the plugin is enabled.",
                    defaultValue = PluginStaticConfigValue.BoolValue(false),
                ),
                PluginStaticConfigField(
                    fieldKey = "token",
                    fieldType = PluginStaticConfigFieldType.StringField,
                    description = "Bot token",
                    hint = "Required for provider auth.",
                    defaultValue = PluginStaticConfigValue.StringValue("sk-demo"),
                    section = "credentials",
                ),
                PluginStaticConfigField(
                    fieldKey = "provider",
                    fieldType = PluginStaticConfigFieldType.StringField,
                    defaultValue = PluginStaticConfigValue.StringValue("openai"),
                    options = listOf(
                        PluginStaticConfigOption.Plain("openai"),
                        PluginStaticConfigOption.Labeled("gemini", "Gemini"),
                    ),
                    section = "credentials",
                ),
                PluginStaticConfigField(
                    fieldKey = "internal_note",
                    fieldType = PluginStaticConfigFieldType.StringField,
                    description = "Internal only",
                    invisible = true,
                    section = "advanced",
                ),
            ),
        )

        var draftValues by mutableStateOf<Map<String, PluginSettingDraftValue>>(emptyMap())

        composeRule.setContent {
            MaterialTheme {
                PluginStaticConfigRenderer(
                    model = buildPluginStaticConfigRenderModel(
                        schema = staticSchema,
                        draftValues = draftValues,
                    ),
                    onDraftChange = { fieldKey, draftValue ->
                        draftValues = draftValues + (fieldKey to draftValue)
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.SchemaStaticConfigTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.schemaStaticConfigSectionTag("general")).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.schemaStaticConfigSectionTag("credentials")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PluginUiSpec.schemaStaticConfigSectionTag("advanced")).assertCountEquals(0)
        composeRule.onNodeWithTag(PluginUiSpec.schemaStaticConfigDescriptionTag("token")).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.schemaStaticConfigHintTag("token")).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.schemaStaticConfigDefaultTag("token")).assertIsDisplayed()
        composeRule.onAllNodesWithText("Internal Note", useUnmergedTree = true).assertCountEquals(0)

        composeRule.onNodeWithTag(PluginUiSpec.schemaStaticConfigToggleTag("enabled")).performClick()
        composeRule.onNodeWithTag(PluginUiSpec.schemaStaticConfigTextInputTag("token"))
            .performTextReplacement("sk-live")
        composeRule.onNodeWithTag(
            PluginUiSpec.schemaStaticConfigSelectOptionTag("provider", "gemini"),
        ).performClick()

        composeRule.runOnIdle {
            check(draftValues["enabled"] == PluginSettingDraftValue.Toggle(true))
            check(draftValues["token"] == PluginSettingDraftValue.Text("sk-live"))
            check(draftValues["provider"] == PluginSettingDraftValue.Text("gemini"))
        }
    }
}
