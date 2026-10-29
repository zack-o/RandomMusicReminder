package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.ui.components.EventEditorDialog
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class V4EditorIntegrationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun secondWheelUpdatesDraftAndTurningItOffClearsHiddenSeconds() {
        var latest:EventDraftUi?=null
        val initial=EventDraftUi(title="抢票开售",onceDate=LocalDate.of(2030,1,1),scheduleType=UiScheduleType.ONCE)
        compose.setContent { LexingTheme { EventEditorDialog(initial,emptyList(),30,60,{latest=it},{},{},null) } }
        compose.onNodeWithTag("clock_seconds_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("clock_wheel_秒").performTouchInput { swipeUp() }
        compose.waitUntil(5000) { latest?.eventTime?.second?.let { it != 0 } == true }
        compose.onNodeWithTag("clock_seconds_toggle").performClick()
        compose.runOnIdle { assertEquals(0,latest?.eventTime?.second) }
    }
    @Test fun shiftCreationShowsExplicitCycleReviewBeforeWriting() {
        var saved:EventDraftUi?=null
        val initial=EventDraftUi(title="工作交接",onceDate=LocalDate.of(2030,1,1),scheduleType=UiScheduleType.SHIFT)
        compose.setContent { LexingTheme { EventEditorDialog(initial,emptyList(),30,60,{},{},{saved=it},null) } }
        compose.onNodeWithText("时间窗随机").assertIsNotEnabled()
        compose.onNodeWithText("重复",useUnmergedTree=true).performClick()
        compose.onNodeWithText("每个周期如何安排").performScrollTo().assertExists()
        compose.onNodeWithText("保存提醒").performClick()
        compose.onNodeWithText("确认轮班提醒").assertIsDisplayed()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("确认保存轮班").performClick()
        compose.runOnIdle { assertEquals(UiScheduleType.SHIFT,saved?.scheduleType);assertEquals(6,saved?.shiftCycle?.days?.size) }
    }
}
