package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.calendar.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.tasks.*
import com.muyex.randommusicreminder.domain.time.ReminderClock
import com.muyex.randommusicreminder.domain.transfer.TransferRules
import java.time.*
import java.time.format.DateTimeFormatter

data class PreviewSnapshot(val stored:EventDetails?=null,val occurrences:List<ScheduledOccurrence> = emptyList(),
    val overrides:Map<LocalDate,CalendarDayType> = emptyMap(),val taskStates:Map<TaskIdentity,TaskState> = emptyMap())
data class SchedulePreviewItem(val id:String,val atEpochMillis:Long,val endEpochMillis:Long?=null,
    val baseDate:LocalDate,val label:String,val reason:String)
data class SchedulePreviewOmission(val date:LocalDate,val reason:String)
data class SchedulePreview(val generatedAt:Long,val zone:String,val untilExclusive:Long,val items:List<SchedulePreviewItem>,
    val omissions:List<SchedulePreviewOmission>,val warnings:List<String>,val hasMore:Boolean,val paused:Boolean)

class SchedulePreviewEngine {
    // 未保存的窗口只用首个可用分钟检查范围/边界，不执行抽签；既有选定分钟仍由Planner原分支读取。
    private val planner=OccurrencePlanner { candidates,_,_->candidates.firstOrNull() }
    fun preview(draft:EventDetails,snapshot:PreviewSnapshot,now:Instant,zone:ZoneId):SchedulePreview {
        TransferRules.validateEvent(draft)
        val until=now.plusSeconds(90L*86400)
        val warnings=mutableListOf("按当前草稿和本机记录核对；保存时会重新计算。系统权限、省电和投递延迟仍影响实际到达。")
        val stored=snapshot.stored
        val id=stored?.event?.id ?: draft.event.id.takeIf { it>0 } ?: Long.MAX_VALUE
        val prepared=SchedulePreparation.prepare(draft.event,stored?.event,now,zone) { 1L }.copy(id=id)
        val rules=draft.rules.mapIndexed { index,rule -> rule.copy(eventId=id,
            id=stored?.rules?.firstOrNull { it.offsetMinutes==rule.offsetMinutes }?.id ?: (Long.MAX_VALUE-index-1)) }
        val details=draft.copy(event=prepared,rules=rules)
        if(prepared.holidayPolicy!=HolidayPolicy.NONE && (now.atZone(zone).year..until.atZone(zone).year).any { it !in MainlandHolidayCalendar.coveredYears })
            warnings+="预览范围含未收录官方假期的年份；没有自定义覆盖的日期按原重复规则。"
        if(prepared.timingMode==TimingMode.RANDOM_WINDOW) warnings+="随机主提醒显示可用范围，不展示抽签结果；打开预览不会锁定随机分钟。固定补充与主提醒重合时合并。"
        if(!prepared.enabled) return SchedulePreview(now.toEpochMilli(),zone.id,until.toEpochMilli(),emptyList(),emptyList(),
            listOf("事项已停用，保存后不会安排提醒。启用后请重新预览。")+warnings,false,true)
        val existing=snapshot.occurrences.filter { it.eventId==id && it.kind==OccurrenceKind.ORIGINAL }
        val byId=existing.associateBy { it.id }
        val omissions=mutableListOf<SchedulePreviewOmission>()
        val terminal=setOf(OccurrenceState.COMPLETED,OccurrenceState.MISSED,OccurrenceState.RINGING)
        if(prepared.timingMode==TimingMode.RANDOM_WINDOW) existing.filter {
            !it.windowBackup && it.selectedLocalMinute!=null && it.state in terminal &&
                (it.windowEndAtEpochMillis ?: it.triggerAtEpochMillis)>=now.toEpochMilli()
        }.forEach { omissions+=SchedulePreviewOmission(it.baseDate,"随机主提醒本次已处理或正在提示，不会重新抽签") }
        val raw=planner.plan(listOf(details),now,until,zone,snapshot.overrides,existing)
        if(prepared.holidayPolicy!=HolidayPolicy.NONE && raw.any { it.baseDate.year !in MainlandHolidayCalendar.coveredYears && it.baseDate !in snapshot.overrides } && warnings.none { it.contains("未收录") })
            warnings+="提醒对应的事项日期含未收录官方假期的年份，按原重复规则；跨年提前提醒也按事项日期判断。"
        val accepted=raw.filter { row ->
            val old=byId[row.id]
            val state=row.taskOccurrenceKey?.let { snapshot.taskStates[TaskIdentity(id,it)] }
            val reason=when {
                old?.state in terminal -> when(old?.state) {
                    OccurrenceState.RINGING -> "本次正在提示，不会重新安排"
                    OccurrenceState.MISSED -> "本次已记为遗漏，不会补排"
                    else -> "本次提示已处理，不会重新安排"
                }
                state!=null && !state.permits(row.triggerAtEpochMillis,row.windowStartAtEpochMillis) ->
                    if(state.action==TaskActionType.DONE) "本次待办已完成，后续提示已取消" else "撤销前的时刻或已开始随机窗口不补排"
                else -> null
            }
            if(reason!=null) omissions+=SchedulePreviewOmission(row.baseDate,reason)
            reason==null
        }
        val items=if(prepared.timingMode==TimingMode.RANDOM_WINDOW) {
            accepted.groupBy { it.baseDate }.map { (date,rows) ->
                val main=rows.firstOrNull { !it.windowBackup }
                val first=main ?: rows.first()
                val start=if(main!=null) checkNotNull(first.windowStartAtEpochMillis) else first.triggerAtEpochMillis
                val savedWindow=byId[first.id]?.selectedLocalMinute!=null
                val effectiveStart=if(main!=null && !savedWindow) maxOf(start,nextWholeMinute(prepared.windowFirstNotBeforeEpochMillis ?: prepared.createdAtEpochMillis)) else start
                SchedulePreviewItem(first.id,effectiveStart,if(main!=null) first.windowEndAtEpochMillis else null,date,
                    if(main!=null) if(savedWindow) "既有窗口：主提醒仍待触发" else "随机主提醒1次" else "窗口末固定补充",
                    dateReason(prepared,date,snapshot)+if(main==null) "；只列仍待安排的补充" else
                        if(prepared.windowBackupEnabled) "；末分钟最多补充1次，重合合并" else "；没有固定补充")
            }
        } else accepted.map { row ->
            val rule=rules.first { it.id==row.reminderRuleId }
            val nominal=Instant.ofEpochMilli(checkNotNull(row.taskAtEpochMillis)).atZone(zone)
            val clock=if(prepared.scheduleType==ScheduleType.SHIFT) row.taskLabel.orEmpty() else "${row.baseDate} ${ReminderClock.format(prepared.localTime)}"
            val requested=if(prepared.scheduleType==ScheduleType.SHIFT) DayOverrides.effectiveShiftDay(prepared,row.baseDate)?.times
                ?.firstOrNull { TaskIdentity.shift(row.baseDate,it.key)==row.taskOccurrenceKey }?.time else prepared.localTime
            val adjusted=requested!=null && nominal.toLocalTime()!=requested
            SchedulePreviewItem(row.id,row.triggerAtEpochMillis,baseDate=row.baseDate,
                label=if(rule.offsetMinutes==0) "准时提醒" else "提前${-rule.offsetMinutes}分钟",
                reason="$clock；${dateReason(prepared,row.baseDate,snapshot)}"+
                    if(adjusted) "；时区跳时调整为${nominal.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))}" else "")
        }
        // 与Planner共享日期资格；仅展示本次90天范围中有限的原因，避免长周期拖慢界面。
        val firstDate=now.atZone(zone).toLocalDate()
        val lastDate=until.minusNanos(1).atZone(zone).toLocalDate()
        var date=firstDate
        while(date<=lastDate) {
            val decision=ScheduleDateRules.evaluate(prepared,date,snapshot.overrides[date])
            if(!decision.included) omissions+=SchedulePreviewOmission(date,decision.reason)
            date=date.plusDays(1)
        }
        if(prepared.timingMode==TimingMode.RANDOM_WINDOW) listOf(firstDate.minusDays(1),firstDate).forEach { startDate ->
            if(ScheduleDateRules.evaluate(prepared,startDate,snapshot.overrides[startDate]).included) {
                val candidates=RandomWindow.candidates(startDate,prepared.localTime,prepared.windowEndTime,prepared.windowCrossesMidnight,zone)
                if(candidates.isNotEmpty() && now>=candidates.first().instant && now<=candidates.last().instant &&
                    (prepared.windowFirstNotBeforeEpochMillis ?: prepared.createdAtEpochMillis)>=candidates.last().instant.toEpochMilli() &&
                    existing.none { it.baseDate==startDate && it.selectedLocalMinute!=null })
                    omissions+=SchedulePreviewOmission(startDate,"此窗口已经开始，按当前设置跳过；可返回基本页核对是否使用剩余时间")
            }
        }
        if(prepared.resumedAfterEpochMillis!=null) warnings+="已应用回收恢复后的时间边界，恢复前时刻和当时已开始的随机窗口不补排。"
        if(prepared.dayOverrideBarriers.isNotEmpty()) warnings+="已应用单日调整的时间边界；恢复原规则后只安排未来时刻，不补响过去、旧稍后或已开始的随机窗口。"
        warnings+="只核对生成时间之后的原始提醒，过去时刻不补排；不含旧稍后和试响。既有随机窗口仍显示完整范围。"
        val sorted=items.sortedWith(compareBy(SchedulePreviewItem::atEpochMillis,SchedulePreviewItem::id))
        return SchedulePreview(now.toEpochMilli(),zone.id,until.toEpochMilli(),sorted.take(14),
            omissions.distinct().sortedBy { it.date }.take(14),warnings,sorted.size>14,false)
    }
    private fun dateReason(event:ReminderEvent,date:LocalDate,snapshot:PreviewSnapshot)=ScheduleDateRules.evaluate(event,date,snapshot.overrides[date]).reason
    private fun nextWholeMinute(value:Long)=(Math.floorDiv(value,60000)+1)*60000
}
