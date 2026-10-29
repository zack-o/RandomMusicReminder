package com.muyex.randommusicreminder.domain.calendar

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CalendarDayLabelTest {
    @Test fun namedHolidayAndMakeupHaveDifferentKinds() {
        assertEquals("国庆节", calendarDayLabel(LocalDate.parse("2026-10-03"), null).name)
        assertEquals(CalendarDayKind.MAKEUP, calendarDayLabel(LocalDate.parse("2026-10-10"), null).kind)
        assertNull(MainlandHolidayCalendar.holidayName(LocalDate.parse("2027-10-01")))
    }
    @Test fun customLayerWinsAndUnknownIsNotCertifiedWorkday() {
        val date = LocalDate.parse("2026-10-01")
        assertEquals("我有值班", calendarDayLabel(date, CalendarOverride(date, CalendarDayType.WORK, "我有值班")).name)
        assertEquals(CalendarDayKind.UNKNOWN, calendarDayLabel(LocalDate.parse("2030-01-01"), null).kind)
    }
}
