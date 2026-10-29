package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.calendar.CalendarDayType
import com.muyex.randommusicreminder.domain.calendar.HolidayPolicy
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ShiftOccurrencePlannerTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val date=LocalDate.of(2030,1,1)
    private fun details(cycle:ShiftCycle=ShiftCycle.template("白夜休休"))=EventDetails(ReminderEvent(id=1,title="轮班交接",scheduleType=ScheduleType.SHIFT,
        localDate=date,localTime=LocalTime.NOON,shiftCycle=cycle),listOf(ReminderRule(id=2,eventId=1,offsetMinutes=0)))
    private fun instant(day:LocalDate,h:Int=0)=day.atTime(h,0).atZone(zone).toInstant()
    @Test fun oneCycleOnlyRemindsOnConfiguredDaysAndDoesNotResetNextMonth() {
        val event=details();val planner=OccurrencePlanner()
        val result=planner.plan(listOf(event),instant(date),instant(date.plusDays(8)),zone)
        assertEquals(listOf(date,date.plusDays(1),date.plusDays(4),date.plusDays(5)),result.map { it.baseDate })
        assertTrue(result.first().sourceLabel.orEmpty().contains("白班"))
        val later=planner.plan(listOf(event),instant(date.plusDays(30)),instant(date.plusDays(36)),zone)
        assertEquals(listOf(date.plusDays(32),date.plusDays(33)),later.map { it.baseDate })
    }
    @Test fun nightNextDayBelongsToStartingShiftEvenAtEndDate() {
        val cycle=ShiftCycle(listOf(ShiftDay("夜班",times=listOf(ShiftTime(LocalTime.of(19,30)),ShiftTime(LocalTime.of(8,0),true)))))
        val event=details(cycle).let { it.copy(event=it.event.copy(endDate=date)) }
        val result=OccurrencePlanner().plan(listOf(event),instant(date.plusDays(1)),instant(date.plusDays(2)),zone)
        assertEquals(1,result.size);assertEquals(date,result.single().baseDate)
        assertEquals(instant(date.plusDays(1),8).toEpochMilli(),result.single().triggerAtEpochMillis)
        assertTrue(result.single().sourceLabel.orEmpty().contains("次日 08:00"))
    }
    @Test fun filteringOneDayDoesNotAdvanceOrResetCycle() {
        val event=details().let { it.copy(event=it.event.copy(holidayPolicy=HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS)) }
        val result=OccurrencePlanner().plan(listOf(event),instant(date),instant(date.plusDays(8)),zone,
            overrides=mapOf(date to CalendarDayType.REST))
        assertFalse(result.any { it.baseDate==date })
        assertTrue(result.any { it.baseDate==date.plusDays(4) && it.sourceLabel.orEmpty().contains("白班") })
    }
    @Test fun fixedTimeKeepsSecondsAndEditingShiftTimeKeepsSlotIdentity() {
        val event=details();val planner=OccurrencePlanner()
        val first=planner.plan(listOf(event),instant(date),instant(date.plusDays(1)),zone).single()
        val cycle=event.event.shiftCycle!!
        val changed=cycle.copy(days=cycle.days.mapIndexed { i, day -> if(i==0) day.copy(times=day.times.map { it.copy(time=LocalTime.of(9,59,55)) }) else day })
        val edited=planner.plan(listOf(event.copy(event=event.event.copy(shiftCycle=changed))),instant(date),instant(date.plusDays(1)),zone).single()
        assertEquals(first.id,edited.id)
        assertEquals(55,Instant.ofEpochMilli(edited.triggerAtEpochMillis).atZone(zone).second)
        val stored=ScheduledOccurrence(first.id,1,2,date,first.triggerAtEpochMillis,state=OccurrenceState.COMPLETED)
        assertTrue(planner.reconcile(listOf(edited),listOf(stored)).upserts.isEmpty())
    }
    @Test fun nightNextDayUsesLocalCalendarAcrossDaylightSavingChange() {
        val ny=ZoneId.of("America/New_York");val anchor=LocalDate.of(2026,3,7)
        val cycle=ShiftCycle(listOf(ShiftDay("夜班",times=listOf(ShiftTime(LocalTime.of(19,30)),ShiftTime(LocalTime.of(8,0),true)))))
        val event=details(cycle).let { it.copy(event=it.event.copy(localDate=anchor,endDate=anchor)) }
        val plan=OccurrencePlanner().plan(listOf(event),anchor.atStartOfDay(ny).toInstant(),anchor.plusDays(2).atStartOfDay(ny).toInstant(),ny)
        assertEquals(listOf(LocalTime.of(19,30),LocalTime.of(8,0)),plan.map { Instant.ofEpochMilli(it.triggerAtEpochMillis).atZone(ny).toLocalTime() })
        assertTrue(plan.all { it.baseDate==anchor })
    }
}
