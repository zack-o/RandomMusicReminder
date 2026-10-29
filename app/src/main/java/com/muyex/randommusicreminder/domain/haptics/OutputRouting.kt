package com.muyex.randommusicreminder.domain.haptics

import com.muyex.randommusicreminder.domain.model.PlaybackOccurrence

data class RoutedOutput(val mode: ReminderOutputMode, val audio: PlaybackOccurrence?, val vibration: PlaybackOccurrence?)
object OutputRouting {
    fun select(occurrences: List<PlaybackOccurrence>): RoutedOutput {
        val ordered=occurrences.distinctBy { it.occurrenceId }.sortedBy { it.occurrenceId }
        return RoutedOutput(ReminderOutputMode.merge(ordered.map { it.outputMode }),ordered.firstOrNull { it.outputMode.audio },ordered.firstOrNull { it.outputMode.vibration })
    }
}
