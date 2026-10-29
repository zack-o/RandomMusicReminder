package com.muyex.randommusicreminder.domain.ringing

import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.PlaybackOccurrence
import com.muyex.randommusicreminder.domain.model.RingClaim
import com.muyex.randommusicreminder.domain.model.RingSession
import com.muyex.randommusicreminder.domain.model.RingSessionState

class RingCoordinator {
    /**
     * 按计划时间而非广播抵达时间合并，系统先后投递同刻闹钟时仍只会创建一个会话。
     * 数据层应在同一事务内读取候选项并应用本决策，确保多条 Service intent 下的幂等性。
     */
    fun claimSimultaneous(
        anchorOccurrenceId: String,
        pendingOccurrences: List<PlaybackOccurrence>,
    ): RingClaim? {
        val anchor = pendingOccurrences.firstOrNull {
            it.occurrenceId == anchorOccurrenceId
        } ?: return null

        val simultaneous = pendingOccurrences
            .asSequence()
            .filter { it.triggerAtEpochMillis == anchor.triggerAtEpochMillis }
            .distinctBy(PlaybackOccurrence::occurrenceId)
            .sortedBy(PlaybackOccurrence::occurrenceId)
            .toList()
        if (simultaneous.isEmpty()) return null

        return RingClaim(
            occurrenceIds = simultaneous.map(PlaybackOccurrence::occurrenceId),
            scheduledTriggerAtEpochMillis = anchor.triggerAtEpochMillis,
            durationMillis = simultaneous.maxOf(PlaybackOccurrence::ringDurationMillis),
            alertMode = if (simultaneous.any { it.alertMode == AlertMode.STRONG }) {
                AlertMode.STRONG
            } else {
                AlertMode.GENTLE
            },
        )
    }

    fun createSession(
        sessionId: String,
        claim: RingClaim,
        selectedTrackId: Long?,
        startedAtEpochMillis: Long,
    ): RingSession {
        require(claim.occurrenceIds.isNotEmpty()) { "A ring session needs at least one occurrence" }
        return RingSession(
            id = sessionId,
            scheduledTriggerAtEpochMillis = claim.scheduledTriggerAtEpochMillis,
            startedAtEpochMillis = startedAtEpochMillis,
            deadlineEpochMillis = startedAtEpochMillis + claim.durationMillis,
            selectedTrackId = selectedTrackId,
            state = RingSessionState.ACTIVE,
        )
    }
}
