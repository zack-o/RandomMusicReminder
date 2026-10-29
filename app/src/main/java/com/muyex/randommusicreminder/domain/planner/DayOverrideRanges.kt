package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.ScheduleType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RangeAdjustmentAction { SKIP, RESTORE_SKIPS }
data class RangeAdjustmentScope(val type:ScheduleType,val start:LocalDate,val end:LocalDate?=null)
data class RangeAdjustmentPlan(
    val from:LocalDate,val through:LocalDate,val action:RangeAdjustmentAction,
    val before:List<DayOverride>,val after:List<DayOverride>,val changedDates:Set<LocalDate>,
    val calendarDays:Long,val addedSkips:Int,val alreadySkipped:Int,val overwrittenShifts:Int,
    val keptShifts:Int,val removedSkips:Int,
    val scope:RangeAdjustmentScope,
)

object DayOverrideRanges {
    fun preview(scope:RangeAdjustmentScope,current:List<DayOverride>,from:LocalDate,through:LocalDate,
        action:RangeAdjustmentAction,keepReplacements:Boolean=true):RangeAdjustmentPlan {
        require(from.year in 1900..2200 && through.year in 1900..2200 && through>=from) { "请填写1900～2200年的有效起止日期，结束不得早于开始" }
        DayOverrides.validate(current,scope.type)
        val count=ChronoUnit.DAYS.between(from,through)+1
        val inRange=current.filter { it.date in from..through }
        val skips=inRange.count { it.kind==DayOverrideKind.SKIP }
        val shifts=inRange.count { it.kind==DayOverrideKind.REPLACE_SHIFT }
        val result:List<DayOverride>
        var added=0
        if(action==RangeAdjustmentAction.SKIP) {
            require(count<=DayOverrides.MAX_DATES) { "一次暂停最多100个日历日（含起止），请缩短区间" }
            require(from>=scope.start && (scope.end==null || through<=scope.end)) { "暂停区间须在当前事项的开始和结束日期内" }
            require(scope.type!=ScheduleType.ONCE || (from==scope.start && through==scope.start)) { "单次事项只能暂停指定发生日期" }
            val map=current.associateBy { it.date }.toMutableMap()
            repeat(count.toInt()) { offset ->
                val date=from.plusDays(offset.toLong());val previous=map[date]
                if(previous==null) added++
                if(!(keepReplacements && previous?.kind==DayOverrideKind.REPLACE_SHIFT)) map[date]=DayOverride(date,DayOverrideKind.SKIP)
            }
            require(map.size<=DayOverrides.MAX_DATES) { "合并后共${map.size}个调整日期，最多100个；本次未加入，请缩短区间或先恢复旧调整" }
            result=map.values.sortedBy { it.date }
        } else result=current.filterNot { it.date in from..through && it.kind==DayOverrideKind.SKIP }.sortedBy { it.date }
        DayOverrides.validate(result,scope.type)
        return RangeAdjustmentPlan(from,through,action,current.toList(),result,DayOverrides.changedDates(current,result),count,
            added,skips,if(action==RangeAdjustmentAction.SKIP && !keepReplacements) shifts else 0,
            if(action==RangeAdjustmentAction.RESTORE_SKIPS || keepReplacements) shifts else 0,
            if(action==RangeAdjustmentAction.RESTORE_SKIPS) skips else 0,scope)
    }

    fun apply(plan:RangeAdjustmentPlan,current:List<DayOverride>,scope:RangeAdjustmentScope):List<DayOverride> {
        require(scope==plan.scope && DayOverrides.fingerprint(current,true)==DayOverrides.fingerprint(plan.before,true)) { "草稿已有其他调整，请重新核对区间后再确认" }
        return plan.after.toList()
    }
}
