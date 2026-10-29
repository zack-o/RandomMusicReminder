package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.ui.components.DayOverrideEditor
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DayOverrideUiIntegrationTest {
    @get:Rule val compose=createComposeRule()
    private val date=LocalDate.of(2030,1,1)
    @Test fun skipIsAddedOnlyAfterConfirmationAndDoesNotSaveTheReminder() {
        var changed:List<DayOverride>?=null
        compose.setContent { LexingTheme { DayOverrideEditor(UiScheduleType.DAILY,date,null,ShiftCycle.template(),emptyList()) { changed=it } } }
        compose.onNodeWithText("跳过这一天").performClick()
        compose.runOnIdle { assertNull(changed) }
        compose.onNodeWithText("加入当前草稿").performClick()
        compose.runOnIdle { assertEquals(listOf(DayOverride(date,DayOverrideKind.SKIP)),changed) }
    }
    @Test fun restoringOneDateRequiresConfirmationAndRetainsOtherDates() {
        val values=listOf(DayOverride(date,DayOverrideKind.SKIP),DayOverride(date.plusDays(1),DayOverrideKind.SKIP))
        var changed:List<DayOverride>?=null
        compose.setContent { LexingTheme { DayOverrideEditor(UiScheduleType.SHIFT,date,null,ShiftCycle.template(),values) { changed=it } } }
        compose.onAllNodesWithText("恢复原规则")[0].performScrollTo().performClick()
        compose.runOnIdle { assertNull(changed) }
        compose.onNodeWithText("恢复到当前草稿").performClick()
        compose.runOnIdle { assertEquals(listOf(values[1]),changed) }
    }
}
