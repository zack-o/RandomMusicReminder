package com.muyex.randommusicreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.*
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.domain.tasks.*
import com.muyex.randommusicreminder.domain.transfer.TransferRules
import java.time.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TaskCompletionIntegrationTest {
    private val date=LocalDate.of(2030,1,1)
    private val zone=ZoneId.systemDefault()
    private fun at(hour:Int,minute:Int=0)=date.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
    private class Fixture(val db:AppDatabase,var now:Long,val zone:ZoneId) {
        val cancelled=mutableListOf<String>()
        val events=EventRepository(db,nowEpochMillis={now},cancelScheduledAlarm={cancelled+=it})
        val tasks=TaskRepository(db,events,{now},{zone},{cancelled+=it})
        suspend fun plan():List<PlannedOccurrence> {
            val result=OccurrencePlanner().plan(events.getEvents(),Instant.ofEpochMilli(now),Instant.ofEpochMilli(now).plusSeconds(2*86400),zone)
            events.upsertPlannedOccurrences(result)
            return result
        }
    }
    private suspend fun fixture(block:suspend (Fixture)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        try { block(Fixture(db,at(6),zone)) } finally { db.close() }
    }
    private fun details(title:String="交接事项",tracking:Boolean=true)=EventDetails(
        ReminderEvent(title=title,scheduleType=ScheduleType.DAILY,localDate=date,localTime=LocalTime.of(9,0),trackCompletion=tracking),
        listOf(ReminderRule(offsetMinutes=-30,snoozeMinutes=10),ReminderRule(offsetMinutes=0,snoozeMinutes=10)))

    @Test fun completingOneOccurrenceIsIdempotentCancelsItsPointsAndBlocksStalePlans()=runBlocking { fixture { f ->
        val id=f.events.saveEvent(details());val plans=f.plan()
        val choice=f.tasks.choices(id).choices.first()
        assertEquals(2,choice.pendingCount)
        assertTrue(f.tasks.complete(choice).changed);assertFalse(f.tasks.complete(choice).changed)
        assertEquals(1,f.tasks.exportRecords().size)
        assertTrue(f.events.getOccurrencesForEvent(id).filter { it.baseDate==date }.all { it.state==OccurrenceState.CANCELLED })
        assertTrue(f.events.getOccurrencesForEvent(id).filter { it.baseDate==date.plusDays(1) }.all { it.state==OccurrenceState.SCHEDULED })
        f.events.upsertPlannedOccurrences(plans)
        assertTrue(f.events.getOccurrencesForEvent(id).filter { it.baseDate==date }.all { it.state==OccurrenceState.CANCELLED })
    } }

    @Test fun reopenUsesLatestActionAndOnlyAllowsFuturePoints()=runBlocking { fixture { f ->
        val id=f.events.saveEvent(details());val plans=f.plan();val choice=f.tasks.choices(id).choices.first()
        f.tasks.complete(choice);val action=f.tasks.exportRecords().single().id
        f.now=at(8,45);f.tasks.reopen(action)
        assertTrue(runCatching { f.tasks.reopen(action) }.isFailure)
        f.events.upsertPlannedOccurrences(plans)
        val today=f.events.getOccurrencesForEvent(id).filter { it.baseDate==date }
        assertEquals(OccurrenceState.CANCELLED,today.single { it.triggerAtEpochMillis==at(8,30) }.state)
        assertEquals(OccurrenceState.SCHEDULED,today.single { it.triggerAtEpochMillis==at(9) }.state)
        assertEquals(listOf(TaskActionType.REOPEN,TaskActionType.DONE),f.tasks.exportRecords().map { it.action })
    } }

    @Test fun shiftSlotsIncludingNextDayRemainIndependent()=runBlocking { fixture { f ->
        val first=ShiftTime(LocalTime.of(8,0));val evening=ShiftTime(LocalTime.of(20,0));val next=ShiftTime(LocalTime.of(8,0),true)
        val data=details().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.SHIFT,endDate=date,
            shiftCycle=ShiftCycle(listOf(ShiftDay("白夜轮换",times=listOf(first,evening,next)))))) }
        val id=f.events.saveEvent(data);f.plan()
        val choices=f.tasks.choices(id).choices;assertEquals(3,choices.size)
        f.tasks.complete(choices.single { it.key==TaskIdentity.shift(date,first.key) })
        assertEquals(4,f.events.getOccurrencesForEvent(id).count { it.state==OccurrenceState.SCHEDULED })
        assertEquals(setOf(TaskIdentity.shift(date,evening.key),TaskIdentity.shift(date,next.key)),f.tasks.choices(id).choices.map { it.key }.toSet())
    } }

    @Test fun mixedRingingRetainsOtherTaskAndCompletedAnchorCanSnoozeOnlyRemainingTask()=runBlocking { fixture { f ->
        val first=f.events.saveEvent(details("取票"));val second=f.events.saveEvent(details("提交待办"));f.plan()
        val anchor=f.events.getOccurrencesForEvent(first).single { it.baseDate==date && it.triggerAtEpochMillis==at(9) }
        f.now=at(9);val claim=checkNotNull(f.events.claimSimultaneous(anchor.id,null))
        val choices=f.tasks.sessionChoices(anchor.id).choices;assertEquals(2,choices.size)
        f.tasks.complete(choices.single { it.eventId==first })
        assertEquals(RingSessionState.ACTIVE,f.db.ringSessionDao().getById(claim.session.id)?.state)
        val snoozes=f.events.createSnoozeOccurrences(anchor.id,10)
        assertEquals(listOf(second),snoozes.map { it.eventId })
        assertEquals(TaskIdentity.day(date),snoozes.single().taskOccurrenceKey)
        f.tasks.complete(f.tasks.sessionChoices(anchor.id).choices.single())
        assertEquals(RingStopReason.TASK_COMPLETED,f.db.ringSessionDao().getById(claim.session.id)?.stopReason)
        assertEquals(OccurrenceState.CANCELLED,f.events.getScheduledOccurrence(snoozes.single().id)?.state)
    } }

    @Test fun stoppingPlaybackDoesNotCompleteTaskAndOldUnfinishedInstancesSurviveOccurrencePruning()=runBlocking { fixture { f ->
        val id=f.events.saveEvent(details());f.plan()
        val anchor=f.events.getOccurrencesForEvent(id).first();f.now=anchor.triggerAtEpochMillis
        val session=checkNotNull(f.events.claimSimultaneous(anchor.id,null))
        f.events.completeRingSession(session.session.id,RingStopReason.USER)
        assertTrue(f.tasks.exportRecords().isEmpty())
        f.db.openHelper.writableDatabase.execSQL("DELETE FROM scheduled_occurrences WHERE eventId=?",arrayOf(id))
        f.now+=40L*86400000
        assertTrue(f.tasks.choices(id,TaskChoiceRange.OLDEST_OVERDUE).choices.any { it.baseDate==date })
    } }

    @Test fun actionSnapshotsSurvivePermanentDeletionWhileInstancesCascade()=runBlocking { fixture { f ->
        val id=f.events.saveEvent(details());f.plan();f.tasks.complete(f.tasks.choices(id).choices.first())
        val action=f.tasks.exportRecords().single();f.events.deleteEvent(id)
        assertTrue(runCatching { f.tasks.reopen(action.id) }.isFailure)
        assertEquals(1,f.events.permanentlyDeleteEvents(listOf(id)))
        assertNull(f.db.taskDao().instance(id,TaskIdentity.day(date)))
        assertEquals("交接事项",f.tasks.exportRecords().single().title)
    } }

    @Test fun trackingIsOptInAndEnablingMaterializesExistingUnchangedSchedules()=runBlocking { fixture { f ->
        val id=f.events.saveEvent(details(tracking=false));val plans=f.plan()
        assertNull(f.db.taskDao().instance(id,TaskIdentity.day(date)))
        val stored=checkNotNull(f.events.getEvent(id));f.now=at(10)
        f.events.saveEvent(stored.copy(event=stored.event.copy(trackCompletion=true)))
        f.tasks.initializeTracking(id)
        assertNotNull(f.db.taskDao().instance(id,TaskIdentity.day(date)))
        f.events.retainTaskInstances(plans)
        assertTrue(f.tasks.choices(id).choices.any { it.baseDate==date })
    } }

    @Test fun versionSevenBacksUpOnlyTrackingPreferenceAndDoesNotChangeImportIdentity() {
        val data=details();val root=JSONObject(BackupCodec.encode(BackupPackage(events=listOf(BackupEvent(data)))))
        assertEquals(8,root.getInt("version"))
        assertTrue(BackupCodec.decode(root.toString()).events.single().details.event.trackCompletion)
        val json=root.toString();assertFalse(json.contains("task_actions"));assertFalse(json.contains("completionTrackingStartedAt"))
        root.put("version",6);root.getJSONArray("events").getJSONObject(0).remove("trackCompletion")
        assertFalse(BackupCodec.decode(root.toString()).events.single().details.event.trackCompletion)
        assertEquals(TransferRules.fingerprint(data),TransferRules.fingerprint(data.copy(event=data.event.copy(trackCompletion=false))))
    }

    @Test fun staleConfirmationIsRejectedAfterEditingAndAfterReopening()=runBlocking { fixture { f ->
        val id=f.events.saveEvent(details());f.plan();val beforeEdit=f.tasks.choices(id).choices.first()
        f.now+=1000;val stored=checkNotNull(f.events.getEvent(id))
        f.events.saveEvent(stored.copy(event=stored.event.copy(title="修改后的事项")))
        assertTrue(runCatching { f.tasks.complete(beforeEdit) }.isFailure)
        val current=f.tasks.choices(id).choices.first();f.tasks.complete(current)
        f.tasks.reopen(f.tasks.exportRecords().first().id)
        assertTrue(runCatching { f.tasks.complete(current) }.isFailure)
        assertEquals(2,f.tasks.exportRecords().size)
    } }

    @Test fun savedCompletionReportsRescheduleFailureAndTestInstancesCannotBeCompleted()=runBlocking { fixture { f ->
        val id=f.events.saveEvent(details());f.plan()
        val test=f.events.createTestOccurrence(id);assertNotNull(f.events.claimSimultaneous(test.id,null))
        assertTrue(f.tasks.sessionChoices(test.id).choices.isEmpty())
        val repository=TaskRepository(f.db,f.events,{f.now},{zone},onChanged={false})
        val result=repository.complete(repository.choices(id).choices.first())
        assertTrue(result.changed);assertFalse(result.rescheduled)
        assertEquals(1,repository.exportRecords().size)
    } }
}
