package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.ui.components.EventEditorDialog
import com.muyex.randommusicreminder.ui.screens.SettingsHubScreen
import com.muyex.randommusicreminder.ui.screens.AppearanceScreen
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import com.muyex.randommusicreminder.domain.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfessionalUiIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private fun editor() { compose.setContent { LexingTheme { EventEditorDialog(EventDraftUi(), emptyList(), 30, 60, {}, {}, {}, null) } } }
    @Test fun editorSectionsDoNotRenderEntireLongForm() {
        editor()
        compose.onNodeWithText("事项名称").assertExists()
        compose.onNodeWithText("重复").performClick()
        compose.onNodeWithText("事项名称").assertDoesNotExist()
        compose.onNodeWithText("每月").performClick()
        compose.onNodeWithText("取当月最后一天").assertExists()
        captureUi("editor-monthly")
        compose.onNodeWithText("每年").assertExists()
        compose.onNodeWithText("每N天").performClick()
        compose.onNodeWithText("每隔多少天").assertExists()
        captureUi("editor-interval")
    }
    @Test fun reminderPointAcceptsDaysWithoutManualMinuteConversion() {
        editor()
        compose.onNodeWithText("提醒点", useUnmergedTree = true).performClick()
        compose.onNodeWithText("添加提醒点").performClick()
        compose.onNodeWithText("提前量（0表示准时）").performTextReplacement("7")
        compose.onNodeWithText("天").performClick()
        compose.onNodeWithText("保存此提醒点").performClick()
        compose.onNodeWithText("提前7天").assertExists()
        captureUi("editor-points")
    }
    @Test fun toolsAreReachableFromOneSettingsCenter() {
        var route: AppSection? = null
        compose.setContent { LexingTheme { SettingsHubScreen(SettingsUi(), { route = it }) } }
        compose.onNodeWithText("外观与主题").performClick()
        compose.runOnIdle { assertEquals(AppSection.APPEARANCE, route) }
        compose.onNodeWithText("AI助手").assertExists()
        compose.onNodeWithText("响铃历史").assertExists()
        compose.onNodeWithText("导入导出").performScrollTo().assertExists()
    }
    @Test fun customThemeRejectsInvalidColorAndCanApplyValidColor() {
        var received: AppearanceSettings? = null
        compose.setContent { LexingTheme { AppearanceScreen(AppearanceSettings(palette = ThemePalette.CUSTOM), { received = it }) } }
        compose.onNodeWithText("自定义主色 #RRGGBB").performTextReplacement("#12ZZ00")
        compose.onNodeWithText("应用自定义颜色").performClick()
        compose.runOnIdle { assertNull(received) }
        compose.onNodeWithText("自定义主色 #RRGGBB").performTextReplacement("#336699")
        compose.onNodeWithText("应用自定义颜色").performClick()
        compose.runOnIdle { assertEquals("#336699", received?.customColor) }
    }
}
