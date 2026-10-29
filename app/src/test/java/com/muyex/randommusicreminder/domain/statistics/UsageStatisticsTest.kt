package com.muyex.randommusicreminder.domain.statistics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UsageStatisticsTest {
    @Test fun splitsAtLocalMidnightAndPreservesElapsedTime() {
        val result = splitUsage(Instant.parse("2026-10-19T15:59:58Z"), 5_000, ZoneId.of("Asia/Shanghai"))
        assertEquals(mapOf(LocalDate.parse("2026-10-19") to 2_000L, LocalDate.parse("2026-10-20") to 3_000L), result)
        assertEquals(5_000L, result.values.sum())
    }
    @Test fun accountsForTwentyThreeHourDstDay() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant()
        val rows = splitUsage(start, 24 * 3_600_000L, zone)
        assertEquals(23 * 3_600_000L, rows[LocalDate.of(2026, 3, 8)])
        assertEquals(3_600_000L, rows[LocalDate.of(2026, 3, 9)])
    }
    @Test fun accountsForTwentyFiveHourDstDay() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant()
        assertEquals(mapOf(LocalDate.of(2026, 11, 1) to 25 * 3_600_000L), splitUsage(start, 25 * 3_600_000L, zone))
    }
    @Test fun zeroHasNoDurationAndNegativeIsRejected() {
        assertTrue(splitUsage(Instant.EPOCH, 0, ZoneId.of("UTC")).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { splitUsage(Instant.EPOCH, -1, ZoneId.of("UTC")) }
    }
    @Test fun dateRangeIncludesTodayAndSixPreviousDaysButNotTomorrow() {
        val today = LocalDate.parse("2026-10-20")
        val rows = (-8L..1L).map { DailyUsage(today.plusDays(it), 1, 100) }
        assertEquals(7, usageWithin(rows, today, 7).size)
        assertEquals(today, usageWithin(rows, today, 7).first().date)
        assertEquals(9, usageWithin(rows, today, null).size)
    }
}
