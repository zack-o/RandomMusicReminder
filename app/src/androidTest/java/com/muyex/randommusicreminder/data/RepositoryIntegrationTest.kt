package com.muyex.randommusicreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.data.repository.MusicRepository
import com.muyex.randommusicreminder.domain.model.EventDetails
import com.muyex.randommusicreminder.domain.model.MusicPoolMode
import com.muyex.randommusicreminder.domain.model.MusicTrack
import com.muyex.randommusicreminder.domain.model.PlannedOccurrence
import com.muyex.randommusicreminder.domain.model.ReminderEvent
import com.muyex.randommusicreminder.domain.model.ReminderRule
import com.muyex.randommusicreminder.domain.model.ScheduleType
import com.muyex.randommusicreminder.domain.model.OccurrenceKind
import com.muyex.randommusicreminder.domain.model.OccurrenceState
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var eventRepository: EventRepository
    private lateinit var musicRepository: MusicRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventRepository = EventRepository(database)
        musicRepository = MusicRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun eventCrudPersistsMultipleReminderPoints() = runBlocking {
        val id = eventRepository.saveEvent(details())
        val stored = requireNotNull(eventRepository.getEvent(id))
        assertEquals(listOf(-30, 0), stored.rules.map { it.offsetMinutes })

        // UI 编辑时会重新生成规则；仓库按偏移复用 id，避免唯一索引冲突。
        eventRepository.saveEvent(
            stored.copy(
                event = stored.event.copy(title = "更新后的提醒"),
                rules = stored.rules.map { it.copy(id = 0) },
            ),
        )
        assertEquals("更新后的提醒", eventRepository.getEvent(id)?.event?.title)
        assertTrue(eventRepository.setEnabled(id, false))
        assertFalse(requireNotNull(eventRepository.getEvent(id)).event.enabled)
        eventRepository.deleteEvent(id)
        assertNull(eventRepository.getEvent(id))
    }

    @Test
    fun musicTrackPersistsMetadata() = runBlocking {
        val id = musicRepository.saveTrack(
            MusicTrack(
                displayName = "测试音乐.wav",
                uri = "file:///data/user/0/test/files/test.wav",
                mimeType = "audio/wav",
                durationMillis = 1_000,
                fileSizeBytes = 2_048,
            ),
        )

        val stored = requireNotNull(musicRepository.getTrack(id))
        assertEquals(2_048L, stored.fileSizeBytes)
        assertEquals("audio/wav", stored.mimeType)
    }

    @Test
    fun snoozeCoexistsWithOriginalAndStopsAfterThree() = runBlocking {
        val eventId = eventRepository.saveEvent(details())
        val ruleId = requireNotNull(eventRepository.getEvent(eventId)).rules.first().id
        eventRepository.upsertPlannedOccurrences(
            listOf(
                PlannedOccurrence(
                    id = "original-occurrence",
                    eventId = eventId,
                    reminderRuleId = ruleId,
                    baseDate = LocalDate.of(2026, 8, 4),
                    triggerAtEpochMillis = 1_000,
                ),
            ),
        )

        val first = requireNotNull(eventRepository.createSnoozeOccurrence("original-occurrence", 10))
        val second = requireNotNull(eventRepository.createSnoozeOccurrence(first.id, 10))
        val third = requireNotNull(eventRepository.createSnoozeOccurrence(second.id, 10))

        assertEquals(4, eventRepository.getOccurrencesForEvent(eventId).size)
        assertEquals(3, third.snoozeCount)
        assertNull(eventRepository.createSnoozeOccurrence(third.id, 10))
    }

    @Test
    fun testReminderDoesNotReplaceOriginalAndOnlyKeepsLatestPendingTest() = runBlocking {
        val eventId = eventRepository.saveEvent(details())
        val first = eventRepository.createTestOccurrence(eventId)
        val second = eventRepository.createTestOccurrence(eventId)
        val occurrences = eventRepository.getOccurrencesForEvent(eventId)

        assertEquals(OccurrenceKind.TEST, second.kind)
        assertEquals(OccurrenceState.CANCELLED, occurrences.first { it.id == first.id }.state)
        assertEquals(1, occurrences.count { it.state == OccurrenceState.SCHEDULED })
        assertTrue(second.triggerAtEpochMillis > System.currentTimeMillis())
        assertTrue(eventRepository.createSnoozeOccurrences(second.id, 10).isEmpty())
        assertEquals(listOf(-30, 0), requireNotNull(eventRepository.getEvent(eventId)).rules.map { it.offsetMinutes })
    }

    @Test
    fun disabledSnoozeAndCompletedReminderCannotCreateNewSnooze() = runBlocking {
        val configured = details().let { it.copy(rules = it.rules.map { rule -> rule.copy(snoozeMinutes = null) }) }
        val eventId = eventRepository.saveEvent(configured)
        val ruleId = requireNotNull(eventRepository.getEvent(eventId)).rules.first().id
        eventRepository.upsertPlannedOccurrences(
            listOf(PlannedOccurrence("no-snooze", eventId, ruleId, LocalDate.of(2026, 8, 4), 1_000)),
        )
        assertNull(eventRepository.createSnoozeOccurrence("no-snooze", 10))

        val stored = requireNotNull(eventRepository.getEvent(eventId))
        eventRepository.saveEvent(stored.copy(rules = stored.rules.map { it.copy(snoozeMinutes = 10) }))
        eventRepository.markOccurrenceCompleted("no-snooze")
        assertNull(eventRepository.createSnoozeOccurrence("no-snooze", 10))
    }

    private fun details() = EventDetails(
        event = ReminderEvent(
            title = "多时间点提醒",
            scheduleType = ScheduleType.DAILY,
            localDate = LocalDate.of(2026, 8, 4),
            localTime = LocalTime.of(8, 0),
            musicPoolMode = MusicPoolMode.GLOBAL,
        ),
        rules = listOf(
            ReminderRule(offsetMinutes = -30, ringDurationSeconds = 30, snoozeMinutes = 10),
            ReminderRule(offsetMinutes = 0, ringDurationSeconds = 60, snoozeMinutes = 10),
        ),
    )
}
