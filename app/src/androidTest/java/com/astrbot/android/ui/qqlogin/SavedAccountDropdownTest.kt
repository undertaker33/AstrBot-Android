package com.astrbot.android.ui.qqlogin

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.astrbot.android.model.SavedQqAccount
import org.junit.Rule
import org.junit.Test

class SavedAccountDropdownTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun savedAccountDropdown_expands_and_shows_other_accounts() {
        composeRule.setContent {
            MaterialTheme {
                SavedAccountDropdown(
                    accounts = listOf(
                        SavedQqAccount(uin = "2643516203", nickName = "Account One"),
                        SavedQqAccount(uin = "1234567890", nickName = "Account Two"),
                    ),
                    selectedUin = "2643516203",
                    enabled = true,
                    onSelect = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Account Two (1234567890)").assertCountEquals(0)
        composeRule.onNodeWithText("Account One (2643516203)").performClick()
        composeRule.onNodeWithText("Account Two (1234567890)").assertIsDisplayed()
    }
}
