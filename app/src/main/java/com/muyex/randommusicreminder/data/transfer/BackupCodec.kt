package com.muyex.randommusicreminder.data.transfer

import com.muyex.randommusicreminder.data.settings.AppSettings
import com.muyex.randommusicreminder.domain.calendar.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.music.PreviewOptions
import com.muyex.randommusicreminder.domain.transfer.TransferRules
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

data class MusicReference(val name: String, val duration: Long, val bytes: Long)
data class BackupEvent(val details: EventDetails, val music: List<MusicReference> = emptyList())
data class BackupPackage(val settings: AppSettings? = null, val events: List<BackupEvent> = emptyList(), val days: List<CalendarOverride> = emptyList())

object BackupCodec {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun encode(data: BackupPackage): String {
        require(data.events.size <= 1000 && data.days.size <= 5000) { "事件或日期数量超过备份上限" }
        data.events.forEach { TransferRules.validateEvent(it.details) }
        return JSONObject().apply {
        put("format", "lexing-backup"); put("version", 8)
        put("settings", data.settings?.let(::settingsToJson) ?: JSONObject.NULL)
        put("events", JSONArray().apply { data.events.forEach { put(eventToJson(it)) } })
        put("days", JSONArray().apply { data.days.forEach { put(JSONObject().put("date", it.date.toString()).put("type", it.type.name).put("note", it.note).put("source", it.source)) } })
    }.toString(2).also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 2 MiB，请减少事件后分批导出" } }
    }

