package com.muyex.randommusicreminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.muyex.randommusicreminder.data.local.entity.EventEntity
import com.muyex.randommusicreminder.data.local.entity.EventSchedulingState
import com.muyex.randommusicreminder.data.local.entity.EventTrackCrossRef
import com.muyex.randommusicreminder.data.local.entity.EventWithRelations
import com.muyex.randommusicreminder.data.local.entity.MusicTrackEntity
import com.muyex.randommusicreminder.data.local.entity.PlaybackOccurrenceRow
import com.muyex.randommusicreminder.data.local.entity.ReminderRuleEntity
import com.muyex.randommusicreminder.data.local.entity.RingSessionEntity
import com.muyex.randommusicreminder.data.local.entity.ScheduledOccurrenceEntity
import com.muyex.randommusicreminder.data.local.entity.ShuffleStateEntity
import com.muyex.randommusicreminder.domain.model.OccurrenceState
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.model.ScheduleMode
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Transaction
    @Query("SELECT * FROM events WHERE deletedAtEpochMillis IS NULL ORDER BY localDate, localTime, id")
    fun observeAllWithRelations(): Flow<List<EventWithRelations>>

    @Transaction
    @Query("SELECT * FROM events WHERE deletedAtEpochMillis IS NULL ORDER BY localDate, localTime, id")
    suspend fun getAllWithRelations(): List<EventWithRelations>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId AND deletedAtEpochMillis IS NULL")
    suspend fun getWithRelations(eventId: Long): EventWithRelations?

    @Transaction
    @Query("SELECT * FROM events ORDER BY id")
    suspend fun getAllIncludingDeleted(): List<EventWithRelations>

    @Transaction
    @Query("SELECT * FROM events WHERE deletedAtEpochMillis IS NOT NULL ORDER BY deletedAtEpochMillis DESC, id DESC")
    fun observeDeleted(): Flow<List<EventWithRelations>>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getAnyWithRelations(eventId: Long): EventWithRelations?

    @Query("SELECT id,resumedAfterEpochMillis,trackCompletion,completionTrackingStartedAt,dayOverridesJson,dayOverrideBarriersJson FROM events WHERE enabled = 1 AND deletedAtEpochMillis IS NULL")
    suspend fun getEnabledSchedulingStates(): List<EventSchedulingState>

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("UPDATE events SET enabled = :enabled, updatedAtEpochMillis = :updatedAt WHERE id = :eventId AND deletedAtEpochMillis IS NULL")
    suspend fun setEnabled(eventId: Long, enabled: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM events WHERE id = :eventId AND deletedAtEpochMillis IS NOT NULL")
    suspend fun deleteEvent(eventId: Long): Int

    @Insert
    suspend fun insertRule(rule: ReminderRuleEntity): Long

    @Update
    suspend fun updateRule(rule: ReminderRuleEntity)

    @Query("SELECT id FROM reminder_rules WHERE eventId = :eventId")
    suspend fun getRuleIds(eventId: Long): List<Long>

    @Query("DELETE FROM reminder_rules WHERE eventId = :eventId AND id IN (:ruleIds)")
    suspend fun deleteRules(eventId: Long, ruleIds: List<Long>)

    @Query("DELETE FROM reminder_rules WHERE eventId = :eventId")
    suspend fun deleteAllRules(eventId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackRefs(refs: List<EventTrackCrossRef>)

    @Query("DELETE FROM event_track_cross_ref WHERE eventId = :eventId")
    suspend fun deleteTrackRefs(eventId: Long)
}

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_tracks ORDER BY importedAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<MusicTrackEntity>>

    @Query("SELECT * FROM music_tracks ORDER BY importedAtEpochMillis DESC, id DESC")
    suspend fun getAll(): List<MusicTrackEntity>

    @Query("SELECT * FROM music_tracks WHERE id = :trackId")
    suspend fun getById(trackId: Long): MusicTrackEntity?

    @Query("SELECT * FROM music_tracks WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): MusicTrackEntity?

    @Insert
    suspend fun insert(track: MusicTrackEntity): Long

    @Update
    suspend fun update(track: MusicTrackEntity)

    @Query("UPDATE music_tracks SET enabled = :enabled WHERE id = :trackId")
    suspend fun setEnabled(trackId: Long, enabled: Boolean): Int

    @Query("UPDATE music_tracks SET enabled = :enabled WHERE id IN (:trackIds) AND (:enabled = 0 OR invalidReason IS NULL)")
    suspend fun setEnabledBulk(trackIds: List<Long>, enabled: Boolean): Int

    @Query("UPDATE music_tracks SET invalidReason = :reason WHERE id = :trackId")
    suspend fun setInvalidReason(trackId: Long, reason: String?): Int

    @Query("DELETE FROM music_tracks WHERE id = :trackId")
    suspend fun delete(trackId: Long): Int

    @Query("SELECT * FROM music_tracks WHERE enabled = 1 AND invalidReason IS NULL ORDER BY id")
    suspend fun getEnabledGlobal(): List<MusicTrackEntity>

    @Query(
        """
        SELECT t.* FROM music_tracks AS t
        INNER JOIN event_track_cross_ref AS r ON r.trackId = t.id
        WHERE r.eventId = :eventId AND t.enabled = 1 AND t.invalidReason IS NULL
        ORDER BY t.id
        """,
    )
    suspend fun getEnabledForEvent(eventId: Long): List<MusicTrackEntity>

    @Query("SELECT * FROM shuffle_state WHERE scopeKey = :scopeKey")
    suspend fun getShuffleState(scopeKey: String): ShuffleStateEntity?

    @Upsert
    suspend fun upsertShuffleState(state: ShuffleStateEntity)
}

@Dao
interface OccurrenceDao {
    @Query("UPDATE scheduled_occurrences SET taskOccurrenceKey=:key WHERE id=:id AND taskOccurrenceKey IS NULL")
    suspend fun setTaskKeyIfMissing(id:String,key:String):Int
    @Query("SELECT * FROM scheduled_occurrences WHERE state = 'SCHEDULED' AND kind = 'ORIGINAL' AND selectedLocalMinute IS NOT NULL AND triggerAtEpochMillis = :triggerAt AND triggerAtEpochMillis < :cutoff")
    suspend fun getLateWindowsAt(triggerAt: Long, cutoff: Long): List<ScheduledOccurrenceEntity>
    // 随机窗口的已处理记录必须跨固定提醒点删除保留，避免级联删除后同一天再次响铃。
    @Query("UPDATE scheduled_occurrences SET reminderRuleId = :replacementId WHERE eventId = :eventId AND reminderRuleId IN (:removedIds) AND selectedLocalMinute IS NOT NULL")
    suspend fun retainWindowRecords(eventId: Long, removedIds: List<Long>, replacementId: Long)
    @Upsert
    suspend fun upsertAll(occurrences: List<ScheduledOccurrenceEntity>)

    @Query("SELECT * FROM scheduled_occurrences WHERE id = :occurrenceId")
    suspend fun getById(occurrenceId: String): ScheduledOccurrenceEntity?

    @Query("SELECT * FROM scheduled_occurrences WHERE id IN (:occurrenceIds)")
    suspend fun getByIds(occurrenceIds: List<String>): List<ScheduledOccurrenceEntity>

    @Query("SELECT * FROM scheduled_occurrences ORDER BY triggerAtEpochMillis, id")
    suspend fun getAll(): List<ScheduledOccurrenceEntity>

    @Query("SELECT * FROM scheduled_occurrences WHERE state = 'SCHEDULED' ORDER BY triggerAtEpochMillis, id")
    suspend fun getAllScheduled(): List<ScheduledOccurrenceEntity>

    @Query("SELECT * FROM scheduled_occurrences WHERE state = 'SCHEDULED' ORDER BY triggerAtEpochMillis, id")
    fun observeScheduled(): Flow<List<ScheduledOccurrenceEntity>>

    @Query("SELECT * FROM scheduled_occurrences WHERE eventId = :eventId ORDER BY triggerAtEpochMillis, id")
    suspend fun getForEvent(eventId: Long): List<ScheduledOccurrenceEntity>

    @Query(
        """
        SELECT * FROM scheduled_occurrences
        WHERE state = 'SCHEDULED' AND triggerAtEpochMillis >= :fromEpochMillis
        ORDER BY triggerAtEpochMillis, id
        """,
    )
    suspend fun getSchedulable(fromEpochMillis: Long): List<ScheduledOccurrenceEntity>

    @Query(
        """
        SELECT * FROM scheduled_occurrences
        WHERE state = 'SCHEDULED' AND triggerAtEpochMillis = :triggerAtEpochMillis
        ORDER BY id
        """,
    )
    suspend fun getPendingAt(triggerAtEpochMillis: Long): List<ScheduledOccurrenceEntity>

    @Query(
        """
        UPDATE scheduled_occurrences
        SET scheduleMode = :mode, scheduledAtEpochMillis = :scheduledAtEpochMillis
        WHERE id = :occurrenceId
        """,
    )
    suspend fun markScheduled(
        occurrenceId: String,
        mode: ScheduleMode,
        scheduledAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE scheduled_occurrences
        SET state = 'RINGING', sessionId = :sessionId, firedAtEpochMillis = :firedAtEpochMillis
        WHERE id IN (:occurrenceIds) AND state = 'SCHEDULED'
        """,
    )
    suspend fun markRinging(
        occurrenceIds: List<String>,
        sessionId: String,
        firedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE scheduled_occurrences
        SET state = :state
        WHERE id IN (:occurrenceIds) AND sessionId = :sessionId
        """,
    )
    suspend fun updateSessionOccurrences(
        occurrenceIds: List<String>,
        sessionId: String,
        state: OccurrenceState,
    ): Int

    @Query("UPDATE scheduled_occurrences SET state = :state WHERE id = :occurrenceId")
    suspend fun updateState(occurrenceId: String, state: OccurrenceState): Int

    @Query("UPDATE scheduled_occurrences SET state = :state WHERE id IN (:occurrenceIds)")
    suspend fun updateStates(occurrenceIds: List<String>, state: OccurrenceState): Int

    @Query("SELECT id FROM scheduled_occurrences WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getIdsForSession(sessionId: String): List<String>

    @Query("UPDATE scheduled_occurrences SET state = 'CANCELLED' WHERE eventId = :eventId AND state = 'SCHEDULED'")
    suspend fun cancelPendingForEvent(eventId: Long): Int

    @Query("DELETE FROM scheduled_occurrences WHERE selectedLocalMinute IS NULL AND state IN ('COMPLETED', 'MISSED', 'CANCELLED') AND triggerAtEpochMillis < :beforeEpochMillis")
    suspend fun pruneFinished(beforeEpochMillis: Long): Int

    @Query(
        """
        SELECT
            o.id AS occurrenceId,
            o.eventId AS eventId,
            e.title AS title,
            CASE WHEN e.timingMode = 'RANDOM_WINDOW' THEN e.windowAlertMode ELSE r.alertMode END AS alertMode,
            CASE WHEN e.timingMode = 'RANDOM_WINDOW' THEN e.windowDurationSeconds ELSE r.ringDurationSeconds END AS ringDurationSeconds,
            CASE WHEN e.timingMode = 'RANDOM_WINDOW' THEN e.windowSnoozeMinutes ELSE r.snoozeMinutes END AS snoozeMinutes,
            o.triggerAtEpochMillis AS triggerAtEpochMillis,
            e.outputMode, e.hapticPreset, e.hapticStrength, e.hapticGapMillis, e.hapticUseMusicRhythm,
            e.musicPoolMode AS musicPoolMode
        FROM scheduled_occurrences AS o
        INNER JOIN events AS e ON e.id = o.eventId
        INNER JOIN reminder_rules AS r ON r.id = o.reminderRuleId
        WHERE o.id = :occurrenceId AND o.state IN ('SCHEDULED', 'RINGING') AND e.enabled = 1 AND e.deletedAtEpochMillis IS NULL
        LIMIT 1
        """,
    )
    suspend fun getPlaybackRow(occurrenceId: String): PlaybackOccurrenceRow?

    @Query(
        """
        SELECT
            o.id AS occurrenceId,
            o.eventId AS eventId,
            e.title AS title,
            CASE WHEN e.timingMode = 'RANDOM_WINDOW' THEN e.windowAlertMode ELSE r.alertMode END AS alertMode,
            CASE WHEN e.timingMode = 'RANDOM_WINDOW' THEN e.windowDurationSeconds ELSE r.ringDurationSeconds END AS ringDurationSeconds,
            CASE WHEN e.timingMode = 'RANDOM_WINDOW' THEN e.windowSnoozeMinutes ELSE r.snoozeMinutes END AS snoozeMinutes,
            o.triggerAtEpochMillis AS triggerAtEpochMillis,
            e.outputMode, e.hapticPreset, e.hapticStrength, e.hapticGapMillis, e.hapticUseMusicRhythm,
            e.musicPoolMode AS musicPoolMode
        FROM scheduled_occurrences AS o
        INNER JOIN events AS e ON e.id = o.eventId
        INNER JOIN reminder_rules AS r ON r.id = o.reminderRuleId
        WHERE o.triggerAtEpochMillis = :triggerAtEpochMillis AND o.state = 'SCHEDULED' AND e.enabled = 1 AND e.deletedAtEpochMillis IS NULL
        ORDER BY o.id
        """,
    )
    suspend fun getPlaybackRowsAt(triggerAtEpochMillis: Long): List<PlaybackOccurrenceRow>
}

@Dao
interface RingSessionDao {
    @Insert
    suspend fun insert(session: RingSessionEntity)

    @Query("SELECT * FROM ring_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): RingSessionEntity?

    @Query("SELECT * FROM ring_sessions WHERE state = 'ACTIVE' ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun getActive(): RingSessionEntity?

    @Query(
        """
        UPDATE ring_sessions
        SET state = :state, stopReason = :stopReason
        WHERE id = :sessionId AND state = 'ACTIVE'
        """,
    )
    suspend fun finish(
        sessionId: String,
        state: RingSessionState,
        stopReason: RingStopReason,
    ): Int
}
