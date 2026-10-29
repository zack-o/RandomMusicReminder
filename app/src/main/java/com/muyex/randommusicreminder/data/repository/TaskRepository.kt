package com.muyex.randommusicreminder.data.repository

import androidx.room.withTransaction
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.local.entity.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.tasks.*
import com.muyex.randommusicreminder.domain.time.ReminderClock
import java.time.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TaskRepository(private val database:AppDatabase,private val events:EventRepository,
    private val now:()->Long=System::currentTimeMillis,private val zone:()->ZoneId=ZoneId::systemDefault,
    private val cancelAlarm:(String)->Unit={},private val onChanged:suspend ()->Boolean={true}) {
    private val tasks=database.taskDao()
    private val occurrences=database.occurrenceDao()
    fun observeRecords()=tasks.observeRecords().map { rows -> rows.map { it.record() } }
    suspend fun exportRecords()=tasks.exportRecords().map { it.record() }
    suspend fun initializeTracking(eventId:Long)=database.withTransaction {
        events.getEvent(eventId)?.takeIf { it.event.trackCompletion }?.let { ensureLegacyInstances(it) }
    }

    suspend fun choices(eventId:Long,range:TaskChoiceRange=TaskChoiceRange.UPCOMING):TaskChoiceList=database.withTransaction {
        val details=events.getEvent(eventId) ?: error("事项已移除或在回收站")
        require(details.event.trackCompletion) { "请先编辑事项，开启“记录待办完成”" }
        ensureLegacyInstances(details)
        val dayStart=Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate().atStartOfDay(zone()).toInstant().toEpochMilli()
        val rows=occurrences.getForEvent(eventId)
        val instances=tasks.choices(eventId,dayStart,range!=TaskChoiceRange.UPCOMING,range==TaskChoiceRange.RECENT_OVERDUE)
        TaskChoiceList(eventId,details.event.title,range,instances.map { instance -> choice(details,instance,rows) }.sortedByDescending { it.ringing })
    }

    suspend fun sessionChoices(anchorId:String):TaskChoiceList=database.withTransaction {
        val sessionId=events.findSessionId(anchorId)
        val members=sessionId?.let { occurrences.getByIds(occurrences.getIdsForSession(it)) }.orEmpty().filter { it.state==OccurrenceState.RINGING }
        val result=mutableListOf<TaskChoice>()
        members.map { it.eventId }.distinct().forEach { id ->
            val details=events.getEvent(id) ?: return@forEach
            if(!details.event.trackCompletion) return@forEach
            ensureLegacyInstances(details)
            val all=occurrences.getForEvent(id)
            members.filter { it.eventId==id && it.kind!=OccurrenceKind.TEST }.mapNotNull { keyFor(it,details) }.distinct().forEach { key ->
                val instance=tasks.instance(id,key)
                if(instance!=null && tasks.latest(id,key)?.action!="DONE") result+=choice(details,instance,all)
            }
        }
        TaskChoiceList(0,"选择要完成的事项",TaskChoiceRange.UPCOMING,result.take(60))
    }

    suspend fun complete(choice:TaskChoice):TaskChangeResult=withContext(NonCancellable) {
        val changed=database.withTransaction {
            val details=events.getEvent(choice.eventId) ?: error("事项已移除或在回收站")
            require(details.event.trackCompletion && details.event.updatedAtEpochMillis==choice.eventUpdatedAt) { "事项已修改，请重新核对完成范围" }
            val instance=tasks.instance(choice.eventId,choice.key) ?: error("本次发生不存在，请刷新")
            require(instance.taskAtEpochMillis==choice.taskAt && instance.baseDate==choice.baseDate) { "排程已变化，请重新核对" }
            val latest=tasks.latest(choice.eventId,choice.key)
            if(latest?.action=="DONE") return@withTransaction false
            require((latest?.id ?: 0)==choice.stateRevision) { "完成状态已变化，请重新核对" }
            tasks.insertAction(TaskActionEntity(eventId=choice.eventId,taskKey=choice.key,baseDate=instance.baseDate,
                occurrenceLabel=instance.label,titleSnapshot=details.event.title,action="DONE",happenedAtEpochMillis=now()))
            val rows=occurrences.getForEvent(choice.eventId).filter { it.kind!=OccurrenceKind.TEST && keyFor(it,details)==choice.key }
            val pending=rows.filter { it.state==OccurrenceState.SCHEDULED }.map { it.id }
            pending.forEach(cancelAlarm)
            if(pending.isNotEmpty()) occurrences.updateStates(pending,OccurrenceState.CANCELLED)
            val ringing=rows.filter { it.state==OccurrenceState.RINGING }
            if(ringing.isNotEmpty()) occurrences.updateStates(ringing.map { it.id },OccurrenceState.COMPLETED)
            ringing.mapNotNull { it.sessionId }.distinct().forEach { session ->
                if(occurrences.getByIds(occurrences.getIdsForSession(session)).none { it.state==OccurrenceState.RINGING })
                    events.completeRingSession(session,RingStopReason.TASK_COMPLETED)
            }
            true
        }
        TaskChangeResult(changed,if(changed) onChanged() else true)
    }

    suspend fun reopen(actionId:Long):TaskChangeResult=withContext(NonCancellable) {
        database.withTransaction {
            val action=tasks.action(actionId) ?: error("完成记录不存在")
            require(action.action=="DONE" && tasks.latest(action.eventId,action.taskKey)?.id==actionId) { "此记录已有后续操作，请刷新后核对" }
            val event=events.getEvent(action.eventId)?.event ?: error("事项已移除或在回收站，记录保留但不能撤销")
            tasks.insertAction(action.copy(id=0,titleSnapshot=event.title,action="REOPEN",happenedAtEpochMillis=now()))
        }
        TaskChangeResult(true,onChanged())
    }

    private suspend fun choice(details:EventDetails,instance:TaskInstanceEntity,rows:List<ScheduledOccurrenceEntity>):TaskChoice {
        val matching=rows.filter { it.kind!=OccurrenceKind.TEST && keyFor(it,details)==instance.taskKey }
        return TaskChoice(instance.eventId,instance.taskKey,instance.baseDate,instance.label,instance.taskAtEpochMillis,details.event.title,
            details.event.updatedAtEpochMillis,matching.count { it.state==OccurrenceState.SCHEDULED },matching.any { it.state==OccurrenceState.RINGING },tasks.latest(instance.eventId,instance.taskKey)?.id ?: 0)
    }

    /** Old SHIFT keys are resolved from their stable UUID, never guessed from a display label. */
    private suspend fun keyFor(row:ScheduledOccurrenceEntity,details:EventDetails):String? {
        if(row.kind==OccurrenceKind.TEST) return null
        row.taskOccurrenceKey?.let { return it }
        if(row.kind==OccurrenceKind.SNOOZE) row.originalOccurrenceId?.takeIf { it!=row.id }?.let { id -> occurrences.getById(id)?.takeIf { it.kind==OccurrenceKind.ORIGINAL }?.let { original -> return keyFor(original,details) } }
        if(row.sourceLabel!=null || details.event.scheduleType==ScheduleType.SHIFT) {
            val slot=com.muyex.randommusicreminder.domain.planner.DayOverrides.allShiftTimes(details.event).firstOrNull { TaskIdentity.shiftOccurrenceId(row.eventId,row.reminderRuleId,row.baseDate,it.key)==(row.originalOccurrenceId ?: row.id) } ?: return null
            return TaskIdentity.shift(row.baseDate,slot.key)
        }
        return TaskIdentity.day(row.baseDate)
    }

    private suspend fun ensureLegacyInstances(details:EventDetails) {
        val event=details.event
        val boundary=event.completionTrackingStartedAt ?: return
        occurrences.getForEvent(event.id).filter { it.kind!=OccurrenceKind.TEST && it.state!=OccurrenceState.CANCELLED }.forEach { row ->
            val key=keyFor(row,details) ?: return@forEach
            if(row.taskOccurrenceKey==null) occurrences.setTaskKeyIfMissing(row.id,key)
            if(tasks.instance(event.id,key)!=null) return@forEach
            val available=com.muyex.randommusicreminder.domain.planner.DayOverrides.effectiveShiftDay(event,row.baseDate)?.times.orEmpty()+com.muyex.randommusicreminder.domain.planner.DayOverrides.allShiftTimes(event)
            val slot=available.firstOrNull { TaskIdentity.shift(row.baseDate,it.key)==key }
            val date=if(slot?.nextDay==true) row.baseDate.plusDays(1) else row.baseDate
            val clock=slot?.time ?: event.localTime
            val at=date.atTime(clock).atZone(zone()).toInstant().toEpochMilli()
            if(at<boundary) return@forEach
            val label=row.sourceLabel?.let { "${row.baseDate} · $it" } ?: "${row.baseDate} ${ReminderClock.format(clock)}"
            tasks.upsertInstances(listOf(TaskInstanceEntity(event.id,key,row.baseDate,label,at,now())))
        }
    }
}

private fun TaskActionEntity.record()=TaskActionRecord(id,eventId,taskKey,baseDate,occurrenceLabel,titleSnapshot,TaskActionType.valueOf(action),happenedAtEpochMillis)
