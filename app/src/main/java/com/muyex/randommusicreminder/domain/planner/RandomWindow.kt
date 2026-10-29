package com.muyex.randommusicreminder.domain.planner

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random
import java.util.UUID
import com.muyex.randommusicreminder.domain.model.ReminderEvent

object RandomWindow {
    data class Candidate(val localMinute: Int, val instant: Instant)

    fun scheduleIdentity(event: ReminderEvent): List<Any?> = listOf(event.timingMode, event.localDate,
        event.localTime, event.windowEndTime, event.windowCrossesMidnight, event.scheduleType,
        event.weekdaysMask, event.intervalDays, event.missingDayPolicy, event.endDate, event.holidayPolicy, event.shiftCycle?.fingerprint())

    fun activationBoundary(event: ReminderEvent, now: Instant, zone: ZoneId): Long {
        val today = now.atZone(zone).toLocalDate()
        val active = sequenceOf(today.minusDays(1), today).map { candidates(it, event.localTime,
            event.windowEndTime, event.windowCrossesMidnight, zone) }.firstOrNull {
            it.isNotEmpty() && now >= it.first().instant && now <= it.last().instant
        }
        return (active?.last()?.instant ?: now).toEpochMilli()
    }

    fun endMinute(start: LocalTime, end: LocalTime, crossesMidnight: Boolean): Int {
        require(start.second == 0 && start.nano == 0 && end.second == 0 && end.nano == 0) { "随机窗口使用整分钟" }
        val first = start.hour * 60 + start.minute
        val last = end.hour * 60 + end.minute + if (crossesMidnight) 1440 else 0
        require(last - first in 1..1439) { "结束须晚于开始，窗口须为1分钟至不足24小时；跨日请明确选择次日" }
        return last
    }

    fun candidates(date: LocalDate, start: LocalTime, end: LocalTime, crossesMidnight: Boolean, zone: ZoneId): List<Candidate> {
        val last = endMinute(start, end, crossesMidnight)
        return (start.hour * 60 + start.minute..last).mapNotNull { minute ->
            instantForMinute(date, minute, zone)?.let { Candidate(minute, it) }
        }
    }

    fun instantForMinute(date: LocalDate, minute: Int, zone: ZoneId): Instant? {
        require(minute in 0..2879)
        val local = date.atStartOfDay().plusMinutes(minute.toLong())
        // Missing DST wall minutes do not exist; overlapping minutes use the first occurrence once.
        val offset = zone.rules.getValidOffsets(local).firstOrNull() ?: return null
        return local.toInstant(offset)
    }

    fun choose(candidates: List<Candidate>, seed: Long, date: LocalDate): Candidate? {
        if (candidates.isEmpty()) return null
        val identity = UUID.nameUUIDFromBytes("$seed:$date".toByteArray(StandardCharsets.UTF_8))
        return candidates[Random(identity.mostSignificantBits xor identity.leastSignificantBits).nextInt(candidates.size)]
    }
}
