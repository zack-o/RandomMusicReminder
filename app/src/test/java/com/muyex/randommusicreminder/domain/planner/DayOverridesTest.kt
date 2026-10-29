package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.calendar.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.tasks.*
import com.muyex.randommusicreminder.domain.transfer.TransferRules
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DayOverridesTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val date=LocalDate.of(2030,1,1)
    private val planner=OccurrencePlanner()
    private fun at(day:LocalDate=date,hour:Int=0,minute:Int=0)=day.atTime(hour,minute).atZone(zone).toInstant()
    private fun fixed()=EventDetails(ReminderEvent(1,"临时安排",scheduleType=ScheduleType.DAILY,localDate=date,localTime=LocalTime.of(9,0)),listOf(ReminderRule(2,1,-30),ReminderRule(3,1,0)))
    private fun shift()=fixed().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.SHIFT,shiftCycle=ShiftCycle.template("白夜休休"))) }
    private fun EventDetails.adjust(vararg values:DayOverride)=copy(event=event.copy(dayOverrides=values.toList()))
    private fun plan(data:EventDetails,days:Long=4)=planner.plan(listOf(data),at(),at(date.plusDays(days)),zone)

    @Test fun skippingDateRemovesAllItsPointsAndPreservesNextCycle() {
        val data=fixed().adjust(DayOverride(date,DayOverrideKind.SKIP))
        assertEquals(6,plan(data).size);assertFalse(plan(data).any { it.baseDate==date })
        assertEquals(date.plusDays(1),plan(data).first().baseDate)
    }
    @Test fun skipWinsOverCustomWorkdayReplacement() {
        val event=fixed().event.copy(holidayPolicy=HolidayPolicy.MAINLAND_WORKDAYS,dayOverrides=listOf(DayOverride(date,DayOverrideKind.SKIP)))
        assertFalse(ScheduleDateRules.evaluate(event,date,CalendarDayType.WORK).included)
        assertTrue(ScheduleDateRules.evaluate(event,date,CalendarDayType.WORK).reason.contains("跳过"))
    }
    @Test fun shiftSkipDoesNotMovePhaseAcrossMonthOrYear() {
        val data=shift();val changed=data.adjust(DayOverride(date,DayOverrideKind.SKIP))
        val expected=plan(data,40).filterNot { it.baseDate==date }
        assertEquals(expected.map { it.triggerAtEpochMillis },plan(changed,40).map { it.triggerAtEpochMillis })
    }
    @Test fun replacingRestDayCanEnableItWithoutChangingOtherDays() {
        val data=shift();val rest=date.plusDays(2)
        val adjustment=DayOverride(rest,DayOverrideKind.REPLACE_SHIFT,ShiftDay("临时白班",times=listOf(ShiftTime(LocalTime.of(10,0)))))
        val changed=plan(data.adjust(adjustment));assertEquals(2,changed.count { it.baseDate==rest })
        assertEquals(plan(data).map { it.triggerAtEpochMillis },changed.filterNot { it.baseDate==rest }.map { it.triggerAtEpochMillis })
        assertTrue(changed.filter { it.baseDate==rest }.all { it.sourceLabel.orEmpty().contains("单日替换") })
    }
    @Test fun nextDayTimesAndAdvanceKeepStartingDayAtEndDate() {
        val data=shift().let { it.copy(event=it.event.copy(endDate=date)) }
        val night=ShiftDay("夜班",times=listOf(ShiftTime(LocalTime.of(0,10),true)))
        val adjustment=DayOverride(date,DayOverrideKind.REPLACE_SHIFT,night)
        val result=plan(data.adjust(adjustment))
        assertEquals(listOf(at(hour=23,minute=40).toEpochMilli(),at(date.plusDays(1),0,10).toEpochMilli()),result.map { it.triggerAtEpochMillis })
        assertTrue(result.all { it.baseDate==date })
    }
    @Test fun replaceDoesNotBypassCalendarOrStartEndDates() {
        val data=shift();val value=DayOverride(date,DayOverrideKind.REPLACE_SHIFT,checkNotNull(data.event.shiftCycle).days.first())
        val event=data.adjust(value).event.copy(holidayPolicy=HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS)
        assertFalse(ScheduleDateRules.evaluate(event,date,CalendarDayType.REST).included)
        assertFalse(ScheduleDateRules.evaluate(event.copy(localDate=date.plusDays(1)),date).included)
        assertFalse(ScheduleDateRules.evaluate(event.copy(endDate=date.minusDays(1)),date).included)
    }
    @Test fun editingTimePreservesSlotIdentityAndCompletedTaskDoesNotReplay() {
        val data=shift();val day=checkNotNull(data.event.shiftCycle).days.first()
        val changed=data.adjust(DayOverride(date,DayOverrideKind.REPLACE_SHIFT,day.copy(times=day.times.map { it.copy(time=LocalTime.of(10,0)) })))
        assertEquals(plan(data).filter { it.baseDate==date }.map { it.id },plan(changed).filter { it.baseDate==date }.map { it.id })
        val key=TaskIdentity(1,TaskIdentity.shift(date,day.times.single().key))
        val result=planner.plan(listOf(changed),at(),at(date.plusDays(4)),zone,taskStates=mapOf(key to TaskState(TaskActionType.DONE,0)))
        assertFalse(result.any { it.baseDate==date })
    }
    @Test fun restoredOriginalKeepsBarrierAndDoesNotReplayPastPoints() {
        val data=fixed();val skipped=data.adjust(DayOverride(date,DayOverrideKind.SKIP))
        val prepared=SchedulePreparation.prepare(skipped.event,data.event,at(hour=7),zone) { 0 }
        val restored=SchedulePreparation.prepare(data.event,prepared,at(hour=8,minute=45),zone) { 0 }
        assertEquals(at(hour=8,minute=45).toEpochMilli()+1,restored.dayOverrideBarriers[date])
        assertEquals(listOf(at(hour=9).toEpochMilli()),plan(data.copy(event=restored),1).map { it.triggerAtEpochMillis })
    }
    @Test fun currentSignatureRejectsStaleFuturePlanEvenWhenTimeIsStillFuture() {
        val data=shift();val original=plan(data).first()
        val value=DayOverride(date,DayOverrideKind.REPLACE_SHIFT,checkNotNull(data.event.shiftCycle).days.first().let { it.copy(times=it.times.map { t->t.copy(time=LocalTime.of(10,0)) }) })
        assertFalse(DayOverrides.permits(listOf(value),emptyMap(),date,original.triggerAtEpochMillis,null,original.dayOverrideSignature))
        assertTrue(DayOverrides.permits(emptyList(),emptyMap(),date,original.triggerAtEpochMillis,null,""))
    }
    @Test fun randomSkipDoesNotChangeOtherDaysSeedRevisionOrChoices() {
        val data=fixed().let { it.copy(event=it.event.copy(timingMode=TimingMode.RANDOM_WINDOW,localTime=LocalTime.of(8,0),windowEndTime=LocalTime.of(8,10),randomSeed=53,timingRevision=2)) }
        val supplied=data.adjust(DayOverride(date.plusDays(1),DayOverrideKind.SKIP))
        val prepared=SchedulePreparation.prepare(supplied.event,data.event,at(),zone) { error("must keep seed") }
        assertEquals(53L,prepared.randomSeed);assertEquals(2L,prepared.timingRevision)
        assertEquals(plan(data).filterNot { it.baseDate==date.plusDays(1) }.map { it.triggerAtEpochMillis },plan(supplied.copy(event=prepared)).map { it.triggerAtEpochMillis })
    }
    @Test fun restoringStartedRandomWindowSkipsItAndLeavesTomorrow() {
        val data=fixed().let { it.copy(event=it.event.copy(timingMode=TimingMode.RANDOM_WINDOW,localTime=LocalTime.of(8,0),windowEndTime=LocalTime.of(8,10),randomSeed=53)) }
        val skipped=SchedulePreparation.prepare(data.adjust(DayOverride(date,DayOverrideKind.SKIP)).event,data.event,at(hour=7),zone) { 1 }
        val restored=SchedulePreparation.prepare(data.event,skipped,at(hour=8,minute=1),zone) { 1 }
        assertFalse(plan(data.copy(event=restored)).any { it.baseDate==date })
        assertTrue(plan(data.copy(event=restored)).any { it.baseDate==date.plusDays(1) })
    }
    @Test fun barriersRetainOtherDatesAndNeverMoveBackwardAfterClockRollback() {
        val data=fixed();val old=data.event.copy(dayOverrideBarriers=mapOf(date to 1000,date.plusDays(1) to 2000))
        val supplied=data.adjust(DayOverride(date,DayOverrideKind.SKIP)).event
        val prepared=SchedulePreparation.prepare(supplied,old,Instant.ofEpochMilli(500),zone) { 1 }
        assertEquals(mapOf(date to 1000L,date.plusDays(1) to 2000L),prepared.dayOverrideBarriers)
    }
    @Test fun fingerprintIncludesBehaviorAndDatesButIgnoresLocalBarriersAndSlotKeys() {
        val data=shift();val day=checkNotNull(data.event.shiftCycle).days.first()
        val a=data.adjust(DayOverride(date,DayOverrideKind.REPLACE_SHIFT,day))
        val b=a.copy(event=a.event.copy(dayOverrides=a.event.dayOverrides.map { it.copy(day=day.copy(times=day.times.map { t->t.copy(key="another-key") })) },dayOverrideBarriers=mapOf(date to 123L)))
        assertEquals(TransferRules.fingerprint(a),TransferRules.fingerprint(b))
        assertNotEquals(DayOverrides.signature(a.event.dayOverrides,date),DayOverrides.signature(b.event.dayOverrides,date))
        assertNotEquals(TransferRules.fingerprint(data),TransferRules.fingerprint(a))
    }
    @Test fun invalidDuplicatesAndWrongScheduleTypesAreRejected() {
        val skipped=DayOverride(date,DayOverrideKind.SKIP)
        assertTrue(runCatching { DayOverrides.validate(listOf(skipped,skipped),ScheduleType.DAILY) }.isFailure)
        assertTrue(runCatching { DayOverrides.validate(listOf(DayOverride(date,DayOverrideKind.REPLACE_SHIFT,ShiftDay("临时",times=listOf(ShiftTime(LocalTime.NOON))))),ScheduleType.DAILY) }.isFailure)
        assertTrue(runCatching { DayOverrides.validate((0..100).map { skipped.copy(date=date.plusDays(it.toLong())) },ScheduleType.DAILY) }.isFailure)
    }
    @Test fun previewShowsEffectiveReplacementAndSkipReasons() {
        val data=shift();val day=checkNotNull(data.event.shiftCycle).days.first()
        val changed=data.adjust(DayOverride(date,DayOverrideKind.REPLACE_SHIFT,day.copy(times=day.times.map { it.copy(time=LocalTime.of(10,0)) })),DayOverride(date.plusDays(1),DayOverrideKind.SKIP))
        val preview=SchedulePreviewEngine().preview(changed,PreviewSnapshot(data),at(),zone)
        assertEquals(at(hour=9,minute=30).toEpochMilli(),preview.items.first().atEpochMillis)
        assertTrue(preview.items.first().reason.contains("单日替换"));assertTrue(preview.omissions.any { it.date==date.plusDays(1) && it.reason.contains("跳过") })
    }
}
