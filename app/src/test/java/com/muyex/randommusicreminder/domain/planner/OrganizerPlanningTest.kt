package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class OrganizerPlanningTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val date=LocalDate.of(2030,1,1)
    private fun at(hour:Int,minute:Int=0)=date.atTime(hour,minute).atZone(zone).toInstant()
    private fun details(event:ReminderEvent=ReminderEvent(id=1,title="工作事项",scheduleType=ScheduleType.ONCE,localDate=date,localTime=LocalTime.of(10,0)))=
        EventDetails(event,listOf(ReminderRule(id=2,eventId=1,offsetMinutes=0)))
    @Test fun deletedEventCannotPlanEvenIfEnabledFlagIsInconsistent() {
        val original=details()
        assertTrue(OccurrencePlanner().plan(listOf(original.copy(event=original.event.copy(deletedAtEpochMillis=1))),at(9),at(11),zone).isEmpty())
    }
    @Test fun restoreBoundaryExcludesOldAdvanceButKeepsFutureMainReminder() {
        val original=details()
        val restored=original.copy(event=original.event.copy(resumedAfterEpochMillis=at(9,55).toEpochMilli()),
            rules=listOf(ReminderRule(id=2,eventId=1,offsetMinutes=-10),ReminderRule(id=3,eventId=1,offsetMinutes=0)))
        val plan=OccurrencePlanner().plan(listOf(restored),at(9,45),at(11),zone)
        assertEquals(listOf(at(10).toEpochMilli()),plan.map { it.triggerAtEpochMillis })
    }
    @Test fun shiftUsesSameRestoreBoundaryForEachDailySlot() {
        val cycle=ShiftCycle(listOf(ShiftDay("交接",times=listOf(ShiftTime(LocalTime.of(9,50)),ShiftTime(LocalTime.of(10,0))))))
        val original=details().event.copy(scheduleType=ScheduleType.SHIFT,shiftCycle=cycle,resumedAfterEpochMillis=at(9,55).toEpochMilli())
        assertEquals(1,OccurrencePlanner().plan(listOf(details(original)),at(9,45),at(11),zone).size)
    }
    @Test fun restoreSkipsAnAlreadyStartedRandomWindowIncludingItsBackup() {
        val event=details().event.copy(scheduleType=ScheduleType.DAILY,timingMode=TimingMode.RANDOM_WINDOW,localTime=LocalTime.of(9,50),
            windowEndTime=LocalTime.of(10,10),windowBackupEnabled=true,createdAtEpochMillis=at(9).toEpochMilli(),resumedAfterEpochMillis=at(9,55).toEpochMilli())
        val planner=OccurrencePlanner()
        assertTrue(planner.plan(listOf(details(event)),at(9,45),at(11),zone).isEmpty())
        val next=planner.plan(listOf(details(event)),at(9).plusSeconds(86400),at(11).plusSeconds(86400),zone)
        assertEquals(1,next.count { !it.windowBackup })
        assertTrue(next.all { it.baseDate==date.plusDays(1) })
    }
}
