package com.muyex.randommusicreminder.domain.ringing

import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.MusicPoolMode
import com.muyex.randommusicreminder.domain.model.PlaybackOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingCoordinatorTest {
    private val coordinator = RingCoordinator()

    @Test
    fun sameScheduledTimeBecomesOneClaim() {
        val occurrences = listOf(
            occurrence("a", triggerAt = 1_000, duration = 30_000, mode = AlertMode.GENTLE),
            occurrence("b", triggerAt = 1_000, duration = 60_000, mode = AlertMode.STRONG),
            occurrence("c", triggerAt = 2_000, duration = 90_000, mode = AlertMode.GENTLE),
        )

        val claim = requireNotNull(coordinator.claimSimultaneous("a", occurrences))

        assertEquals(listOf("a", "b"), claim.occurrenceIds)
        assertEquals(60_000, claim.durationMillis)
        assertEquals(AlertMode.STRONG, claim.alertMode)
    }

    @Test
    fun missingAnchorDoesNotCreateClaim() {
        assertNull(coordinator.claimSimultaneous("missing", listOf(occurrence("a", 1_000, 1_000))))
    }

    @Test
    fun sessionDeadlineUsesStartTimeAndClaimDuration() {
        val claim = requireNotNull(
            coordinator.claimSimultaneous("a", listOf(occurrence("a", 1_000, 30_000))),
        )

        val session = coordinator.createSession("session", claim, selectedTrackId = 9, startedAtEpochMillis = 5_000)

        assertEquals(35_000, session.deadlineEpochMillis)
        assertEquals(9L, session.selectedTrackId)
    }

    private fun occurrence(
        id: String,
        triggerAt: Long,
        duration: Long,
        mode: AlertMode = AlertMode.GENTLE,
    ): PlaybackOccurrence {
        return PlaybackOccurrence(
            occurrenceId = id,
            eventId = 1,
            title = id,
            alertMode = mode,
            ringDurationMillis = duration,
            snoozeMinutes = null,
            triggerAtEpochMillis = triggerAt,
            musicPoolMode = MusicPoolMode.GLOBAL,
        )
    }
}
