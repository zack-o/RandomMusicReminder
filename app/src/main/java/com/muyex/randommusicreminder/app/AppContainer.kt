package com.muyex.randommusicreminder.app

import android.content.Context
import androidx.room.withTransaction
import com.muyex.randommusicreminder.alarm.AlarmRequest
import com.muyex.randommusicreminder.alarm.AlarmRescheduleHandler
import com.muyex.randommusicreminder.alarm.AlarmRescheduleRegistry
import com.muyex.randommusicreminder.alarm.AndroidAlarmScheduler
import com.muyex.randommusicreminder.alarm.ReminderIntensity
import com.muyex.randommusicreminder.alarm.ScheduleReconciler
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.data.repository.MusicRepository
import com.muyex.randommusicreminder.data.settings.SettingsRepository
import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.ScheduleMode
import com.muyex.randommusicreminder.domain.model.PlaybackOccurrence
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.music.MusicImporter
import com.muyex.randommusicreminder.music.MusicPreviewController
import com.muyex.randommusicreminder.playback.PlaybackDependencyRegistry
import com.muyex.randommusicreminder.playback.PlaybackOccurrenceInfo
import com.muyex.randommusicreminder.playback.PlaybackServiceDependencies
import com.muyex.randommusicreminder.playback.PlaybackStopReason
import com.muyex.randommusicreminder.playback.PlaybackTrackInfo

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val alarmScheduler = AndroidAlarmScheduler(appContext)

    val eventRepository = EventRepository(
        database = database,
        cancelScheduledAlarm = alarmScheduler::cancel,
    )
    val taskRepository = com.muyex.randommusicreminder.data.repository.TaskRepository(database,eventRepository,cancelAlarm={alarmScheduler.cancel(it)},onChanged={com.muyex.randommusicreminder.playback.TaskPlaybackChanges.publish();runCatching { scheduleReconciler.reconcileAll() }.isSuccess})
    val schedulePreviewRepository=com.muyex.randommusicreminder.data.repository.SchedulePreviewRepository(database,eventRepository)
    val musicRepository = MusicRepository(database)
    val settingsRepository = SettingsRepository(appContext)
    val calendarRepository = com.muyex.randommusicreminder.data.repository.CalendarRepository(database)
    val aiSettings = com.muyex.randommusicreminder.ai.AiSettingsStore(appContext)
    val aiActions = com.muyex.randommusicreminder.data.repository.AiActionRepository(database, eventRepository)
    val aiClient = com.muyex.randommusicreminder.ai.AiClient()
    val dataTransfer = com.muyex.randommusicreminder.data.transfer.DataTransferService(appContext, database, eventRepository, musicRepository, calendarRepository, settingsRepository)
    val usageRepository = com.muyex.randommusicreminder.data.repository.UsageRepository(database)
    val usageTracker = UsageTracker(usageRepository, settingsRepository)
    val musicImporter = MusicImporter(appContext)
    val musicPreviewController = MusicPreviewController(appContext)
    val hapticPatterns = com.muyex.randommusicreminder.music.HapticPatternStore(appContext)
    val scheduleReconciler = ScheduleReconciler(
        database = database,
        eventRepository = eventRepository,
        alarmScheduler = alarmScheduler,
        settingsRepository = settingsRepository,
    )

    suspend fun saveReminder(details:com.muyex.randommusicreminder.domain.model.EventDetails):Long=database.withTransaction {
        val id=eventRepository.saveEvent(details)
        taskRepository.initializeTracking(id)
        id
    }

    init {
        AlarmRescheduleRegistry.install(
            AlarmRescheduleHandler { scheduleReconciler.reconcileAll() },
        )
        PlaybackDependencyRegistry.install(playbackDependencies())
    }

    suspend fun scheduleTestReminder(eventId: Long) {
        val occurrence = eventRepository.createTestOccurrence(eventId)
        try {
            val info = checkNotNull(playbackDependencies().loadOccurrence(occurrence.id))
            val result = alarmScheduler.schedule(
                AlarmRequest(
                    occurrenceId = occurrence.id,
                    eventId = eventId.toString(),
                    title = "试响 · ${info.title}",
                    triggerAtEpochMs = occurrence.triggerAtEpochMillis,
                    ringDurationMs = 2_000L,
                    intensity = info.intensity,
                    outputMode = info.outputMode,
                ),
            )
            eventRepository.markScheduled(
                occurrence.id,
                if (result.mode == com.muyex.randommusicreminder.alarm.AlarmScheduleMode.EXACT) {
                    ScheduleMode.EXACT
                } else {
                    ScheduleMode.INEXACT
                },
            )
        } catch (error: Exception) {
            eventRepository.cancelOccurrence(occurrence.id)
            throw error
        }
    }

    private fun playbackDependencies() = object : PlaybackServiceDependencies {
        override suspend fun loadOccurrence(occurrenceId: String): PlaybackOccurrenceInfo? {
            val group = eventRepository.loadPlaybackGroup(occurrenceId)
            val occurrences = group.ifEmpty {
                listOfNotNull(eventRepository.loadPlaybackOccurrence(occurrenceId))
            }
            return describeOccurrences(occurrenceId,occurrences)
        }

        override suspend fun preparePlayback(occurrenceId: String): PlaybackOccurrenceInfo? {
            val claimed=eventRepository.claimSimultaneous(occurrenceId,null,requireDue=true) ?: return null
            return try { describeOccurrences(occurrenceId,claimed.occurrences) }
            catch(error: Exception) { onPlaybackStopped(occurrenceId,PlaybackStopReason.PLAYBACK_ERROR);throw error }
        }

        override suspend fun refreshPlayback(occurrenceId:String):com.muyex.randommusicreminder.playback.PlaybackRefresh {
            val sessionId=eventRepository.findSessionId(occurrenceId) ?: return com.muyex.randommusicreminder.playback.PlaybackRefresh.Keep
            val session=database.ringSessionDao().getById(sessionId) ?: return com.muyex.randommusicreminder.playback.PlaybackRefresh.Keep
            if(session.state!=com.muyex.randommusicreminder.domain.model.RingSessionState.ACTIVE) return com.muyex.randommusicreminder.playback.PlaybackRefresh.Finish(
                if(session.stopReason==RingStopReason.TASK_COMPLETED) PlaybackStopReason.TASK_COMPLETED else PlaybackStopReason.USER_STOPPED)
            val rows=database.occurrenceDao().getByIds(database.occurrenceDao().getIdsForSession(sessionId)).filter { it.state==com.muyex.randommusicreminder.domain.model.OccurrenceState.RINGING }
            val active=rows.mapNotNull { eventRepository.loadPlaybackOccurrence(it.id) }
            if(active.isEmpty()) return com.muyex.randommusicreminder.playback.PlaybackRefresh.Finish(PlaybackStopReason.USER_STOPPED)
            val current=System.currentTimeMillis()
            val remaining=active.maxOf { row -> (rows.first { it.id==row.occurrenceId }.firedAtEpochMillis ?: session.startedAtEpochMillis)+row.ringDurationMillis-current }
            if(remaining<=0) return com.muyex.randommusicreminder.playback.PlaybackRefresh.Finish(PlaybackStopReason.DURATION_REACHED)
            val info=checkNotNull(describeOccurrences(occurrenceId,active)).copy(occurrenceId=occurrenceId,ringDurationMs=remaining,continuation=true)
            return com.muyex.randommusicreminder.playback.PlaybackRefresh.Update(info)
        }

        private suspend fun describeOccurrences(occurrenceId: String, occurrences: List<com.muyex.randommusicreminder.domain.model.PlaybackOccurrence>): PlaybackOccurrenceInfo? {
            if (occurrences.isEmpty()) return null
            val routing=com.muyex.randommusicreminder.domain.haptics.OutputRouting.select(occurrences)
            val audio=routing.audio;val vibration=routing.vibration
            val anchor = occurrences.firstOrNull { it.occurrenceId == occurrenceId }
                ?: occurrences.first()
            val scheduled = eventRepository.getScheduledOccurrence(anchor.occurrenceId)
            val isTest = scheduled?.kind == OccurrenceKind.TEST
            val snoozeMinutes = occurrences.firstNotNullOfOrNull { item ->
                val count = eventRepository.getScheduledOccurrence(item.occurrenceId)?.snoozeCount ?: 0
                item.snoozeMinutes?.takeIf { count < 3 }
            }
            val windowItems = occurrences.mapNotNull { eventRepository.getScheduledOccurrence(it.occurrenceId) }.filter { it.selectedLocalMinute != null }
            val timingLabel = windowItems.mapNotNull { item -> eventRepository.getEvent(item.eventId)?.event?.let { event ->
                "${if (item.windowBackup) "窗口末补充" else "随机主提醒"} ${event.localTime} — ${if(event.windowCrossesMidnight) "次日 " else ""}${event.windowEndTime}"
            } }.plus(occurrences.mapNotNull { eventRepository.getScheduledOccurrence(it.occurrenceId)?.sourceLabel }).distinct().joinToString("；").takeIf { it.isNotBlank() }
            val hasBackup = !isTest && windowItems.any { eventRepository.getEvent(it.eventId)?.event?.windowBackupEnabled == true }
            return PlaybackOccurrenceInfo(
                occurrenceId = occurrenceId,
                eventId = anchor.eventId.toString(),
                title = if (isTest) "试响 · ${combinedTitle(occurrences)}" else combinedTitle(occurrences),
                intensity = if (occurrences.any { it.alertMode == AlertMode.STRONG }) {
                    ReminderIntensity.STRONG
                } else {
                    ReminderIntensity.GENTLE
                },
                ringDurationMs = if (isTest) 2_000L else occurrences.maxOf { it.ringDurationMillis },
                snoozeMinutes = if (isTest) null else snoozeMinutes,
                isTest = isTest, timingLabel = timingLabel, hasWindowBackup = hasBackup,
                hasTaskCompletion=!isTest && occurrences.any { eventRepository.getEvent(it.eventId)?.event?.trackCompletion==true },
                outputMode=routing.mode,
                haptics=vibration?.haptics ?: com.muyex.randommusicreminder.domain.haptics.HapticSettings(),
                audioEventId=audio?.eventId?.toString(),vibrationEventId=vibration?.eventId?.toString(),
                outputSources=if(occurrences.size>1) listOfNotNull(audio?.let { "音乐来自${it.title}" },vibration?.let { "震动来自${it.title}（采用其触感设置）" }).joinToString("；").take(500) else null,
            )
        }

        override suspend fun selectNextTrack(eventId: String): PlaybackTrackInfo? {
            val id = eventId.toLongOrNull() ?: return null
            return musicRepository.selectNextTrack(id)?.let { track ->
                PlaybackTrackInfo(
                    id = track.id.toString(),
                    uri = track.uri,
                    displayName = track.displayName,
                )
            }
        }

        override suspend fun onPlaybackStarted(occurrenceId: String, trackId: String?): Boolean {
            return eventRepository.claimSimultaneous(
                anchorOccurrenceId = occurrenceId,
                selectedTrackId = trackId?.toLongOrNull(),
                requireDue=true,
            ) != null
        }

        override suspend fun onTrackFailed(
            occurrenceId: String,
            trackId: String?,
            message: String,
        ) {
            trackId?.toLongOrNull()?.let { musicRepository.markInvalid(it, message) }
        }

        override suspend fun onPlaybackStopped(
            occurrenceId: String,
            reason: PlaybackStopReason,
        ) {
            val sessionId = eventRepository.findSessionId(occurrenceId)
            if (sessionId != null) {
                eventRepository.completeRingSession(sessionId, reason.toDomain())
            } else {
                eventRepository.markOccurrenceCompleted(occurrenceId)
            }
        }

        override suspend fun onMusicPlaying(occurrenceId: String, trackName: String) {
            eventRepository.recordPlayingMusic(occurrenceId, trackName)
        }

        override suspend fun acknowledgeWindow(occurrenceId: String) {
            eventRepository.acknowledgeWindow(occurrenceId)
        }

        override suspend fun createSnooze(occurrenceId: String, minutes: Int): AlarmRequest? {
            val snoozes = eventRepository.createSnoozeOccurrences(occurrenceId, minutes)
            val anchor = snoozes.firstOrNull() ?: return null
            val group = eventRepository.loadPlaybackGroup(anchor.id)
            if (group.isEmpty()) return null
            return AlarmRequest(
                occurrenceId = anchor.id,
                eventId = anchor.eventId.toString(),
                title = combinedTitle(group),
                outputMode = com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.merge(group.map { it.outputMode }),
                triggerAtEpochMs = anchor.triggerAtEpochMillis,
                ringDurationMs = group.maxOf { it.ringDurationMillis },
                intensity = if (group.any { it.alertMode == AlertMode.STRONG }) {
                    ReminderIntensity.STRONG
                } else {
                    ReminderIntensity.GENTLE
                },
            )
        }
    }

    private fun combinedTitle(occurrences: List<PlaybackOccurrence>): String {
        val titles = occurrences.map { it.title.ifBlank { "未命名提醒" } }.distinct()
        return when (titles.size) {
            0 -> "提醒时间到了"
            1 -> titles.first()
            2 -> titles.joinToString("、")
            else -> "${titles.first()} 等 ${titles.size} 项提醒"
        }
    }

    private fun PlaybackStopReason.toDomain(): RingStopReason = when (this) {
        PlaybackStopReason.USER_STOPPED -> RingStopReason.USER
        PlaybackStopReason.SNOOZED -> RingStopReason.SNOOZED
        PlaybackStopReason.DURATION_REACHED -> RingStopReason.TIMEOUT
        PlaybackStopReason.REPLACED_BY_NEW_REMINDER -> RingStopReason.REPLACED
        PlaybackStopReason.PLAYBACK_ERROR -> RingStopReason.ERROR
        PlaybackStopReason.TASK_COMPLETED -> RingStopReason.TASK_COMPLETED
    }
}
