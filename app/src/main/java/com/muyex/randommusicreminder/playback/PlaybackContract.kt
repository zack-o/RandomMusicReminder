package com.muyex.randommusicreminder.playback

import com.muyex.randommusicreminder.alarm.AlarmRequest
import com.muyex.randommusicreminder.alarm.ReminderIntensity
import com.muyex.randommusicreminder.domain.haptics.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PlaybackOccurrenceInfo(
    val occurrenceId: String,
    val eventId: String,
    val title: String,
    val intensity: ReminderIntensity,
    val ringDurationMs: Long,
    val snoozeMinutes: Int? = 10,
    val isTest: Boolean = false,
    val timingLabel: String? = null,
    val hasWindowBackup: Boolean = false,
    val outputMode: ReminderOutputMode = ReminderOutputMode.MUSIC,
    val haptics: HapticSettings = HapticSettings(),
    val audioEventId: String? = null,
    val vibrationEventId: String? = null,
    val outputSources: String? = null,
    val outputStatus: String? = null,
    val hasTaskCompletion: Boolean = false,
    val continuation: Boolean = false,
)

data class PlaybackTrackInfo(
    val id: String,
    val uri: String,
    val displayName: String,
)

enum class PlaybackStopReason {
    USER_STOPPED,
    SNOOZED,
    DURATION_REACHED,
    REPLACED_BY_NEW_REMINDER,
    PLAYBACK_ERROR,
    TASK_COMPLETED,
}

sealed interface PlaybackRefresh {
    data object Keep: PlaybackRefresh
    data class Update(val occurrence: PlaybackOccurrenceInfo):PlaybackRefresh
    data class Finish(val reason: PlaybackStopReason):PlaybackRefresh
}

object TaskPlaybackChanges {
    private val mutable=MutableSharedFlow<Unit>(extraBufferCapacity=1)
    val changes=mutable.asSharedFlow()
    fun publish() { mutable.tryEmit(Unit) }
}

interface PlaybackServiceDependencies {
    suspend fun refreshPlayback(occurrenceId:String):PlaybackRefresh=PlaybackRefresh.Keep
    suspend fun loadOccurrence(occurrenceId: String): PlaybackOccurrenceInfo?

    suspend fun preparePlayback(occurrenceId: String): PlaybackOccurrenceInfo? {
        val occurrence=loadOccurrence(occurrenceId) ?: return null
        return if(onPlaybackStarted(occurrenceId,null)) occurrence else null
    }

    /** 由仓库原子更新 shuffle bag 并返回下一首，服务层不再二次随机。 */
    suspend fun selectNextTrack(eventId: String): PlaybackTrackInfo?

    /** 返回 false 表示 occurrence 已被处理或取消，服务不得打断当前有效播放。 */
    suspend fun onPlaybackStarted(occurrenceId: String, trackId: String?): Boolean

    suspend fun onTrackFailed(occurrenceId: String, trackId: String?, message: String)

    suspend fun onPlaybackStopped(occurrenceId: String, reason: PlaybackStopReason)

    suspend fun onMusicPlaying(occurrenceId: String, trackName: String) = Unit

    suspend fun createSnooze(occurrenceId: String, minutes: Int): AlarmRequest?
    suspend fun acknowledgeWindow(occurrenceId: String) = Unit
}

object PlaybackDependencyRegistry {
    @Volatile
    private var dependencies: PlaybackServiceDependencies? = null

    fun install(dependencies: PlaybackServiceDependencies) {
        this.dependencies = dependencies
    }

    fun clear() {
        dependencies = null
    }

    internal fun current(): PlaybackServiceDependencies? = dependencies
}

internal data class PlaybackStartRequest(
    val occurrenceId: String,
    val eventId: String,
    val title: String,
    val intensity: ReminderIntensity,
    val ringDurationMs: Long,
    val outputMode: ReminderOutputMode = ReminderOutputMode.MUSIC,
) {
    fun asOccurrence(): PlaybackOccurrenceInfo = PlaybackOccurrenceInfo(
        occurrenceId = occurrenceId,
        eventId = eventId,
        title = title,
        intensity = intensity,
        ringDurationMs = ringDurationMs,
        outputMode = outputMode,
    )

    companion object {
        fun fromAlarm(request: AlarmRequest) = PlaybackStartRequest(
            occurrenceId = request.occurrenceId,
            eventId = request.eventId,
            title = request.title,
            intensity = request.intensity,
            ringDurationMs = request.ringDurationMs,
            outputMode = request.outputMode,
        )
    }
}

internal object PlaybackExtras {
    const val OCCURRENCE_ID = "playback_occurrence_id"
    const val EVENT_ID = "playback_event_id"
    const val TITLE = "playback_title"
    const val INTENSITY = "playback_intensity"
    const val RING_DURATION = "playback_ring_duration"
    const val SNOOZE_MINUTES = "playback_snooze_minutes"
    const val TIMING_LABEL = "playback_timing_label"
    const val WINDOW_BACKUP = "playback_window_backup"
    const val RETRY_START = "playback_retry_start"
    const val SNOOZE_ENABLED = "playback_snooze_enabled"
    const val OUTPUT_MODE = "playback_output_mode"
    const val OUTPUT_SOURCES = "playback_output_sources"
    const val OUTPUT_STATUS = "playback_output_status"
    const val TASK_COMPLETION_ENABLED = "playback_task_completion_enabled"
}