    fun decode(text: String): BackupPackage {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "文件超过 2 MiB" }
        val root = JSONObject(text)
        require(root.str("format") == "lexing-backup" && root.int("version") in 1..8) { "不是受支持的乐醒版本1～8备份" }
        val eventArray = root.getJSONArray("events"); val dayArray = root.getJSONArray("days")
        require(eventArray.length() <= 1000 && dayArray.length() <= 5000) { "事件或日期数量超过导入上限" }
        val events = (0 until eventArray.length()).map { eventFromJson(eventArray.getJSONObject(it)) }
        val days = (0 until dayArray.length()).map {
            val item = dayArray.getJSONObject(it)
            CalendarOverride(LocalDate.parse(item.str("date")), CalendarDayType.valueOf(item.str("type")),
                item.str("note"), item.str("source"))
        }
        require(days.map { it.date }.distinct().size == days.size) { "同一天不能包含多条日历覆盖" }
        return BackupPackage(if (root.isNull("settings")) null else settingsFromJson(root.getJSONObject("settings")), events, days)
    }

    fun eventToJson(item: BackupEvent): JSONObject = JSONObject().apply {
        val event = item.details.event
        put("title", event.title); put("note", event.note); put("enabled", event.enabled)
        put("tags", JSONArray(com.muyex.randommusicreminder.domain.organizer.EventTags.normalize(event.tags)))
        put("dayOverrides",JSONArray(DayOverrideCodec.encode(event.dayOverrides)))
        put("trackCompletion",event.trackCompletion);put("outputMode",event.outputMode.name);put("haptics",HapticSettingsCodec.encode(event.haptics))
        put("repeat", event.scheduleType.name); put("date", event.localDate.toString()); put("time", event.localTime.toString())
        put("weekdays", event.weekdaysMask); put("holidayPolicy", event.holidayPolicy.name); put("musicPool", event.musicPoolMode.name)
        put("intervalDays", event.intervalDays); put("missingDayPolicy", event.missingDayPolicy.name); put("endDate", event.endDate?.toString() ?: JSONObject.NULL)
        put("timingMode", event.timingMode.name); put("windowEndTime", event.windowEndTime.toString())
        put("windowCrossesMidnight", event.windowCrossesMidnight); put("windowAlertMode", event.windowAlertMode.name)
        put("windowDurationSeconds", event.windowDurationSeconds); put("windowSnoozeMinutes", event.windowSnoozeMinutes ?: JSONObject.NULL)
        put("windowBackupEnabled", event.windowBackupEnabled); put("hideWindowSelection", event.hideWindowSelection); put("shiftCycle", event.shiftCycle?.let { JSONArray(ShiftCycleCodec.encode(it)) } ?: JSONObject.NULL)
        put("rules", JSONArray().apply { item.details.rules.forEach { rule -> put(JSONObject().apply {
            put("offset", rule.offsetMinutes); put("mode", rule.alertMode.name); put("duration", rule.ringDurationSeconds)
            put("snooze", rule.snoozeMinutes ?: JSONObject.NULL)
        }) } })
        put("music", JSONArray().apply { item.music.forEach { put(JSONObject().put("name", it.name).put("duration", it.duration).put("bytes", it.bytes)) } })
    }

    fun eventFromJson(item: JSONObject): BackupEvent {
        val rules = item.getJSONArray("rules"); val music = item.getJSONArray("music")
        require(rules.length() in 1..32 && music.length() <= 1000) { "提醒点或音乐引用数量无效" }
        val event = ReminderEvent(title = item.str("title"), note = item.str("note"), enabled = item.bool("enabled"),
            dayOverrides=if(item.has("dayOverrides")) DayOverrideCodec.decode(item.getJSONArray("dayOverrides").toString()) else emptyList(),
            trackCompletion=item.has("trackCompletion") && item.bool("trackCompletion"),
            tags = if(item.has("tags")) EventTagsCodec.decode(item.getJSONArray("tags").toString()) else emptyList(),
            outputMode = if(item.has("outputMode")) com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.valueOf(item.str("outputMode")) else com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.MUSIC,
            haptics = if(item.has("haptics")) HapticSettingsCodec.decode(item.getJSONObject("haptics")) else com.muyex.randommusicreminder.domain.haptics.HapticSettings(),
            scheduleType = ScheduleType.valueOf(item.str("repeat")), localDate = LocalDate.parse(item.str("date")),
            localTime = LocalTime.parse(item.str("time")), weekdaysMask = item.int("weekdays"),
            holidayPolicy = HolidayPolicy.valueOf(item.str("holidayPolicy")), musicPoolMode = MusicPoolMode.valueOf(item.str("musicPool")),
            intervalDays = if (item.has("intervalDays")) item.int("intervalDays") else 1,
            missingDayPolicy = if (item.has("missingDayPolicy")) MissingDayPolicy.valueOf(item.str("missingDayPolicy")) else MissingDayPolicy.SKIP,
            endDate = if (item.isNull("endDate")) null else LocalDate.parse(item.str("endDate")),
            timingMode = if (item.has("timingMode")) TimingMode.valueOf(item.str("timingMode")) else TimingMode.FIXED,
            windowEndTime = if (item.has("windowEndTime")) LocalTime.parse(item.str("windowEndTime")) else LocalTime.of(8, 10),
            windowCrossesMidnight = item.has("windowCrossesMidnight") && item.bool("windowCrossesMidnight"),
            windowAlertMode = if (item.has("windowAlertMode")) AlertMode.valueOf(item.str("windowAlertMode")) else AlertMode.GENTLE,
            windowDurationSeconds = if (item.has("windowDurationSeconds")) item.int("windowDurationSeconds") else 30,
            windowSnoozeMinutes = if (item.isNull("windowSnoozeMinutes")) null else item.int("windowSnoozeMinutes"),
            windowBackupEnabled = item.has("windowBackupEnabled") && item.bool("windowBackupEnabled"),
            hideWindowSelection = !item.has("hideWindowSelection") || item.bool("hideWindowSelection"),
            shiftCycle = if (item.isNull("shiftCycle")) null else ShiftCycleCodec.decode(item.getJSONArray("shiftCycle").toString()))
        val details = EventDetails(event, (0 until rules.length()).map {
            val rule = rules.getJSONObject(it)
            ReminderRule(offsetMinutes = rule.int("offset"), alertMode = AlertMode.valueOf(rule.str("mode")),
                ringDurationSeconds = rule.int("duration"), snoozeMinutes = if (rule.isNull("snooze")) null else rule.int("snooze"))
        })
        TransferRules.validateEvent(details)
        val refs = (0 until music.length()).map {
            val ref = music.getJSONObject(it)
            MusicReference(ref.str("name"), ref.long("duration"), ref.long("bytes")).also {
                require(it.name.isNotBlank() && it.name.length <= 500 && it.duration > 0 && it.bytes >= 0) { "音乐引用无效" }
            }
        }
        return BackupEvent(details, refs)
    }

    private fun settingsToJson(settings: AppSettings) = JSONObject().apply {
        put("gentle", settings.gentleDurationSeconds); put("strong", settings.strongDurationSeconds)
        put("snooze", settings.defaultSnoozeMinutes); put("maxSnooze", settings.maxSnoozeCount)
        put("notifyMissed", settings.notifyMissedReminders); put("usage", settings.usageStatsEnabled)
        put("previewDuration", settings.previewOptions.durationSeconds); put("previewVolume", settings.previewOptions.volumePercent); put("previewStart", settings.previewOptions.startSeconds)
        put("appearance", JSONObject().put("mode", settings.appearance.mode.name).put("palette", settings.appearance.palette.name).put("customColor", settings.appearance.customColor))
    }

    private fun settingsFromJson(item: JSONObject): AppSettings = AppSettings(
        gentleDurationSeconds = item.int("gentle"), strongDurationSeconds = item.int("strong"), defaultSnoozeMinutes = item.int("snooze"),
        maxSnoozeCount = item.int("maxSnooze"), notifyMissedReminders = item.bool("notifyMissed"), usageStatsEnabled = item.bool("usage"),
        previewOptions = PreviewOptions(item.int("previewDuration"), item.int("previewVolume"), item.int("previewStart")),
        appearance = if (!item.has("appearance")) com.muyex.randommusicreminder.domain.settings.AppearanceSettings() else item.getJSONObject("appearance").let {
            com.muyex.randommusicreminder.domain.settings.AppearanceSettings(com.muyex.randommusicreminder.domain.settings.ThemeMode.valueOf(it.str("mode")), com.muyex.randommusicreminder.domain.settings.ThemePalette.valueOf(it.str("palette")), it.str("customColor"))
        },
    ).also { require(it.gentleDurationSeconds in 5..1800 && it.strongDurationSeconds in 5..1800 && it.defaultSnoozeMinutes in setOf(5, 10, 15) && it.maxSnoozeCount == 3) { "设置参数无效" } }
}

internal fun JSONObject.long(name: String): Long {
    val value = get(name)
    require(value is Number) { "$name 须为整数" }
    return value.toString().toLongOrNull() ?: throw IllegalArgumentException("$name 须为有效整数")
}
internal fun JSONObject.int(name: String): Int = long(name).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "$name 超出范围" } }.toInt()
internal fun JSONObject.bool(name: String): Boolean = get(name).let { require(it is Boolean) { "$name 须为布尔值" }; it }
internal fun JSONObject.str(name: String): String = get(name).let { require(it is String) { "$name 须为文本" }; it }
