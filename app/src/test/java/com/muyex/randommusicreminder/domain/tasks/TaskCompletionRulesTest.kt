package com.muyex.randommusicreminder.domain.tasks

import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.planner.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class TaskCompletionRulesTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val date=LocalDate.of(2030,1,1)
    private val planner=OccurrencePlanner()
    private fun at(day:LocalDate=date,hour:Int=0,minute:Int=0)=day.atTime(hour,minute).atZone(zone).toInstant()
    private fun fixed()=EventDetails(ReminderEvent(1,"提交周报",scheduleType=ScheduleType.DAILY,localDate=date,localTime=LocalTime.of(9,0),trackCompletion=true),
        listOf(ReminderRule(2,1,-30),ReminderRule(3,1,0)))
    @Test fun advancePointsShareOccurrenceIdentityButNextDayIsIndependent() {
        val plan=planner.plan(listOf(fixed()),at(),at(date.plusDays(2)),zone)
        assertEquals(4,plan.size)
        assertEquals(1,plan.filter { it.baseDate==date }.map { it.taskOccurrenceKey }.distinct().size)
        assertNotEquals(plan.first().taskOccurrenceKey,plan.last().taskOccurrenceKey)
        assertTrue(plan.all { it.taskAtEpochMillis==at(it.baseDate,9).toEpochMilli() })
    }
    @Test fun completingOneDateSuppressesAllItsPointsAndKeepsNextCycle() {
        val states=mapOf(TaskIdentity(1,TaskIdentity.day(date)) to TaskState(TaskActionType.DONE,at(hour=8).toEpochMilli()))
        val plan=planner.plan(listOf(fixed()),at(),at(date.plusDays(2)),zone,taskStates=states)
        assertEquals(2,plan.size);assertTrue(plan.all { it.baseDate==date.plusDays(1) })
    }
    @Test fun reopeningDoesNotReplayEarlierPointWithinMissedGrace() {
        val states=mapOf(TaskIdentity(1,TaskIdentity.day(date)) to TaskState(TaskActionType.REOPEN,at(hour=8,minute=45).toEpochMilli()))
        val plan=planner.plan(listOf(fixed()),at(hour=8,minute=25),at(hour=10),zone,taskStates=states)
        assertEquals(1,plan.size);assertEquals(at(hour=9).toEpochMilli(),plan.single().triggerAtEpochMillis)
    }
    @Test fun shiftTimesAndNextDayTimesHaveSeparateKeysForTheSameStartingDay() {
        val first=com.muyex.randommusicreminder.domain.planner.ShiftTime(LocalTime.of(8,0))
        val second=com.muyex.randommusicreminder.domain.planner.ShiftTime(LocalTime.of(20,0))
        val next=com.muyex.randommusicreminder.domain.planner.ShiftTime(LocalTime.of(8,0),true)
        val cycle=ShiftCycle(listOf(ShiftDay("轮班",times=listOf(first,second,next))))
        val event=fixed().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.SHIFT,shiftCycle=cycle,endDate=date)) }
        val states=mapOf(TaskIdentity(1,TaskIdentity.shift(date,first.key)) to TaskState(TaskActionType.DONE,0))
        val plan=planner.plan(listOf(event),at(),at(date.plusDays(2)),zone,taskStates=states)
        assertEquals(4,plan.size)
        assertEquals(setOf(TaskIdentity.shift(date,second.key),TaskIdentity.shift(date,next.key)),plan.map { it.taskOccurrenceKey }.toSet())
        assertTrue(plan.all { it.baseDate==date })
    }
    @Test fun editingShiftTimeRetainsCompletedIdentity() {
        val slot=ShiftTime(LocalTime.of(8,0));val changed=slot.copy(time=LocalTime.of(10,0))
        val cycle=ShiftCycle(listOf(ShiftDay("白班",times=listOf(changed))))
        val event=fixed().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.SHIFT,shiftCycle=cycle)) }
        val states=mapOf(TaskIdentity(1,TaskIdentity.shift(date,slot.key)) to TaskState(TaskActionType.DONE,0))
        assertTrue(planner.plan(listOf(event),at(),at(date.plusDays(1)),zone,taskStates=states).isEmpty())
    }
    @Test fun switchingFixedToRandomDoesNotReplayCompletedDateAndReopenSkipsStartedWindow() {
        val event=fixed().let { it.copy(event=it.event.copy(timingMode=TimingMode.RANDOM_WINDOW,localTime=LocalTime.of(8,0),windowEndTime=LocalTime.of(8,10),windowBackupEnabled=true,createdAtEpochMillis=at().toEpochMilli())) }
        val key=TaskIdentity(1,TaskIdentity.day(date))
        assertTrue(planner.plan(listOf(event),at(),at(hour=10),zone,taskStates=mapOf(key to TaskState(TaskActionType.DONE,0))).isEmpty())
        assertTrue(planner.plan(listOf(event),at(),at(hour=10),zone,taskStates=mapOf(key to TaskState(TaskActionType.REOPEN,at(hour=8,minute=1).toEpochMilli()))).isEmpty())
    }
    @Test fun reopenRequiresStrictlyFutureAndDayKeyUsesOriginalBaseDate() {
        val state=TaskState(TaskActionType.REOPEN,1000)
        assertFalse(state.permits(1000));assertTrue(state.permits(1001))
        assertFalse(state.permits(5000,999));assertFalse(state.permits(5000,1000));assertTrue(state.permits(5000,1001))
        val event=fixed().let { it.copy(event=it.event.copy(localTime=LocalTime.of(0,10))) }
        val plan=planner.plan(listOf(event),at(date.minusDays(1),23),at(hour=1),zone)
        assertTrue(plan.all { it.taskOccurrenceKey==TaskIdentity.day(date) })
    }
}
