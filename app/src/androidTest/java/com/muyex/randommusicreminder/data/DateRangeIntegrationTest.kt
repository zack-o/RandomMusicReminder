package com.muyex.randommusicreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.domain.tasks.TaskIdentity
import java.time.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DateRangeIntegrationTest {
    private val date=LocalDate.of(2030,1,1)
    private val zone=ZoneId.systemDefault()
    @Test fun savedRangeCancelsOnlySelectedDaysAndRestoreDoesNotReplayPastOrClearTasks()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        try {
            var now=date.atTime(6,0).atZone(zone).toInstant().toEpochMilli()
            val events=EventRepository(db,nowEpochMillis={now})
            val id=events.saveEvent(EventDetails(ReminderEvent(title="区间待办",scheduleType=ScheduleType.DAILY,localDate=date,localTime=LocalTime.of(9,0),trackCompletion=true),
                listOf(ReminderRule(offsetMinutes=-30),ReminderRule(offsetMinutes=0))))
            val original=checkNotNull(events.getEvent(id))
            val rows=OccurrencePlanner().plan(listOf(original),Instant.ofEpochMilli(now),date.plusDays(4).atStartOfDay(zone).toInstant(),zone)
            events.upsertPlannedOccurrences(rows)
            val scope=RangeAdjustmentScope(ScheduleType.DAILY,date)
            val pause=DayOverrideRanges.preview(scope,original.event.dayOverrides,date,date.plusDays(2),RangeAdjustmentAction.SKIP)
            events.saveEvent(original.copy(event=original.event.copy(dayOverrides=DayOverrideRanges.apply(pause,original.event.dayOverrides,scope))))
            events.upsertPlannedOccurrences(rows)
            assertTrue(events.getOccurrencesForEvent(id).filter { it.baseDate<=date.plusDays(2) }.all { it.state==OccurrenceState.CANCELLED })
            assertEquals(2,events.getOccurrencesForEvent(id).count { it.state==OccurrenceState.SCHEDULED })
            assertNotNull(db.taskDao().instance(id,TaskIdentity.day(date)))
            now=date.atTime(8,45).atZone(zone).toInstant().toEpochMilli()
            val stored=checkNotNull(events.getEvent(id));val restore=DayOverrideRanges.preview(scope,stored.event.dayOverrides,date,date.plusDays(2),RangeAdjustmentAction.RESTORE_SKIPS)
            events.saveEvent(stored.copy(event=stored.event.copy(dayOverrides=DayOverrideRanges.apply(restore,stored.event.dayOverrides,scope))))
            events.upsertPlannedOccurrences(rows)
            val today=events.getOccurrencesForEvent(id).filter { it.baseDate==date }
            assertEquals(OccurrenceState.CANCELLED,today.minBy { it.triggerAtEpochMillis }.state)
            assertEquals(OccurrenceState.SCHEDULED,today.maxBy { it.triggerAtEpochMillis }.state)
            assertTrue(db.taskDao().exportRecords().isEmpty())
        } finally { db.close() }
    }
    @Test fun rangeRemainsJsonEightDateEntriesAndPreservesExistingReplacementKeys() {
        val replacement=DayOverride(date.plusDays(1),DayOverrideKind.REPLACE_SHIFT,ShiftDay("临时夜班",times=listOf(ShiftTime(LocalTime.of(20,0)))))
        val scope=RangeAdjustmentScope(ScheduleType.SHIFT,date)
        val plan=DayOverrideRanges.preview(scope,listOf(replacement),date,date.plusDays(2),RangeAdjustmentAction.SKIP)
        val event=EventDetails(ReminderEvent(title="区间备份",scheduleType=ScheduleType.SHIFT,localDate=date,localTime=LocalTime.NOON,shiftCycle=ShiftCycle.template(),dayOverrides=plan.after),listOf(ReminderRule(offsetMinutes=0)))
        val root=JSONObject(BackupCodec.encode(BackupPackage(events=listOf(BackupEvent(event)))))
        assertEquals(8,root.getInt("version"))
        val decoded=BackupCodec.decode(root.toString()).events.single().details.event.dayOverrides
        assertEquals(plan.after,decoded);assertEquals(replacement,decoded.single { it.date==replacement.date })
        assertEquals(3,root.getJSONArray("events").getJSONObject(0).getJSONArray("dayOverrides").length())
    }
}
