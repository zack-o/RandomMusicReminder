package com.muyex.randommusicreminder.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.muyex.randommusicreminder.ai.*
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.*
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.planner.OccurrencePlanner
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class V3IntegrationTest {
    private val date = LocalDate.of(2030, 10, 28)
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(h: Int, m: Int) = date.atTime(h,m).atZone(zone).toInstant()
    private fun draft() = EventDetails(ReminderEvent(title="时间窗测试",scheduleType=ScheduleType.DAILY,localDate=date,
        localTime=LocalTime.of(8,0),timingMode=TimingMode.RANDOM_WINDOW,windowEndTime=LocalTime.of(8,10)),
        listOf(ReminderRule(offsetMinutes=-10),ReminderRule(offsetMinutes=0)))

    @Test fun portableWindowBackupOmitsSchedulingIdentityAndLegacyStaysFixed() {
        val source = draft().let { it.copy(event=it.event.copy(randomSeed=987,timingRevision=12,windowFirstNotBeforeEpochMillis=123)) }
        val text = BackupCodec.encode(BackupPackage(events=listOf(BackupEvent(source))))
        assertEquals(8,JSONObject(text).getInt("version"))
        assertFalse(text.contains("randomSeed"));assertFalse(text.contains("timingRevision"));assertFalse(text.contains("windowFirstNotBefore"))
        val restored=BackupCodec.decode(text).events.single().details
        assertEquals(TimingMode.RANDOM_WINDOW,restored.event.timingMode);assertEquals(0,restored.event.randomSeed.toInt());assertNull(restored.event.windowSnoozeMinutes)
        val root=JSONObject(text).put("version",2)
        root.getJSONArray("events").getJSONObject(0).remove("timingMode")
        assertEquals(TimingMode.FIXED,BackupCodec.decode(root.toString()).events.single().details.event.timingMode)
    }

    @Test fun windowDraftAndEditsAreStrictlyBoundedToChosenScope() {
        val json="""{"summary":"待确认","tasks":[{"title":"起床","note":"","date":"2030-10-28","time":"08:00","repeat":"DAILY","weekdays":[],"offsets":[0],"duration":30,"mode":"GENTLE","holidayPolicy":"NONE","timingMode":"RANDOM_WINDOW","windowEndTime":"08:10","windowCrossesMidnight":false}],"days":[]}"""
        val parsed=AiDraftCodec.decode(json).data.events.single().details
        assertNull(parsed.event.windowSnoozeMinutes);assertFalse(parsed.event.windowBackupEnabled);assertEquals(1,parsed.activeRules.size)
        val original=draft().let { it.copy(event=it.event.copy(id=5)) }
        assertThrows(IllegalArgumentException::class.java) { AiEditCodec.decode("""{"summary":"x","changes":[{"id":6,"enabled":false}]}""",listOf(original)) }
        assertThrows(IllegalArgumentException::class.java) { AiEditCodec.decode("""{"summary":"x","changes":[{"id":5,"sql":"delete"}]}""",listOf(original)) }
        assertThrows(IllegalArgumentException::class.java) { AiEditCodec.decode("""{"summary":"x","changes":[{"id":5,"windowEndTime":"07:00"}]}""",listOf(original)) }
    }

    @Test fun randomSelectionPersistsAndSnoozeDefaultsOffDespiteRetainedFixedRules() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val repository=EventRepository(db,nowEpochMillis={at(7,0).toEpochMilli()})
            val id=repository.saveEvent(draft());val saved=checkNotNull(repository.getEvent(id))
            assertTrue(saved.event.randomSeed != 0L);assertEquals(2,saved.rules.size)
            val planned=OccurrencePlanner().plan(listOf(saved),at(7,0),at(9,0),zone)
            repository.upsertPlannedOccurrences(planned)
            val occurrence=repository.observeScheduledOccurrences().first().single()
            assertEquals(planned.single().selectedLocalMinute,occurrence.selectedLocalMinute)
            assertTrue(repository.createSnoozeOccurrences(occurrence.id,10).isEmpty())
            repository.saveEvent(saved.copy(event=saved.event.copy(title="改标题")))
            assertEquals(saved.event.randomSeed,checkNotNull(repository.getEvent(id)).event.randomSeed)
        } finally { db.close() }
    }

    @Test fun confirmedModificationCanUndoButCannotOverwriteLaterManualEdit() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            var clock=at(7,0).toEpochMilli()
            val repository=EventRepository(db,nowEpochMillis={clock++});val actions=AiActionRepository(db,repository)
            val id=repository.saveEvent(draft());val before=checkNotNull(repository.getEvent(id))
            val proposal=AiEditCodec.decode("""{"summary":"关闭","changes":[{"id":$id,"enabled":false}]}""",listOf(before))
            assertEquals(1,actions.apply(proposal));assertFalse(checkNotNull(repository.getEvent(id)).event.enabled)
            val record=actions.observe().first().first();assertEquals(1,actions.undo(record.id));assertTrue(checkNotNull(repository.getEvent(id)).event.enabled)
            val fresh=checkNotNull(repository.getEvent(id));actions.apply(proposal.copy(before=listOf(fresh),after=listOf(fresh.copy(event=fresh.event.copy(enabled=false)))))
            val second=actions.observe().first().first();repository.saveEvent(checkNotNull(repository.getEvent(id)).let { it.copy(event=it.event.copy(title="之后手动修改")) })
            try { actions.undo(second.id);fail("must preserve manual edit") } catch (_: IllegalArgumentException) { }
            assertEquals("之后手动修改",checkNotNull(repository.getEvent(id)).event.title)
        } finally { db.close() }
    }

    @Test fun deletingRetainedFixedPointCannotEraseHandledRandomWindow() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val repository=EventRepository(db,nowEpochMillis={at(7,0).toEpochMilli()})
            val id=repository.saveEvent(draft());val original=checkNotNull(repository.getEvent(id))
            val planner=OccurrencePlanner();val chosen=planner.plan(listOf(original),at(7,0),at(9,0),zone).single()
            repository.upsertPlannedOccurrences(listOf(chosen));repository.markOccurrenceCompleted(chosen.id)
            repository.saveEvent(original.copy(rules=original.rules.filter { it.id != chosen.reminderRuleId }))
            val stored=checkNotNull(repository.getScheduledOccurrence(chosen.id))
            assertEquals(OccurrenceState.COMPLETED,stored.state)
            assertTrue(planner.plan(listOf(checkNotNull(repository.getEvent(id))),at(7,0),at(9,0),zone,existing=listOf(stored)).isEmpty())
        } finally { db.close() }
    }

    @Test fun stoppingPreservesBackupButExplicitAwakeCancellationSurvivesReconcile() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val repository=EventRepository(db,nowEpochMillis={at(7,0).toEpochMilli()})
            val id=repository.saveEvent(draft().let { it.copy(event=it.event.copy(windowBackupEnabled=true,randomSeed=42)) })
            val details=checkNotNull(repository.getEvent(id))
            val all=com.muyex.randommusicreminder.domain.planner.RandomWindow.candidates(date,details.event.localTime,details.event.windowEndTime,false,zone)
            // 固定测试实例，避开随机主提醒恰好在末分钟时的合并分支。
            val main=PlannedOccurrence("primary",id,details.rules.first().id,date,at(8,1).toEpochMilli(),481,all.first().instant.toEpochMilli(),all.last().instant.toEpochMilli(),false)
            val backup=main.copy(id="backup",triggerAtEpochMillis=at(8,10).toEpochMilli(),windowBackup=true)
            repository.upsertPlannedOccurrences(listOf(main,backup));repository.markOccurrenceCompleted(main.id)
            assertEquals(OccurrenceState.SCHEDULED,checkNotNull(repository.getScheduledOccurrence(backup.id)).state)
            assertEquals(1,repository.acknowledgeWindow(main.id))
            assertEquals(OccurrenceState.COMPLETED,checkNotNull(repository.getScheduledOccurrence(backup.id)).state)
            assertTrue(OccurrencePlanner().reconcile(listOf(backup),listOf(checkNotNull(repository.getScheduledOccurrence(backup.id)))).upserts.isEmpty())
        } finally { db.close() }
    }

    @Test fun lateSystemDeliveryDropsWindowButKeepsCoincidentFixedReminder() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val repository=EventRepository(db,nowEpochMillis={at(8,5).toEpochMilli()})
            val windowId=repository.saveEvent(draft())
            val window=checkNotNull(repository.getEvent(windowId))
            val fixedId=repository.saveEvent(draft().let { it.copy(event=it.event.copy(title="同刻固定",timingMode=TimingMode.FIXED),rules=listOf(ReminderRule(offsetMinutes=0))) })
            val fixed=checkNotNull(repository.getEvent(fixedId))
            val trigger=at(8,0).toEpochMilli()
            repository.upsertPlannedOccurrences(listOf(
                PlannedOccurrence("late-window",windowId,window.rules.first().id,date,trigger,480,trigger,at(8,10).toEpochMilli()),
                PlannedOccurrence("fixed",fixedId,fixed.rules.first().id,date,trigger)))
            val survivors=repository.loadPlaybackGroup("late-window")
            assertEquals(listOf("fixed"),survivors.map { it.occurrenceId })
            assertEquals(OccurrenceState.MISSED,checkNotNull(repository.getScheduledOccurrence("late-window")).state)
            assertNull(repository.loadPlaybackOccurrence("late-window"))
            assertNotNull(repository.claimSimultaneous(survivors.first().occurrenceId,null))
        } finally { db.close() }
    }

    @Test fun staleReplanningCannotOverwriteAWindowCompletedAfterPlanning() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val repository=EventRepository(db,nowEpochMillis={at(7,0).toEpochMilli()})
            val id=repository.saveEvent(draft());val event=checkNotNull(repository.getEvent(id))
            val planned=OccurrencePlanner().plan(listOf(event),at(7,0),at(9,0),zone).single()
            repository.upsertPlannedOccurrences(listOf(planned))
            repository.markOccurrenceCompleted(planned.id)
            repository.upsertPlannedOccurrences(listOf(planned.copy(triggerAtEpochMillis=planned.triggerAtEpochMillis+60_000L)))
            val stored=checkNotNull(repository.getScheduledOccurrence(planned.id))
            assertEquals(OccurrenceState.COMPLETED,stored.state)
            assertEquals(planned.triggerAtEpochMillis,stored.triggerAtEpochMillis)
        } finally { db.close() }
    }
}
