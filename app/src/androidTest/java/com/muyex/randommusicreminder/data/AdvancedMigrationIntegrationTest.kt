package com.muyex.randommusicreminder.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.local.DatabaseMigrations
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.domain.model.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdvancedMigrationIntegrationTest {
    @Test fun versionFourUpgradesWithoutLosingEventAndPersistsNewFields() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-pro-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = instrumentation.context.assets.open("com.muyex.randommusicreminder.data.local.AppDatabase/4.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices")
                if (indexes != null) for (i in 0 until indexes.length()) sqlite.execSQL(indexes.getJSONObject(i).getString("createSql").replace("\u0024{TABLE_NAME}", table))
            }
            sqlite.execSQL("INSERT INTO events(id,title,note,enabled,scheduleType,localDate,localTime,weekdaysMask,musicPoolMode,createdAtEpochMillis,updatedAtEpochMillis) VALUES (42,'升级保留','备注',1,'DAILY','2026-10-20','09:00',0,'GLOBAL',1,1)")
            sqlite.version = 4
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(DatabaseMigrations.MIGRATION_4_5, DatabaseMigrations.MIGRATION_5_6, DatabaseMigrations.MIGRATION_6_7, DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9, DatabaseMigrations.MIGRATION_9_10, DatabaseMigrations.MIGRATION_10_11).build()
        try {
            val repository = EventRepository(db)
            val original = checkNotNull(repository.getEvent(42))
            assertEquals("升级保留", original.event.title)
            assertTrue(original.event.dayOverrides.isEmpty());assertTrue(original.event.dayOverrideBarriers.isEmpty())
            assertEquals(TimingMode.FIXED, original.event.timingMode); assertEquals(0L, original.event.randomSeed)
            assertEquals(1, original.event.intervalDays); assertEquals(MissingDayPolicy.SKIP, original.event.missingDayPolicy); assertNull(original.event.endDate)
            repository.saveEvent(original.copy(event = original.event.copy(scheduleType = ScheduleType.INTERVAL, intervalDays = 17, endDate = LocalDate.parse("2030-01-01")), rules = listOf(ReminderRule(offsetMinutes = -1440))))
            val updated = checkNotNull(repository.getEvent(42))
            assertEquals(17, updated.event.intervalDays); assertEquals(LocalDate.parse("2030-01-01"), updated.event.endDate)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
