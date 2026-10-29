package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.ReminderEvent
import com.muyex.randommusicreminder.domain.model.TimingMode
import java.time.Instant
import java.time.ZoneId

/** 保存与只读预览使用同一份安排元数据；种子由调用方提供，预览不能生成并保存种子。 */
object SchedulePreparation {
    fun prepare(supplied:ReminderEvent,stored:ReminderEvent?,now:Instant,zone:ZoneId,createSeed:()->Long):ReminderEvent {
        val random=supplied.timingMode==TimingMode.RANDOM_WINDOW
        val changed=stored!=null && RandomWindow.scheduleIdentity(stored)!=RandomWindow.scheduleIdentity(supplied)
        val barriers=stored?.dayOverrideBarriers.orEmpty().toMutableMap()
        DayOverrides.changedDates(stored?.dayOverrides.orEmpty(),supplied.dayOverrides).forEach { date -> barriers[date]=maxOf(barriers[date] ?: 0,now.toEpochMilli()+1) }
        require(barriers.size<=5000) { "本事项的日期调整记录过多" }
        return supplied.copy(
            dayOverrideBarriers=barriers,
            createdAtEpochMillis=stored?.createdAtEpochMillis ?: now.toEpochMilli(),
            resumedAfterEpochMillis=stored?.resumedAfterEpochMillis,
            completionTrackingStartedAt=if(!supplied.trackCompletion) stored?.completionTrackingStartedAt else
                stored?.takeIf { it.trackCompletion }?.completionTrackingStartedAt ?: now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(),
            randomSeed=stored?.randomSeed?.takeIf { it!=0L } ?: if(random) createSeed().takeIf { it!=0L } ?: 1L else 0L,
            timingRevision=(stored?.timingRevision ?: 0L)+if(changed) 1L else 0L,
            windowFirstNotBeforeEpochMillis=supplied.windowFirstNotBeforeEpochMillis ?: stored?.windowFirstNotBeforeEpochMillis?.takeIf { !changed }
                ?: if(random) RandomWindow.activationBoundary(supplied,now,zone) else null,
        )
    }
}
