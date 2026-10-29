package com.muyex.randommusicreminder.domain.selection

import com.muyex.randommusicreminder.domain.model.MusicTrack
import com.muyex.randommusicreminder.domain.model.ShuffleState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackSelectorTest {
    @Test
    fun eachTrackPlaysOnceBeforeNextCycle() {
        val selector = TrackSelector(Random(7))
        val tracks = (1L..3L).map(::track)
        var state = ShuffleState(scopeKey = TrackSelector.GLOBAL_SCOPE)
        val selected = mutableListOf<Long>()

        repeat(4) {
            val result = selector.select(tracks, state, nowEpochMillis = it.toLong())
            selected += requireNotNull(result.track).id
            state = result.nextState
        }

        assertEquals(3, selected.take(3).toSet().size)
        assertNotEquals(selected[2], selected[3])
        assertEquals(2, state.cycle)
    }

    @Test
    fun removedAndInvalidTracksAreSkipped() {
        val selector = TrackSelector(Random(1))
        val state = ShuffleState(
            scopeKey = TrackSelector.GLOBAL_SCOPE,
            cycle = 2,
            remainingTrackIds = listOf(99, 2),
        )

        val result = selector.select(
            candidates = listOf(track(1), track(2).copy(invalidReason = "decode failed")),
            state = state,
            nowEpochMillis = 10,
        )

        assertEquals(1L, result.track?.id)
    }

    @Test
    fun emptyPoolReturnsNull() {
        val selector = TrackSelector(Random(1))
        val result = selector.select(
            candidates = emptyList(),
            state = ShuffleState(scopeKey = TrackSelector.GLOBAL_SCOPE),
            nowEpochMillis = 10,
        )

        assertNull(result.track)
    }

    private fun track(id: Long): MusicTrack {
        return MusicTrack(
            id = id,
            displayName = "Track $id",
            uri = "file:///$id.mp3",
            mimeType = "audio/mpeg",
            durationMillis = 60_000,
        )
    }
}
