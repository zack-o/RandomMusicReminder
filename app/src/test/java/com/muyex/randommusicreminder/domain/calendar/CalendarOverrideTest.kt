package com.muyex.randommusicreminder.domain.calendar

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarOverrideTest {
    private val holiday = LocalDate.parse("2026-10-01")
    @Test fun noneIgnoresCustomCalendar() {
        assertTrue(MainlandHolidayCalendar.shouldInclude(holiday, HolidayPolicy.NONE, true, CalendarDayType.REST))
        assertFalse(MainlandHolidayCalendar.shouldInclude(holiday, HolidayPolicy.NONE, false, CalendarDayType.WORK))
    }
    @Test fun restOverridesOfficialWorkdayForEnabledPolicies() {
        val date = LocalDate.parse("2026-10-12")
        assertFalse(MainlandHolidayCalendar.shouldInclude(date, HolidayPolicy.MAINLAND_WORKDAYS, true, CalendarDayType.REST))
        assertFalse(MainlandHolidayCalendar.shouldInclude(date, HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS, true, CalendarDayType.REST))
    }
    @Test fun workCanOverrideOfficialHoliday() {
        assertTrue(MainlandHolidayCalendar.shouldInclude(holiday, HolidayPolicy.MAINLAND_WORKDAYS, false, CalendarDayType.WORK))
        assertTrue(MainlandHolidayCalendar.shouldInclude(holiday, HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS, true, CalendarDayType.WORK))
    }
    @Test fun skipOnlyDoesNotInventNewDatesEvenForCustomWorkdays() {
        assertFalse(MainlandHolidayCalendar.shouldInclude(holiday, HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS, false, CalendarDayType.WORK))
    }
    @Test fun customDateOutsideCoverageDoesNotCertifyEntireYear() {
        val unknown = LocalDate.parse("2027-10-01")
        assertFalse(MainlandHolidayCalendar.shouldInclude(unknown, HolidayPolicy.MAINLAND_WORKDAYS, true, CalendarDayType.REST))
        assertTrue(MainlandHolidayCalendar.shouldInclude(unknown.plusDays(1), HolidayPolicy.MAINLAND_WORKDAYS, true))
        assertNull(MainlandHolidayCalendar.defaultDayType(unknown))
    }
    @Test fun defaultClassificationMatchesMakeupAndRest() {
        assertEquals(CalendarDayType.REST, MainlandHolidayCalendar.defaultDayType(holiday))
        assertEquals(CalendarDayType.WORK, MainlandHolidayCalendar.defaultDayType(LocalDate.parse("2026-10-10")))
    }
    @Test fun notesAndDatesAreBounded() {
        assertThrows(IllegalArgumentException::class.java) { CalendarOverride(LocalDate.parse("1800-01-01"), CalendarDayType.REST) }
        assertThrows(IllegalArgumentException::class.java) { CalendarOverride(holiday, CalendarDayType.REST, "a".repeat(501)) }
    }
    @Test fun plannerAppliesOverrideToEventDateIncludingAdvanceReminders() {
        val date = LocalDate.parse("2026-10-22")
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val event = com.muyex.randommusicreminder.domain.model.ReminderEvent(1, "会议",
            scheduleType = com.muyex.randommusicreminder.domain.model.ScheduleType.ONCE, localDate = date,
            localTime = java.time.LocalTime.NOON, holidayPolicy = HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS)
        val details = com.muyex.randommusicreminder.domain.model.EventDetails(event,
            listOf(com.muyex.randommusicreminder.domain.model.ReminderRule(1, 1, -1440), com.muyex.randommusicreminder.domain.model.ReminderRule(2, 1, 0)))
        val planner = com.muyex.randommusicreminder.domain.planner.OccurrencePlanner()
        val from = date.minusDays(1).atStartOfDay(zone).toInstant(); val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        assertEquals(2, planner.plan(listOf(details), from, to, zone, mapOf(date.minusDays(1) to CalendarDayType.REST)).size)
        assertTrue(planner.plan(listOf(details), from, to, zone, mapOf(date to CalendarDayType.REST)).isEmpty())
    }
}
