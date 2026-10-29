package com.muyex.randommusicreminder.domain.selection

import com.muyex.randommusicreminder.domain.model.MusicTrack
import com.muyex.randommusicreminder.domain.model.ShuffleState
import com.muyex.randommusicreminder.domain.model.TrackSelection
import kotlin.random.Random

class TrackSelector(
    private val random: Random = Random.Default,
) {
    fun select(
        candidates: List<MusicTrack>,
        state: ShuffleState,
        nowEpochMillis: Long,
    ): TrackSelection {
        val playableById = candidates
            .asSequence()
            .filter { it.id > 0 && it.enabled && it.invalidReason == null }
            .distinctBy(MusicTrack::id)
            .associateBy(MusicTrack::id)

        if (playableById.isEmpty()) {
            return TrackSelection(
                track = null,
                nextState = state.copy(
                    remainingTrackIds = emptyList(),
                    updatedAtEpochMillis = nowEpochMillis,
                ),
            )
        }

        var nextCycle = state.cycle
        var bag = state.remainingTrackIds
            .distinct()
            .filter(playableById::containsKey)

        if (bag.isEmpty()) {
            nextCycle += 1
            bag = playableById.keys.shuffled(random)

            // 新一轮首曲尽量避开上一轮末曲，避免用户连续听到同一首。
            if (bag.size > 1 && bag.first() == state.lastTrackId) {
                bag = bag.toMutableList().also { shuffled ->
                    val replacementIndex = shuffled.indexOfFirst { it != state.lastTrackId }
                    val first = shuffled[0]
                    shuffled[0] = shuffled[replacementIndex]
                    shuffled[replacementIndex] = first
                }
            }
        }

        val selectedId = bag.first()
        return TrackSelection(
            track = playableById.getValue(selectedId),
            nextState = state.copy(
                cycle = nextCycle,
                remainingTrackIds = bag.drop(1),
                lastTrackId = selectedId,
                updatedAtEpochMillis = nowEpochMillis,
            ),
        )
    }

    companion object {
        const val GLOBAL_SCOPE = "GLOBAL"

        fun customScope(eventId: Long): String = "EVENT:$eventId"
    }
}
