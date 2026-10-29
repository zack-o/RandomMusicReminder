package com.muyex.randommusicreminder.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ScheduleType {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    INTERVAL,
    SHIFT,
}

enum class MissingDayPolicy { SKIP, LAST_DAY }

enum class TimingMode { FIXED, RANDOM_WINDOW }

enum class MusicPoolMode {
    GLOBAL,
    CUSTOM,
}

enum class AlertMode {
    GENTLE,
    STRONG,
}

enum class OccurrenceState {
    SCHEDULED,
    RINGING,
    COMPLETED,
    MISSED,
    CANCELLED,
}

enum class OccurrenceKind {
    ORIGINAL,
    SNOOZE,
    TEST,
}

enum class ScheduleMode {
    NONE,
    EXACT,
    INEXACT,
}

enum class RingSessionState {
    ACTIVE,
    COMPLETED,
    FAILED,
}

enum class RingStopReason {
    USER,
    TIMEOUT,
    ERROR,
    SNOOZED,
    REPLACED,
    TASK_COMPLETED,
}

object WeekdayMask {
    const val NONE: Int = 0
    const val ALL: Int = 0b111_1111

    fun contains(mask: Int, day: DayOfWeek): Boolean {
        return mask and (1 shl (day.value - 1)) != 0
    }

    fun from(days: Set<DayOfWeek>): Int {
        return days.fold(NONE) { mask, day -> mask or (1 shl (day.value - 1)) }
    }
}

data class ReminderEvent(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val enabled: Boolean = true,
    val scheduleType: ScheduleType,
    /** ONCE 时是发生日期；DAILY/WEEKLY 时是重复开始日期。 */
    val localDate: LocalDate,
    val localTime: LocalTime,
    val weekdaysMask: Int = WeekdayMask.NONE,
    val musicPoolMode: MusicPoolMode = MusicPoolMode.GLOBAL,
    val createdAtEpochMillis: Long = 0,
    val updatedAtEpochMillis: Long = 0,
    val holidayPolicy: com.muyex.randommusicreminder.domain.calendar.HolidayPolicy = com.muyex.randommusicreminder.domain.calendar.HolidayPolicy.NONE,
    val intervalDays: Int = 1,
    val missingDayPolicy: MissingDayPolicy = MissingDayPolicy.SKIP,
    val endDate: LocalDate? = null,
    val timingMode: TimingMode = TimingMode.FIXED,
    val windowEndTime: LocalTime = LocalTime.of(8, 10),
    val windowCrossesMidnight: Boolean = false,
    val windowAlertMode: AlertMode = AlertMode.GENTLE,
    val windowDurationSeconds: Int = 30,
    val windowSnoozeMinutes: Int? = null,
    val windowBackupEnabled: Boolean = false,
    val hideWindowSelection: Boolean = true,
    /** Local-only scheduling identity; excluded from portable configuration backups. */
    val randomSeed: Long = 0,
    val timingRevision: Long = 0,
    val windowFirstNotBeforeEpochMillis: Long? = null,
    val shiftCycle: com.muyex.randommusicreminder.domain.planner.ShiftCycle? = null,
    val dayOverrides:List<com.muyex.randommusicreminder.domain.planner.DayOverride> = emptyList(),
    /** 本机日期操作下界，恢复原规则后仍保留，不进入普通备份。 */
    val dayOverrideBarriers:Map<LocalDate,Long> = emptyMap(),
    val tags: List<String> = emptyList(),
    val deletedAtEpochMillis: Long? = null,
    /** Local-only restore boundary; never exported as a portable schedule. */
    val resumedAfterEpochMillis: Long? = null,
    val outputMode: com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode = com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.MUSIC,
    val haptics: com.muyex.randommusicreminder.domain.haptics.HapticSettings = com.muyex.randommusicreminder.domain.haptics.HapticSettings(),
    val trackCompletion: Boolean = false,
    val completionTrackingStartedAt: Long? = null,
)

data class ReminderRule(
    val id: Long = 0,
    val eventId: Long = 0,
    /** 相对于事件时刻的分钟数；负数表示提前，正数表示延后。 */
    val offsetMinutes: Int,
    val alertMode: AlertMode = AlertMode.GENTLE,
    val ringDurationSeconds: Int = 60,
    /** null 表示不提供稍后提醒。 */
    val snoozeMinutes: Int? = null,
)

