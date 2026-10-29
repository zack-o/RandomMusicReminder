package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.EventDetails
import com.muyex.randommusicreminder.domain.model.MusicPoolMode
import com.muyex.randommusicreminder.domain.model.OccurrenceState
import com.muyex.randommusicreminder.domain.model.ReminderEvent
import com.muyex.randommusicreminder.domain.model.ReminderRule
import com.muyex.randommusicreminder.domain.model.ScheduleType
import com.muyex.randommusicreminder.domain.model.ScheduledOccurrence
import com.muyex.randommusicreminder.domain.model.WeekdayMask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class OccurrencePlannerTest {
    private val planner = OccurrencePlanner()
    private val utc = ZoneId.of("UTC")

    @Test
    fun onceEventCreatesOneOccurrencePerRule() {
        val details = event(
            type = ScheduleType.ONCE,
            date = LocalDate.of(2026, 8, 4),
            rules = listOf(rule(11, -60), rule(12, 0)),
        )

        val result = planner.plan(
            events = listOf(details),
            fromInclusive = Instant.parse("2026-08-04T00:00:00Z"),
            untilExclusive = Instant.parse("2026-08-05T00:00:00Z"),
            zoneId = utc,
        )

        assertEquals(2, result.size)
        assertEquals(
            listOf(
                Instant.parse("2026-08-04T08:00:00Z").toEpochMilli(),
                Instant.parse("2026-08-04T09:00:00Z").toEpochMilli(),
            ),
            result.map { it.triggerAtEpochMillis },
        )
    }

    @Test
    fun dailyEventHonorsStartDateAndWindow() {
        val details = event(
            type = ScheduleType.DAILY,
            date = LocalDate.of(2026, 8, 5),
            rules = listOf(rule(11, 0)),
        )

        val result = planner.plan(
            events = listOf(details),
            fromInclusive = Instant.parse("2026-08-04T00:00:00Z"),
            untilExclusive = Instant.parse("2026-08-07T00:00:00Z"),
            zoneId = utc,
        )

        assertEquals(2, result.size)
        assertEquals(LocalDate.of(2026, 8, 5), result.first().baseDate)
        assertEquals(LocalDate.of(2026, 8, 6), result.last().baseDate)
    }

    @Test
    fun weeklyEventUsesMondayBasedMask() {
        val details = event(
            type = ScheduleType.WEEKLY,
            date = LocalDate.of(2026, 8, 3),
            weekdaysMask = WeekdayMask.from(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            rules = listOf(rule(11, 0)),
        )

        val result = planner.plan(
            events = listOf(details),
            fromInclusive = Instant.parse("2026-08-03T00:00:00Z"),
            untilExclusive = Instant.parse("2026-08-10T00:00:00Z"),
            zoneId = utc,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 5)),
            result.map { it.baseDate },
        )
    }

    @Test
    fun earlyReminderCanCrossIntoPreviousDay() {
        val details = event(
            type = ScheduleType.ONCE,
            date = LocalDate.of(2026, 8, 5),
            localTime = LocalTime.of(0, 10),
            rules = listOf(rule(11, -30)),
        )

        val result = planner.plan(
            events = listOf(details),
            fromInclusive = Instant.parse("2026-08-04T23:00:00Z"),
            untilExclusive = Instant.parse("2026-08-05T01:00:00Z"),
            zoneId = utc,
        )

        assertEquals(Instant.parse("2026-08-04T23:40:00Z").toEpochMilli(), result.single().triggerAtEpochMillis)
        assertEquals(LocalDate.of(2026, 8, 5), result.single().baseDate)
    }

    @Test
    fun localTimeIsRecomputedForCurrentTimeZone() {
        val details = event(
            type = ScheduleType.ONCE,
            date = LocalDate.of(2026, 8, 5),
            rules = listOf(rule(11, 0)),
        )
        val from = Instant.parse("2026-08-04T00:00:00Z")
        val until = Instant.parse("2026-08-06T00:00:00Z")

        val utcTrigger = planner.plan(listOf(details), from, until, utc).single().triggerAtEpochMillis
        val shanghaiTrigger = planner.plan(
            listOf(details),
            from,
            until,
            ZoneId.of("Asia/Shanghai"),
        ).single().triggerAtEpochMillis

        assertEquals(8 * 60 * 60 * 1_000L, utcTrigger - shanghaiTrigger)
    }

    @Test
    fun daylightSavingGapKeepsRealAdvanceDuration() {
        val details = event(
            type = ScheduleType.ONCE,
            date = LocalDate.of(2026, 3, 8),
            localTime = LocalTime.of(3, 30),
            rules = listOf(rule(11, -60)),
        )

        val result = planner.plan(
            events = listOf(details),
            fromInclusive = Instant.parse("2026-03-08T00:00:00Z"),
            untilExclusive = Instant.parse("2026-03-09T00:00:00Z"),
            zoneId = ZoneId.of("America/New_York"),
        )

        assertEquals(Instant.parse("2026-03-08T06:30:00Z").toEpochMilli(), result.single().triggerAtEpochMillis)
    }

    @Test
    fun reconcileDoesNotDuplicateStableOccurrence() {
        val details = event(
            type = ScheduleType.ONCE,
            date = LocalDate.of(2026, 8, 5),
            rules = listOf(rule(11, 0)),
        )
        val desired = planner.plan(
            events = listOf(details),
            fromInclusive = Instant.parse("2026-08-05T00:00:00Z"),
            untilExclusive = Instant.parse("2026-08-06T00:00:00Z"),
            zoneId = utc,
        ).single()
        val existing = ScheduledOccurrence(
            id = desired.id,
            eventId = desired.eventId,
            reminderRuleId = desired.reminderRuleId,
            baseDate = desired.baseDate,
            triggerAtEpochMillis = desired.triggerAtEpochMillis,
            state = OccurrenceState.COMPLETED,
        )

        val plan = planner.reconcile(listOf(desired), listOf(existing))

        assertEquals(emptyList<Any>(), plan.upserts)
        assertEquals(emptyList<String>(), plan.cancellationIds)
    }

    private fun event(
        type: ScheduleType,
        date: LocalDate,
        localTime: LocalTime = LocalTime.of(9, 0),
        weekdaysMask: Int = 0,
        rules: List<ReminderRule>,
    ): EventDetails {
        return EventDetails(
            event = ReminderEvent(
                id = 1,
                title = "Test",
                scheduleType = type,
                localDate = date,
                localTime = localTime,
                weekdaysMask = weekdaysMask,
                musicPoolMode = MusicPoolMode.GLOBAL,
            ),
            rules = rules,
        )
    }

    private fun rule(id: Long, offset: Int): ReminderRule {
        return ReminderRule(
            id = id,
            eventId = 1,
            offsetMinutes = offset,
            alertMode = AlertMode.GENTLE,
        )
    }
}
