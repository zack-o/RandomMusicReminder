package com.muyex.randommusicreminder.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate

enum class CalendarDayKind { HOLIDAY, MAKEUP, CUSTOM_REST, CUSTOM_WORK, WEEKEND, WORKDAY, UNKNOWN }
data class CalendarDayLabel(val kind: CalendarDayKind, val badge: String, val name: String)

fun calendarDayLabel(date: LocalDate, custom: CalendarOverride?): CalendarDayLabel = when {
    custom != null -> CalendarDayLabel(if (custom.type == CalendarDayType.REST) CalendarDayKind.CUSTOM_REST else CalendarDayKind.CUSTOM_WORK,
        if (custom.type == CalendarDayType.REST) "自休" else "自班", custom.note.ifBlank { if (custom.type == CalendarDayType.REST) "自定义休息" else "自定义工作" })
    MainlandHolidayCalendar.holidayName(date) != null -> CalendarDayLabel(CalendarDayKind.HOLIDAY, "假", MainlandHolidayCalendar.holidayName(date).orEmpty())
    MainlandHolidayCalendar.isMakeupWorkday(date) -> CalendarDayLabel(CalendarDayKind.MAKEUP, "补班", "调休补班")
    date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> CalendarDayLabel(CalendarDayKind.WEEKEND, "周末", if (date.year in MainlandHolidayCalendar.coveredYears) "周末" else "周末（官方安排未知）")
    date.year !in MainlandHolidayCalendar.coveredYears -> CalendarDayLabel(CalendarDayKind.UNKNOWN, "—", "官方安排未知")
    else -> CalendarDayLabel(CalendarDayKind.WORKDAY, "", "工作日")
}
