package com.muyex.randommusicreminder.domain.statistics

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UsageSessionClockTest {
    private var wall = Instant.parse("2026-10-20T00:00:00Z")
    private var elapsed = 1000L
    private val chunks = mutableListOf<UsageChunk>()
    private val clock = UsageSessionClock({ wall }, { elapsed }, { ZoneId.of("Asia/Shanghai") }, chunks::add)
    private fun advance(millis: Long) { elapsed += millis; wall = wall.plusMillis(millis) }

    @Test fun backgroundAndDisabledVisibilityDoNotCreateRecords() {
        clock.setVisible(true); advance(500); clock.flush(); clock.setVisible(false)
        clock.setEnabled(true); advance(500); clock.flush()
        assertTrue(chunks.isEmpty())
    }
    @Test fun repeatStartDuringConfigurationChangeDoesNotCountAgain() {
        clock.setEnabled(true); clock.setVisible(true); advance(500); clock.setVisible(true); clock.setVisible(false)
        assertEquals(1, chunks.count { it.launch })
        assertEquals(500L, chunks.sumOf { it.elapsedMillis })
    }
    @Test fun backgroundReturnCreatesANewSessionWithoutCountingBackgroundTime() {
        clock.setEnabled(true); clock.setVisible(true); advance(1000); clock.setVisible(false)
        advance(90_000); clock.setVisible(true); advance(2000); clock.setVisible(false)
        assertEquals(2, chunks.count { it.launch })
        assertEquals(3000L, chunks.sumOf { it.elapsedMillis })
    }
    @Test fun periodicFlushAndStopDoNotDoubleCount() {
        clock.setEnabled(true); clock.setVisible(true); advance(15_000); clock.flush(); advance(2000); clock.setVisible(false); clock.flush()
        assertEquals(17_000L, chunks.sumOf { it.elapsedMillis })
        assertFalse(clock.isActive)
    }
    @Test fun turningOffStatisticsFlushesOnlyPreviouslyEnabledTime() {
        clock.setEnabled(true); clock.setVisible(true); advance(500); clock.setEnabled(false)
        advance(5000); clock.flush(); clock.setVisible(false)
        assertEquals(500L, chunks.sumOf { it.elapsedMillis })
        assertEquals(1, chunks.count { it.launch })
    }
    @Test fun wallClockJumpDoesNotChangeMeasuredDuration() {
        clock.setEnabled(true); clock.setVisible(true); advance(500); wall = wall.plusSeconds(86400); clock.setVisible(false)
        assertEquals(500L, chunks.sumOf { it.elapsedMillis })
    }
}
