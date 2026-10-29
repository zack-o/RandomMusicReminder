package com.muyex.randommusicreminder.ui

import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.ui.components.*
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ImportPreviewLabelsTest {
    @Test fun offsetsUseHumanReadableChinese() {
        assertEquals("准时提醒", importOffsetLabel(0))
        assertEquals("提前30分钟", importOffsetLabel(-30))
        assertEquals("提前1天2小时5分钟", importOffsetLabel(-1565))
    }
    @Test fun weeklyScheduleHidesEnumAndMask() {
        val event = ReminderEvent(title = "会议", scheduleType = ScheduleType.WEEKLY, localDate = LocalDate.of(2030, 1, 1), localTime = LocalTime.of(9, 0), weekdaysMask = 0b0010101)
        assertEquals("09:00  每周一、三、五 · 从 2030-01-01 开始", importScheduleLabel(event))
    }
    @Test fun rulesExplainIntensityAndSnooze() {
        assertEquals("准时提醒 · 温和提醒 · 30秒 · 不提供稍后提醒", importRuleLabel(ReminderRule(offsetMinutes = 0, ringDurationSeconds = 30)))
    }
}
