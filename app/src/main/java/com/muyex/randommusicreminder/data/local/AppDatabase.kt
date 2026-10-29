package com.muyex.randommusicreminder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.muyex.randommusicreminder.data.local.dao.EventDao
import com.muyex.randommusicreminder.data.local.dao.MusicDao
import com.muyex.randommusicreminder.data.local.dao.OccurrenceDao
import com.muyex.randommusicreminder.data.local.dao.RingSessionDao
import com.muyex.randommusicreminder.data.local.entity.EventEntity
import com.muyex.randommusicreminder.data.local.entity.EventTrackCrossRef
import com.muyex.randommusicreminder.data.local.entity.MusicTrackEntity
import com.muyex.randommusicreminder.data.local.entity.ReminderRuleEntity
import com.muyex.randommusicreminder.data.local.entity.RingSessionEntity
import com.muyex.randommusicreminder.data.local.entity.ScheduledOccurrenceEntity
import com.muyex.randommusicreminder.data.local.entity.ShuffleStateEntity
import com.muyex.randommusicreminder.data.local.entity.RingHistoryEntity
import com.muyex.randommusicreminder.data.local.dao.RingHistoryDao

@Database(
    entities = [
        EventEntity::class,
        ReminderRuleEntity::class,
        MusicTrackEntity::class,
        EventTrackCrossRef::class,
        ScheduledOccurrenceEntity::class,
        ShuffleStateEntity::class,
        RingSessionEntity::class,
        RingHistoryEntity::class,
        com.muyex.randommusicreminder.data.local.entity.UsageDailyEntity::class,
        com.muyex.randommusicreminder.data.local.entity.CalendarOverrideEntity::class,
        com.muyex.randommusicreminder.data.local.entity.AiActionEntity::class,
        com.muyex.randommusicreminder.data.local.entity.TaskInstanceEntity::class,
        com.muyex.randommusicreminder.data.local.entity.TaskActionEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
@TypeConverters(DbConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    abstract fun musicDao(): MusicDao

    abstract fun occurrenceDao(): OccurrenceDao

    abstract fun ringSessionDao(): RingSessionDao

    abstract fun ringHistoryDao(): RingHistoryDao

    abstract fun usageDao(): com.muyex.randommusicreminder.data.local.dao.UsageDao

    abstract fun calendarDao(): com.muyex.randommusicreminder.data.local.dao.CalendarDao
    abstract fun aiActionDao(): com.muyex.randommusicreminder.data.local.dao.AiActionDao
    abstract fun taskDao(): com.muyex.randommusicreminder.data.local.dao.TaskDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "random_music_reminder.db",
                ).addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5, DatabaseMigrations.MIGRATION_5_6, DatabaseMigrations.MIGRATION_6_7, DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9, DatabaseMigrations.MIGRATION_9_10, DatabaseMigrations.MIGRATION_10_11).build().also { instance = it }
            }
        }
    }
}
