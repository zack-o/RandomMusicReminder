package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.*
import java.time.LocalDate

enum class DayOverrideKind { SKIP, REPLACE_SHIFT }
data class DayOverride(val date:LocalDate,val kind:DayOverrideKind,val day:ShiftDay?=null)

object DayOverrides {
    const val MAX_DATES=100
    fun validate(values:List<DayOverride>,type:ScheduleType) {
        require(values.size<=MAX_DATES && values.map { it.date }.distinct().size==values.size) { "单日调整最多100个日期，同一天不能重复" }
        values.forEach { value ->
            require(value.date.year in 1900..2200) { "调整日期须为1900～2200年" }
            if(value.kind==DayOverrideKind.SKIP) require(value.day==null) { "跳过日期不能同时包含轮班时刻" }
            else {
                require(type==ScheduleType.SHIFT) { "替换当天时刻仅适用于轮班" }
                val day=requireNotNull(value.day) { "请设置当天轮班" }
                require(day.label.isNotBlank() && day.label.length<=30 && day.times.size<=8 && (!day.enabled || day.times.isNotEmpty())) { "临时轮班须填写名称，启用时设置1～8个时刻" }
                require(day.times.map { it.time to it.nextDay }.distinct().size==day.times.size) { "临时时刻不能重复" }
                require(day.times.map { it.key }.distinct().size==day.times.size && day.times.all { it.time.nano==0 && Regex("[A-Za-z0-9-]{1,64}").matches(it.key) }) { "临时时刻标识无效" }
            }
        }
    }
    fun at(event:ReminderEvent,date:LocalDate)=event.dayOverrides.firstOrNull { it.date==date }
    fun effectiveShiftDay(event:ReminderEvent,date:LocalDate):ShiftDay? {
        val override=at(event,date)
        return when(override?.kind) {
            DayOverrideKind.SKIP -> null
            DayOverrideKind.REPLACE_SHIFT -> override.day
            null -> event.shiftCycle?.dayOn(event.localDate,date)
        }
    }
    fun fingerprint(values:List<DayOverride>,includeKeys:Boolean=false):String=values.sortedBy { it.date }.joinToString("|") { value ->
        val day=value.day
        "${value.date}:${value.kind}:"+if(day==null) "" else "${day.label.length}:${day.label}:${day.rest}:${day.enabled}:"+
            day.times.sortedWith(compareBy<ShiftTime> { it.nextDay }.thenBy { it.time }).joinToString(",") { "${it.time}:${it.nextDay}"+if(includeKeys) ":${it.key}" else "" }
    }
    fun signature(values:List<DayOverride>,date:LocalDate)=fingerprint(values.filter { it.date==date },true)
    fun changedDates(before:List<DayOverride>,after:List<DayOverride>)=(before.map { it.date }+after.map { it.date }).distinct()
        .filter { signature(before,it)!=signature(after,it) }.toSet()
    fun permits(values:List<DayOverride>,barriers:Map<LocalDate,Long>,date:LocalDate,trigger:Long,windowStart:Long?,expectedSignature:String):Boolean {
        if(values.any { it.date==date && it.kind==DayOverrideKind.SKIP } || expectedSignature!=signature(values,date)) return false
        val after=barriers[date] ?: return true
        return trigger>=after && (windowStart==null || windowStart>=after)
    }
    fun allShiftTimes(event:ReminderEvent)=event.shiftCycle?.days.orEmpty().flatMap { it.times }+event.dayOverrides.mapNotNull { it.day }.flatMap { it.times }
}
