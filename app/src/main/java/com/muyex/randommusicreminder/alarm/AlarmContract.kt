package com.muyex.randommusicreminder.alarm

import android.content.Intent
import android.net.Uri
import com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode

enum class ReminderIntensity {
    GENTLE,
    STRONG;

    companion object {
        fun from(value: String?): ReminderIntensity =
            entries.firstOrNull { it.name == value } ?: GENTLE
    }
}

data class AlarmRequest(
    val occurrenceId: String,
    val eventId: String,
    val title: String,
    val triggerAtEpochMs: Long,
    val ringDurationMs: Long,
    val intensity: ReminderIntensity,
    val outputMode: ReminderOutputMode = ReminderOutputMode.MUSIC,
)

enum class AlarmScheduleMode {
    EXACT,
    INEXACT_FALLBACK,
}

data class AlarmScheduleResult(
    val mode: AlarmScheduleMode,
    val scheduledAtEpochMs: Long,
)

internal object AlarmIntentContract {
    const val ACTION_FIRE = "com.muyex.randommusicreminder.action.FIRE_REMINDER"
    const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_TRIGGER_AT = "extra_trigger_at"
    const val EXTRA_RING_DURATION = "extra_ring_duration"
    const val EXTRA_INTENSITY = "extra_intensity"
    const val EXTRA_OUTPUT_MODE = "extra_output_mode"

    fun dataUri(occurrenceId: String): Uri = Uri.Builder()
        .scheme("lexing")
        .authority("alarm")
        .appendPath("occurrence")
        .appendPath(occurrenceId)
        .build()

    fun putRequest(intent: Intent, request: AlarmRequest): Intent = intent.apply {
        action = ACTION_FIRE
        data = dataUri(request.occurrenceId)
        putExtra(EXTRA_OCCURRENCE_ID, request.occurrenceId)
        putExtra(EXTRA_EVENT_ID, request.eventId)
        putExtra(EXTRA_TITLE, request.title)
        putExtra(EXTRA_TRIGGER_AT, request.triggerAtEpochMs)
        putExtra(EXTRA_RING_DURATION, request.ringDurationMs)
        putExtra(EXTRA_INTENSITY, request.intensity.name)
        putExtra(EXTRA_OUTPUT_MODE, request.outputMode.name)
    }

    fun requestFrom(intent: Intent): AlarmRequest? {
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return null
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return null
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, -1L)
        if (triggerAt < 0L) return null
        return AlarmRequest(
            occurrenceId = occurrenceId,
            eventId = eventId,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            triggerAtEpochMs = triggerAt,
            ringDurationMs = intent.getLongExtra(EXTRA_RING_DURATION, 60_000L),
            intensity = ReminderIntensity.from(intent.getStringExtra(EXTRA_INTENSITY)),
            outputMode = readOutputMode(intent.getStringExtra(EXTRA_OUTPUT_MODE)),
        )
    }

    private fun readOutputMode(value: String?): ReminderOutputMode = if(value==null) ReminderOutputMode.MUSIC else ReminderOutputMode.entries.firstOrNull { it.name==value } ?: ReminderOutputMode.NOTIFICATION
}
