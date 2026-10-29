package com.muyex.randommusicreminder.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.ringing.RingHistoryEntry

// 历史使用名称快照，不建立到事件或音乐的级联外键，删除原对象后仍保留记录。
@Entity(tableName = "ring_history", indices = [Index("startedAtEpochMillis"), Index("anchorOccurrenceId")])
data class RingHistoryEntity(
    @PrimaryKey val sessionId: String,
    val anchorOccurrenceId: String,
    val title: String,
    val eventCount: Int,
    val scheduledAtEpochMillis: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val musicName: String? = null,
    val alertMode: AlertMode,
    val kind: OccurrenceKind,
    val state: RingSessionState,
    val stopReason: RingStopReason? = null,
    val timingSummary: String? = null,
) {
    fun toDomain() = RingHistoryEntry(
        sessionId, title, eventCount, scheduledAtEpochMillis, startedAtEpochMillis,
        endedAtEpochMillis, musicName, alertMode, kind, state, stopReason, timingSummary,
    )
}
