package com.muyex.randommusicreminder.domain.tasks

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

data class TaskIdentity(val eventId: Long, val key: String) {
    companion object {
        fun day(date: LocalDate)="day:$date"
        fun shift(date: LocalDate,slotKey: String)="shift:$date:$slotKey"
        fun shiftOccurrenceId(eventId:Long,ruleId:Long,date:LocalDate,slotKey:String)=UUID.nameUUIDFromBytes("$eventId:$ruleId:$date:shift:$slotKey".toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
enum class TaskActionType(val label:String) { DONE("完成本次"), REOPEN("撤销完成") }
data class TaskState(val action: TaskActionType,val atEpochMillis:Long) {
    fun permits(triggerAt:Long,windowStartAt:Long?=null):Boolean = action==TaskActionType.REOPEN && triggerAt>atEpochMillis && (windowStartAt==null || windowStartAt>atEpochMillis)
}
enum class TaskChoiceRange(val label:String) { UPCOMING("今天及未来"), RECENT_OVERDUE("近期逾期"), OLDEST_OVERDUE("最早逾期") }
data class TaskChoice(val eventId:Long,val key:String,val baseDate:LocalDate,val label:String,val taskAt:Long,
    val title:String,val eventUpdatedAt:Long,val pendingCount:Int,val ringing:Boolean,val stateRevision:Long=0)
data class TaskChoiceList(val eventId:Long,val title:String,val range:TaskChoiceRange,val choices:List<TaskChoice>)
data class TaskActionRecord(val id:Long,val eventId:Long,val key:String,val baseDate:LocalDate,val label:String,
    val title:String,val action:TaskActionType,val atEpochMillis:Long)
data class TaskChangeResult(val changed:Boolean,val rescheduled:Boolean)
