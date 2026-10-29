package com.muyex.randommusicreminder.domain.ringing

import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingHistoryTest {
    private val entry = RingHistoryEntry(
        "session", "喝水", 1, 900, 1_000, null, null,
        AlertMode.GENTLE, OccurrenceKind.ORIGINAL, RingSessionState.ACTIVE, null,
    )

    @Test
    fun oldRecordDoesNotInventEndTimeOrDuration() {
        val old = entry.copy(state = RingSessionState.COMPLETED, stopReason = RingStopReason.TIMEOUT)
        assertNull(old.durationMillis)
        assertNull(old.musicName)
    }

    @Test
    fun durationUsesRecordedEndTime() {
        assertEquals(15_000L, entry.copy(endedAtEpochMillis = 16_000).durationMillis)
    }

    @Test
    fun backwardsClockDoesNotDisplayNegativeDuration() {
        assertEquals(0L, entry.copy(endedAtEpochMillis = 500).durationMillis)
    }

    @Test
    fun stopResultsRemainDistinct() {
        val expected = mapOf(
            RingStopReason.USER to "手动停止",
            RingStopReason.TIMEOUT to "到时自动结束",
            RingStopReason.SNOOZED to "已稍后提醒",
            RingStopReason.REPLACED to "由新提醒接替",
        )
        expected.forEach { (reason, label) ->
            assertEquals(label, entry.copy(state = RingSessionState.COMPLETED, stopReason = reason).resultLabel)
        }
    }

    @Test
    fun interruptedPlaybackIsNotShownAsNormallyFinished() {
        assertEquals("播放异常或中断", entry.copy(state = RingSessionState.FAILED).resultLabel)
        assertEquals("响铃中", entry.resultLabel)
    }
}
