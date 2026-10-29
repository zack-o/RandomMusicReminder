package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object RecurrenceDates {
    fun matches(event: ReminderEvent, date: LocalDate): Boolean {
        if (date < event.localDate || (event.endDate != null && date > event.endDate)) return false
        return when (event.scheduleType) {
            ScheduleType.ONCE -> date == event.localDate
            ScheduleType.DAILY -> true
            ScheduleType.WEEKLY -> WeekdayMask.contains(event.weekdaysMask, date.dayOfWeek)
            ScheduleType.INTERVAL -> event.intervalDays in 1..3650 && ChronoUnit.DAYS.between(event.localDate, date) % event.intervalDays == 0L
            ScheduleType.MONTHLY -> matchesDay(event, date)
            ScheduleType.YEARLY -> date.month == event.localDate.month && matchesDay(event, date)
            ScheduleType.SHIFT -> DayOverrides.effectiveShiftDay(event,date)?.let { it.enabled && it.times.isNotEmpty() } ?: false
        }
    }

    private fun matchesDay(event: ReminderEvent, date: LocalDate): Boolean {
        val last = YearMonth.from(date).lengthOfMonth()
        val requested = event.localDate.dayOfMonth
        // 每次从原锚点取日号；1月31日截到2月末后，3月仍是31日，不漂移成28日。
        val day = if (requested <= last) requested else if (event.missingDayPolicy == MissingDayPolicy.LAST_DAY) last else return false
        return date.dayOfMonth == day
    }
}
