package com.muyex.randommusicreminder.domain.ringing

class SnoozePolicy(
    private val maxCount: Int = 3,
    private val allowedMinutes: Set<Int> = setOf(5, 10, 15),
) {
    fun nextCount(currentCount: Int, minutes: Int): Int? {
        require(currentCount >= 0) { "Snooze count must not be negative" }
        require(minutes in allowedMinutes) { "Snooze duration must be 5, 10 or 15 minutes" }
        return (currentCount + 1).takeIf { it <= maxCount }
    }
}
