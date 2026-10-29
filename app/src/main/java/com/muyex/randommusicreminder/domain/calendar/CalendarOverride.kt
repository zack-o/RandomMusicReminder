package com.muyex.randommusicreminder.domain.calendar

import java.time.LocalDate

enum class CalendarDayType { REST, WORK }

data class CalendarOverride(
    val date: LocalDate,
    val type: CalendarDayType,
    val note: String = "",
    val source: String = "手动设置",
    val updatedAt: Long = 0,
) {
    init {
        require(date.year in 1900..2200) { "日期须在 1900～2200 年" }
        require(note.length <= 500 && source.length <= 200) { "日历备注或来源过长" }
    }
}
