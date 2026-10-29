package com.muyex.randommusicreminder.data

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.util.UUID
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.*
import com.muyex.randommusicreminder.data.settings.SettingsRepository
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.domain.calendar.*
import com.muyex.randommusicreminder.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataMergeIntegrationTest {
    private fun isolatedFilesContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val files = File(base.cacheDir, "merge-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
        // Room 使用内存数据库，恢复快照也写入独立测试缓存，不干扰用户的最近一次导入快照。
        return object : ContextWrapper(base) { override fun getFilesDir(): File = files }
    }
    @Test fun mergeIsIdempotentAndRetainsExistingCalendarWithoutEnablingTasks() = runBlocking {
        val context = isolatedFilesContext()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val events = EventRepository(db); val music = MusicRepository(db); val calendar = CalendarRepository(db)
            val transfer = DataTransferService(context, db, events, music, calendar, SettingsRepository(context))
            val date = LocalDate.parse("2027-01-02")
            calendar.setDays(listOf(date), CalendarDayType.WORK, "已存在")
            val event = BackupEvent(EventDetails(ReminderEvent(title = "新增", scheduleType = ScheduleType.DAILY,
                localDate = date, localTime = LocalTime.NOON), listOf(ReminderRule(offsetMinutes = 0))))
            val preview = transfer.prepare(BackupPackage(events = listOf(event, event), days = listOf(CalendarOverride(date, CalendarDayType.REST))))
            assertEquals(1, preview.events.size); assertEquals(0, preview.days.size)
            assertEquals(1, transfer.merge(preview, false).events)
            assertFalse(events.getEvents().single().event.enabled)
            assertEquals(0, transfer.merge(preview, true).events)
            assertEquals(CalendarDayType.WORK, calendar.getAll().single().type)
        } finally { db.close() }
    }
    @Test fun missingMusicAndEmptyCustomPoolSkipRatherThanChangePoolSilently() = runBlocking {
        val context = isolatedFilesContext()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val transfer = DataTransferService(context, db, EventRepository(db), MusicRepository(db), CalendarRepository(db), SettingsRepository(context))
            val event = EventDetails(ReminderEvent(title = "自选", scheduleType = ScheduleType.DAILY,
                localDate = LocalDate.parse("2027-01-02"), localTime = LocalTime.NOON, musicPoolMode = MusicPoolMode.CUSTOM), listOf(ReminderRule(offsetMinutes = 0)))
            val preview = transfer.prepare(BackupPackage(events = listOf(BackupEvent(event), BackupEvent(event, listOf(MusicReference("不存在.mp3", 10000, 500))))))
            assertTrue(preview.events.isEmpty()); assertEquals(2, preview.warnings.size)
        } finally { db.close() }
    }
}
