package com.muyex.randommusicreminder.alarm

import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.data.settings.SettingsRepository
import com.muyex.randommusicreminder.domain.model.AlertMode
import com.muyex.randommusicreminder.domain.model.OccurrenceState
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.ScheduleMode
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.planner.OccurrencePlanner
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduleReconciler(
    private val database: AppDatabase,
    private val eventRepository: EventRepository,
    private val alarmScheduler: AndroidAlarmScheduler,
    private val settingsRepository: SettingsRepository? = null,
    private val planner: OccurrencePlanner = OccurrencePlanner(),
    private val now: () -> Instant = Instant::now,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutex = Mutex()

    suspend fun reconcileAll() = mutex.withLock {
        val current = now()
        val notifyMissed = settingsRepository?.settings?.first()?.notifyMissedReminders ?: true
        val graceStart = if (notifyMissed) current.minus(MISSED_GRACE) else current
        val windowEnd = current.plus(PLANNING_HORIZON)
        // 异常退出或删除事件后可能没有对应 occurrence；历史不可永久显示为响铃中。
        database.ringHistoryDao().markInterrupted(current.minus(MAX_RING_RECOVERY_AGE).toEpochMilli())
        val occurrenceDao = database.occurrenceDao()
        val allOccurrences = occurrenceDao.getAll()
        val existing = allOccurrences.filter { it.state == OccurrenceState.SCHEDULED }

        val randomGraceStart = current.minusSeconds(60)
        val expired = existing.filter { it.triggerAtEpochMillis <
            (if (it.selectedLocalMinute != null && it.kind == OccurrenceKind.ORIGINAL) randomGraceStart else graceStart).toEpochMilli() }
        if (expired.isNotEmpty()) {
            expired.forEach { alarmScheduler.cancel(it.id) }
            occurrenceDao.updateStates(expired.map { it.id }, OccurrenceState.MISSED)
        }

        val staleRinging = allOccurrences.filter { occurrence ->
            occurrence.state == OccurrenceState.RINGING &&
                (occurrence.firedAtEpochMillis ?: occurrence.triggerAtEpochMillis) <
                current.minus(MAX_RING_RECOVERY_AGE).toEpochMilli()
        }

        val events = eventRepository.getEvents()
        val enabledEventIds = events.filter { it.event.enabled }.mapTo(hashSetOf()) { it.event.id }
        val disabledSnoozes = allOccurrences.filter { occurrence ->
            occurrence.kind != OccurrenceKind.ORIGINAL &&
                occurrence.state == OccurrenceState.SCHEDULED &&
                occurrence.eventId !in enabledEventIds
        }
        if (disabledSnoozes.isNotEmpty()) {
            disabledSnoozes.forEach { alarmScheduler.cancel(it.id) }
            occurrenceDao.updateStates(disabledSnoozes.map { it.id }, OccurrenceState.CANCELLED)
        }
        if (staleRinging.isNotEmpty()) {
            occurrenceDao.updateStates(staleRinging.map { it.id }, OccurrenceState.MISSED)
            staleRinging.mapNotNull { it.sessionId }.distinct().forEach { sessionId ->
                database.ringSessionDao().finish(
                    sessionId = sessionId,
                    state = RingSessionState.FAILED,
                    stopReason = RingStopReason.ERROR,
                )
            }
        }

        val expiredIds = expired.mapTo(hashSetOf()) { it.id }
        val staleIds = staleRinging.mapTo(hashSetOf()) { it.id }
        val relevantExisting = allOccurrences
            .filterNot { it.id in expiredIds || it.id in staleIds }
            .filter { it.kind == OccurrenceKind.ORIGINAL }
            .map { entity ->
                com.muyex.randommusicreminder.domain.model.ScheduledOccurrence(
                    id = entity.id,
                    eventId = entity.eventId,
                    reminderRuleId = entity.reminderRuleId,
                    baseDate = entity.baseDate,
                    triggerAtEpochMillis = entity.triggerAtEpochMillis,
                    kind = entity.kind,
                    originalOccurrenceId = entity.originalOccurrenceId,
                    snoozeCount = entity.snoozeCount,
                    state = entity.state,
                    scheduleMode = entity.scheduleMode,
                    sessionId = entity.sessionId,
                    scheduledAtEpochMillis = entity.scheduledAtEpochMillis,
                    firedAtEpochMillis = entity.firedAtEpochMillis,
                    selectedLocalMinute = entity.selectedLocalMinute, windowStartAtEpochMillis = entity.windowStartAtEpochMillis,
                    windowEndAtEpochMillis = entity.windowEndAtEpochMillis, windowBackup = entity.windowBackup, sourceLabel = entity.sourceLabel,taskOccurrenceKey=entity.taskOccurrenceKey,dayOverrideSignature=entity.dayOverrideSignature,
                )
            }
        val overrides = database.calendarDao().getAll().associate { java.time.LocalDate.parse(it.date) to com.muyex.randommusicreminder.domain.calendar.CalendarDayType.valueOf(it.dayType) }
        val allOriginals = allOccurrences.filter { it.kind == OccurrenceKind.ORIGINAL }.map { entity ->
            com.muyex.randommusicreminder.domain.model.ScheduledOccurrence(entity.id, entity.eventId, entity.reminderRuleId,
                entity.baseDate, entity.triggerAtEpochMillis, state=entity.state, selectedLocalMinute=entity.selectedLocalMinute,
                windowStartAtEpochMillis=entity.windowStartAtEpochMillis, windowEndAtEpochMillis=entity.windowEndAtEpochMillis,
                windowBackup=entity.windowBackup,sourceLabel=entity.sourceLabel,taskOccurrenceKey=entity.taskOccurrenceKey,dayOverrideSignature=entity.dayOverrideSignature)
        }
        val taskStates=database.taskDao().states().associate { com.muyex.randommusicreminder.domain.tasks.TaskIdentity(it.eventId,it.taskKey) to
            com.muyex.randommusicreminder.domain.tasks.TaskState(com.muyex.randommusicreminder.domain.tasks.TaskActionType.valueOf(it.action),it.happenedAtEpochMillis) }
        val desired = withContext(Dispatchers.Default) { planner.plan(
            events = events,
            fromInclusive = graceStart,
            untilExclusive = windowEnd,
            zoneId = zoneId(),
            overrides = overrides, existing = allOriginals, randomFromInclusive = randomGraceStart,taskStates=taskStates,
        ) }
        val plan = planner.reconcile(desired, relevantExisting)

        if (plan.cancellationIds.isNotEmpty()) {
            plan.cancellationIds.forEach(alarmScheduler::cancel)
            occurrenceDao.updateStates(plan.cancellationIds, OccurrenceState.CANCELLED)
        }
        eventRepository.upsertPlannedOccurrences(plan.upserts)
        eventRepository.retainTaskInstances(desired)

        // 同一毫秒触发的 occurrence 只注册一个 PendingIntent，Receiver 再原子认领整组。
        occurrenceDao.getAllScheduled()
            .filter { it.triggerAtEpochMillis >= (if (it.selectedLocalMinute != null && it.kind == OccurrenceKind.ORIGINAL) randomGraceStart else graceStart).toEpochMilli() }
            .groupBy { it.triggerAtEpochMillis }
            .toSortedMap()
            .forEach { (_, group) ->
                val rows = occurrenceDao.getPlaybackRowsAt(group.first().triggerAtEpochMillis)
                if (rows.isEmpty()) return@forEach
                val anchor = group.minBy { it.id }
                group.filterNot { it.id == anchor.id }.forEach { alarmScheduler.cancel(it.id) }
                val request = AlarmRequest(
                    occurrenceId = anchor.id,
                    eventId = anchor.eventId.toString(),
                    title = combinedTitle(rows.map { it.title }),
                    triggerAtEpochMs = anchor.triggerAtEpochMillis,
                    ringDurationMs = rows.maxOf { it.ringDurationSeconds } * 1_000L,
                    outputMode = com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.merge(rows.map { it.outputMode }),
                    intensity = if (rows.any { it.alertMode == AlertMode.STRONG }) {
                        ReminderIntensity.STRONG
                    } else {
                        ReminderIntensity.GENTLE
                    },
                )
                val result = alarmScheduler.schedule(request)
                val mode = when (result.mode) {
                    AlarmScheduleMode.EXACT -> ScheduleMode.EXACT
                    AlarmScheduleMode.INEXACT_FALLBACK -> ScheduleMode.INEXACT
                }
                group.forEach { eventRepository.markScheduled(it.id, mode) }
            }

        // 在 90 天窗口耗尽前由系统唤醒一次，保证长期不打开应用时重复提醒仍会续排。
        alarmScheduler.scheduleMaintenance(current.plus(MAINTENANCE_INTERVAL).toEpochMilli())
        occurrenceDao.pruneFinished(current.minus(FINISHED_RETENTION).toEpochMilli())
    }

    private fun combinedTitle(titles: List<String>): String {
        val unique = titles.map { it.ifBlank { "未命名提醒" } }.distinct()
        return when (unique.size) {
            0 -> "提醒时间到了"
            1 -> unique.first()
            2 -> unique.joinToString("、")
            else -> "${unique.first()} 等 ${unique.size} 项提醒"
        }
    }

    private companion object {
        val MISSED_GRACE: Duration = Duration.ofMinutes(15)
        val PLANNING_HORIZON: Duration = Duration.ofDays(90)
        val MAX_RING_RECOVERY_AGE: Duration = Duration.ofMinutes(30)
        val FINISHED_RETENTION: Duration = Duration.ofDays(30)
        val MAINTENANCE_INTERVAL: Duration = Duration.ofDays(60)
    }
}
