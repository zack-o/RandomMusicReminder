package com.muyex.randommusicreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.MusicRepository
import com.muyex.randommusicreminder.data.repository.UsageRepository
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.domain.calendar.HolidayPolicy
import com.muyex.randommusicreminder.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmallToolsRepositoryIntegrationTest {
    private fun database(): AppDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()

    @Test fun bulkEnableIgnoresInvalidAndUnselectedTracks() = runBlocking {
        val db = database()
        try {
            val repo = MusicRepository(db)
            fun track(name: String, invalid: String? = null) = MusicTrack(displayName = name, uri = "file:///$name", mimeType = "audio/wav",
                durationMillis = 1000, enabled = false, invalidReason = invalid)
            val valid = repo.saveTrack(track("valid"))
            val invalid = repo.saveTrack(track("invalid", "损坏"))
            val untouched = repo.saveTrack(track("untouched"))
            assertEquals(1, repo.setEnabled(listOf(valid, invalid, valid), true))
            assertTrue(requireNotNull(repo.getTrack(valid)).enabled)
            assertFalse(requireNotNull(repo.getTrack(invalid)).enabled)
            assertFalse(requireNotNull(repo.getTrack(untouched)).enabled)
            assertEquals(0, repo.setEnabled(emptyList(), false))
        } finally { db.close() }
    }

    @Test fun usageAccumulationAndHolidayPolicyArePersistent() = runBlocking {
        val db = database()
        try {
            val usage = UsageRepository(db)
            usage.record(Instant.parse("2026-10-19T15:59:58Z"), 5000, true, ZoneId.of("Asia/Shanghai"))
            usage.record(Instant.parse("2026-10-20T00:00:00Z"), 1000, true, ZoneId.of("Asia/Shanghai"))
            val rows = usage.observeDaily().first()
            assertEquals(6000L, rows.sumOf { it.foregroundMillis })
            assertEquals(2, rows.sumOf { it.launches })
            val events = EventRepository(db)
            val id = events.saveEvent(EventDetails(ReminderEvent(title = "工作", scheduleType = ScheduleType.DAILY,
                localDate = LocalDate.of(2026, 1, 1), localTime = LocalTime.NOON, holidayPolicy = HolidayPolicy.MAINLAND_WORKDAYS),
                listOf(ReminderRule(offsetMinutes = 0))))
            assertEquals(HolidayPolicy.MAINLAND_WORKDAYS, events.getEvent(id)?.event?.holidayPolicy)
            events.saveEvent(requireNotNull(events.getEvent(id)).copy(event = requireNotNull(events.getEvent(id)).event.copy(title = "新名称")))
            assertEquals(HolidayPolicy.MAINLAND_WORKDAYS, events.getEvent(id)?.event?.holidayPolicy)
        } finally { db.close() }
    }
}
