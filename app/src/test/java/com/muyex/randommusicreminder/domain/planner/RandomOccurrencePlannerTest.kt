package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RandomOccurrencePlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 10, 28)
    private fun at(hour: Int, minute: Int) = date.atTime(hour, minute).atZone(zone).toInstant()
    private fun event() = EventDetails(ReminderEvent(id=1,title="起床",scheduleType=ScheduleType.DAILY,
        localDate=date,localTime=LocalTime.of(8,0),timingMode=TimingMode.RANDOM_WINDOW,
        windowEndTime=LocalTime.of(8,10),randomSeed=42),
        listOf(ReminderRule(id=1,eventId=1,offsetMinutes=-10), ReminderRule(id=2,eventId=1,offsetMinutes=0)))
    private fun PlannedOccurrence.stored(state: OccurrenceState = OccurrenceState.SCHEDULED) = ScheduledOccurrence(
        id,eventId,reminderRuleId,baseDate,triggerAtEpochMillis,state=state,selectedLocalMinute=selectedLocalMinute,
        windowStartAtEpochMillis=windowStartAtEpochMillis,windowEndAtEpochMillis=windowEndAtEpochMillis,windowBackup=windowBackup)

    @Test fun windowUsesOneActiveRuleAndKeepsFixedConfiguration() {
        val details=event()
        assertEquals(2,details.rules.size)
        assertEquals(1,details.activeRules.size)
        assertNull(details.activeRules.single().snoozeMinutes)
        val plan=OccurrencePlanner().plan(listOf(details),at(7,50),at(9,0),zone)
        assertEquals(1,plan.size)
        assertTrue(plan.single().selectedLocalMinute in 480..490)
    }

    @Test fun existingSelectionSurvivesReplanningAndTimezoneChanges() {
        val planner=OccurrencePlanner();val details=event()
        val first=planner.plan(listOf(details),at(7,0),at(9,0),zone).single()
        val restored=planner.plan(listOf(details),at(7,0),at(9,0),zone,existing=listOf(first.stored())).single()
        assertEquals(first,restored)
        val tokyo=ZoneId.of("Asia/Tokyo")
        val moved=planner.plan(listOf(details),date.atTime(7,0).atZone(tokyo).toInstant(),date.atTime(9,0).atZone(tokyo).toInstant(),tokyo,existing=listOf(first.stored())).single()
        assertEquals(first.selectedLocalMinute,moved.selectedLocalMinute)
    }

    @Test fun missedSelectionIsNotRedrawnInsideRemainingWindow() {
        val planner=OccurrencePlanner();val first=planner.plan(listOf(event()),at(7,0),at(9,0),zone).single()
        val now=Instant.ofEpochMilli(first.triggerAtEpochMillis).plusSeconds(1)
        assertTrue(planner.plan(listOf(event()),now,at(9,0),zone,existing=listOf(first.stored())).isEmpty())
    }

    @Test fun completedWindowDoesNotReturnAfterClockChangeOrRevision() {
        val planner=OccurrencePlanner();val details=event();val first=planner.plan(listOf(details),at(7,0),at(9,0),zone).single()
        val edited=details.copy(event=details.event.copy(timingRevision=1,localTime=LocalTime.of(8,1)))
        assertTrue(planner.plan(listOf(edited),at(7,0),at(9,0),zone,existing=listOf(first.stored(OccurrenceState.COMPLETED))).isEmpty())
    }

    @Test fun optionalBackupIsDistinctAndNotEnabledByDefault() {
        val details=event().let { it.copy(event=it.event.copy(windowBackupEnabled=true)) }
        val plan=OccurrencePlanner().plan(listOf(details),at(7,0),at(9,0),zone)
        assertEquals(2,plan.size)
        assertEquals(at(8,10).toEpochMilli(),plan.single { it.windowBackup }.triggerAtEpochMillis)
    }

    @Test fun terminalFixedOccurrenceCannotBeResurrectedByReconcile() {
        val desired=PlannedOccurrence("same",1,1,date,2000)
        val old=ScheduledOccurrence("same",1,1,date,1000,state=OccurrenceState.COMPLETED)
        assertTrue(OccurrencePlanner().reconcile(listOf(desired),listOf(old)).upserts.isEmpty())
    }
}
