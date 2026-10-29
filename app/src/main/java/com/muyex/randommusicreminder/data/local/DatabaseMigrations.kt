package com.muyex.randommusicreminder.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_10_11: Migration = object : Migration(10,11) {
        override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `dayOverridesJson` TEXT NOT NULL DEFAULT '[]'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `dayOverrideBarriersJson` TEXT NOT NULL DEFAULT '{}'""".trimIndent())
            db.execSQL("""ALTER TABLE `scheduled_occurrences` ADD COLUMN `dayOverrideSignature` TEXT NOT NULL DEFAULT ''""".trimIndent())
        }
    }
    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `trackCompletion` INTEGER NOT NULL DEFAULT 0""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `completionTrackingStartedAt` INTEGER""".trimIndent())
            db.execSQL("""ALTER TABLE `scheduled_occurrences` ADD COLUMN `taskOccurrenceKey` TEXT""".trimIndent())
            db.execSQL("""UPDATE `scheduled_occurrences` SET `taskOccurrenceKey`='day:' || `baseDate` WHERE `kind`!='TEST' AND `sourceLabel` IS NULL AND `eventId` IN (SELECT `id` FROM `events` WHERE `scheduleType`!='SHIFT')""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS `task_instances` (`eventId` INTEGER NOT NULL, `taskKey` TEXT NOT NULL, `baseDate` TEXT NOT NULL, `label` TEXT NOT NULL, `taskAtEpochMillis` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`eventId`,`taskKey`), FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""".trimIndent())
            db.execSQL("""CREATE INDEX IF NOT EXISTS `index_task_instances_eventId` ON `task_instances` (`eventId`)""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS `task_actions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `taskKey` TEXT NOT NULL, `baseDate` TEXT NOT NULL, `occurrenceLabel` TEXT NOT NULL, `titleSnapshot` TEXT NOT NULL, `action` TEXT NOT NULL, `happenedAtEpochMillis` INTEGER NOT NULL)""".trimIndent())
            db.execSQL("""CREATE INDEX IF NOT EXISTS `index_task_actions_eventId_taskKey_id` ON `task_actions` (`eventId`,`taskKey`,`id`)""".trimIndent())
        }
    }

    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `outputMode` TEXT NOT NULL DEFAULT 'MUSIC'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `hapticPreset` TEXT NOT NULL DEFAULT 'PULSE'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `hapticStrength` TEXT NOT NULL DEFAULT 'STANDARD'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `hapticGapMillis` INTEGER NOT NULL DEFAULT 1000""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `hapticUseMusicRhythm` INTEGER NOT NULL DEFAULT 0""".trimIndent())
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `tagsJson` TEXT NOT NULL DEFAULT '[]'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `deletedAtEpochMillis` INTEGER""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `resumedAfterEpochMillis` INTEGER""".trimIndent())
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `shiftCycleJson` TEXT NOT NULL DEFAULT ''""".trimIndent())
            db.execSQL("""ALTER TABLE `scheduled_occurrences` ADD COLUMN `sourceLabel` TEXT""".trimIndent())
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `timingMode` TEXT NOT NULL DEFAULT 'FIXED'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `windowEndTime` TEXT NOT NULL DEFAULT '08:10'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `windowCrossesMidnight` INTEGER NOT NULL DEFAULT 0""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `windowAlertMode` TEXT NOT NULL DEFAULT 'GENTLE'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `windowDurationSeconds` INTEGER NOT NULL DEFAULT 30""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `windowSnoozeMinutes` INTEGER""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `windowBackupEnabled` INTEGER NOT NULL DEFAULT 0""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `hideWindowSelection` INTEGER NOT NULL DEFAULT 1""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `randomSeed` INTEGER NOT NULL DEFAULT 0""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `timingRevision` INTEGER NOT NULL DEFAULT 0""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `windowFirstNotBeforeEpochMillis` INTEGER""".trimIndent())
            db.execSQL("""ALTER TABLE `scheduled_occurrences` ADD COLUMN `selectedLocalMinute` INTEGER""".trimIndent())
            db.execSQL("""ALTER TABLE `scheduled_occurrences` ADD COLUMN `windowStartAtEpochMillis` INTEGER""".trimIndent())
            db.execSQL("""ALTER TABLE `scheduled_occurrences` ADD COLUMN `windowEndAtEpochMillis` INTEGER""".trimIndent())
            db.execSQL("""ALTER TABLE `scheduled_occurrences` ADD COLUMN `windowBackup` INTEGER NOT NULL DEFAULT 0""".trimIndent())
            db.execSQL("""ALTER TABLE `ring_history` ADD COLUMN `timingSummary` TEXT""".trimIndent())
            db.execSQL("""CREATE TABLE IF NOT EXISTS `ai_actions` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `state` TEXT NOT NULL, `beforeJson` TEXT NOT NULL, `afterJson` TEXT NOT NULL, PRIMARY KEY(`id`))""".trimIndent())
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `intervalDays` INTEGER NOT NULL DEFAULT 1""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `missingDayPolicy` TEXT NOT NULL DEFAULT 'SKIP'""".trimIndent())
            db.execSQL("""ALTER TABLE `events` ADD COLUMN `endDate` TEXT""".trimIndent())
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `calendar_overrides` (
                    `date` TEXT NOT NULL PRIMARY KEY,
                    `dayType` TEXT NOT NULL,
                    `note` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                ALTER TABLE `events` ADD COLUMN `holidayPolicy` TEXT NOT NULL DEFAULT 'NONE'
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `usage_daily` (
                    `date` TEXT NOT NULL PRIMARY KEY,
                    `launches` INTEGER NOT NULL,
                    `foregroundMillis` INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `ring_history` (
                    `sessionId` TEXT NOT NULL PRIMARY KEY,
                    `anchorOccurrenceId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `eventCount` INTEGER NOT NULL,
                    `scheduledAtEpochMillis` INTEGER NOT NULL,
                    `startedAtEpochMillis` INTEGER NOT NULL,
                    `endedAtEpochMillis` INTEGER,
                    `musicName` TEXT,
                    `alertMode` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `stopReason` TEXT
                )
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_ring_history_anchorOccurrenceId`
                ON `ring_history` (`anchorOccurrenceId`)
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_ring_history_startedAtEpochMillis`
                ON `ring_history` (`startedAtEpochMillis`)
            """.trimIndent())
            // 旧会话没有结束时间；不要把计划时长或缺失曲目伪造成实际播放信息。
            db.execSQL("""
                INSERT OR IGNORE INTO ring_history (
                    sessionId, anchorOccurrenceId, title, eventCount, scheduledAtEpochMillis, startedAtEpochMillis,
                    endedAtEpochMillis, musicName, alertMode, kind, state, stopReason
                )
                SELECT s.id, COALESCE(MIN(o.id), s.id), COALESCE(GROUP_CONCAT(DISTINCT e.title), '已删除的提醒'),
                    COUNT(DISTINCT e.id), s.scheduledTriggerAtEpochMillis, s.startedAtEpochMillis,
                    NULL, t.displayName,
                    CASE WHEN MAX(CASE WHEN r.alertMode = 'STRONG' THEN 1 ELSE 0 END) = 1
                        THEN 'STRONG' ELSE 'GENTLE' END,
                    CASE WHEN MAX(CASE WHEN o.kind = 'TEST' THEN 1 ELSE 0 END) = 1 THEN 'TEST'
                        WHEN MAX(CASE WHEN o.kind = 'SNOOZE' THEN 1 ELSE 0 END) = 1 THEN 'SNOOZE'
                        ELSE 'ORIGINAL' END,
                    s.state, s.stopReason
                FROM ring_sessions s
                LEFT JOIN scheduled_occurrences o ON o.sessionId = s.id
                LEFT JOIN events e ON e.id = o.eventId
                LEFT JOIN reminder_rules r ON r.id = o.reminderRuleId
                LEFT JOIN music_tracks t ON t.id = s.selectedTrackId
                GROUP BY s.id
            """.trimIndent())
        }
    }
}
