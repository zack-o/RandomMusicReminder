package com.muyex.randommusicreminder.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.muyex.randommusicreminder.ai.AiDraftCodec
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.haptics.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.music.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HapticsDataIntegrationTest {
    private fun isolatedContext(): Context {
        val base=ApplicationProvider.getApplicationContext<Context>()
        val directory=File(base.cacheDir,"haptics-data-${UUID.randomUUID()}").apply { check(mkdirs()) }
        return object: ContextWrapper(base) { override fun getFilesDir()=directory;override fun getApplicationContext():Context=this }
    }
    private fun silentWav(context:Context):File {
        val bytes=160000;val buffer=ByteBuffer.allocate(44+bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36+bytes).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes)
        return File(File(context.filesDir,MusicImporter.DIRECTORY_NAME).apply { check(mkdirs()) },"silent.wav").apply { writeBytes(buffer.array()) }
    }
    @Test fun decoderRejectsSilentWavWithoutInventingRhythmOrCreatingCache()=runBlocking {
        val context=isolatedContext();val file=silentWav(context)
        val error=runCatching { HapticPatternStore(context).generate("1",Uri.fromFile(file).toString(),0,10) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("过静"))
        assertNull(HapticPatternStore(context).read("1",Uri.fromFile(file).toString()))
    }
    @Test fun cacheRequiresMatchingSourceAndSafePulseParameters()=runBlocking {
        val context=isolatedContext();val file=silentWav(context);val uri=Uri.fromFile(file).toString()
        val cached=File(File(context.filesDir,"haptic-rhythms-v1").apply { check(mkdirs()) },"1.json")
        val json=JSONObject().put("version",1).put("source","${file.name}:${file.length()}:${file.lastModified()}")
            .put("startSeconds",0).put("lengthSeconds",10).put("durationMillis",10000)
            .put("pulses",JSONArray().put(JSONArray(listOf(500,60,120))).put(JSONArray(listOf(1000,60,160))))
        cached.writeText(json.toString())
        assertEquals(2,HapticPatternStore(context).read("1",uri)?.pattern?.pulses?.size)
        json.getJSONArray("pulses").getJSONArray(0).put(1,99999);cached.writeText(json.toString())
        assertNull(HapticPatternStore(context).read("1",uri))
        json.put("source","different");cached.writeText(json.toString())
        assertNull(HapticPatternStore(context).read("1",uri))
    }
    @Test fun roomAndBackupKeepExplicitVibrationModeAndLegacyBackupsDefaultToMusic()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(isolatedContext(),AppDatabase::class.java).build()
        try {
            val repo=EventRepository(db)
            val details=EventDetails(ReminderEvent(title="安静提醒",scheduleType=ScheduleType.DAILY,localDate=LocalDate.of(2030,1,1),localTime=LocalTime.NOON,
                outputMode=ReminderOutputMode.VIBRATION,haptics=HapticSettings(HapticPreset.SOFT,HapticStrength.LIGHT,700,true)),listOf(ReminderRule(offsetMinutes=0,snoozeMinutes=10)))
            val id=repo.saveEvent(details)
            assertEquals(details.event.haptics,repo.getEvent(id)?.event?.haptics)
            assertEquals(ReminderOutputMode.VIBRATION,repo.getEvent(id)?.event?.outputMode)
            val stored=checkNotNull(repo.getEvent(id))
            repo.upsertPlannedOccurrences(listOf(PlannedOccurrence("vibration-test",id,stored.rules.single().id,stored.event.localDate,1893456000000)))
            assertEquals(ReminderOutputMode.VIBRATION,repo.loadPlaybackOccurrence("vibration-test")?.outputMode)
            assertNotNull(repo.claimSimultaneous("vibration-test",null))
            val snooze=repo.createSnoozeOccurrences("vibration-test",10).single()
            assertEquals(ReminderOutputMode.VIBRATION,repo.loadPlaybackOccurrence(snooze.id)?.outputMode)
            repo.deleteEvent(id);repo.restoreEvent(id)
            assertEquals(ReminderOutputMode.VIBRATION,repo.getEvent(id)?.event?.outputMode)
            assertFalse(checkNotNull(repo.getEvent(id)).event.enabled)
            val root=JSONObject(BackupCodec.encode(BackupPackage(events=listOf(BackupEvent(details)))))
            assertEquals(8,root.getInt("version"))
            assertEquals(details,BackupCodec.decode(root.toString()).events.single().details)
            val event=root.getJSONArray("events").getJSONObject(0);event.remove("outputMode");event.remove("haptics");root.put("version",5)
            assertEquals(ReminderOutputMode.MUSIC,BackupCodec.decode(root.toString()).events.single().details.event.outputMode)
        } finally { db.close() }
    }
    @Test fun aiRespectsExplicitNoSoundAndRejectsUnknownOutputInsteadOfDowngrading() {
        val task=JSONObject().put("title","安静提醒").put("note","").put("date","2030-01-01").put("time","09:00")
            .put("repeat","ONCE").put("weekdays",JSONArray()).put("offsets",JSONArray(listOf(0))).put("duration",30)
            .put("mode","GENTLE").put("holidayPolicy","NONE").put("outputMode","VIBRATION")
        val root=JSONObject().put("summary","确认仅震动").put("tasks",JSONArray().put(task)).put("days",JSONArray())
        assertEquals(ReminderOutputMode.VIBRATION,AiDraftCodec.decode(root.toString()).data.events.single().details.event.outputMode)
        task.put("outputMode","UNKNOWN")
        assertThrows(IllegalArgumentException::class.java) { AiDraftCodec.decode(root.toString()) }
    }
}
