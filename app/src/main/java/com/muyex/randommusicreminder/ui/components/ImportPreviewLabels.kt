package com.muyex.randommusicreminder.ui.components

import com.muyex.randommusicreminder.domain.model.*
import java.time.format.DateTimeFormatter

internal fun importScheduleLabel(event: ReminderEvent): String {
    val repeat = when (event.scheduleType) {
        ScheduleType.ONCE -> "单次 · ${event.localDate}"
        ScheduleType.DAILY -> "每天 · 从 ${event.localDate} 开始"
        ScheduleType.MONTHLY -> "每月${event.localDate.dayOfMonth}日 · ${if (event.missingDayPolicy == MissingDayPolicy.SKIP) "缺日跳过" else "缺日取月末"}"
        ScheduleType.YEARLY -> "每年${event.localDate.monthValue}月${event.localDate.dayOfMonth}日 · ${if (event.missingDayPolicy == MissingDayPolicy.SKIP) "缺日跳过" else "缺日取月末"}"
        ScheduleType.INTERVAL -> "每${event.intervalDays}天 · 从 ${event.localDate} 开始"
        ScheduleType.SHIFT -> "${event.shiftCycle?.days?.size ?: 0}天轮班 · 第1天 ${event.localDate}"
        ScheduleType.WEEKLY -> "每周" + (1..7).filter { event.weekdaysMask and (1 shl (it - 1)) != 0 }
            .joinToString("、") { listOf("一", "二", "三", "四", "五", "六", "日")[it - 1] } + " · 从 ${event.localDate} 开始"
    }
    return (if(event.scheduleType == ScheduleType.SHIFT) repeat else "${com.muyex.randommusicreminder.domain.time.ReminderClock.format(event.localTime)}  $repeat") + (event.endDate?.let { " · 至$it" } ?: "")
}

internal fun importOffsetLabel(minutes: Int): String {
    if (minutes == 0) return "准时提醒"
    val amount = -minutes.toLong()
    val parts = listOfNotNull(if (amount / 1440 > 0) "${amount / 1440}天" else null,
        if (amount % 1440 / 60 > 0) "${amount % 1440 / 60}小时" else null,
        if (amount % 60 > 0) "${amount % 60}分钟" else null)
    return "提前${parts.joinToString("")}"
}

internal fun importRuleLabel(rule: ReminderRule): String =
    "${importOffsetLabel(rule.offsetMinutes)} · ${if (rule.alertMode == AlertMode.GENTLE) "温和提醒" else "强提醒"} · ${rule.ringDurationSeconds}秒" +
        (rule.snoozeMinutes?.let { " · 可稍后${it}分钟" } ?: " · 不提供稍后提醒")
