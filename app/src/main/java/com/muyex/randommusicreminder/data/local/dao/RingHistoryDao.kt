package com.muyex.randommusicreminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.muyex.randommusicreminder.data.local.entity.RingHistoryEntity
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import kotlinx.coroutines.flow.Flow

@Dao
interface RingHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: RingHistoryEntity)

    @Query("SELECT * FROM ring_history ORDER BY startedAtEpochMillis DESC, sessionId DESC")
    fun observeAll(): Flow<List<RingHistoryEntity>>

    @Query("SELECT * FROM ring_history WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): RingHistoryEntity?

    @Query("SELECT * FROM ring_history WHERE anchorOccurrenceId = :occurrenceId ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun getForOccurrence(occurrenceId: String): RingHistoryEntity?

    @Query("UPDATE ring_history SET musicName = :musicName WHERE sessionId = :sessionId AND state = 'ACTIVE'")
    suspend fun updateMusic(sessionId: String, musicName: String): Int

    @Query("""
        UPDATE ring_history SET state = :state, stopReason = :reason, endedAtEpochMillis = :endedAt
        WHERE sessionId = :sessionId AND state = 'ACTIVE'
    """)
    suspend fun finish(sessionId: String, state: RingSessionState, reason: RingStopReason, endedAt: Long?): Int

    @Query("""
        UPDATE ring_history SET state = 'FAILED', stopReason = 'ERROR'
        WHERE state = 'ACTIVE' AND startedAtEpochMillis < :beforeEpochMillis
    """)
    suspend fun markInterrupted(beforeEpochMillis: Long): Int
}
