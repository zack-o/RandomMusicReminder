package com.muyex.randommusicreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muyex.randommusicreminder.data.local.AppDatabase
import com.muyex.randommusicreminder.data.repository.EventRepository
import com.muyex.randommusicreminder.domain.model.EventDetails
import com.muyex.randommusicreminder.domain.model.PlannedOccurrence
import com.muyex.randommusicreminder.domain.model.ReminderEvent
import com.muyex.randommusicreminder.domain.model.ReminderRule
import com.muyex.randommusicreminder.domain.model.RingSessionState
import com.muyex.randommusicreminder.domain.model.RingStopReason
import com.muyex.randommusicreminder.domain.model.ScheduleType
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RingHistoryIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: EventRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = EventRepository(database, nowEpochMillis = { now })
    }

    @After
    fun close() = database.close()

    @Test
    fun historySurvivesEventRenameAndDeletionAndStopIsIdempotent() = runBlocking {
        val eventId = createOccurrence("喝水", "occurrence-1")
        val claim = requireNotNull(repository.claimSimultaneous("occurrence-1", null, "history-1"))
        repository.recordPlayingMusic("occurrence-1", "森林.mp3")
        val event = requireNotNull(repository.getEvent(eventId))
        repository.saveEvent(event.copy(event = event.event.copy(title = "新名称")))
        repository.deleteEvent(eventId)
        repository.permanentlyDeleteEvents(listOf(eventId))

        assertEquals("history-1", repository.findSessionId("occurrence-1"))
        now = 18_000
        assertTrue(repository.completeRingSession(claim.session.id, RingStopReason.USER))
        now = 22_000
        assertFalse(repository.completeRingSession(claim.session.id, RingStopReason.TIMEOUT))
        val history = repository.observeRingHistory().first().single()
        assertEquals("喝水", history.title)
        assertEquals("森林.mp3", history.musicName)
        assertEquals(18_000L, history.endedAtEpochMillis)
        assertEquals(17_000L, history.durationMillis)
        assertEquals(RingStopReason.USER, history.stopReason)
    }

    @Test
    fun simultaneousEventsCreateOnlyOneHistoryEntry() = runBlocking {
        createOccurrence("会议", "meeting")
        createOccurrence("喝水", "water")
        assertNotNull(repository.claimSimultaneous("meeting", null, "merged"))
        assertNull(repository.claimSimultaneous("water", null))
        val history = repository.observeRingHistory().first().single()
        assertEquals(2, history.eventCount)
        assertTrue(history.title.contains("会议"))
        assertTrue(history.title.contains("喝水"))
    }

    @Test
    fun interruptedHistoryDoesNotInventEndTime() = runBlocking {
        createOccurrence("会议", "meeting")
        repository.claimSimultaneous("meeting", null, "interrupted")
        database.ringHistoryDao().markInterrupted(2_000)
        val history = repository.observeRingHistory().first().single()
        assertEquals(RingSessionState.FAILED, history.state)
        assertNull(history.endedAtEpochMillis)
    }

    private suspend fun createOccurrence(title: String, occurrenceId: String): Long {
        val eventId = repository.saveEvent(
            EventDetails(
                ReminderEvent(title = title, scheduleType = ScheduleType.DAILY,
                    localDate = LocalDate.of(2026, 10, 19), localTime = LocalTime.NOON),
                rules = listOf(ReminderRule(offsetMinutes = 0, ringDurationSeconds = 30)),
            ),
        )
        val ruleId = requireNotNull(repository.getEvent(eventId)).rules.single().id
        repository.upsertPlannedOccurrences(
            listOf(PlannedOccurrence(occurrenceId, eventId, ruleId, LocalDate.of(2026, 10, 19), 900)),
        )
        return eventId
    }
}