data class EventDetails(
    val event: ReminderEvent,
    val rules: List<ReminderRule>,
    val customTrackIds: Set<Long> = emptySet(),
) {
    /** Fixed-point configuration remains stored while a random window is active. */
    val activeRules: List<ReminderRule>
        get() = if (event.timingMode == TimingMode.FIXED) rules else rules.minByOrNull { it.id }?.let {
            listOf(it.copy(offsetMinutes = 0, alertMode = event.windowAlertMode,
                ringDurationSeconds = event.windowDurationSeconds, snoozeMinutes = event.windowSnoozeMinutes))
        }.orEmpty()
}

data class MusicTrack(
    val id: Long = 0,
    val displayName: String,
    val uri: String,
    val mimeType: String,
    val durationMillis: Long,
    val fileSizeBytes: Long = 0,
    val enabled: Boolean = true,
    val importedAtEpochMillis: Long = 0,
    val invalidReason: String? = null,
)

data class PlannedOccurrence(
    val id: String,
    val eventId: Long,
    val reminderRuleId: Long,
    val baseDate: LocalDate,
    val triggerAtEpochMillis: Long,
    val selectedLocalMinute: Int? = null,
    val windowStartAtEpochMillis: Long? = null,
    val windowEndAtEpochMillis: Long? = null,
    val windowBackup: Boolean = false,
    val sourceLabel: String? = null,
    val taskOccurrenceKey: String? = null,
    val dayOverrideSignature:String="",
    val taskLabel: String? = null,
    val taskAtEpochMillis: Long? = null,
)

data class OccurrencePlan(
    val upserts: List<PlannedOccurrence>,
    val cancellationIds: List<String>,
)

data class ScheduledOccurrence(
    val id: String,
    val eventId: Long,
    val reminderRuleId: Long,
    val baseDate: LocalDate,
    val triggerAtEpochMillis: Long,
    val kind: OccurrenceKind = OccurrenceKind.ORIGINAL,
    val originalOccurrenceId: String? = null,
    val snoozeCount: Int = 0,
    val state: OccurrenceState = OccurrenceState.SCHEDULED,
    val scheduleMode: ScheduleMode = ScheduleMode.NONE,
    val sessionId: String? = null,
    val scheduledAtEpochMillis: Long? = null,
    val firedAtEpochMillis: Long? = null,
    val selectedLocalMinute: Int? = null,
    val windowStartAtEpochMillis: Long? = null,
    val windowEndAtEpochMillis: Long? = null,
    val windowBackup: Boolean = false,
    val sourceLabel: String? = null,
    val taskOccurrenceKey: String? = null,
    val dayOverrideSignature:String="",
)

data class PlaybackOccurrence(
    val occurrenceId: String,
    val eventId: Long,
    val title: String,
    val alertMode: AlertMode,
    val ringDurationMillis: Long,
    val snoozeMinutes: Int?,
    val triggerAtEpochMillis: Long,
    val musicPoolMode: MusicPoolMode,
    val outputMode: com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode = com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.MUSIC,
    val haptics: com.muyex.randommusicreminder.domain.haptics.HapticSettings = com.muyex.randommusicreminder.domain.haptics.HapticSettings(),
)

data class ShuffleState(
    val scopeKey: String,
    val cycle: Long = 0,
    val remainingTrackIds: List<Long> = emptyList(),
    val lastTrackId: Long? = null,
    val updatedAtEpochMillis: Long = 0,
)

data class TrackSelection(
    val track: MusicTrack?,
    val nextState: ShuffleState,
)

data class RingSession(
    val id: String,
    val scheduledTriggerAtEpochMillis: Long,
    val startedAtEpochMillis: Long,
    val deadlineEpochMillis: Long,
    val selectedTrackId: Long?,
    val state: RingSessionState,
    val stopReason: RingStopReason? = null,
)

data class RingClaim(
    val occurrenceIds: List<String>,
    val scheduledTriggerAtEpochMillis: Long,
    val durationMillis: Long,
    val alertMode: AlertMode,
)

data class ClaimedRingSession(
    val claim: RingClaim,
    val session: RingSession,
    val occurrences: List<PlaybackOccurrence>,
)
