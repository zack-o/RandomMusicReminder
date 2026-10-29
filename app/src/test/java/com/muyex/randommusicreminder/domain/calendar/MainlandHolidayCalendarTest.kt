package com.muyex.randommusicreminder.domain.calendar

import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.planner.OccurrencePlanner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MainlandHolidayCalendarTest {
    private fun include(date: String, policy: HolidayPolicy, original: Boolean = true) =
        MainlandHolidayCalendar.shouldInclude(LocalDate.parse(date), policy, original)

    @Test fun noneAlwaysPreservesOriginalRule() {
        assertTrue(include("2026-10-01", HolidayPolicy.NONE))
        assertFalse(include("2026-10-10", HolidayPolicy.NONE, false))
    }
    @Test fun holidaySkippingDoesNotInventMakeupOccurrences() {
        assertFalse(include("2026-02-16", HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS))
        assertFalse(include("2026-02-14", HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS, false))
        assertTrue(include("2026-02-14", HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS))
    }
    @Test fun workdaysIncludeOfficialMakeupWeekendsAndExcludeHolidays() {
        listOf("01-04", "02-14", "02-28", "05-09", "09-20", "10-10").forEach {
            assertTrue(include("2026-$it", HolidayPolicy.MAINLAND_WORKDAYS, false))
        }
        assertFalse(include("2026-10-01", HolidayPolicy.MAINLAND_WORKDAYS))
        assertFalse(include("2026-10-11", HolidayPolicy.MAINLAND_WORKDAYS))
        assertTrue(include("2026-10-12", HolidayPolicy.MAINLAND_WORKDAYS, false))
    }
    @Test fun unknownYearRetainsOriginalRuleRatherThanGuessing() {
        assertTrue(include("2027-01-01", HolidayPolicy.MAINLAND_WORKDAYS))
        assertFalse(include("2027-01-02", HolidayPolicy.MAINLAND_WORKDAYS, false))
    }
    @Test fun officialDataContainsExactlyThirtyThreeDaysOff() {
        val start = LocalDate.of(2026, 1, 1)
        assertEquals(33, (0L..364L).count {
            !MainlandHolidayCalendar.shouldInclude(start.plusDays(it), HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS, true)
        })
    }
    @Test fun crossDayRemindersFollowEventDateNotTriggerDate() {
        val zone = ZoneId.of("Asia/Shanghai")
        val details = EventDetails(ReminderEvent(1, "工作", scheduleType = ScheduleType.DAILY,
            localDate = LocalDate.of(2026, 2, 13), localTime = LocalTime.of(8, 0), holidayPolicy = HolidayPolicy.MAINLAND_WORKDAYS),
            listOf(ReminderRule(1, 1, -1440), ReminderRule(2, 1, 0)))
        val rows = OccurrencePlanner().plan(listOf(details), LocalDate.of(2026, 2, 13).atStartOfDay(zone).toInstant(),
            LocalDate.of(2026, 2, 16).atStartOfDay(zone).toInstant(), zone)
        assertTrue(rows.any { it.baseDate == LocalDate.of(2026, 2, 14) && it.reminderRuleId == 1L })
        assertFalse(rows.any { it.baseDate == LocalDate.of(2026, 2, 15) })
    }
    @Test fun weeklyWorkdayPolicyAddsMakeupSaturday() {
        val zone = ZoneId.of("Asia/Shanghai")
        val details = EventDetails(ReminderEvent(1, "上班", scheduleType = ScheduleType.WEEKLY,
            localDate = LocalDate.of(2026, 2, 1), localTime = LocalTime.NOON,
            weekdaysMask = WeekdayMask.from(setOf(java.time.DayOfWeek.MONDAY)), holidayPolicy = HolidayPolicy.MAINLAND_WORKDAYS),
            listOf(ReminderRule(1, 1, 0)))
        val rows = OccurrencePlanner().plan(listOf(details), LocalDate.of(2026, 2, 14).atStartOfDay(zone).toInstant(),
            LocalDate.of(2026, 2, 16).atStartOfDay(zone).toInstant(), zone)
        assertEquals(listOf(LocalDate.of(2026, 2, 14)), rows.map { it.baseDate })
    }
}
