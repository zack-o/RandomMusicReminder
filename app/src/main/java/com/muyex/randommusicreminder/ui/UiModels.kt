package com.muyex.randommusicreminder.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.muyex.randommusicreminder.domain.ringing.RingHistoryEntry

enum class AppSection(val label: String) {
    REMINDERS("提醒"),
    MUSIC("音乐"),
    SETTINGS("设置"),
    HISTORY("响铃历史"),
    STATISTICS("数据统计"),
    CALENDAR("日历"),
    AI("AI助手"),
    DATA("导入导出"),
    APPEARANCE("外观与主题"), REMINDER_SETTINGS("提醒与权限"),
    TAGS("标签管理"), TRASH("回收站"), COMPLETIONS("完成记录"),
}

enum class UiScheduleType(val label: String) {
    ONCE("单次"),
    DAILY("每日"),
    WEEKLY("每周"),
    MONTHLY("每月"), YEARLY("每年"), INTERVAL("每N天"), SHIFT("轮班"),
}

enum class UiAlertMode(val label: String, val description: String) {
    GENTLE("温和", "普通提醒通知，不主动打断当前操作"),
    STRONG("强提醒", "全屏提示并持续响铃，适合不能错过的事情"),
}

enum class UiMusicPoolMode(val label: String) {
    GLOBAL("全部音乐"),
    CUSTOM("自选音乐"),
}

data class ReminderListItem(
    val id: Long,
    val title: String,
    val scheduleText: String,
    val timeText: String,
    val alertMode: UiAlertMode,
    val durationSeconds: Int,
    val musicText: String,
    val enabled: Boolean,
    val nextReminderText: String = "尚未安排下一次提醒",
    val holidayPolicy: com.muyex.randommusicreminder.domain.calendar.HolidayPolicy = com.muyex.randommusicreminder.domain.calendar.HolidayPolicy.NONE,
    val eventTimeText: String = "",
    val randomWindow: Boolean = false,
    val selectionText: String? = null,
    val nextAtEpochMillis: Long? = null,
    val hasActiveBackup: Boolean = false,
    val note: String = "", val createdAtEpochMillis: Long = 0, val updatedAtEpochMillis: Long = 0,
    val tags: List<String> = emptyList(), val deletedAtEpochMillis: Long? = null,
    val trackCompletion:Boolean=false,
    val outputMode: com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode = com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.MUSIC,
)

data class MusicListItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val available: Boolean,
    val durationMillis: Long = 0,
    val fileSizeBytes: Long = 0,
    val importedAtEpochMillis: Long = 0,
    val uri: String = "",
)

data class ReminderPointDraftUi(
    /** 0 表示准时，负数表示相对事件时刻提前。 */
    val offsetMinutes: Int = 0,
    val alertMode: UiAlertMode = UiAlertMode.GENTLE,
    val durationSeconds: Int = 30,
    val snoozeMinutes: Int? = 10,
)

data class EventDraftUi(
    val id: Long? = null,
    val title: String = "",
    val note: String = "",
    val scheduleType: UiScheduleType = UiScheduleType.DAILY,
    val onceDate: LocalDate = LocalDate.now(),
    val weeklyDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
    val eventTime: LocalTime = LocalTime.of(8, 0),
    val reminderPoints: List<ReminderPointDraftUi> = listOf(ReminderPointDraftUi()),
    val alertMode: UiAlertMode = UiAlertMode.GENTLE,
    val durationSeconds: Int = 30,
    val snoozeMinutes: Int? = 10,
    val musicPoolMode: UiMusicPoolMode = UiMusicPoolMode.GLOBAL,
    val customTrackIds: Set<Long> = emptySet(),
    val enabled: Boolean = true,
    val holidayPolicy: com.muyex.randommusicreminder.domain.calendar.HolidayPolicy = com.muyex.randommusicreminder.domain.calendar.HolidayPolicy.NONE,
    val intervalDays: Int = 1,
    val missingDayPolicy: com.muyex.randommusicreminder.domain.model.MissingDayPolicy = com.muyex.randommusicreminder.domain.model.MissingDayPolicy.SKIP,
    val endDate: LocalDate? = null,
    val timingMode: com.muyex.randommusicreminder.domain.model.TimingMode = com.muyex.randommusicreminder.domain.model.TimingMode.FIXED,
    val windowEndTime: LocalTime = LocalTime.of(8, 10),
    val windowCrossesMidnight: Boolean = false,
    val windowAlertMode: UiAlertMode = UiAlertMode.GENTLE,
    val windowDurationSeconds: Int = 30,
    val windowSnoozeMinutes: Int? = null,
    val windowBackupEnabled: Boolean = false,
    val hideWindowSelection: Boolean = true,
    val windowFirstNotBeforeEpochMillis: Long? = null,
    val shiftCycle: com.muyex.randommusicreminder.domain.planner.ShiftCycle = com.muyex.randommusicreminder.domain.planner.ShiftCycle.template(),
    val tags: List<String> = emptyList(),
    val dayOverrides:List<com.muyex.randommusicreminder.domain.planner.DayOverride> = emptyList(),
    val trackCompletion:Boolean=false,
    val outputMode: com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode = com.muyex.randommusicreminder.domain.haptics.ReminderOutputMode.MUSIC,
    val haptics: com.muyex.randommusicreminder.domain.haptics.HapticSettings = com.muyex.randommusicreminder.domain.haptics.HapticSettings(),
)

data class SettingsUi(
    val gentleDurationSeconds: Int = 30,
    val strongDurationSeconds: Int = 60,
    val defaultSnoozeMinutes: Int = 10,
    val notifyMissedReminders: Boolean = true,
    val previewOptions: com.muyex.randommusicreminder.domain.music.PreviewOptions = com.muyex.randommusicreminder.domain.music.PreviewOptions(),
    val usageStatsEnabled: Boolean = true,
    val appearance: com.muyex.randommusicreminder.domain.settings.AppearanceSettings = com.muyex.randommusicreminder.domain.settings.AppearanceSettings(),
)

data class PermissionHealthUi(
    val notificationsGranted: Boolean,
    val exactAlarmGranted: Boolean,
    val fullScreenIntentGranted: Boolean,
    val fullScreenIntentRequired: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val backgroundRestricted: Boolean = false,
) {
    val allReady: Boolean
        get() = notificationsGranted && exactAlarmGranted &&
            (!fullScreenIntentRequired || fullScreenIntentGranted)
}

data class AppUiState(
    val selectedSection: AppSection = AppSection.REMINDERS,
    val events: List<ReminderListItem> = emptyList(),
    val deletedEvents: List<ReminderListItem> = emptyList(),
    val organizerBusy: Boolean = false,
    val completionChoices:com.muyex.randommusicreminder.domain.tasks.TaskChoiceList?=null,
    val taskRecords:List<com.muyex.randommusicreminder.domain.tasks.TaskActionRecord> = emptyList(),
    val tracks: List<MusicListItem> = emptyList(),
    val settings: SettingsUi = SettingsUi(),
    val editingEvent: EventDraftUi? = null,
    val previewingTrackId: Long? = null,
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val message: String? = null,
    val ringHistory: List<RingHistoryEntry> = emptyList(),
    val importProgress: MusicImportProgress? = null,
    val dailyUsage: List<com.muyex.randommusicreminder.domain.statistics.DailyUsage> = emptyList(),
)
