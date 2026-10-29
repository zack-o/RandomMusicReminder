package com.muyex.randommusicreminder.domain.planner

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ShiftTime(val time: LocalTime, val nextDay: Boolean = false, val key: String = UUID.randomUUID().toString())
data class ShiftDay(val label: String, val rest: Boolean = false, val enabled: Boolean = true, val times: List<ShiftTime>)

data class ShiftCycle(val days: List<ShiftDay>) {
    fun validate() {
        require(days.size in 1..60) { "轮班周期须为1～60天" }
        require(days.any { it.enabled && it.times.isNotEmpty() }) { "至少有一天启用提醒并设置时刻" }
        val keys = mutableSetOf<String>()
        days.forEach { day ->
            require(day.label.isNotBlank() && day.label.length <= 30) { "班次名称须为1～30字" }
            require(day.times.size <= 8 && (!day.enabled || day.times.isNotEmpty())) { "启用日须设置1～8个时刻" }
            require(day.times.map { it.time to it.nextDay }.distinct().size == day.times.size) { "同一轮班日不能重复设置相同时刻" }
            day.times.forEach { item ->
                require(item.time.nano == 0 && Regex("[A-Za-z0-9-]{1,64}").matches(item.key) && keys.add(item.key)) { "轮班时刻或稳定标识无效" }
            }
        }
    }

    fun dayIndex(anchor: LocalDate, date: LocalDate): Int? {
        val difference = ChronoUnit.DAYS.between(anchor, date)
        return if (difference < 0 || days.isEmpty()) null else (difference % days.size).toInt()
    }
    fun dayOn(anchor: LocalDate, date: LocalDate): ShiftDay? = dayIndex(anchor,date)?.let { days[it] }

    /** Portable identity compares behavior, without the per-slot persistence keys. */
    fun fingerprint(): String = days.joinToString("|") { day ->
        "${day.label.length}:${day.label}:${day.rest}:${day.enabled}:" + day.times.sortedWith(compareBy<ShiftTime> { it.nextDay }.thenBy { it.time }).joinToString(",") { "${it.time}:${it.nextDay}" }
    }

    companion object {
        fun template(pattern: String = "白白夜夜休休"): ShiftCycle {
            require(pattern.length in 1..60 && pattern.all { it in "白夜休" })
            return ShiftCycle(pattern.map { value -> when(value) {
                '白' -> ShiftDay("白班", times=listOf(ShiftTime(LocalTime.of(8,30))))
                '夜' -> ShiftDay("夜班", times=listOf(ShiftTime(LocalTime.of(19,30))))
                else -> ShiftDay("休息",rest=true,enabled=false,times=emptyList())
            } })
        }
    }
}
