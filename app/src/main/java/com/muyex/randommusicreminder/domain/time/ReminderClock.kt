package com.muyex.randommusicreminder.domain.time

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ReminderClock {
    private val minute = DateTimeFormatter.ofPattern("HH:mm")
    private val second = DateTimeFormatter.ofPattern("HH:mm:ss")
    fun parse(value: String, allowSeconds: Boolean = true): LocalTime? {
        val pattern = if (allowSeconds) "^([01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?$" else "^([01]\\d|2[0-3]):[0-5]\\d$"
        if (!Regex(pattern).matches(value)) return null
        return runCatching { LocalTime.parse(value) }.getOrNull()
    }
    fun format(time: LocalTime): String = time.format(if (time.second != 0) second else minute)
}
