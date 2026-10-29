package com.muyex.randommusicreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.planner.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SchedulePreviewIntegrationTest {
    private val zone=ZoneId.systemDefault()
    private val date=LocalDate.of(2030,1,1)
    private val now=date.atStartOfDay(zone).toInstant()
    private fun details(random:Boolean=false)=EventDetails(ReminderEvent(title="预览核对",scheduleType=ScheduleType.DAILY,localDate=date,
        localTime=LocalTime.of(8,0),trackCompletion=true,timingMode=if(random) TimingMode.RANDOM_WINDOW else TimingMode.FIXED,
        windowEndTime=LocalTime.of(8,10),windowBackupEnabled=true),listOf(ReminderRule(offsetMinutes=0)))
    private suspend fun fixture(block:suspend (AppDatabase,EventRepository,SchedulePreviewRepository)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        try {
            val events=EventRepository(db,nowEpochMillis={now.toEpochMilli()})
            block(db,events,SchedulePreviewRepository(db,events,{now},{zone}))
        } finally { db.close() }
    }
    private fun snapshot(db:AppDatabase):List<String> {
        val result=mutableListOf<String>()
        val sqlite=db.openHelper.writableDatabase
        val tables=sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { cursor ->
            buildList { while(cursor.moveToNext()) add(cursor.getString(0)) }
        }
        tables.forEach { table -> sqlite.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
            while(cursor.moveToNext()) result+="$table:"+(0 until cursor.columnCount).joinToString("|") { if(cursor.isNull(it)) "<null>" else cursor.getString(it) }
        } }
        return result
    }
    @Test fun unsavedRandomPreviewDoesNotInsertEventSeedOrAnyOtherRows()=runBlocking { fixture { db,_,repo ->
        val before=snapshot(db);val draft=details(true)
        val first=repo.preview(draft);val second=repo.preview(draft)
        assertEquals(first,second);assertEquals(14,first.items.size)
        assertEquals(before,snapshot(db));assertEquals(0L,draft.event.randomSeed)
    } }
    @Test fun existingCompletedTaskIsExcludedAndPreviewLeavesAllTablesUnchanged()=runBlocking { fixture { db,events,repo ->
        val id=events.saveEvent(details());val stored=checkNotNull(events.getEvent(id))
        events.upsertPlannedOccurrences(OccurrencePlanner().plan(listOf(stored),now,now.plusSeconds(2*86400),zone))
        val tasks=TaskRepository(db,events,{now.toEpochMilli()},{zone});tasks.complete(tasks.choices(id).choices.first())
        val before=snapshot(db)
        val preview=repo.preview(stored.copy(rules=stored.rules.map { it.copy(id=0) }))
        assertFalse(preview.items.any { it.baseDate==date });assertTrue(preview.omissions.any { it.reason.contains("已完成") })
        assertEquals(before,snapshot(db))
    } }
    @Test fun storedRandomSelectionSurvivesRepeatedPreviewWithoutRedrawOrStateChange()=runBlocking { fixture { db,events,repo ->
        val id=events.saveEvent(details(true));val stored=checkNotNull(events.getEvent(id))
        events.upsertPlannedOccurrences(OccurrencePlanner().plan(listOf(stored),now,now.plusSeconds(2*86400),zone))
        val before=snapshot(db);val first=repo.preview(stored);assertEquals(first,repo.preview(stored))
        assertTrue(first.items.first().label.contains("既有窗口"));assertNotNull(first.items.first().endEpochMillis)
        assertEquals(before,snapshot(db))
    } }
    @Test fun recycledDraftIsRejectedAndRestoredPausedReminderIsNotShownAsScheduled()=runBlocking { fixture { db,events,repo ->
        val id=events.saveEvent(details());val stored=checkNotNull(events.getEvent(id));events.deleteEvent(id)
        val deleted=snapshot(db);assertTrue(runCatching { repo.preview(stored) }.isFailure);assertEquals(deleted,snapshot(db))
        events.restoreEvent(id);val restored=checkNotNull(events.getEvent(id));val before=snapshot(db)
        val preview=repo.preview(restored);assertTrue(preview.paused);assertTrue(preview.items.isEmpty())
        assertEquals(before,snapshot(db))
    } }
}
