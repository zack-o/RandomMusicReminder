package com.muyex.randommusicreminder.domain.planner

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RandomWindowTest {
    @Test fun newWindowSkipsAlreadyStartedWindowByDefaultIncludingCrossMidnight() {
        val date=java.time.LocalDate.of(2030,10,28);val zone=java.time.ZoneId.of("Asia/Shanghai")
        val event=com.muyex.randommusicreminder.domain.model.ReminderEvent(title="窗口",scheduleType=com.muyex.randommusicreminder.domain.model.ScheduleType.DAILY,
            localDate=date,localTime=java.time.LocalTime.of(8,0),windowEndTime=java.time.LocalTime.of(8,10))
        fun at(day: java.time.LocalDate,h: Int,m: Int)=day.atTime(h,m).atZone(zone).toInstant()
        assertEquals(at(date,8,10).toEpochMilli(),RandomWindow.activationBoundary(event,at(date,8,5),zone))
        assertEquals(at(date,7,0).toEpochMilli(),RandomWindow.activationBoundary(event,at(date,7,0),zone))
        val cross=event.copy(localTime=java.time.LocalTime.of(23,55),windowEndTime=java.time.LocalTime.of(0,10),windowCrossesMidnight=true)
        assertEquals(at(date.plusDays(1),0,10).toEpochMilli(),RandomWindow.activationBoundary(cross,at(date.plusDays(1),0,5),zone))
    }
    private val date = LocalDate.of(2026, 10, 28)
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun range() = RandomWindow.candidates(date, LocalTime.of(8, 0), LocalTime.of(8, 10), false, zone)

    @Test fun endpointsAreIncludedAndSelectionIsStable() {
        val values = range()
        assertEquals(11, values.size)
        assertEquals(480, values.first().localMinute)
        assertEquals(490, values.last().localMinute)
        assertEquals(RandomWindow.choose(values, 2841, date), RandomWindow.choose(range(), 2841, date))
        assertTrue((0L..100L).all { RandomWindow.choose(values, it, date) in values })
    }

    @Test fun crossMidnightBelongsToStartDate() {
        val values = RandomWindow.candidates(date, LocalTime.of(23, 55), LocalTime.of(0, 10), true, zone)
        assertEquals(16, values.size)
        assertEquals(date.plusDays(1).atTime(0, 10).atZone(zone).toInstant(), values.last().instant)
    }

    @Test fun partialCreationOnlyHasStrictlyFutureMinutes() {
        val now = date.atTime(8, 5, 30).atZone(zone).toInstant()
        val values = range().filter { it.instant > now }
        assertEquals(5, values.size)
        assertEquals(486, values.first().localMinute)
    }

    @Test(expected = IllegalArgumentException::class) fun equalTimesAreNotAnImplicitWholeDay() {
        RandomWindow.candidates(date, LocalTime.of(8, 0), LocalTime.of(8, 0), false, zone)
    }

    @Test fun missingAndOverlappingDstMinutesAreHandledOnce() {
        val ny = ZoneId.of("America/New_York")
        val gap = RandomWindow.candidates(LocalDate.of(2026, 3, 8), LocalTime.of(1, 55), LocalTime.of(3, 5), false, ny)
        assertEquals(11, gap.size)
        assertFalse(gap.any { it.localMinute in 120..179 })
        val overlap = RandomWindow.candidates(LocalDate.of(2026, 11, 1), LocalTime.of(1, 0), LocalTime.of(1, 10), false, ny)
        assertEquals(11, overlap.size)
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), overlap.first().instant)
    }
}
