package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.ui.screens.TrashScreen
import com.muyex.randommusicreminder.ui.screens.TagsScreen
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OrganizerUiIntegrationTest {
    @get:Rule val compose=createComposeRule()
    private fun item(id:Long)=ReminderListItem(id,"事项$id","每日","准时",UiAlertMode.GENTLE,30,"全部音乐",false,tags=listOf("工作"),deletedAtEpochMillis=1000)
    @Test fun clearingTrashRequiresConfirmationAndSubmitsOnlyTheDisplayedSnapshot() {
        var deleted:List<Long>?=null
        compose.setContent { LexingTheme { TrashScreen(listOf(item(1),item(2)),false,{},{deleted=it}) } }
        compose.onNodeWithText("清空回收站（全部2项）").performScrollTo().performClick()
        compose.onNodeWithText("清空这2项？").assertIsDisplayed()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("确认永久删除").performClick()
        compose.runOnIdle { assertEquals(listOf(1L,2L),deleted) }
    }
    @Test fun restoringItemUsesExplicitDisabledConfirmation() {
        var restored:Long?=null
        compose.setContent { LexingTheme { TrashScreen(listOf(item(1)),false,{restored=it},{}) } }
        compose.onNodeWithText("恢复").performScrollTo().performClick()
        compose.runOnIdle { assertNull(restored) }
        compose.onNodeWithText("恢复并停用").performClick()
        compose.runOnIdle { assertEquals(1L,restored) }
    }
    @Test fun renamingTagShowsAffectedCountsAndWaitsForConfirmation() {
        var result:Pair<String,String>?=null
        compose.setContent { LexingTheme { TagsScreen(listOf(item(1).copy(deletedAtEpochMillis=null)),listOf(item(2)),false,{old,new->result=old to new},{}) } }
        compose.onNodeWithText("改名").performScrollTo().performClick()
        compose.onNodeWithText("标签名称").performTextReplacement("出行")
        compose.runOnIdle { assertNull(result) }
        compose.onNodeWithText("确认改名").performClick()
        compose.runOnIdle { assertEquals("工作" to "出行",result) }
    }
}
