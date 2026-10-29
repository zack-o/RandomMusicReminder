package com.muyex.randommusicreminder.domain.ringing

import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason

data class RingHistoryEntry(
    val sessionId: String,
    val title: String,
    val eventCount: Int,
    val scheduledAtEpochMillis: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val musicName: String?,
    val alertMode: AlertMode,
    val kind: OccurrenceKind,
    val state: RingSessionState,
    val stopReason: RingStopReason?,
    val timingSummary: String? = null,
) {
    val durationMillis: Long?
        get() = endedAtEpochMillis?.let { (it - startedAtEpochMillis).coerceAtLeast(0L) }

    val resultLabel: String
        get() = when {
            state == RingSessionState.ACTIVE -> "响铃中"
            state == RingSessionState.FAILED -> "播放异常或中断"
            stopReason == RingStopReason.USER -> "手动停止"
            stopReason == RingStopReason.TASK_COMPLETED -> "本次事项已完成"
            stopReason == RingStopReason.TIMEOUT -> "到时自动结束"
            stopReason == RingStopReason.SNOOZED -> "已稍后提醒"
            stopReason == RingStopReason.REPLACED -> "由新提醒接替"
            else -> "已结束"
        }
}
