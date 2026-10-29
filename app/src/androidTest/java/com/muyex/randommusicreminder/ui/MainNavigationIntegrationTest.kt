package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.muyex.randommusicreminder.app.MainActivity
import org.junit.Rule
import org.junit.Test

class MainNavigationIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun bottomNavigationSwitchesWithoutMainPagerAndToolsRemainInSettings() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("我的提醒").fetchSemanticsNodes().isNotEmpty() }
        captureUi("reminders")
        compose.onNodeWithText("音乐", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("page_MUSIC").assertIsDisplayed()
        compose.onNodeWithText("日历", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("page_CALENDAR").assertIsDisplayed()
        captureUi("calendar")
        compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithText("设置中心").assertIsDisplayed()
        captureUi("settings-hub")
        compose.onNodeWithText("外观与主题").performClick()
        compose.onNodeWithTag("main_pager").assertDoesNotExist()
        compose.onNodeWithText("海蓝").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("海蓝") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        captureUi("appearance", expectDark = false)
        compose.onNodeWithText("深色").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("深色") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertFalse(androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
        captureUi("appearance-dark", expectDark = true)
        compose.onNodeWithText("跟随系统").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("跟随系统") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("设置中心").assertIsDisplayed()
    }
}
