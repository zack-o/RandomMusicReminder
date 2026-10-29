package com.muyex.randommusicreminder.domain.transfer

import com.muyex.randommusicreminder.domain.model.*

object TransferRules {
    fun validateEvent(details: EventDetails) {
        val event = details.event
        com.muyex.randommusicreminder.domain.organizer.EventTags.normalize(event.tags)
        event.haptics.validate()
        require(event.title.isNotBlank() && event.title.length <= 500 && event.note.length <= 10_000) { "事项名称或详细内容无效" }
        require(event.localDate.year in 1900..2200) { "事件日期须在 1900～2200 年" }
        require(event.intervalDays in 1..3650) { "间隔须为1～3650个日历日" }
        require(event.localTime.nano == 0) { "事项时刻精确到秒，不使用小数秒" }
        event.shiftCycle?.validate()
        com.muyex.randommusicreminder.domain.planner.DayOverrides.validate(event.dayOverrides,event.scheduleType)
        if (event.scheduleType == ScheduleType.SHIFT) {
            requireNotNull(event.shiftCycle) { "轮班事项需要周期配置" }
            require(event.timingMode == TimingMode.FIXED) { "轮班目前使用逐日固定时刻，不混用随机窗口" }
        }
        require(event.endDate == null || (event.endDate >= event.localDate && event.endDate.year in 1900..2200)) { "结束日期不得早于开始日期，且须在1900～2200年" }
        if (event.timingMode == TimingMode.RANDOM_WINDOW) {
            com.muyex.randommusicreminder.domain.planner.RandomWindow.endMinute(event.localTime, event.windowEndTime, event.windowCrossesMidnight)
            require(event.windowDurationSeconds in 5..1800 && event.windowSnoozeMinutes in setOf(null,5,10,15)) { "随机主提醒参数超出范围" }
        }
        require(details.rules.size in 1..32 && details.rules.map { it.offsetMinutes }.distinct().size == details.rules.size) { "须有 1～32 个不重复提醒点" }
        require(details.rules.all { it.offsetMinutes in -5_256_000..0 && it.ringDurationSeconds in 5..1800 && it.snoozeMinutes in setOf(null, 5, 10, 15) }) { "提醒点参数超出范围" }
        require(event.weekdaysMask in 0..127 && (event.scheduleType != ScheduleType.WEEKLY || event.weekdaysMask != 0)) { "每周重复须选择有效星期" }
    }

    fun fingerprint(details: EventDetails): String {
        val event = details.event
        // 不使用数据库 id/启用状态判重，避免同一份备份反复导入后重复触发。
        val fields = listOf(event.title.trim(), event.note.trim(), event.scheduleType.name, event.localDate.toString(),
            event.localTime.toString(), event.weekdaysMask.toString(), event.holidayPolicy.name, event.musicPoolMode.name,
            details.customTrackIds.sorted().joinToString(","), event.intervalDays.toString(), event.missingDayPolicy.name, event.endDate?.toString().orEmpty(),
            event.timingMode.name, event.windowEndTime.toString(), event.windowCrossesMidnight.toString(), event.windowAlertMode.name,
            event.windowDurationSeconds.toString(), event.windowSnoozeMinutes.toString(), event.windowBackupEnabled.toString(), event.hideWindowSelection.toString(),
            event.shiftCycle?.fingerprint().orEmpty(),event.outputMode.name,event.haptics.toString(),com.muyex.randommusicreminder.domain.planner.DayOverrides.fingerprint(event.dayOverrides)) + details.rules.sortedBy { it.offsetMinutes }.map {
            "${it.offsetMinutes},${it.alertMode},${it.ringDurationSeconds},${it.snoozeMinutes}"
        }
        return fields.joinToString("") { "${it.length}:$it" }
    }
}

fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in setOf('=', '+', '-', '@') || value.firstOrNull() in setOf('\t', '\r', '\n')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}
