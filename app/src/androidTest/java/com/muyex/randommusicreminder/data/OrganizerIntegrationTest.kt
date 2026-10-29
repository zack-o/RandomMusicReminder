package com.muyex.randommusicreminder.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.*
import com.muyex.randommusicreminder.data.settings.SettingsRepository
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.model.*
import java.time.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OrganizerIntegrationTest {
    private fun details(title:String="工作交接",tags:List<String> = listOf("工作"))=EventDetails(
        ReminderEvent(title=title,tags=tags,scheduleType=ScheduleType.DAILY,localDate=LocalDate.of(2030,1,1),localTime=LocalTime.of(9,0)),
        listOf(ReminderRule(offsetMinutes=0)))
    private fun context()=ApplicationProvider.getApplicationContext<Context>()
    private fun isolatedFilesContext(): Context {
        val base=context()
        val directory=File(base.cacheDir,"organizer-${UUID.randomUUID()}").apply { check(mkdirs()) }
        return object: ContextWrapper(base) { override fun getFilesDir()=directory }
    }

    @Test fun trashCancelsPendingAndRejectsStalePlansWhileRestoreKeepsConfigurationDisabled()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context(),AppDatabase::class.java).build()
        try {
            var now=1000L;val cancelled=mutableListOf<String>()
            val repo=EventRepository(db,nowEpochMillis={now},cancelScheduledAlarm={cancelled+=it})
            val id=repo.saveEvent(details());val original=checkNotNull(repo.getEvent(id));val rule=original.rules.single().id
            val pending=PlannedOccurrence("pending",id,rule,original.event.localDate,20000)
            repo.upsertPlannedOccurrences(listOf(pending))
            now=2000;assertTrue(repo.deleteEvent(id));assertNull(repo.getEvent(id));assertTrue(repo.getEvents().isEmpty())
            assertEquals(listOf("pending"),cancelled)
            assertEquals(OccurrenceState.CANCELLED,repo.getScheduledOccurrence("pending")?.state)
            repo.upsertPlannedOccurrences(listOf(pending))
            assertEquals(OccurrenceState.CANCELLED,repo.getScheduledOccurrence("pending")?.state)
            assertFalse(repo.setEnabled(id,true))
            assertTrue(runCatching { repo.saveEvent(original) }.isFailure)
            val deleted=repo.observeDeletedEvents().first().single()
            assertEquals(original.event.tags,deleted.event.tags);assertEquals(original.rules,deleted.rules)
            now=3000;assertTrue(repo.restoreEvent(id))
            val restored=checkNotNull(repo.getEvent(id))
            assertFalse(restored.event.enabled);assertNull(restored.event.deletedAtEpochMillis)
            assertEquals(original.event.createdAtEpochMillis,restored.event.createdAtEpochMillis)
            assertEquals(3001L,restored.event.resumedAfterEpochMillis)
            assertEquals(OccurrenceState.CANCELLED,repo.getScheduledOccurrence("pending")?.state)
            assertTrue(repo.setEnabled(id,true))
            repo.upsertPlannedOccurrences(listOf(pending.copy(id="old-before-restore",triggerAtEpochMillis=2000)))
            assertNull(repo.getScheduledOccurrence("old-before-restore"))
        } finally { db.close() }
    }
    @Test fun tagRenameMergesAndPreservesSchedulesIncludingDeletedItems()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context(),AppDatabase::class.java).build()
        try {
            val repo=EventRepository(db)
            val active=repo.saveEvent(details(tags=listOf("工作","生活")))
            val deleted=repo.saveEvent(details("缴费",listOf("工作")));repo.deleteEvent(deleted)
            val original=checkNotNull(repo.getEvent(active))
            repo.upsertPlannedOccurrences(listOf(PlannedOccurrence("kept",active,original.rules.single().id,original.event.localDate,20000)))
            assertEquals(2,repo.renameTag("工作","生活"))
            assertEquals(listOf("生活"),repo.getEvent(active)?.event?.tags)
            assertTrue(checkNotNull(repo.getEvent(active)).event.enabled)
            assertEquals(OccurrenceState.SCHEDULED,repo.getScheduledOccurrence("kept")?.state)
            assertFalse(repo.observeDeletedEvents().first().single().event.enabled)
            assertEquals(original.rules,repo.getEvent(active)?.rules)
            assertEquals(2,repo.removeTag("生活"));assertEquals(2,repo.getEventsIncludingDeleted().size)
        } finally { db.close() }
    }
    @Test fun permanentDeletionIsAtomicAndOnlyDeletesTheConfirmedTrashIds()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context(),AppDatabase::class.java).build()
        try {
            val repo=EventRepository(db)
            val first=repo.saveEvent(details("一"));val active=repo.saveEvent(details("二"))
            repo.deleteEvent(first)
            assertTrue(runCatching { repo.permanentlyDeleteEvents(listOf(first,active)) }.isFailure)
            assertEquals(2,repo.getEventsIncludingDeleted().size)
            val snapshot=listOf(first)
            val added=repo.saveEvent(details("后加入"));repo.deleteEvent(added)
            assertEquals(1,repo.permanentlyDeleteEvents(snapshot))
            assertEquals(added,repo.observeDeletedEvents().first().single().event.id)
            assertNotNull(repo.getEvent(active))
        } finally { db.close() }
    }
    @Test fun ordinaryBackupKeepsTagsButDoesNotExportLocalTrashOrRestoreState() {
        val item=details(tags=listOf("抢票","出行"))
        val encoded=BackupCodec.encode(BackupPackage(events=listOf(BackupEvent(item))))
        val root=JSONObject(encoded);assertEquals(8,root.getInt("version"))
        val event=root.getJSONArray("events").getJSONObject(0)
        assertFalse(event.has("deletedAtEpochMillis"));assertFalse(event.has("resumedAfterEpochMillis"))
        assertEquals(item,BackupCodec.decode(encoded).events.single().details)
        event.remove("tags");root.put("version",4)
        assertTrue(BackupCodec.decode(root.toString()).events.single().details.event.tags.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { EventTagsCodec.decode("[1]") }
    }
    @Test fun importCannotRecreateTrashByChangingOnlyTags()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context(),AppDatabase::class.java).build()
        try {
            val repo=EventRepository(db);val original=details();val id=repo.saveEvent(original);repo.deleteEvent(id)
            val isolated=isolatedFilesContext()
            val service=DataTransferService(isolated,db,repo,MusicRepository(db),CalendarRepository(db),SettingsRepository(isolated))
            val imported=original.copy(event=original.event.copy(tags=listOf("不同标签")))
            val preview=service.prepare(BackupPackage(events=listOf(BackupEvent(imported))))
            assertTrue(preview.events.isEmpty());assertEquals(1,preview.warnings.size)
            assertTrue(service.snapshot().events.isEmpty())
        } finally { db.close() }
    }
}
