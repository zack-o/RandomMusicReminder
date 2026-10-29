package com.muyex.randommusicreminder.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate

enum class HolidayPolicy(val label: String) {
    NONE("不处理节假日"),
    SKIP_OFFICIAL_HOLIDAYS("跳过官方放假日"),
    MAINLAND_WORKDAYS("仅法定工作日"),
}

object MainlandHolidayCalendar {
    val coveredYears: Set<Int> = setOf(2026)
    const val SOURCE_URL = "https://www.beijing.gov.cn/zhengce/zhengcefagui/202511/t20251104_4258873.html"

    // 国办发明电〔2025〕7号，2026-10-20 核对；不按农历推算未来调休。
    private val holidays = buildSet {
        listOf("01-01" to "01-03", "02-15" to "02-23", "04-04" to "04-06", "05-01" to "05-05",
            "06-19" to "06-21", "09-25" to "09-27", "10-01" to "10-07").forEach { (first, last) ->
            var date = LocalDate.parse("2026-$first")
            val end = LocalDate.parse("2026-$last")
            while (date <= end) { add(date); date = date.plusDays(1) }
        }
    }
    private val makeupWorkdays = setOf("01-04", "02-14", "02-28", "05-09", "09-20", "10-10")
        .mapTo(hashSetOf()) { LocalDate.parse("2026-$it") }

    fun holidayName(date: LocalDate): String? {
        if (date !in holidays) return null
        return when (date.monthValue) { 1 -> "元旦"; 2 -> "春节"; 4 -> "清明节"; 5 -> "劳动节"; 6 -> "端午节"; 9 -> "中秋节"; 10 -> "国庆节"; else -> null }
    }
    fun isMakeupWorkday(date: LocalDate): Boolean = date in makeupWorkdays

    fun defaultDayType(date: LocalDate): CalendarDayType? {
        if (date.year !in coveredYears) return null
        return if (date in makeupWorkdays || (date !in holidays && date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
            CalendarDayType.WORK else CalendarDayType.REST
    }

    fun shouldInclude(date: LocalDate, policy: HolidayPolicy, matchesOriginal: Boolean, customDay: CalendarDayType? = null): Boolean {
        if (policy == HolidayPolicy.NONE) return matchesOriginal
        // 自定义日历是覆盖层，不改官方原始表，也不会把局部日期标记为全年已验证。
        if (customDay != null) return customDay == CalendarDayType.WORK &&
            (policy == HolidayPolicy.MAINLAND_WORKDAYS || matchesOriginal)
        // 缺少年份时明确降级为原重复规则，不能把未知年份当作“没有假期”。
        if (date.year !in coveredYears) return matchesOriginal
        return when (policy) {
            HolidayPolicy.NONE -> matchesOriginal
            HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS -> matchesOriginal && date !in holidays
            HolidayPolicy.MAINLAND_WORKDAYS -> date in makeupWorkdays ||
                (date !in holidays && date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        }
    }
}
