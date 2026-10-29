package com.muyex.randommusicreminder.data.repository

import androidx.room.withTransaction
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.domain.calendar.CalendarDayType
import com.muyex.randommusicreminder.domain.model.EventDetails
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.domain.tasks.*
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SchedulePreviewRepository(private val database:AppDatabase,private val events:EventRepository,
    private val now:()->Instant=Instant::now,private val zone:()->ZoneId=ZoneId::systemDefault) {
    suspend fun preview(draft:EventDetails):SchedulePreview {
        val generated=now();val timeZone=zone()
        val snapshot=database.withTransaction {
            val stored=if(draft.event.id>0) checkNotNull(events.getEvent(draft.event.id)) { "事项已移除或在回收站，请关闭编辑后重新选择" } else null
            PreviewSnapshot(stored,
                stored?.let { events.getOccurrencesForEvent(it.event.id) }.orEmpty(),
                database.calendarDao().getAll().associate { LocalDate.parse(it.date) to CalendarDayType.valueOf(it.dayType) },
                database.taskDao().states().filter { it.eventId==stored?.event?.id }.associate { TaskIdentity(it.eventId,it.taskKey) to TaskState(TaskActionType.valueOf(it.action),it.happenedAtEpochMillis) })
        }
        return withContext(Dispatchers.Default) { SchedulePreviewEngine().preview(draft,snapshot,generated,timeZone) }
    }
}
