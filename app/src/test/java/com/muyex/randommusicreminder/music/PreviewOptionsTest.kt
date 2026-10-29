package com.muyex.randommusicreminder.music

import com.muyex.randommusicreminder.domain.music.PreviewOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreviewOptionsTest {
    @Test fun defaultIsShortAndQuiet() {
        assertEquals(5, PreviewOptions().durationSeconds)
        assertEquals(5, PreviewOptions().volumePercent)
    }
    @Test fun acceptsAdvancedBoundariesAndSilentPreview() {
        assertEquals(0, PreviewOptions(300, 0, 86_400).volumePercent)
        assertEquals(100, PreviewOptions(1, 100, 0).volumePercent)
    }
    @Test fun rejectsInvalidValuesInsteadOfSilentlyChangingThem() {
        listOf({ PreviewOptions(durationSeconds = 0) }, { PreviewOptions(durationSeconds = 301) },
            { PreviewOptions(volumePercent = -1) }, { PreviewOptions(volumePercent = 101) },
            { PreviewOptions(startSeconds = -1) }, { PreviewOptions(startSeconds = 86_401) }).forEach {
            assertThrows(IllegalArgumentException::class.java) { it() }
        }
    }
    @Test fun clipsAtTrackEndAndUsesRequestedOffset() {
        assertEquals(12_000L, PreviewOptions(5, 5, 10).endPositionMillis(12_000))
        assertEquals(15_000L, PreviewOptions(5, 5, 10).endPositionMillis(90_000))
    }
    @Test fun rejectsAnOffsetAtOrBeyondTheEnd() {
        assertThrows(IllegalArgumentException::class.java) { PreviewOptions(startSeconds = 10).endPositionMillis(10_000) }
        assertThrows(IllegalArgumentException::class.java) { PreviewOptions().endPositionMillis(0) }
    }
}
