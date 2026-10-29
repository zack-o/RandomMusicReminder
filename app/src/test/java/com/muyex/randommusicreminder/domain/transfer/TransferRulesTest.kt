package com.muyex.randommusicreminder.domain.transfer

import com.muyex.randommusicreminder.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TransferRulesTest {
    private val details = EventDetails(ReminderEvent(title = "会议", scheduleType = ScheduleType.DAILY,
        localDate = LocalDate.parse("2026-10-20"), localTime = LocalTime.NOON), listOf(ReminderRule(offsetMinutes = -30), ReminderRule(offsetMinutes = 0)))
    @Test fun validatesNormalData() { TransferRules.validateEvent(details) }
    @Test fun fingerprintIgnoresDatabaseIdentityEnabledAndRuleOrder() {
        val copy = details.copy(event = details.event.copy(id = 99, enabled = false), rules = details.rules.reversed().map { it.copy(id = 99, eventId = 99) })
        assertEquals(TransferRules.fingerprint(details), TransferRules.fingerprint(copy))
    }
    @Test fun changesInTimeAndCustomMusicAreNotDuplicates() {
        assertNotEquals(TransferRules.fingerprint(details), TransferRules.fingerprint(details.copy(event = details.event.copy(localTime = LocalTime.MIDNIGHT))))
        assertNotEquals(TransferRules.fingerprint(details), TransferRules.fingerprint(details.copy(customTrackIds = setOf(2))))
    }
    @Test fun fingerprintLengthPrefixPreventsSeparatorCollisions() {
        val a = details.copy(event = details.event.copy(title = "a:b", note = "c"))
        val b = details.copy(event = details.event.copy(title = "a", note = "b:c"))
        assertNotEquals(TransferRules.fingerprint(a), TransferRules.fingerprint(b))
    }
    @Test fun rejectsDuplicateAndUnsupportedReminderRules() {
        assertThrows(IllegalArgumentException::class.java) { TransferRules.validateEvent(details.copy(rules = listOf(details.rules[0], details.rules[0]))) }
        assertThrows(IllegalArgumentException::class.java) { TransferRules.validateEvent(details.copy(rules = listOf(ReminderRule(offsetMinutes = 5)))) }
        assertThrows(IllegalArgumentException::class.java) { TransferRules.validateEvent(details.copy(rules = listOf(ReminderRule(offsetMinutes = 0, snoozeMinutes = 2)))) }
    }
    @Test fun weeklyRequiresAtLeastOneDay() {
        assertThrows(IllegalArgumentException::class.java) { TransferRules.validateEvent(details.copy(event = details.event.copy(scheduleType = ScheduleType.WEEKLY))) }
    }
    @Test fun csvEscapesFormulaQuotesAndNewlines() {
        assertEquals("\"'=1+1\"", csvCell("=1+1"))
        assertEquals("\"'  @SUM(A1)\"", csvCell("  @SUM(A1)"))
        assertEquals("\"a\"\"b\nc\"", csvCell("a\"b\nc"))
        assertEquals("\"普通文字\"", csvCell("普通文字"))
    }
}
