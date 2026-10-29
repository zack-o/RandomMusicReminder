package com.muyex.randommusicreminder.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.muyex.randommusicreminder.ai.AiDraftCodec
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.planner.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class V4ShiftAndClockIntegrationTest {
    private val date=LocalDate.of(2030,1,1)
    private fun details()=EventDetails(ReminderEvent(title="轮班交接",scheduleType=ScheduleType.SHIFT,localDate=date,
        localTime=LocalTime.of(9,59,55),shiftCycle=ShiftCycle.template()),listOf(ReminderRule(offsetMinutes=0)))
    @Test fun codecAndPortableBackupKeepCycleKeysAndSecondValues() {
        val original=details()
        assertEquals(original.event.shiftCycle,ShiftCycleCodec.decode(ShiftCycleCodec.encode(original.event.shiftCycle)))
        val encoded=BackupCodec.encode(BackupPackage(events=listOf(BackupEvent(original))))
        assertEquals(8,JSONObject(encoded).getInt("version"))
        assertEquals(original,BackupCodec.decode(encoded).events.single().details)
        val root=JSONObject(encoded).put("version",3);val event=root.getJSONArray("events").getJSONObject(0)
        event.put("repeat","DAILY");event.remove("shiftCycle")
        assertNull(BackupCodec.decode(root.toString()).events.single().details.event.shiftCycle)
    }
    @Test fun roomStoresPerDayTimesAndOccurrenceSourceWithoutPlaying()= runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val repository=EventRepository(db)
            val original=details();val id=repository.saveEvent(original);val stored=checkNotNull(repository.getEvent(id))
            assertEquals(original.event.shiftCycle,stored.event.shiftCycle)
            assertEquals(55,stored.event.localTime.second)
            val zone=ZoneId.of("Asia/Shanghai")
            val plan=OccurrencePlanner().plan(listOf(stored),date.atStartOfDay(zone).toInstant(),date.plusDays(6).atStartOfDay(zone).toInstant(),zone)
            assertEquals(4,plan.size)
            repository.upsertPlannedOccurrences(plan)
            assertEquals(plan.first().sourceLabel,repository.getScheduledOccurrence(plan.first().id)?.sourceLabel)
        } finally { db.close() }
    }
    @Test fun aiCanCreateExplicitShiftButRejectsEmptyEnabledDay() {
        val json="""{"summary":"请确认轮班","tasks":[{"title":"轮班交接","note":"","date":"2030-01-01","time":"00:00","repeat":"SHIFT","weekdays":[],"offsets":[0],"duration":30,"mode":"GENTLE","holidayPolicy":"NONE","shiftDays":[{"label":"白班","rest":false,"enabled":true,"times":[{"time":"08:30:15","nextDay":false}]},{"label":"休息","rest":true,"enabled":false,"times":[]}]}],"days":[]}"""
        val parsed=AiDraftCodec.decode(json).data.events.single().details
        assertEquals(ScheduleType.SHIFT,parsed.event.scheduleType)
        assertEquals(15,parsed.event.shiftCycle?.days?.first()?.times?.first()?.time?.second)
        val bad=JSONObject(json);bad.getJSONArray("tasks").getJSONObject(0).getJSONArray("shiftDays").getJSONObject(1).put("enabled",true)
        assertThrows(IllegalArgumentException::class.java) { AiDraftCodec.decode(bad.toString()) }
    }
}
