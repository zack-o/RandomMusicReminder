package com.muyex.randommusicreminder.domain.time

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ReminderClockTest {
    @Test fun preciseTicketReminderRetainsSeconds() {
        val value = LocalTime.of(9,59,55)
        assertEquals(value, ReminderClock.parse("09:59:55"))
        assertEquals("09:59:55", ReminderClock.format(value))
        assertEquals(value, ReminderClock.parse(ReminderClock.format(value)))
    }
    @Test fun rejectsAmbiguousOrOutOfRangeInput() {
        listOf("9:59", "24:00", "12:60", "12:59:60", "12:00:00.1", "12:00Z", "12:00 ").forEach { assertNull(ReminderClock.parse(it)) }
    }
    @Test fun minuteWindowDoesNotSilentlyTruncateSeconds() {
        assertNull(ReminderClock.parse("09:59:55", false))
        assertEquals(LocalTime.of(9,59),ReminderClock.parse("09:59",false))
        assertEquals("09:59",ReminderClock.format(LocalTime.of(9,59)))
    }
}
