package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.calendar.*
import com.muyex.randommusicreminder.domain.model.*
import java.time.LocalDate

data class DateEligibility(val included:Boolean,val reason:String)

object ScheduleDateRules {
    fun evaluate(event:ReminderEvent,date:LocalDate,custom:CalendarDayType?=null):DateEligibility {
        if(date<event.localDate) return DateEligibility(false,"尚未到开始日期")
        if(event.endDate!=null && date>event.endDate) return DateEligibility(false,"已超过结束日期")
        val adjustment=DayOverrides.at(event,date)
        if(adjustment?.kind==DayOverrideKind.SKIP) return DateEligibility(false,"单日调整：这一天已跳过")
        val original=RecurrenceDates.matches(event,date)
        val replaceWeekdays=event.scheduleType in setOf(ScheduleType.DAILY,ScheduleType.WEEKLY)
        if(!original && !replaceWeekdays) return DateEligibility(false,originalReason(event,date))
        val included=MainlandHolidayCalendar.shouldInclude(date,event.holidayPolicy,original,custom)
        val reason=when {
            event.holidayPolicy==HolidayPolicy.NONE -> if(original) originalIncluded(event,date) else originalReason(event,date)
            custom==CalendarDayType.REST -> "自定义休息日，按本事项的日历策略跳过"
            custom==CalendarDayType.WORK && included -> "自定义工作日"+if(!original) "，替代原星期选择" else "，符合重复规则"
            date.year !in MainlandHolidayCalendar.coveredYears -> if(original) "该年份官方假期未收录，按原重复规则" else originalReason(event,date)
            included && event.holidayPolicy==HolidayPolicy.MAINLAND_WORKDAYS -> if(MainlandHolidayCalendar.isMakeupWorkday(date)) "官方调休补班日" else "法定工作日"
            !included && !original && event.holidayPolicy==HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS -> originalReason(event,date)
            !included -> MainlandHolidayCalendar.holidayName(date)?.let { "官方放假：$it" } ?: "休息日，不符合法定工作日策略"
            else -> originalIncluded(event,date)+"，未被日历过滤"
        }
        return DateEligibility(included,if(adjustment?.kind==DayOverrideKind.REPLACE_SHIFT) "单日替换 · ${adjustment.day?.label}；$reason" else reason)
    }
    private fun originalIncluded(event:ReminderEvent,date:LocalDate)=when(event.scheduleType) {
        ScheduleType.ONCE -> "指定日期"
        ScheduleType.DAILY -> "每日重复"
        ScheduleType.WEEKLY -> "符合每周选择"
        ScheduleType.MONTHLY -> "符合每月日号与缺日策略"
        ScheduleType.YEARLY -> "符合每年月日与缺日策略"
        ScheduleType.INTERVAL -> "按开始日每${event.intervalDays}天"
        ScheduleType.SHIFT -> event.shiftCycle?.let { "轮班第${checkNotNull(it.dayIndex(event.localDate,date))+1}/${it.days.size}天" } ?: "轮班配置缺失"
    }
    private fun originalReason(event:ReminderEvent,date:LocalDate)=when(event.scheduleType) {
        ScheduleType.SHIFT -> DayOverrides.effectiveShiftDay(event,date)?.let { "${it.label}：此周期日已关闭提醒" } ?: "不在轮班周期内"
        ScheduleType.WEEKLY -> "不在所选星期内"
        ScheduleType.INTERVAL -> "不在每${event.intervalDays}天的周期日"
        ScheduleType.MONTHLY -> "不符合每月日号或缺日策略"
        ScheduleType.YEARLY -> "不符合每年月日或缺日策略"
        else -> "不符合原重复日期"
    }
}
