package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.ai.AiConfig
import com.muyex.randommusicreminder.domain.model.TimingMode
import com.muyex.randommusicreminder.ui.components.EventEditorDialog
import com.muyex.randommusicreminder.ui.screens.AiScreen
import com.muyex.randommusicreminder.ui.screens.ReminderScreen
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class V3UiIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @Composable private fun Frame(content: @Composable () -> Unit) {
        LexingTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding()) { content() } }
    }
    @Test fun randomEditorCanSwipeSubpagesAndRequiresSaveReview() {
        var saved: EventDraftUi? = null
        val draft = EventDraftUi(title="早晨起床",onceDate=LocalDate.of(2030,10,28),timingMode=TimingMode.RANDOM_WINDOW)
        compose.setContent { Frame { EventEditorDialog(draft,emptyList(),30,60,{}, {}, {saved=it},null) } }
        compose.onNodeWithText("时间窗随机").assertIsSelected()
        captureUi("v3-window-editor")
        compose.onNodeWithTag("editor_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("何时发生").assertIsDisplayed()
        compose.onNodeWithText("响铃",useUnmergedTree=true).performClick()
        compose.onNodeWithText("一次主提醒").assertIsDisplayed()
        captureUi("v3-window-ringing")
        compose.onNodeWithText("保存提醒").performClick()
        compose.onNodeWithText("确认随机时间窗").assertIsDisplayed()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("确认保存").performClick()
        compose.runOnIdle { assertNotNull(saved);assertNull(saved?.windowSnoozeMinutes);assertEquals(false,saved?.windowBackupEnabled) }
    }
    @Test fun homeHidesSelectionUntilUserExplicitlyRevealsIt() {
        val item=ReminderListItem(1,"早晨起床","每天","随机主提醒1次 · 稍后关闭",UiAlertMode.GENTLE,30,"全部音乐随机",true,
            nextReminderText="下次时间窗 10-28 08:00 — 10-28 08:10 · 已安排",eventTimeText="08:00 — 08:10",randomWindow=true,
            selectionText="本次已选 10-28 08:06",nextAtEpochMillis=1000)
        compose.setContent { Frame { ReminderScreen(listOf(item),{},{},{_,_->},{}) } }
        compose.onNodeWithText("本次已选 10-28 08:06").assertDoesNotExist()
        captureUi("v3-home")
        compose.onAllNodesWithText("早晨起床").onFirst().performClick()
        compose.onNodeWithText("查看本次已选时刻").performClick()
        compose.onNodeWithText("本次已选 10-28 08:06").assertIsDisplayed()
    }
    @Test fun workbenchOffersTaskSpecificActionsAndMultipleConnectionProfiles() {
        val config=AiConfig(true,"https://api.deepseek.com","deepseek-flash",profileName="DeepSeek")
        compose.setContent { Frame { AiScreen(ToolsState(config=config,hasKey=true,profiles=listOf(config)),{_,_->},{},{_,_->},{},{},{},{},{}) } }
        compose.onNodeWithText("修改所选提醒").performScrollTo().assertExists()
        captureUi("v3-ai-workbench")
        compose.onNodeWithText("音乐搭配建议").assertExists()
        compose.onNodeWithText("新增连接").performScrollTo().performClick()
        compose.onNodeWithText("百炼·北京").performScrollTo().assertExists()
        compose.onNodeWithText("连接名称").assertExists()
    }
}
