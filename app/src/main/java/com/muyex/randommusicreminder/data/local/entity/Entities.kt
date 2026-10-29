package com.muyex.randommusicreminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.MusicPoolMode
import com.muyex.randommusicreminder.domain.model.OccurrenceState
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.model.ScheduleMode
import com.muyex.randommusicreminder.domain.model.ScheduleType
import java.time.LocalDate
import java.time.LocalTime

data class EventSchedulingState(val id: Long, val resumedAfterEpochMillis: Long?,val trackCompletion:Boolean,val completionTrackingStartedAt:Long?,val dayOverridesJson:String="[]",val dayOverrideBarriersJson:String="{}")

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val enabled: Boolean = true,
    val scheduleType: ScheduleType,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val weekdaysMask: Int = 0,
    val musicPoolMode: MusicPoolMode = MusicPoolMode.GLOBAL,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "'NONE'")
    val holidayPolicy: com.muyex.randommusicreminder.domain.calendar.HolidayPolicy = com.muyex.randommusicreminder.domain.calendar.HolidayPolicy.NONE,
    @ColumnInfo(defaultValue = "1") val intervalDays: Int = 1,
    @ColumnInfo(defaultValue = "'SKIP'") val missingDayPolicy: com.muyex.randommusicreminder.domain.model.MissingDayPolicy = com.muyex.randommusicreminder.domain.model.MissingDayPolicy.SKIP,
    val endDate: LocalDate? = null,
    @ColumnInfo(defaultValue = "'FIXED'") val timingMode: com.muyex.randommusicreminder.domain.model.TimingMode = com.muyex.randommusicreminder.domain.model.TimingMode.FIXED,
    @ColumnInfo(defaultValue = "'08:10'") val windowEndTime: LocalTime = LocalTime.of(8, 10),
    @ColumnInfo(defaultValue = "0") val windowCrossesMidnight: Boolean = false,
    @ColumnInfo(defaultValue = "'GENTLE'") val windowAlertMode: AlertMode = AlertMode.GENTLE,
    @ColumnInfo(defaultValue = "30") val windowDurationSeconds: Int = 30,
    val windowSnoozeMinutes: Int? = null,
    @ColumnInfo(defaultValue = "0") val windowBackupEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val hideWindowSelection: Boolean = true,
    @ColumnInfo(defaultValue = "0") val randomSeed: Long = 0,
    @ColumnInfo(defaultValue = "0") val timingRevision: Long = 0,
    val windowFirstNotBeforeEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "''") val shiftCycleJson: String = "",
    @ColumnInfo(defaultValue = "'[]'") val tagsJson: String = "[]",
    val deletedAtEpochMillis: Long? = null,
    val resumedAfterEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "'MUSIC'") val outputMode: com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode = com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.MUSIC,
    @ColumnInfo(defaultValue = "'PULSE'") val hapticPreset: com.muyex.randommusicreminder.domain.haptics.HapticPreset = com.muyex.randommusicreminder.domain.haptics.HapticPreset.PULSE,
    @ColumnInfo(defaultValue = "'STANDARD'") val hapticStrength: com.muyex.randommusicreminder.domain.haptics.HapticStrength = com.muyex.randommusicreminder.domain.haptics.HapticStrength.STANDARD,
    @ColumnInfo(defaultValue = "1000") val hapticGapMillis: Int = 1000,
    @ColumnInfo(defaultValue = "0") val hapticUseMusicRhythm: Boolean = false,
    @ColumnInfo(defaultValue = "0") val trackCompletion: Boolean = false,
    val completionTrackingStartedAt: Long? = null,
    @ColumnInfo(defaultValue = "'[]'") val dayOverridesJson:String="[]",
    @ColumnInfo(defaultValue = "'{}'") val dayOverrideBarriersJson:String="{}",

)

@Entity(
    tableName = "reminder_rules",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("eventId"),
        Index(value = ["eventId", "offsetMinutes"], unique = true),
    ],
)
data class ReminderRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: Long,
    val offsetMinutes: Int,
    val alertMode: AlertMode = AlertMode.GENTLE,
    val ringDurationSeconds: Int = 60,
    val snoozeMinutes: Int? = null,
)

@Entity(
    tableName = "music_tracks",
    indices = [Index(value = ["uri"], unique = true)],
)
data class MusicTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val uri: String,
    val mimeType: String,
    val durationMillis: Long,
    val fileSizeBytes: Long = 0,
    val enabled: Boolean = true,
    val importedAtEpochMillis: Long,
    val invalidReason: String? = null,
)

@Entity(
    tableName = "event_track_cross_ref",
    primaryKeys = ["eventId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MusicTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("eventId"), Index("trackId")],
)
data class EventTrackCrossRef(
    val eventId: Long,
    val trackId: Long,
)

@Entity(
    tableName = "scheduled_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReminderRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderRuleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("eventId"),
        Index("reminderRuleId"),
        Index("triggerAtEpochMillis"),
        Index(value = ["state", "triggerAtEpochMillis"]),
        Index(value = ["eventId", "reminderRuleId", "baseDate"]),
    ],
)
data class ScheduledOccurrenceEntity(
    @PrimaryKey
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
    @ColumnInfo(defaultValue = "0") val windowBackup: Boolean = false,
    val sourceLabel: String? = null,
    val taskOccurrenceKey: String? = null,
    @ColumnInfo(defaultValue = "''") val dayOverrideSignature:String="",
)

@Entity(tableName = "shuffle_state")
data class ShuffleStateEntity(
    @PrimaryKey
    val scopeKey: String,
    val cycle: Long = 0,
    val remainingTrackIds: List<Long> = emptyList(),
    val lastTrackId: Long? = null,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "ring_sessions",
    indices = [Index("state"), Index("scheduledTriggerAtEpochMillis")],
)
data class RingSessionEntity(
    @PrimaryKey
    val id: String,
    val scheduledTriggerAtEpochMillis: Long,
    val startedAtEpochMillis: Long,
    val deadlineEpochMillis: Long,
    val selectedTrackId: Long? = null,
    val state: RingSessionState = RingSessionState.ACTIVE,
    val stopReason: RingStopReason? = null,
)

data class EventWithRelations(
    @Embedded
    val event: EventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "eventId",
    )
    val rules: List<ReminderRuleEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EventTrackCrossRef::class,
            parentColumn = "eventId",
            entityColumn = "trackId",
        ),
    )
    val customTracks: List<MusicTrackEntity>,
)

data class PlaybackOccurrenceRow(
    @ColumnInfo(name = "occurrenceId")
    val occurrenceId: String,
    val eventId: Long,
    val title: String,
    val alertMode: AlertMode,
    val ringDurationSeconds: Int,
    val snoozeMinutes: Int?,
    val triggerAtEpochMillis: Long,
    val musicPoolMode: MusicPoolMode,
    val outputMode: com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode,
    val hapticPreset: com.muyex.randommusicreminder.domain.haptics.HapticPreset,
    val hapticStrength: com.muyex.randommusicreminder.domain.haptics.HapticStrength,
    val hapticGapMillis: Int,
    val hapticUseMusicRhythm: Boolean,
)
