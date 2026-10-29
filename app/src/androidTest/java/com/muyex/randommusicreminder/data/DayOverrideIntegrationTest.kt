package com.muyex.randommusicreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.planner.*
import com.muyex.randommusicreminder.ai.AiEditCodec
import java.time.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DayOverrideIntegrationTest {
    private val date=LocalDate.of(2030,1,1)
    private val zone=ZoneId.systemDefault()
    private fun at(hour:Int,minute:Int=0)=date.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
    private class Fixture(val db:AppDatabase,var now:Long) {
        val cancelled=mutableListOf<String>()
        val repo=EventRepository(db,nowEpochMillis={now},cancelScheduledAlarm={cancelled+=it})
    }
    private suspend fun fixture(block:suspend (Fixture)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        try { block(Fixture(db,at(6))) } finally { db.close() }
    }
    private fun data()=EventDetails(ReminderEvent(title="临时交接",scheduleType=ScheduleType.DAILY,localDate=date,localTime=LocalTime.of(9,0)),
        listOf(ReminderRule(offsetMinutes=-30,snoozeMinutes=10),ReminderRule(offsetMinutes=0,snoozeMinutes=10)))
    private suspend fun planned(f:Fixture,id:Long):List<PlannedOccurrence> {
        val result=OccurrencePlanner().plan(listOf(checkNotNull(f.repo.getEvent(id))),Instant.ofEpochMilli(at(6)),Instant.ofEpochMilli(at(6)).plusSeconds(3*86400),zone)
        f.repo.upsertPlannedOccurrences(result);return result
    }
    @Test fun skipCancelsPendingSnoozeAndPointsButLeavesRingingAndNextDay()=runBlocking { fixture { f ->
        val id=f.repo.saveEvent(data());val original=planned(f,id);val anchor=original.first()
        f.now=anchor.triggerAtEpochMillis;val claim=checkNotNull(f.repo.claimSimultaneous(anchor.id,null))
        val snooze=f.repo.createSnoozeOccurrences(anchor.id,10).single()
        val stored=checkNotNull(f.repo.getEvent(id));f.repo.saveEvent(stored.copy(event=stored.event.copy(dayOverrides=listOf(DayOverride(date,DayOverrideKind.SKIP)))))
        assertEquals(OccurrenceState.CANCELLED,f.repo.getScheduledOccurrence(snooze.id)?.state)
        assertEquals(OccurrenceState.RINGING,f.repo.getScheduledOccurrence(anchor.id)?.state)
        assertEquals(RingSessionState.ACTIVE,f.db.ringSessionDao().getById(claim.session.id)?.state)
        assertTrue(f.repo.createSnoozeOccurrences(anchor.id,10).isEmpty())
        f.repo.upsertPlannedOccurrences(original)
        assertTrue(f.repo.getOccurrencesForEvent(id).filter { it.baseDate==date && it.id!=anchor.id }.all { it.state==OccurrenceState.CANCELLED })
        assertTrue(f.repo.getOccurrencesForEvent(id).filter { it.baseDate==date.plusDays(1) }.all { it.state==OccurrenceState.SCHEDULED })
    } }
    @Test fun restoreDoesNotRearmOldPointInsideLateGraceButKeepsFuturePoint()=runBlocking { fixture { f ->
        val id=f.repo.saveEvent(data());val old=planned(f,id)
        var stored=checkNotNull(f.repo.getEvent(id));f.repo.saveEvent(stored.copy(event=stored.event.copy(dayOverrides=listOf(DayOverride(date,DayOverrideKind.SKIP)))))
        f.now=at(8,45);stored=checkNotNull(f.repo.getEvent(id));f.repo.saveEvent(stored.copy(event=stored.event.copy(dayOverrides=emptyList())))
        f.repo.upsertPlannedOccurrences(old)
        assertEquals(OccurrenceState.CANCELLED,f.repo.getScheduledOccurrence(old.first().id)?.state)
        assertEquals(OccurrenceState.SCHEDULED,f.repo.getScheduledOccurrence(old[1].id)?.state)
        assertEquals(f.now+1,checkNotNull(f.repo.getEvent(id)).event.dayOverrideBarriers[date])
    } }
    @Test fun replacementRejectsStalePlansAndOldBroadcastCannotClaimFutureSameId()=runBlocking { fixture { f ->
        val cycle=ShiftCycle.template("白休")
        val id=f.repo.saveEvent(data().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.SHIFT,shiftCycle=cycle)) });val old=planned(f,id)
        val stored=checkNotNull(f.repo.getEvent(id));val replacement=cycle.days.first().let { it.copy(times=it.times.map { t->t.copy(time=LocalTime.of(10,0)) }) }
        f.now=at(7);f.repo.saveEvent(stored.copy(event=stored.event.copy(dayOverrides=listOf(DayOverride(date,DayOverrideKind.REPLACE_SHIFT,replacement)))))
        f.repo.upsertPlannedOccurrences(old)
        assertEquals(OccurrenceState.CANCELLED,f.repo.getScheduledOccurrence(old.first().id)?.state)
        val current=planned(f,id).first();assertEquals(old.first().id,current.id)
        f.now=at(8,30);assertNull(f.repo.claimSimultaneous(current.id,null,requireDue=true))
        assertEquals(OccurrenceState.SCHEDULED,f.repo.getScheduledOccurrence(current.id)?.state)
        f.now=current.triggerAtEpochMillis;assertNotNull(f.repo.claimSimultaneous(current.id,null,requireDue=true))
    } }
    @Test fun randomSkipAndRestoreKeepSeedRevisionAndAlreadySelectedMinutes()=runBlocking { fixture { f ->
        val id=f.repo.saveEvent(data().let { it.copy(event=it.event.copy(timingMode=TimingMode.RANDOM_WINDOW,localTime=LocalTime.of(8,0),windowEndTime=LocalTime.of(8,10))) })
        val old=planned(f,id);val stored=checkNotNull(f.repo.getEvent(id));val target=date.plusDays(1)
        f.repo.saveEvent(stored.copy(event=stored.event.copy(dayOverrides=listOf(DayOverride(target,DayOverrideKind.SKIP)))))
        var changed=checkNotNull(f.repo.getEvent(id));assertEquals(stored.event.randomSeed,changed.event.randomSeed);assertEquals(stored.event.timingRevision,changed.event.timingRevision)
        planned(f,id);f.now+=1000;f.repo.saveEvent(changed.copy(event=changed.event.copy(dayOverrides=emptyList())))
        val restored=planned(f,id)
        assertEquals(old.map { it.triggerAtEpochMillis },restored.map { it.triggerAtEpochMillis })
        changed=checkNotNull(f.repo.getEvent(id));assertTrue(changed.event.dayOverrideBarriers.containsKey(target))
    } }
    @Test fun backupEightKeepsOnlyPortableOverridesAndLegacySevenDefaultsEmpty() {
        val slot=ShiftTime(LocalTime.of(20,0));val adjustment=DayOverride(date,DayOverrideKind.REPLACE_SHIFT,ShiftDay("临时夜班",times=listOf(slot)))
        val details=data().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.SHIFT,shiftCycle=ShiftCycle.template(),dayOverrides=listOf(adjustment),dayOverrideBarriers=mapOf(date to at(7)))) }
        val root=JSONObject(BackupCodec.encode(BackupPackage(events=listOf(BackupEvent(details)))))
        assertEquals(8,root.getInt("version"));assertFalse(root.toString().contains("dayOverrideBarriers"))
        val decoded=BackupCodec.decode(root.toString()).events.single().details
        assertEquals(listOf(adjustment),decoded.event.dayOverrides);assertTrue(decoded.event.dayOverrideBarriers.isEmpty())
        val edit=AiEditCodec.decode("""{"summary":"改标题","changes":[{"id":1,"title":"保留临时轮班"}]}""",listOf(details.copy(event=details.event.copy(id=1))))
        assertEquals(listOf(adjustment),edit.after.single().event.dayOverrides)
        root.put("version",7);root.getJSONArray("events").getJSONObject(0).remove("dayOverrides")
        assertTrue(BackupCodec.decode(root.toString()).events.single().details.event.dayOverrides.isEmpty())
    }
    @Test fun recycleAndRestorePreserveAdjustmentWhilePaused()=runBlocking { fixture { f ->
        val adjustment=DayOverride(date.plusDays(1),DayOverrideKind.SKIP)
        val id=f.repo.saveEvent(data().let { it.copy(event=it.event.copy(dayOverrides=listOf(adjustment))) })
        val before=checkNotNull(f.repo.getEvent(id)).event.dayOverrideBarriers
        f.repo.deleteEvent(id);f.repo.restoreEvent(id)
        val restored=checkNotNull(f.repo.getEvent(id)).event
        assertFalse(restored.enabled);assertEquals(listOf(adjustment),restored.dayOverrides);assertEquals(before,restored.dayOverrideBarriers)
    } }
}
