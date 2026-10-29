package com.muyex.randommusicreminder.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muyex.randommusicreminder.ai.AiDraftCodec
import com.muyex.randommusicreminder.data.transfer.*
import com.muyex.randommusicreminder.data.settings.AppSettings
import com.muyex.randommusicreminder.domain.calendar.*
import com.muyex.randommusicreminder.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupAndAiCodecTest {
    private val data = BackupPackage(AppSettings(), listOf(BackupEvent(EventDetails(ReminderEvent(title = "测试", scheduleType = ScheduleType.DAILY,
        localDate = LocalDate.parse("2026-10-20"), localTime = LocalTime.NOON), listOf(ReminderRule(offsetMinutes = -10), ReminderRule(offsetMinutes = 0))))),
        listOf(CalendarOverride(LocalDate.parse("2027-01-02"), CalendarDayType.REST, "公司休假")))

    @Test fun nativeJsonRoundTripContainsNoPathsOrSecrets() {
        val encoded = BackupCodec.encode(data)
        assertFalse(encoded.contains("file://")); assertFalse(encoded.contains("apiKey"))
        assertEquals(data, BackupCodec.decode(encoded))
    }
    @Test fun rejectsUnknownVersionAndFractionalInteger() {
        val root = JSONObject(BackupCodec.encode(data)).put("version", 99)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
        root.put("version", 2).getJSONObject("settings").put("gentle", 30.5)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
    }
    @Test fun refusesOversizeAndDuplicateCalendarDays() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(" ".repeat(BackupCodec.MAX_BYTES + 1)) }
        val root = JSONObject(BackupCodec.encode(data)); val days = root.getJSONArray("days"); days.put(days.get(0))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
    }
    @Test fun rejectsStringCoercionAndCanReadEmptyLegacyCustomPoolForPreview() {
        val root = JSONObject(BackupCodec.encode(data))
        root.getJSONArray("events").getJSONObject(0).put("title", 123)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
        val emptyPool = data.events.single().copy(details = data.events.single().details.copy(event = data.events.single().details.event.copy(musicPoolMode = MusicPoolMode.CUSTOM)))
        assertEquals(MusicPoolMode.CUSTOM, BackupCodec.decode(BackupCodec.encode(data.copy(events = listOf(emptyPool)))).events.single().details.event.musicPoolMode)
    }
    @Test fun aiDraftParsesOnlyStructuredBoundedActions() {
        val json = """{"summary":"请确认","tasks":[{"title":"会议","note":"","date":"2026-10-21","time":"15:00","repeat":"ONCE","weekdays":[],"offsets":[-30,0],"duration":30,"mode":"GENTLE","holidayPolicy":"NONE"}],"days":[{"date":"2027-01-02","type":"REST","note":"通知识别"}]}"""
        val draft = AiDraftCodec.decode(json)
        assertEquals(2, draft.data.events.single().details.rules.size)
        assertEquals("AI 草稿（用户确认）", draft.data.days.single().source)
        val modified = JSONObject(json); modified.getJSONArray("tasks").getJSONObject(0).getJSONArray("offsets").put(0, 30)
        assertThrows(IllegalArgumentException::class.java) { AiDraftCodec.decode(modified.toString()) }
    }
    @Test fun aiDoesNotAcceptArbitraryCommandsAsTasks() {
        assertThrows(Exception::class.java) { AiDraftCodec.decode("""{"sql":"DELETE FROM events"}""") }
    }
    @Test fun versionOneImportsDefaultsAndVersionTwoKeepsProfessionalRules() {
        val legacy = JSONObject(BackupCodec.encode(data)).put("version", 1)
        legacy.getJSONObject("settings").remove("appearance")
        val event = legacy.getJSONArray("events").getJSONObject(0)
        event.remove("intervalDays"); event.remove("missingDayPolicy"); event.remove("endDate")
        assertEquals(1, BackupCodec.decode(legacy.toString()).events.single().details.event.intervalDays)
        val advanced = data.events.single().details.let { it.copy(event = it.event.copy(scheduleType = ScheduleType.INTERVAL, intervalDays = 17, endDate = LocalDate.parse("2030-01-01"))) }
        val appearance = com.muyex.randommusicreminder.domain.settings.AppearanceSettings(palette = com.muyex.randommusicreminder.domain.settings.ThemePalette.CUSTOM, customColor = "#336699")
        val newer = data.copy(events = listOf(BackupEvent(advanced)), settings = AppSettings(appearance = appearance))
        assertEquals(newer, BackupCodec.decode(BackupCodec.encode(newer)))
    }
}
