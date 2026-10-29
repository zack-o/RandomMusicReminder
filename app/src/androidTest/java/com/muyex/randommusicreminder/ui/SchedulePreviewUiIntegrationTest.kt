package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.ui.components.*
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SchedulePreviewUiIntegrationTest {
    @get:Rule val compose=createComposeRule()
    private val draft=EventDraftUi(title="开售提醒",onceDate=LocalDate.of(2030,1,1))
    private fun result()=SchedulePreview(1000,"Asia/Shanghai",2000,emptyList(),emptyList(),listOf("核对规则"),false,false)
    @Test fun previewCanReturnWithoutSavingOrLosingItsDraft() {
        var dismissed=false;var requested:EventDraftUi?=null
        compose.setContent { LexingTheme { SchedulePreviewDialog(draft,{requested=it;result()}) { dismissed=true } } }
        compose.onNodeWithText("此范围没有未来提示").assertExists()
        compose.onNodeWithText("未来90天 · 最多14条安排").assertExists()
        compose.onNodeWithText("返回编辑并核对保存").performClick()
        compose.runOnIdle { assertTrue(dismissed);assertEquals(draft,requested) }
    }
    @Test fun failedReadShowsRetryAndSuccessfulRetryReplacesTheError() {
        var calls=0
        compose.setContent { LexingTheme { SchedulePreviewDialog(draft,{if(++calls==1) error("读取暂不可用") else result()},{}) } }
        compose.onNodeWithText("读取暂不可用").assertIsDisplayed()
        compose.onNodeWithText("重新计算").performClick()
        compose.onNodeWithText("此范围没有未来提示").assertExists()
        compose.runOnIdle { assertEquals(2,calls) }
    }
    @Test fun invalidEditorDraftCannotStartPreviewOrSave() {
        var previews=0;var saves=0
        compose.setContent { LexingTheme { EventEditorDialog(draft.copy(title=""),emptyList(),30,60,{}, {}, {saves++},null,onPreview={previews++;result()}) } }
        compose.onNodeWithTag("schedule_preview_open").performClick()
        compose.onNodeWithText("请填写事项名称").assertExists()
        compose.runOnIdle { assertEquals(0,previews);assertEquals(0,saves) }
    }
}
