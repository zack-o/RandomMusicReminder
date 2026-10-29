package com.muyex.randommusicreminder.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.muyex.randommusicreminder.domain.tasks.*
import com.muyex.randommusicreminder.ui.components.TaskCompletionDialog
import com.muyex.randommusicreminder.ui.screens.CompletionHistoryScreen
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TaskCompletionUiIntegrationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun selectingOccurrenceRequiresSecondConfirmation() {
        val date=LocalDate.of(2030,1,1)
        val choice=TaskChoice(1,TaskIdentity.day(date),date,"2030-01-01 09:00",1000L,"取票",1,2,false)
        var completed:TaskChoice?=null
        compose.setContent { LexingTheme { TaskCompletionDialog(TaskChoiceList(1,"取票",TaskChoiceRange.UPCOMING,listOf(choice)),false,{},{completed=it},{}) } }
        compose.onNodeWithText("核对完成范围").performClick()
        compose.runOnIdle { assertNull(completed) }
        compose.onNodeWithText("确认完成本次").performClick()
        compose.runOnIdle { assertEquals(choice,completed) }
    }
    @Test fun undoIsProtectedByConfirmationAndOldActionCannotBeSelected() {
        val date=LocalDate.of(2030,1,1)
        val old=TaskActionRecord(1,1,TaskIdentity.day(date),date,"2030-01-01 09:00","提交周报",TaskActionType.DONE,1000)
        val latest=old.copy(id=2,action=TaskActionType.REOPEN)
        val current=old.copy(id=3,key=TaskIdentity.day(date.plusDays(1)),label="2030-01-02 09:00")
        var reopened:Long?=null
        compose.setContent { LexingTheme { CompletionHistoryScreen(listOf(current,latest,old),setOf(1L),false,{reopened=it},{}) } }
        compose.onAllNodesWithText("撤销这次完成").assertCountEquals(1)
        compose.onNodeWithText("撤销这次完成").performScrollTo().performClick()
        compose.runOnIdle { assertNull(reopened) }
        compose.onNodeWithText("确认撤销完成").performClick()
        compose.runOnIdle { assertEquals(3L,reopened) }
    }
}
