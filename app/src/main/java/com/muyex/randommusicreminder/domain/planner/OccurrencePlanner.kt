package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.EventDetails
import com.muyex.randommusicreminder.domain.model.OccurrencePlan
import com.muyex.randommusicreminder.domain.model.OccurrenceState
import com.muyex.randommusicreminder.domain.model.PlannedOccurrence
import com.muyex.randommusicreminder.domain.model.ScheduleType
import com.muyex.randommusicreminder.domain.model.ScheduledOccurrence
import com.muyex.randommusicreminder.domain.model.WeekdayMask
import com.muyex.randommusicreminder.domain.model.TimingMode
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import com.muyex.randommusicreminder.domain.tasks.*
import com.muyex.randommusicreminder.domain.time.ReminderClock
import kotlin.math.absoluteValue
import com.muyex.randommusicreminder.domain.calendar.MainlandHolidayCalendar
import com.muyex.randommusicreminder.domain.calendar.CalendarDayType

class OccurrencePlanner(
    private val selectWindow:(List<RandomWindow.Candidate>,Long,LocalDate)->RandomWindow.Candidate?=RandomWindow::choose,
) {
    fun plan(
        events: List<EventDetails>,
        fromInclusive: Instant,
        untilExclusive: Instant,
        zoneId: ZoneId,
        overrides: Map<LocalDate, CalendarDayType> = emptyMap(),
        existing: List<ScheduledOccurrence> = emptyList(),
        randomFromInclusive: Instant = fromInclusive,
        taskStates: Map<TaskIdentity,TaskState> = emptyMap(),
    ): List<PlannedOccurrence> {
        require(untilExclusive > fromInclusive) { "Planning window must not be empty" }

        val existingByEvent = existing.groupBy { it.eventId }
        val eventsById=events.associateBy { it.event.id }
        return events
            .asSequence()
            .filter { it.event.enabled && it.event.deletedAtEpochMillis == null }
            .flatMap { details ->
                val boundary = details.event.resumedAfterEpochMillis?.let(Instant::ofEpochMilli)
                val effectiveFrom = if(boundary == null) fromInclusive else maxOf(fromInclusive,boundary)
                if(effectiveFrom >= untilExclusive) emptySequence() else planEvent(details, effectiveFrom, untilExclusive, zoneId, overrides,
                    existingByEvent[details.event.id].orEmpty(),maxOf(randomFromInclusive,effectiveFrom))
            }
            .map { item -> item.copy(dayOverrideSignature=DayOverrides.signature(checkNotNull(eventsById[item.eventId]).event.dayOverrides,item.baseDate)) }
            .filter { item ->
                val event=checkNotNull(eventsById[item.eventId]).event
                DayOverrides.permits(event.dayOverrides,event.dayOverrideBarriers,item.baseDate,item.triggerAtEpochMillis,item.windowStartAtEpochMillis,item.dayOverrideSignature) &&
                    item.taskOccurrenceKey?.let { taskStates[TaskIdentity(item.eventId,it)]?.permits(item.triggerAtEpochMillis,item.windowStartAtEpochMillis) } != false
            }
            .sortedWith(compareBy(PlannedOccurrence::triggerAtEpochMillis, PlannedOccurrence::id))
            .toList()
    }

    fun reconcile(
        desired: List<PlannedOccurrence>,
        existing: List<ScheduledOccurrence>,
    ): OccurrencePlan {
        val desiredById = desired.associateBy(PlannedOccurrence::id)
        val existingById = existing.associateBy(ScheduledOccurrence::id)

        val upserts = desired.filter { planned ->
            val current = existingById[planned.id]
            current == null || current.state == OccurrenceState.CANCELLED ||
                (current.state == OccurrenceState.SCHEDULED && (current.triggerAtEpochMillis != planned.triggerAtEpochMillis || current.sourceLabel != planned.sourceLabel || current.taskOccurrenceKey != planned.taskOccurrenceKey || current.dayOverrideSignature!=planned.dayOverrideSignature))
        }
        val cancellationIds = existing
            .asSequence()
            .filter { it.state == OccurrenceState.SCHEDULED }
            .filter { it.id !in desiredById }
            .map(ScheduledOccurrence::id)
            .toList()

        return OccurrencePlan(upserts = upserts, cancellationIds = cancellationIds)
    }

    private fun planEvent(
        details: EventDetails,
        fromInclusive: Instant,
        untilExclusive: Instant,
        zoneId: ZoneId,
        overrides: Map<LocalDate, CalendarDayType>,
        existing: List<ScheduledOccurrence>,
        randomFromInclusive: Instant,
    ): Sequence<PlannedOccurrence> {
        val event = details.event
        if (event.id <= 0 || details.rules.isEmpty()) return emptySequence()

        val maximumOffsetDays = if (event.timingMode == TimingMode.RANDOM_WINDOW) 1L else details.rules.maxOf { rule ->
            (rule.offsetMinutes.toLong().absoluteValue + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY
        } + if (event.scheduleType == ScheduleType.SHIFT) 1L else 0L
        val firstWindowDate = fromInclusive.atZone(zoneId).toLocalDate().minusDays(maximumOffsetDays)
        val lastWindowDate = untilExclusive
            .minusNanos(1)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(maximumOffsetDays)

        return baseDates(details, firstWindowDate, lastWindowDate, overrides).flatMap { baseDate ->
            if (event.scheduleType == ScheduleType.SHIFT) {
                return@flatMap planShift(details,baseDate,fromInclusive,untilExclusive,zoneId)
            }
            if (event.timingMode == TimingMode.RANDOM_WINDOW) {
                return@flatMap planWindow(details, baseDate, randomFromInclusive, untilExclusive, zoneId, existing)
            }
            details.rules.asSequence()
                .filter { it.id > 0 }
                .mapNotNull { rule ->
                    // 先解析事件的真实时区时刻，再按持续分钟回退，避免 DST 跳时把“提前 60 分钟”压成零分钟。
                    val trigger = LocalDateTime.of(baseDate, event.localTime)
                        .atZone(zoneId)
                        .plusMinutes(rule.offsetMinutes.toLong())
                        .toInstant()
                    if (trigger < fromInclusive || trigger >= untilExclusive) {
                        null
                    } else {
                        PlannedOccurrence(
                            id = stableOccurrenceId(event.id, rule.id, baseDate),
                            eventId = event.id,
                            reminderRuleId = rule.id,
                            baseDate = baseDate,
                            triggerAtEpochMillis = trigger.toEpochMilli(),
                            taskOccurrenceKey=TaskIdentity.day(baseDate),taskLabel="$baseDate ${ReminderClock.format(event.localTime)}",
                            taskAtEpochMillis=trigger.minusSeconds(rule.offsetMinutes*60L).toEpochMilli(),
                        )
                    }
                }
        }
    }

    private fun planShift(details: EventDetails, date: LocalDate, from: Instant, until: Instant, zone: ZoneId): Sequence<PlannedOccurrence> {
        val event=details.event
        val cycle=event.shiftCycle ?: return emptySequence()
        val index=cycle.dayIndex(event.localDate,date) ?: return emptySequence()
        val day=DayOverrides.effectiveShiftDay(event,date) ?: return emptySequence()
        if (!day.enabled) return emptySequence()
        return day.times.asSequence().flatMap { slot -> details.rules.asSequence().filter { it.id>0 }.mapNotNull { rule ->
            val occurrence=LocalDateTime.of(if(slot.nextDay) date.plusDays(1) else date,slot.time).atZone(zone)
                .plusMinutes(rule.offsetMinutes.toLong()).toInstant()
            if(occurrence<from || occurrence>=until) null else PlannedOccurrence(
                id=TaskIdentity.shiftOccurrenceId(event.id,rule.id,date,slot.key),
                taskOccurrenceKey=TaskIdentity.shift(date,slot.key),taskLabel="$date · ${day.label} · ${if(slot.nextDay) "次日 " else ""}${ReminderClock.format(slot.time)}",
                taskAtEpochMillis=occurrence.minusSeconds(rule.offsetMinutes*60L).toEpochMilli(),
                eventId=event.id,reminderRuleId=rule.id,baseDate=date,triggerAtEpochMillis=occurrence.toEpochMilli(),
                sourceLabel=(if(DayOverrides.at(event,date)?.kind==DayOverrideKind.REPLACE_SHIFT) "单日替换 · " else "")+"轮班第${index+1}/${cycle.days.size}天 · ${day.label} · ${if(slot.nextDay) "次日 " else ""}${com.muyex.randommusicreminder.domain.time.ReminderClock.format(slot.time)}")
        } }
    }

    private fun planWindow(details: EventDetails, date: LocalDate, from: Instant, until: Instant,
        zone: ZoneId, existing: List<ScheduledOccurrence>): Sequence<PlannedOccurrence> {
        val event = details.event
        val rule = details.activeRules.firstOrNull()?.takeIf { it.id > 0 } ?: return emptySequence()
        val id = windowId(event.id, rule.id, date, event.timingRevision, false)
        val previous = existing.filter { it.eventId == event.id && it.baseDate == date &&
            it.kind == OccurrenceKind.ORIGINAL && it.selectedLocalMinute != null }
        val terminal = setOf(OccurrenceState.COMPLETED, OccurrenceState.MISSED, OccurrenceState.RINGING)
        // An edited rule must not replay a day which has already been handled.
        if (previous.any { !it.windowBackup && it.id != id && it.state in terminal }) return emptySequence()
        val all = RandomWindow.candidates(date, event.localTime, event.windowEndTime, event.windowCrossesMidnight, zone)
        if (all.isEmpty()) return emptySequence()
        if(event.resumedAfterEpochMillis?.let { all.first().instant.toEpochMilli() < it } == true) return emptySequence()
        val current = previous.firstOrNull { it.id == id }
        val chosen = if (current?.selectedLocalMinute != null) {
            all.firstOrNull { it.localMinute == current.selectedLocalMinute }
        } else {
            val notBefore = event.windowFirstNotBeforeEpochMillis ?: event.createdAtEpochMillis
            selectWindow(all.filter { it.instant.toEpochMilli() > notBefore }, event.randomSeed, date)
        } ?: return emptySequence()
        return sequenceOf(false, true).filter { !it || event.windowBackupEnabled }.mapNotNull { backup ->
            val key = windowId(event.id, rule.id, date, event.timingRevision, backup)
            val stored = previous.firstOrNull { it.id == key }
            if (stored?.state in terminal) return@mapNotNull null
            if (backup && chosen.instant == all.last().instant) return@mapNotNull null
            val trigger = if (backup) all.last().instant else chosen.instant
            if (trigger < from || trigger >= until) return@mapNotNull null
            PlannedOccurrence(key, event.id, rule.id, date, trigger.toEpochMilli(), chosen.localMinute,
                all.first().instant.toEpochMilli(), all.last().instant.toEpochMilli(), backup,
                taskOccurrenceKey=TaskIdentity.day(date),taskLabel="$date ${event.localTime}～${if(event.windowCrossesMidnight) "次日 " else ""}${event.windowEndTime}",taskAtEpochMillis=all.first().instant.toEpochMilli())
        }
    }

    private fun windowId(eventId: Long, ruleId: Long, date: LocalDate, revision: Long, backup: Boolean): String =
        UUID.nameUUIDFromBytes("$eventId:$ruleId:$date:window:$revision:$backup".toByteArray(StandardCharsets.UTF_8)).toString()

    private fun baseDates(
        details: EventDetails,
        firstWindowDate: LocalDate,
        lastWindowDate: LocalDate,
        overrides: Map<LocalDate, CalendarDayType>,
    ): Sequence<LocalDate> {
        val event = details.event
        val candidates = if (event.scheduleType == ScheduleType.ONCE) sequenceOf(event.localDate).filter { it in firstWindowDate..lastWindowDate }
            else datesBetween(maxOf(event.localDate, firstWindowDate), minOf(lastWindowDate, event.endDate ?: lastWindowDate))
        return candidates.filter { date -> ScheduleDateRules.evaluate(event,date,overrides[date]).included }
    }

    private fun datesBetween(start: LocalDate, endInclusive: LocalDate): Sequence<LocalDate> {
        if (start > endInclusive) return emptySequence()
        val count = ChronoUnit.DAYS.between(start, endInclusive) + 1
        return generateSequence(start) { it.plusDays(1) }.take(count.toInt())
    }

    private fun stableOccurrenceId(eventId: Long, ruleId: Long, baseDate: LocalDate): String {
        val key = "$eventId:$ruleId:$baseDate"
        return UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private companion object {
        const val MINUTES_PER_DAY = 24L * 60L
    }
}
