package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.domain.model.ScheduleType
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.ui.components.DayOverrideRangeDialog
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DateRangeUiIntegrationTest {
    @get:Rule val compose=createComposeRule()
    private val date=LocalDate.of(2030,1,1)
    private val scope=RangeAdjustmentScope(ScheduleType.SHIFT,date)
    private fun replacement()=DayOverride(date.plusDays(1),DayOverrideKind.REPLACE_SHIFT,ShiftDay("临时夜班",times=listOf(ShiftTime(LocalTime.of(20,0)))))
    @Test fun pauseRangeRequiresConfirmationAndIncludesBothEnds() {
        var applied:List<DayOverride>?=null
        compose.setContent { LexingTheme { DayOverrideRangeDialog(scope,date,emptyList(),{applied=it},{}) } }
        compose.onNodeWithText("结束日期").performTextReplacement("2030-01-03")
        compose.onNodeWithText("核对区间操作").performClick()
        compose.onNodeWithText("新增跳过3天",substring=true).assertExists()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("确认加入草稿").performClick()
        compose.runOnIdle { assertEquals(listOf(date,date.plusDays(1),date.plusDays(2)),applied?.map { it.date }) }
    }
    @Test fun keepIsDefaultAndOverwritingRequiresExplicitSelection() {
        var applied:List<DayOverride>?=null
        compose.setContent { LexingTheme { DayOverrideRangeDialog(scope,date,listOf(replacement()),{applied=it},{}) } }
        compose.onNodeWithText("结束日期").performTextReplacement("2030-01-03")
        compose.onNodeWithText("核对区间操作").performClick()
        compose.onNodeWithText("保留临时班次1天",substring=true).assertExists()
        compose.onNodeWithText("返回修改").performClick()
        compose.onNodeWithText("一并替换为跳过").performScrollTo().performClick()
        compose.onNodeWithText("核对区间操作").performClick()
        compose.onNodeWithText("覆盖临时班次1天",substring=true).assertExists()
        compose.onNodeWithText("确认加入草稿").performClick()
        compose.runOnIdle { assertTrue(checkNotNull(applied).all { it.kind==DayOverrideKind.SKIP }) }
    }
    @Test fun restoreOnlyRemovesSkipsWithinRange() {
        val values=listOf(DayOverride(date,DayOverrideKind.SKIP),replacement(),DayOverride(date.plusDays(10),DayOverrideKind.SKIP))
        var applied:List<DayOverride>?=null
        compose.setContent { LexingTheme { DayOverrideRangeDialog(scope,date,values,{applied=it},{}) } }
        compose.onNodeWithText("恢复区间内跳过").performClick()
        compose.onNodeWithText("结束日期").performTextReplacement("2030-01-03")
        compose.onNodeWithText("核对区间操作").performClick()
        compose.onNodeWithText("确认加入草稿").performClick()
        compose.runOnIdle { assertEquals(values.drop(1),applied) }
    }
    @Test fun quotaFailureDoesNotApplyPartialRange() {
        val before=(0 until 99).map { DayOverride(date.plusDays(1000+it.toLong()),DayOverrideKind.SKIP) }
        var applied:List<DayOverride>?=null
        compose.setContent { LexingTheme { DayOverrideRangeDialog(scope,date,before,{applied=it},{}) } }
        compose.onNodeWithText("结束日期").performTextReplacement("2030-01-03")
        compose.onNodeWithText("核对区间操作").performClick()
        compose.onNodeWithText("合并后共102个",substring=true).assertIsDisplayed()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("确认加入草稿").assertDoesNotExist()
    }
}
