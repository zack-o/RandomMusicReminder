package com.muyex.randommusicreminder.domain.statistics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyUsage(val date: LocalDate, val launches: Int, val foregroundMillis: Long)

fun splitUsage(start: Instant, elapsedMillis: Long, zone: ZoneId): Map<LocalDate, Long> {
    require(elapsedMillis >= 0) { "使用时长不能为负数" }
    val result = linkedMapOf<LocalDate, Long>()
    var cursor = start
    var remaining = elapsedMillis
    while (remaining > 0) {
        val date = cursor.atZone(zone).toLocalDate()
        val nextDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        val part = minOf(remaining, java.time.Duration.between(cursor, nextDay).toMillis())
        result[date] = (result[date] ?: 0) + part
        cursor = cursor.plusMillis(part)
        remaining -= part
    }
    return result
}

fun usageWithin(rows: List<DailyUsage>, today: LocalDate, days: Int?): List<DailyUsage> {
    require(days == null || days > 0)
    val first = days?.let { today.minusDays(it.toLong() - 1) }
    return rows.filter { it.date <= today && (first == null || it.date >= first) }.sortedByDescending { it.date }
}
