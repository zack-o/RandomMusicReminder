package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.calendar.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class AdvancedRecurrenceTest {
    private val month = ReminderEvent(1, "月末", scheduleType = ScheduleType.MONTHLY, localDate = LocalDate.parse("2030-01-31"), localTime = LocalTime.NOON)
    @Test fun missingMonthDaySkipsWithoutDrift() {
        assertFalse(RecurrenceDates.matches(month, LocalDate.parse("2030-02-28")))
        assertTrue(RecurrenceDates.matches(month, LocalDate.parse("2030-03-31")))
    }
    @Test fun lastDayPolicyClampsEachMonthFromOriginalAnchor() {
        val event = month.copy(missingDayPolicy = MissingDayPolicy.LAST_DAY)
        assertTrue(RecurrenceDates.matches(event, LocalDate.parse("2030-02-28")))
        assertFalse(RecurrenceDates.matches(event, LocalDate.parse("2030-03-28")))
        assertTrue(RecurrenceDates.matches(event, LocalDate.parse("2030-03-31")))
    }
    @Test fun leapAnniversaryUsesExplicitMissingDayPolicy() {
        val event = month.copy(scheduleType = ScheduleType.YEARLY, localDate = LocalDate.parse("2028-02-29"))
        assertFalse(RecurrenceDates.matches(event, LocalDate.parse("2029-02-28")))
        assertTrue(RecurrenceDates.matches(event.copy(missingDayPolicy = MissingDayPolicy.LAST_DAY), LocalDate.parse("2029-02-28")))
        assertTrue(RecurrenceDates.matches(event, LocalDate.parse("2032-02-29")))
        assertFalse(RecurrenceDates.matches(event, LocalDate.parse("2032-03-29")))
    }
    @Test fun intervalsAreAnchoredAndEndIsInclusive() {
        val event = month.copy(scheduleType = ScheduleType.INTERVAL, localDate = LocalDate.parse("2030-01-01"), intervalDays = 3, endDate = LocalDate.parse("2030-01-07"))
        assertFalse(RecurrenceDates.matches(event, LocalDate.parse("2029-12-29")))
        assertTrue(RecurrenceDates.matches(event, LocalDate.parse("2030-01-04")))
        assertTrue(RecurrenceDates.matches(event, LocalDate.parse("2030-01-07")))
        assertFalse(RecurrenceDates.matches(event, LocalDate.parse("2030-01-10")))
    }
    @Test fun workdayPolicyCannotTurnMonthlyIntoEveryWorkday() {
        val event = month.copy(localDate = LocalDate.parse("2026-01-05"), holidayPolicy = HolidayPolicy.MAINLAND_WORKDAYS)
        val items = OccurrencePlanner().plan(listOf(EventDetails(event, listOf(ReminderRule(id = 1, offsetMinutes = 0)))), Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.parse("2026-01-05")), items.map { it.baseDate })
    }
    @Test fun earlierDayReminderCrossesMonthAndRespectsEndDate() {
        val event = month.copy(localDate = LocalDate.parse("2030-01-03"), endDate = LocalDate.parse("2030-02-03"))
        val items = OccurrencePlanner().plan(listOf(EventDetails(event, listOf(ReminderRule(id = 1, offsetMinutes = -3 * 1440)))), Instant.parse("2030-01-30T00:00:00Z"), Instant.parse("2030-03-05T00:00:00Z"), ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.parse("2030-02-03")), items.map { it.baseDate })
        assertEquals(Instant.parse("2030-01-31T12:00:00Z").toEpochMilli(), items.single().triggerAtEpochMillis)
    }
    @Test fun intervalAcrossDstRetainsWallClockTime() {
        val event = month.copy(scheduleType = ScheduleType.INTERVAL, localDate = LocalDate.parse("2030-03-09"), intervalDays = 1, localTime = LocalTime.of(9, 0))
        val zone = ZoneId.of("America/New_York")
        val items = OccurrencePlanner().plan(listOf(EventDetails(event, listOf(ReminderRule(id = 1, offsetMinutes = 0)))), Instant.parse("2030-03-09T00:00:00Z"), Instant.parse("2030-03-12T00:00:00Z"), zone)
        assertTrue(items.all { Instant.ofEpochMilli(it.triggerAtEpochMillis).atZone(zone).hour == 9 })
    }
}
