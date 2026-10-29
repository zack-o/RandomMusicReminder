package com.muyex.randommusicreminder.data.repository

import com.muyex.randommusicreminder.data.local.entity.EventEntity
import com.muyex.randommusicreminder.data.local.entity.EventWithRelations
import com.muyex.randommusicreminder.data.local.entity.MusicTrackEntity
import com.muyex.randommusicreminder.data.local.entity.PlaybackOccurrenceRow
import com.muyex.randommusicreminder.data.local.entity.ReminderRuleEntity
import com.muyex.randommusicreminder.data.local.entity.RingSessionEntity
import com.muyex.randommusicreminder.data.local.entity.ScheduledOccurrenceEntity
import com.muyex.randommusicreminder.data.local.entity.ShuffleStateEntity
import com.muyex.randommusicreminder.domain.model.EventDetails
import com.muyex.randommusicreminder.domain.model.MusicTrack
import com.muyex.randommusicreminder.domain.model.PlaybackOccurrence
import com.muyex.randommusicreminder.domain.model.ReminderEvent
import com.muyex.randommusicreminder.domain.model.ReminderRule
import com.muyex.randommusicreminder.domain.model.RingSession
import com.muyex.randommusicreminder.domain.model.ScheduledOccurrence
import com.muyex.randommusicreminder.domain.model.ShuffleState

internal fun EventWithRelations.toDomain(): EventDetails {
    return EventDetails(
        event = event.toDomain(),
        rules = rules.map(ReminderRuleEntity::toDomain).sortedBy { it.offsetMinutes },
        customTrackIds = customTracks.mapTo(linkedSetOf(), MusicTrackEntity::id),
    )
}

internal fun EventEntity.toDomain(): ReminderEvent {
    return ReminderEvent(
        id = id,
        title = title,
        note = note,
        enabled = enabled,
        scheduleType = scheduleType,
        localDate = localDate,
        localTime = localTime,
        weekdaysMask = weekdaysMask,
        musicPoolMode = musicPoolMode,
        outputMode=outputMode,haptics=com.muyex.randommusicreminder.domain.haptics.HapticSettings(hapticPreset,hapticStrength,hapticGapMillis,hapticUseMusicRhythm),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        holidayPolicy = holidayPolicy,
        intervalDays = intervalDays, missingDayPolicy = missingDayPolicy, endDate = endDate,
        timingMode = timingMode, windowEndTime = windowEndTime, windowCrossesMidnight = windowCrossesMidnight,
        windowAlertMode = windowAlertMode, windowDurationSeconds = windowDurationSeconds,
        windowSnoozeMinutes = windowSnoozeMinutes, windowBackupEnabled = windowBackupEnabled,
        hideWindowSelection = hideWindowSelection, randomSeed = randomSeed, timingRevision = timingRevision,
        windowFirstNotBeforeEpochMillis = windowFirstNotBeforeEpochMillis,
        shiftCycle = runCatching { com.muyex.randommusicreminder.data.transfer.ShiftCycleCodec.decode(shiftCycleJson) }.getOrNull(),
        tags = runCatching { com.muyex.randommusicreminder.data.transfer.EventTagsCodec.decode(tagsJson) }.getOrDefault(emptyList()),
        deletedAtEpochMillis = deletedAtEpochMillis,
        resumedAfterEpochMillis = resumedAfterEpochMillis,
        trackCompletion=trackCompletion,completionTrackingStartedAt=completionTrackingStartedAt,
        dayOverrides=com.muyex.randommusicreminder.data.transfer.DayOverrideCodec.decode(dayOverridesJson).also { com.muyex.randommusicreminder.domain.planner.DayOverrides.validate(it,scheduleType) },
        dayOverrideBarriers=com.muyex.randommusicreminder.data.transfer.DayOverrideCodec.decodeBarriers(dayOverrideBarriersJson),
    )
}

internal fun ReminderRuleEntity.toDomain(): ReminderRule {
    return ReminderRule(
        id = id,
        eventId = eventId,
        offsetMinutes = offsetMinutes,
        alertMode = alertMode,
        ringDurationSeconds = ringDurationSeconds,
        snoozeMinutes = snoozeMinutes,
    )
}

internal fun MusicTrackEntity.toDomain(): MusicTrack {
    return MusicTrack(
        id = id,
        displayName = displayName,
        uri = uri,
        mimeType = mimeType,
        durationMillis = durationMillis,
        fileSizeBytes = fileSizeBytes,
        enabled = enabled,
        importedAtEpochMillis = importedAtEpochMillis,
        invalidReason = invalidReason,
    )
}

internal fun ScheduledOccurrenceEntity.toDomain(): ScheduledOccurrence {
    return ScheduledOccurrence(
        id = id,
        eventId = eventId,
        reminderRuleId = reminderRuleId,
        baseDate = baseDate,
        triggerAtEpochMillis = triggerAtEpochMillis,
        kind = kind,
        originalOccurrenceId = originalOccurrenceId,
        snoozeCount = snoozeCount,
        state = state,
        scheduleMode = scheduleMode,
        sessionId = sessionId,
        scheduledAtEpochMillis = scheduledAtEpochMillis,
        firedAtEpochMillis = firedAtEpochMillis,
        selectedLocalMinute = selectedLocalMinute, windowStartAtEpochMillis = windowStartAtEpochMillis,
        windowEndAtEpochMillis = windowEndAtEpochMillis, windowBackup = windowBackup, sourceLabel = sourceLabel,taskOccurrenceKey=taskOccurrenceKey,dayOverrideSignature=dayOverrideSignature,
    )
}

internal fun PlaybackOccurrenceRow.toDomain(): PlaybackOccurrence {
    return PlaybackOccurrence(
        occurrenceId = occurrenceId,
        eventId = eventId,
        title = title,
        alertMode = alertMode,
        ringDurationMillis = ringDurationSeconds * 1_000L,
        snoozeMinutes = snoozeMinutes,
        triggerAtEpochMillis = triggerAtEpochMillis,
        musicPoolMode = musicPoolMode,
        outputMode=outputMode,haptics=com.muyex.randommusicreminder.domain.haptics.HapticSettings(hapticPreset,hapticStrength,hapticGapMillis,hapticUseMusicRhythm),
    )
}

internal fun ShuffleStateEntity.toDomain(): ShuffleState {
    return ShuffleState(
        scopeKey = scopeKey,
        cycle = cycle,
        remainingTrackIds = remainingTrackIds,
        lastTrackId = lastTrackId,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

internal fun ShuffleState.toEntity(): ShuffleStateEntity {
    return ShuffleStateEntity(
        scopeKey = scopeKey,
        cycle = cycle,
        remainingTrackIds = remainingTrackIds,
        lastTrackId = lastTrackId,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

internal fun RingSession.toEntity(): RingSessionEntity {
    return RingSessionEntity(
        id = id,
        scheduledTriggerAtEpochMillis = scheduledTriggerAtEpochMillis,
        startedAtEpochMillis = startedAtEpochMillis,
        deadlineEpochMillis = deadlineEpochMillis,
        selectedTrackId = selectedTrackId,
        state = state,
        stopReason = stopReason,
    )
}
