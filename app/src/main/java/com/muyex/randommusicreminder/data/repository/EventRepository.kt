package com.muyex.randommusicreminder.data.repository

import androidx.room.withTransaction
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.local.entity.EventEntity
import com.muyex.randommusicreminder.data.local.entity.EventTrackCrossRef
import com.muyex.randommusicreminder.data.local.entity.ReminderRuleEntity
import com.muyex.randommusicreminder.data.local.entity.ScheduledOccurrenceEntity
import com.muyex.randommusicreminder.data.local.entity.RingHistoryEntity
import com.muyex.randommusicreminder.domain.ringing.RingHistoryEntry
import com.muyex.randommusicreminder.domain.model.ClaimedRingSession
import com.muyex.randommusicreminder.domain.model.EventDetails
import com.muyex.randommusicreminder.domain.model.OccurrenceState
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.PlannedOccurrence
import com.muyex.randommusicreminder.domain.model.PlaybackOccurrence
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.model.ScheduleMode
import com.muyex.randommusicreminder.domain.model.ScheduledOccurrence
import com.muyex.randommusicreminder.domain.model.ScheduleType
import com.muyex.randommusicreminder.domain.ringing.RingCoordinator
import com.muyex.randommusicreminder.domain.ringing.SnoozePolicy
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EventRepository(
    private val database: AppDatabase,
    private val ringCoordinator: RingCoordinator = RingCoordinator(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val cancelScheduledAlarm: (String) -> Unit = {},
) {
    private val eventDao = database.eventDao()
    private val occurrenceDao = database.occurrenceDao()
    private val ringSessionDao = database.ringSessionDao()
    private val snoozePolicy = SnoozePolicy()
    private val random = java.security.SecureRandom()

    fun observeEvents(): Flow<List<EventDetails>> {
        return eventDao.observeAllWithRelations().map { rows -> rows.map { it.toDomain() } }
    }

    suspend fun getEvents(): List<EventDetails> = eventDao.getAllWithRelations().map { it.toDomain() }

    suspend fun getEventsIncludingDeleted(): List<EventDetails> = eventDao.getAllIncludingDeleted().map { it.toDomain() }

    fun observeDeletedEvents(): Flow<List<EventDetails>> = eventDao.observeDeleted().map { rows -> rows.map { it.toDomain() } }

    fun observeScheduledOccurrences(): Flow<List<ScheduledOccurrence>> =
        occurrenceDao.observeScheduled().map { rows -> rows.map { it.toDomain() } }

    fun observeRingHistory(): Flow<List<RingHistoryEntry>> =
        database.ringHistoryDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun findSessionId(occurrenceId: String): String? =
        occurrenceDao.getById(occurrenceId)?.sessionId
            ?: database.ringHistoryDao().getForOccurrence(occurrenceId)?.sessionId

    suspend fun recordPlayingMusic(occurrenceId: String, musicName: String) {
        findSessionId(occurrenceId)?.let { database.ringHistoryDao().updateMusic(it, musicName) }
    }

    suspend fun createTestOccurrence(eventId: Long): ScheduledOccurrence = database.withTransaction {
        val details = getEvent(eventId) ?: error("提醒不存在")
        require(details.event.enabled) { "请先启用这个提醒" }
        val rule = details.activeRules.maxByOrNull { it.offsetMinutes } ?: error("没有可测试的提醒点")
        val previous = occurrenceDao.getForEvent(eventId).filter {
            it.kind == OccurrenceKind.TEST && it.state == OccurrenceState.SCHEDULED
        }
        previous.forEach { cancelScheduledAlarm(it.id) }
        if (previous.isNotEmpty()) {
            occurrenceDao.updateStates(previous.map { it.id }, OccurrenceState.CANCELLED)
        }
        val triggerAt = nowEpochMillis() + 10_000L
        val occurrence = ScheduledOccurrenceEntity(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            reminderRuleId = rule.id,
            baseDate = Instant.ofEpochMilli(triggerAt).atZone(ZoneId.systemDefault()).toLocalDate(),
            triggerAtEpochMillis = triggerAt,
            kind = OccurrenceKind.TEST,
        )
        occurrenceDao.upsertAll(listOf(occurrence))
        occurrence.toDomain()
    }

    suspend fun cancelOccurrence(occurrenceId: String) {
        cancelScheduledAlarm(occurrenceId)
        occurrenceDao.updateState(occurrenceId, OccurrenceState.CANCELLED)
    }

    suspend fun getEvent(eventId: Long): EventDetails? = eventDao.getWithRelations(eventId)?.toDomain()

    suspend fun saveEvent(details: EventDetails): Long = database.withTransaction {
        validate(details)
        val now = nowEpochMillis()
        val supplied = details.event
        require(supplied.deletedAtEpochMillis == null) { "请先从回收站恢复事项" }
        val storedRelations = supplied.id.takeIf { it > 0 }
            ?.let { eventDao.getWithRelations(it) ?: error("Event $it does not exist") }
        val storedEvent = storedRelations?.event?.toDomain()
        val incoming=com.muyex.randommusicreminder.domain.planner.SchedulePreparation.prepare(supplied,storedEvent,Instant.ofEpochMilli(now),ZoneId.systemDefault()) { random.nextLong() }
        val adjustmentsJson=com.muyex.randommusicreminder.data.transfer.DayOverrideCodec.encode(incoming.dayOverrides)
        val adjustmentBarriersJson=com.muyex.randommusicreminder.data.transfer.DayOverrideCodec.encodeBarriers(incoming.dayOverrideBarriers)
        if (supplied.id > 0) cancelPendingAlarmsForEvent(supplied.id)
        val eventId = if (incoming.id == 0L) {
            eventDao.insertEvent(
                EventEntity(
                    id = 0,
                    title = incoming.title.trim(),
                    note = incoming.note.trim(),
                    enabled = incoming.enabled,
                    scheduleType = incoming.scheduleType,
                    localDate = incoming.localDate,
                    localTime = incoming.localTime,
                    weekdaysMask = incoming.weekdaysMask,
                    musicPoolMode = incoming.musicPoolMode,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    holidayPolicy = incoming.holidayPolicy,
                    intervalDays = incoming.intervalDays, missingDayPolicy = incoming.missingDayPolicy, endDate = incoming.endDate,
                    timingMode = incoming.timingMode, windowEndTime = incoming.windowEndTime,
                    windowCrossesMidnight = incoming.windowCrossesMidnight, windowAlertMode = incoming.windowAlertMode,
                    windowDurationSeconds = incoming.windowDurationSeconds, windowSnoozeMinutes = incoming.windowSnoozeMinutes,
                    windowBackupEnabled = incoming.windowBackupEnabled, hideWindowSelection = incoming.hideWindowSelection,
                    randomSeed = incoming.randomSeed, timingRevision = incoming.timingRevision,
                    windowFirstNotBeforeEpochMillis = incoming.windowFirstNotBeforeEpochMillis,
                    shiftCycleJson = com.muyex.randommusicreminder.data.transfer.ShiftCycleCodec.encode(incoming.shiftCycle),
                    tagsJson = com.muyex.randommusicreminder.data.transfer.EventTagsCodec.encode(incoming.tags),
                    trackCompletion=incoming.trackCompletion,completionTrackingStartedAt=incoming.completionTrackingStartedAt,
                    dayOverridesJson=adjustmentsJson,dayOverrideBarriersJson=adjustmentBarriersJson,
                    outputMode=incoming.outputMode,hapticPreset=incoming.haptics.preset,hapticStrength=incoming.haptics.strength,
                    hapticGapMillis=incoming.haptics.repeatGapMillis,hapticUseMusicRhythm=incoming.haptics.useMusicRhythm,
                ),
            )
        } else {
            val stored = checkNotNull(storedRelations).event
            eventDao.updateEvent(
                EventEntity(
                    id = incoming.id,
                    title = incoming.title.trim(),
                    note = incoming.note.trim(),
                    enabled = incoming.enabled,
                    scheduleType = incoming.scheduleType,
                    localDate = incoming.localDate,
                    localTime = incoming.localTime,
                    weekdaysMask = incoming.weekdaysMask,
                    musicPoolMode = incoming.musicPoolMode,
                    createdAtEpochMillis = stored.createdAtEpochMillis,
                    updatedAtEpochMillis = now,
                    holidayPolicy = incoming.holidayPolicy,
                    intervalDays = incoming.intervalDays, missingDayPolicy = incoming.missingDayPolicy, endDate = incoming.endDate,
                    timingMode = incoming.timingMode, windowEndTime = incoming.windowEndTime,
                    windowCrossesMidnight = incoming.windowCrossesMidnight, windowAlertMode = incoming.windowAlertMode,
                    windowDurationSeconds = incoming.windowDurationSeconds, windowSnoozeMinutes = incoming.windowSnoozeMinutes,
                    windowBackupEnabled = incoming.windowBackupEnabled, hideWindowSelection = incoming.hideWindowSelection,
                    randomSeed = incoming.randomSeed, timingRevision = incoming.timingRevision,
                    windowFirstNotBeforeEpochMillis = incoming.windowFirstNotBeforeEpochMillis,
                    shiftCycleJson = com.muyex.randommusicreminder.data.transfer.ShiftCycleCodec.encode(incoming.shiftCycle),
                    tagsJson = com.muyex.randommusicreminder.data.transfer.EventTagsCodec.encode(incoming.tags),
                    trackCompletion=incoming.trackCompletion,completionTrackingStartedAt=incoming.completionTrackingStartedAt,
                    dayOverridesJson=adjustmentsJson,dayOverrideBarriersJson=adjustmentBarriersJson,
                    outputMode=incoming.outputMode,hapticPreset=incoming.haptics.preset,hapticStrength=incoming.haptics.strength,
                    hapticGapMillis=incoming.haptics.repeatGapMillis,hapticUseMusicRhythm=incoming.haptics.useMusicRhythm,
                    resumedAfterEpochMillis = stored.resumedAfterEpochMillis,
                ),
            )
            incoming.id
        }

        val existingRules = storedRelations?.rules.orEmpty()
        val existingRuleIds = existingRules.mapTo(hashSetOf()) { it.id }
        val existingRulesByOffset = existingRules.associateBy { it.offsetMinutes }
        val retainedRuleIds = mutableSetOf<Long>()
        details.rules.forEach { rule ->
            val targetRuleId = when {
                rule.id > 0 -> rule.id
                else -> existingRulesByOffset[rule.offsetMinutes]?.id ?: 0L
            }
            val entity = ReminderRuleEntity(
                id = targetRuleId,
                eventId = eventId,
                offsetMinutes = rule.offsetMinutes,
                alertMode = rule.alertMode,
                ringDurationSeconds = rule.ringDurationSeconds,
                snoozeMinutes = rule.snoozeMinutes,
            )
            if (targetRuleId == 0L) {
                retainedRuleIds += eventDao.insertRule(entity)
            } else {
                require(targetRuleId in existingRuleIds) {
                    "Rule $targetRuleId does not belong to event $eventId"
                }
                eventDao.updateRule(entity)
                retainedRuleIds += targetRuleId
            }
        }
        val changedDates=com.muyex.randommusicreminder.domain.planner.DayOverrides.changedDates(storedEvent?.dayOverrides.orEmpty(),incoming.dayOverrides)
        val obsolete=occurrenceDao.getForEvent(eventId).filter { it.baseDate in changedDates && it.kind!=OccurrenceKind.TEST && it.state==OccurrenceState.SCHEDULED }
        obsolete.forEach { cancelScheduledAlarm(it.id) }
        if(obsolete.isNotEmpty()) occurrenceDao.updateStates(obsolete.map { it.id },OccurrenceState.CANCELLED)
        val removedRuleIds = existingRuleIds - retainedRuleIds
        if (removedRuleIds.isNotEmpty()) {
            occurrenceDao.retainWindowRecords(eventId, removedRuleIds.toList(), retainedRuleIds.min())
            eventDao.deleteRules(eventId, removedRuleIds.toList())
        }

        eventDao.deleteTrackRefs(eventId)
        val refs = details.customTrackIds
            .filter { it > 0 }
            .distinct()
            .map { EventTrackCrossRef(eventId, it) }
        if (refs.isNotEmpty()) eventDao.insertTrackRefs(refs)
        eventId
    }

    suspend fun setEnabled(eventId: Long, enabled: Boolean): Boolean {
        cancelPendingAlarmsForEvent(eventId)
        return eventDao.setEnabled(eventId, enabled, nowEpochMillis()) > 0
    }

    suspend fun deleteEvent(eventId: Long): Boolean = database.withTransaction {
        val stored = eventDao.getWithRelations(eventId)?.event ?: return@withTransaction false
        val now = maxOf(nowEpochMillis(),stored.updatedAtEpochMillis + 1)
        eventDao.updateEvent(stored.copy(enabled=false,deletedAtEpochMillis=now,updatedAtEpochMillis=now))
        cancelPendingAlarmsForEvent(eventId)
        val pending = occurrenceDao.getForEvent(eventId).filter { it.state == OccurrenceState.SCHEDULED }.map { it.id }
        if(pending.isNotEmpty()) occurrenceDao.updateStates(pending,OccurrenceState.CANCELLED)
        true
    }

    suspend fun restoreEvent(eventId: Long): Boolean = database.withTransaction {
        val stored = eventDao.getAnyWithRelations(eventId)?.event ?: return@withTransaction false
        if(stored.deletedAtEpochMillis == null) return@withTransaction false
        val now = maxOf(nowEpochMillis(),stored.updatedAtEpochMillis + 1)
        eventDao.updateEvent(stored.copy(enabled=false,deletedAtEpochMillis=null,updatedAtEpochMillis=now,resumedAfterEpochMillis=now+1))
        true
    }

    suspend fun permanentlyDeleteEvents(eventIds: List<Long>): Int = database.withTransaction {
        val ids = eventIds.distinct()
        require(ids.isNotEmpty()) { "请选择回收站事项" }
        ids.forEach { id -> require(eventDao.getAnyWithRelations(id)?.event?.deletedAtEpochMillis != null) { "事项已恢复或移除，请刷新回收站" } }
        ids.sumOf { id -> cancelPendingAlarmsForEvent(id); eventDao.deleteEvent(id) }
    }

    suspend fun renameTag(old: String, new: String): Int = changeTag(old,com.muyex.randommusicreminder.domain.organizer.EventTags.normalize(listOf(new)).single())
    suspend fun removeTag(tag: String): Int = changeTag(tag,null)

    private suspend fun changeTag(old: String, replacement: String?): Int = database.withTransaction {
        val tags = com.muyex.randommusicreminder.domain.organizer.EventTags
        val key = tags.key(old)
        var count=0
        eventDao.getAllIncludingDeleted().forEach { row ->
            val current = row.toDomain().event.tags
            if(current.any { tags.key(it)==key }) {
                val changed = tags.normalize(current.mapNotNull { if(tags.key(it)==key) replacement else it })
                if(changed != current) {
                    eventDao.updateEvent(row.event.copy(tagsJson=com.muyex.randommusicreminder.data.transfer.EventTagsCodec.encode(changed),
                        updatedAtEpochMillis=maxOf(nowEpochMillis(),row.event.updatedAtEpochMillis+1)))
                    count++
                }
            }
        }
        count
    }

    suspend fun getOccurrencesForEvent(eventId: Long): List<ScheduledOccurrence> {
        return occurrenceDao.getForEvent(eventId).map { it.toDomain() }
    }

    suspend fun getSchedulableOccurrences(fromEpochMillis: Long): List<ScheduledOccurrence> {
        return occurrenceDao.getSchedulable(fromEpochMillis).map { it.toDomain() }
    }

    suspend fun upsertPlannedOccurrences(occurrences: List<PlannedOccurrence>) = database.withTransaction {
        if(occurrences.isEmpty()) return@withTransaction
        val enabled=eventDao.getEnabledSchedulingStates().associateBy { it.id }
        val dayConfigs=enabled.mapValues { (_,row)->com.muyex.randommusicreminder.data.transfer.DayOverrideCodec.decode(row.dayOverridesJson) to com.muyex.randommusicreminder.data.transfer.DayOverrideCodec.decodeBarriers(row.dayOverrideBarriersJson) }
        val states=database.taskDao().states().associate { com.muyex.randommusicreminder.domain.tasks.TaskIdentity(it.eventId,it.taskKey) to
            com.muyex.randommusicreminder.domain.tasks.TaskState(com.muyex.randommusicreminder.domain.tasks.TaskActionType.valueOf(it.action),it.happenedAtEpochMillis) }
        val terminal=setOf(OccurrenceState.COMPLETED,OccurrenceState.MISSED,OccurrenceState.RINGING)
        val accepted=occurrences.filter { planned ->
            val event=enabled[planned.eventId]
            val boundary=event?.resumedAfterEpochMillis
            event!=null && dayConfigs[planned.eventId]?.let { (values,barriers)->com.muyex.randommusicreminder.domain.planner.DayOverrides.permits(values,barriers,planned.baseDate,planned.triggerAtEpochMillis,planned.windowStartAtEpochMillis,planned.dayOverrideSignature) } == true &&
                !(boundary!=null && (planned.triggerAtEpochMillis<boundary || (planned.windowStartAtEpochMillis!=null && planned.windowStartAtEpochMillis<boundary))) &&
                (planned.taskOccurrenceKey?.let { states[com.muyex.randommusicreminder.domain.tasks.TaskIdentity(planned.eventId,it)]?.permits(planned.triggerAtEpochMillis,planned.windowStartAtEpochMillis) } != false) &&
                occurrenceDao.getById(planned.id)?.state !in terminal
        }
        occurrenceDao.upsertAll(accepted.map { planned -> ScheduledOccurrenceEntity(id=planned.id,eventId=planned.eventId,reminderRuleId=planned.reminderRuleId,
            baseDate=planned.baseDate,triggerAtEpochMillis=planned.triggerAtEpochMillis,selectedLocalMinute=planned.selectedLocalMinute,
            windowStartAtEpochMillis=planned.windowStartAtEpochMillis,windowEndAtEpochMillis=planned.windowEndAtEpochMillis,windowBackup=planned.windowBackup,
            sourceLabel=planned.sourceLabel,taskOccurrenceKey=planned.taskOccurrenceKey,dayOverrideSignature=planned.dayOverrideSignature) })
        retainTaskInstances(accepted)
    }

    suspend fun retainTaskInstances(planned:List<PlannedOccurrence>) = database.withTransaction {
        val enabled=eventDao.getEnabledSchedulingStates().associateBy { it.id }
        val instances=planned.distinctBy { it.eventId to it.taskOccurrenceKey }.mapNotNull { item ->
            val event=enabled[item.eventId] ?: return@mapNotNull null
            if(!com.muyex.randommusicreminder.domain.planner.DayOverrides.permits(
                com.muyex.randommusicreminder.data.transfer.DayOverrideCodec.decode(event.dayOverridesJson),com.muyex.randommusicreminder.data.transfer.DayOverrideCodec.decodeBarriers(event.dayOverrideBarriersJson),
                item.baseDate,item.triggerAtEpochMillis,item.windowStartAtEpochMillis,item.dayOverrideSignature)) return@mapNotNull null
            val key=item.taskOccurrenceKey ?: return@mapNotNull null
            val at=item.taskAtEpochMillis ?: return@mapNotNull null
            if(!event.trackCompletion || at<(event.completionTrackingStartedAt ?: Long.MAX_VALUE) || database.taskDao().latest(item.eventId,key)?.action=="DONE") return@mapNotNull null
            com.muyex.randommusicreminder.data.local.entity.TaskInstanceEntity(item.eventId,key,item.baseDate,item.taskLabel.orEmpty(),at,
                database.taskDao().instance(item.eventId,key)?.createdAtEpochMillis ?: nowEpochMillis())
        }
        if(instances.isNotEmpty()) database.taskDao().upsertInstances(instances)
    }

    suspend fun markScheduled(id: String, mode: ScheduleMode): Boolean {
        return occurrenceDao.markScheduled(id, mode, nowEpochMillis()) > 0
    }

    suspend fun loadPlaybackOccurrence(occurrenceId: String): PlaybackOccurrence? {
        occurrenceDao.getById(occurrenceId)?.let { expireLateWindowAt(it.triggerAtEpochMillis) }
        return occurrenceDao.getPlaybackRow(occurrenceId)?.toDomain()
    }

    suspend fun loadPlaybackGroup(anchorOccurrenceId: String): List<PlaybackOccurrence> {
        val anchor = occurrenceDao.getById(anchorOccurrenceId) ?: return emptyList()
        if (anchor.state != OccurrenceState.SCHEDULED) return emptyList()
        expireLateWindowAt(anchor.triggerAtEpochMillis)
        return occurrenceDao.getPlaybackRowsAt(anchor.triggerAtEpochMillis).map { it.toDomain() }
    }

    private suspend fun expireLateWindowAt(triggerAt: Long) {
        // 系统广播可能在长时间延迟后直接到达，不一定先经过整体重排。
        val late = occurrenceDao.getLateWindowsAt(triggerAt, nowEpochMillis() - 60_000L)
        if (late.isNotEmpty()) {
            late.forEach { cancelScheduledAlarm(it.id) }
            occurrenceDao.updateStates(late.map { it.id }, OccurrenceState.MISSED)
        }
    }

    suspend fun getScheduledOccurrence(occurrenceId: String): ScheduledOccurrence? {
        return occurrenceDao.getById(occurrenceId)?.toDomain()
    }

    suspend fun claimSimultaneous(
        anchorOccurrenceId: String,
        selectedTrackId: Long?,
        sessionId: String = UUID.randomUUID().toString(),
        requireDue:Boolean=false,
    ): ClaimedRingSession? = database.withTransaction {
        val anchor = occurrenceDao.getById(anchorOccurrenceId)
            ?.takeIf { it.state == OccurrenceState.SCHEDULED }
            ?: return@withTransaction null
        // 改时刻后稳定ID继续保留；旧广播不可提前认领已经移到未来的同ID实例。
        if(requireDue && anchor.triggerAtEpochMillis>nowEpochMillis()) return@withTransaction null
        val candidates = occurrenceDao.getPlaybackRowsAt(anchor.triggerAtEpochMillis).map { it.toDomain() }
        val claim = ringCoordinator.claimSimultaneous(anchorOccurrenceId, candidates)
            ?: return@withTransaction null
        val now = nowEpochMillis()
        val session = ringCoordinator.createSession(sessionId, claim, selectedTrackId, now)
        ringSessionDao.insert(session.toEntity())
        val sources = occurrenceDao.getByIds(claim.occurrenceIds)
        val kind = sources.map { it.kind }.distinct().singleOrNull() ?: OccurrenceKind.ORIGINAL
        database.ringHistoryDao().insert(
            RingHistoryEntity(
                sessionId = sessionId,
                anchorOccurrenceId = anchorOccurrenceId,
                title = candidates.map { it.title }.distinct().joinToString("、"),
                eventCount = candidates.map { it.eventId }.distinct().size,
                scheduledAtEpochMillis = claim.scheduledTriggerAtEpochMillis,
                startedAtEpochMillis = now,
                musicName = selectedTrackId?.let { database.musicDao().getById(it)?.displayName },
                alertMode = claim.alertMode,
                kind = kind,
                state = RingSessionState.ACTIVE,
                timingSummary = sources.filter { it.selectedLocalMinute != null }.map { item ->
                    val format = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                    fun clock(value: Long?) = value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(format) }.orEmpty()
                    "${if (item.windowBackup) "固定补充" else "随机主提醒"} ${clock(item.windowStartAtEpochMillis)}–${clock(item.windowEndAtEpochMillis)}；选定 ${clock(item.triggerAtEpochMillis)}"
                }.plus(sources.mapNotNull { it.sourceLabel }).plus(candidates.filter { it.outputMode != com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.MUSIC }.map { "${it.title}：${it.outputMode.label}" }).distinct().joinToString("\n").ifBlank { null },
            ),
        )
        val claimedCount = occurrenceDao.markRinging(claim.occurrenceIds, sessionId, now)
        check(claimedCount == claim.occurrenceIds.size) { "Occurrence claim was not atomic" }
        ClaimedRingSession(claim, session, candidates.filter { it.occurrenceId in claim.occurrenceIds })
    }

    suspend fun completeRingSession(sessionId: String, reason: RingStopReason): Boolean {
        return database.withTransaction {
            val sessionState = if (reason == RingStopReason.ERROR) {
                RingSessionState.FAILED
            } else {
                RingSessionState.COMPLETED
            }
            val changed = ringSessionDao.finish(sessionId, sessionState, reason)
            if (changed == 0) return@withTransaction false
            database.ringHistoryDao().finish(sessionId, sessionState, reason, nowEpochMillis())
            val occurrenceIds = occurrenceDao.getIdsForSession(sessionId)
            if (occurrenceIds.isNotEmpty()) {
                occurrenceDao.updateSessionOccurrences(
                    occurrenceIds = occurrenceIds,
                    sessionId = sessionId,
                    state = OccurrenceState.COMPLETED,
                )
            }
            true
        }
    }

    suspend fun createSnoozeOccurrence(
        sourceOccurrenceId: String,
        minutes: Int,
    ): ScheduledOccurrence? = createSnoozeOccurrences(sourceOccurrenceId, minutes).firstOrNull()

    suspend fun createSnoozeOccurrences(
        sourceOccurrenceId: String,
        minutes: Int,
    ): List<ScheduledOccurrence> = database.withTransaction {
        val source = occurrenceDao.getById(sourceOccurrenceId) ?: return@withTransaction emptyList()
        val sessionActive=source.sessionId?.let { ringSessionDao.getById(it)?.state==RingSessionState.ACTIVE } == true
        if(source.state !in setOf(OccurrenceState.SCHEDULED,OccurrenceState.RINGING) && !sessionActive) return@withTransaction emptyList()
        val sources = source.sessionId
            ?.let { occurrenceDao.getByIds(occurrenceDao.getIdsForSession(it)) }
            .orEmpty()
            .ifEmpty { listOf(source) }
        val triggerAt = nowEpochMillis() + minutes * 60_000L
        val snoozes = sources.filter { it.state==OccurrenceState.SCHEDULED || it.state==OccurrenceState.RINGING }.mapNotNull { item ->
            if (item.kind == OccurrenceKind.TEST) return@mapNotNull null
            val details = eventDao.getWithRelations(item.eventId)?.takeIf { it.event.enabled } ?: return@mapNotNull null
            val current=details.event.toDomain()
            if(!com.muyex.randommusicreminder.domain.planner.DayOverrides.permits(current.dayOverrides,current.dayOverrideBarriers,item.baseDate,triggerAt,item.windowStartAtEpochMillis,item.dayOverrideSignature)) return@mapNotNull null
            val rule = details.rules.firstOrNull { it.id == item.reminderRuleId } ?: return@mapNotNull null
            val snooze = if (details.event.timingMode == com.muyex.randommusicreminder.domain.model.TimingMode.RANDOM_WINDOW)
                details.event.windowSnoozeMinutes else rule.snoozeMinutes
            if (snooze == null) return@mapNotNull null
            val nextCount = snoozePolicy.nextCount(item.snoozeCount, minutes) ?: return@mapNotNull null
            val originalId = item.originalOccurrenceId ?: item.id
            ScheduledOccurrenceEntity(
                id = UUID.nameUUIDFromBytes(
                    "$originalId:snooze:$nextCount".toByteArray(StandardCharsets.UTF_8),
                ).toString(),
                eventId = item.eventId,
                reminderRuleId = item.reminderRuleId,
                baseDate = item.baseDate,
                triggerAtEpochMillis = triggerAt,
                kind = OccurrenceKind.SNOOZE,
                originalOccurrenceId = originalId,
                snoozeCount = nextCount,
                selectedLocalMinute = item.selectedLocalMinute, windowStartAtEpochMillis = item.windowStartAtEpochMillis,
                windowEndAtEpochMillis = item.windowEndAtEpochMillis, windowBackup = item.windowBackup, sourceLabel = item.sourceLabel,taskOccurrenceKey=item.taskOccurrenceKey,dayOverrideSignature=item.dayOverrideSignature,
            )
        }
        if (snoozes.isNotEmpty()) occurrenceDao.upsertAll(snoozes)
        snoozes.map { it.toDomain() }
    }

    suspend fun markOccurrenceCompleted(occurrenceId: String): Boolean {
        return occurrenceDao.updateState(occurrenceId, OccurrenceState.COMPLETED) > 0
    }

    /** 只有用户明确确认收到才取消同一窗口的待触发补充；普通停止不调用。 */
    suspend fun acknowledgeWindow(occurrenceId: String): Int = database.withTransaction {
        val source = occurrenceDao.getById(occurrenceId) ?: return@withTransaction 0
        val sources = source.sessionId?.let { occurrenceDao.getByIds(occurrenceDao.getIdsForSession(it)) }
            .orEmpty().ifEmpty { listOf(source) }
        val windows = sources.filter { it.selectedLocalMinute != null }.map { it.eventId to it.baseDate }.distinct()
        var count = 0
        windows.forEach { (eventId, date) ->
            occurrenceDao.getForEvent(eventId).filter { it.baseDate == date && it.windowBackup && it.state == OccurrenceState.SCHEDULED }
                .forEach { item -> cancelScheduledAlarm(item.id); count += occurrenceDao.updateState(item.id, OccurrenceState.COMPLETED) }
        }
        count
    }

    private suspend fun cancelPendingAlarmsForEvent(eventId: Long) {
        occurrenceDao.getForEvent(eventId)
            .filter { it.state == OccurrenceState.SCHEDULED }
            .forEach { cancelScheduledAlarm(it.id) }
    }

    private fun validate(details: EventDetails) {
        com.muyex.randommusicreminder.domain.transfer.TransferRules.validateEvent(details)
        val event = details.event
        require(event.title.isNotBlank()) { "Event title must not be blank" }
        require(details.rules.isNotEmpty()) { "An event needs at least one reminder rule" }
        require(details.rules.map { it.offsetMinutes }.distinct().size == details.rules.size) {
            "Reminder offsets must be unique within an event"
        }
        require(details.rules.all { it.offsetMinutes <= 0 }) {
            "Reminder offsets must be on time or earlier than the event"
        }
        require(details.rules.all { it.ringDurationSeconds in 5..1_800 }) {
            "Ring duration must be between 5 and 1800 seconds"
        }
        require(details.rules.all { it.snoozeMinutes == null || it.snoozeMinutes in setOf(5, 10, 15) }) {
            "Snooze duration must be 5, 10 or 15 minutes"
        }
        require(event.musicPoolMode != com.muyex.randommusicreminder.domain.model.MusicPoolMode.CUSTOM ||
            details.customTrackIds.isNotEmpty()
        ) { "Custom music pool needs at least one track" }
        if (event.scheduleType == ScheduleType.WEEKLY) {
            require(event.weekdaysMask and 0b111_1111 != 0) { "Weekly event needs at least one weekday" }
            require(event.weekdaysMask and 0b111_1111.inv() == 0) { "Weekday mask contains unsupported bits" }
        }
    }
}
