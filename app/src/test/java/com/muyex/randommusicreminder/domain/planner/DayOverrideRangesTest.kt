package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.tasks.TaskIdentity
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DayOverrideRangesTest {
    private val date=LocalDate.of(2030,1,1)
    private val scope=RangeAdjustmentScope(ScheduleType.SHIFT,date)
    private fun skip(day:LocalDate)=DayOverride(day,DayOverrideKind.SKIP)
    private fun shift(day:LocalDate)=DayOverride(day,DayOverrideKind.REPLACE_SHIFT,ShiftDay("临时夜班",times=listOf(ShiftTime(LocalTime.of(20,0)))))
    private fun preview(values:List<DayOverride>,from:LocalDate=date,to:LocalDate=date.plusDays(2),action:RangeAdjustmentAction=RangeAdjustmentAction.SKIP,keep:Boolean=true)=
        DayOverrideRanges.preview(scope,values,from,to,action,keep)

    @Test fun inclusiveRangeAddsBothEndsWithoutMutatingTheInput() {
        val before=listOf(skip(date.plusDays(10)));val plan=preview(before)
        assertEquals(3L,plan.calendarDays);assertEquals(3,plan.addedSkips);assertEquals(3,plan.changedDates.size)
        assertTrue(plan.after.contains(skip(date)));assertTrue(plan.after.contains(skip(date.plusDays(2))))
        assertEquals(listOf(skip(date.plusDays(10))),before)
    }
    @Test fun leapDayAndYearBoundariesCountCalendarDays() {
        val early=scope.copy(start=LocalDate.of(2020,1,1))
        val leap=DayOverrideRanges.preview(early,emptyList(),LocalDate.of(2028,2,28),LocalDate.of(2028,3,1),RangeAdjustmentAction.SKIP)
        assertEquals(3L,leap.calendarDays);assertTrue(leap.after.any { it.date==LocalDate.of(2028,2,29) })
        val year=DayOverrideRanges.preview(early,emptyList(),LocalDate.of(2029,12,31),date.plusDays(1),RangeAdjustmentAction.SKIP)
        assertEquals(3L,year.calendarDays)
    }
    @Test fun defaultPreservesExistingShiftAndAlreadySkippedDate() {
        val replacement=shift(date.plusDays(1));val before=listOf(replacement,skip(date.plusDays(2)),skip(date.plusDays(10)))
        val plan=preview(before,to=date.plusDays(3))
        assertEquals(2,plan.addedSkips);assertEquals(1,plan.alreadySkipped);assertEquals(1,plan.keptShifts);assertEquals(0,plan.overwrittenShifts)
        assertEquals(replacement,plan.after.single { it.date==replacement.date });assertEquals(2,plan.changedDates.size)
    }
    @Test fun explicitlyOverwritingShiftAndRestoringDoesNotInventOldReplacement() {
        val before=listOf(shift(date.plusDays(1)),skip(date.plusDays(10)))
        val plan=preview(before,keep=false)
        assertEquals(1,plan.overwrittenShifts);assertEquals(0,plan.keptShifts)
        assertTrue(plan.after.filter { it.date<=date.plusDays(2) }.all { it.kind==DayOverrideKind.SKIP })
        val restore=preview(plan.after,action=RangeAdjustmentAction.RESTORE_SKIPS)
        assertEquals(listOf(skip(date.plusDays(10))),restore.after)
    }
    @Test fun restoringOnlySkipsKeepsShiftsAndOutOfRangeDates() {
        val replacement=shift(date.plusDays(1));val outside=skip(date.plusDays(8))
        val plan=preview(listOf(skip(date),replacement,outside),action=RangeAdjustmentAction.RESTORE_SKIPS)
        assertEquals(1,plan.removedSkips);assertEquals(1,plan.keptShifts)
        assertEquals(listOf(replacement,outside),plan.after)
    }
    @Test fun restoreCanCleanOldDatesAcrossLongRangeWithoutExpandingIt() {
        val early=skip(LocalDate.of(1920,1,1));val replacement=shift(date)
        val plan=DayOverrideRanges.preview(scope.copy(end=date),listOf(early,replacement),LocalDate.of(1900,1,1),LocalDate.of(2200,12,31),RangeAdjustmentAction.RESTORE_SKIPS)
        assertEquals(listOf(replacement),plan.after);assertEquals(1,plan.removedSkips)
    }
    @Test fun exactQuotaWorksButOverflowRejectsWholeOperation() {
        val exact=preview(emptyList(),to=date.plusDays(99));assertEquals(100,exact.after.size)
        val before=listOf(skip(date.plusDays(200)))
        assertTrue(runCatching { preview(before,to=date.plusDays(99)) }.isFailure)
        assertEquals(listOf(skip(date.plusDays(200))),before)
    }
    @Test fun longReversedOutOfBoundsAndOldDatesAreRejectedForPause() {
        assertTrue(runCatching { preview(emptyList(),to=date.plusDays(100)) }.isFailure)
        assertTrue(runCatching { preview(emptyList(),from=date.plusDays(1),to=date) }.isFailure)
        assertTrue(runCatching { preview(emptyList(),from=date.minusDays(1)) }.isFailure)
        assertTrue(runCatching { DayOverrideRanges.preview(scope.copy(end=date),emptyList(),date,date.plusDays(1),RangeAdjustmentAction.SKIP) }.isFailure)
        assertTrue(runCatching { preview(emptyList(),from=LocalDate.of(1800,1,1)) }.isFailure)
    }
    @Test fun onceCanOnlyPauseItsOccurrenceDate() {
        val once=scope.copy(type=ScheduleType.ONCE)
        assertEquals(1,DayOverrideRanges.preview(once,emptyList(),date,date,RangeAdjustmentAction.SKIP).after.size)
        assertTrue(runCatching { DayOverrideRanges.preview(once,emptyList(),date,date.plusDays(1),RangeAdjustmentAction.SKIP) }.isFailure)
    }
    @Test fun staleConfirmationRejectsConfigKeyAndScopeChanges() {
        val original=shift(date.plusDays(1));val before=listOf(original)
        val plan=preview(before)
        val changed=original.copy(day=original.day?.let { it.copy(times=it.times.map { slot->slot.copy(key="new-key") }) })
        assertTrue(runCatching { DayOverrideRanges.apply(plan,listOf(changed),scope) }.isFailure)
        assertTrue(runCatching { DayOverrideRanges.apply(plan,before,scope.copy(start=date.plusDays(1))) }.isFailure)
    }
    @Test fun harmlessOrderingDoesNotInvalidateConfirmation() {
        val a=skip(date.plusDays(5));val b=shift(date.plusDays(6));val plan=preview(listOf(a,b))
        assertEquals(plan.after,DayOverrideRanges.apply(plan,listOf(b,a),scope))
    }
    @Test fun noChangePlansRemainExplicitAndDoNotPretendToRestoreShifts() {
        val skipped=(0..2).map { skip(date.plusDays(it.toLong())) }
        val plan=preview(skipped);assertTrue(plan.changedDates.isEmpty());assertEquals(3,plan.alreadySkipped)
        val restore=preview(listOf(shift(date)),action=RangeAdjustmentAction.RESTORE_SKIPS)
        assertTrue(restore.changedDates.isEmpty());assertEquals(0,restore.removedSkips);assertEquals(1,restore.keptShifts)
    }
    @Test fun expandedRangeKeepsShiftPhaseAndNextDayOwnership() {
        val zone=ZoneId.of("Asia/Shanghai");val start=date.atStartOfDay(zone).toInstant()
        val cycle=ShiftCycle(listOf(ShiftDay("夜",times=listOf(ShiftTime(LocalTime.of(8,0),true))),ShiftDay("白",times=listOf(ShiftTime(LocalTime.of(9,0))))))
        val event=EventDetails(ReminderEvent(1,"轮班",scheduleType=ScheduleType.SHIFT,localDate=date,localTime=LocalTime.NOON,shiftCycle=cycle),listOf(ReminderRule(2,1,-30),ReminderRule(3,1,0)))
        val plan=preview(emptyList(),to=date.plusDays(1));val changed=event.copy(event=event.event.copy(dayOverrides=plan.after))
        val original=OccurrencePlanner().plan(listOf(event),start,start.plusSeconds(6*86400),zone)
        val actual=OccurrencePlanner().plan(listOf(changed),start,start.plusSeconds(6*86400),zone)
        assertEquals(original.filterNot { it.baseDate in date..date.plusDays(1) }.map { it.triggerAtEpochMillis },actual.map { it.triggerAtEpochMillis })
        assertFalse(actual.any { it.taskOccurrenceKey==TaskIdentity.shift(date,cycle.days.first().times.first().key) })
    }
    @Test fun rangeRestoreUsesSharedFutureBoundaryForEachChangedDate() {
        val zone=ZoneId.of("Asia/Shanghai");val early=date.atTime(6,0).atZone(zone).toInstant();val later=date.atTime(8,45).atZone(zone).toInstant()
        val original=ReminderEvent(1,"待办",scheduleType=ScheduleType.DAILY,localDate=date,localTime=LocalTime.of(9,0))
        val pausedPlan=DayOverrideRanges.preview(scope.copy(type=ScheduleType.DAILY),emptyList(),date,date.plusDays(2),RangeAdjustmentAction.SKIP)
        val paused=SchedulePreparation.prepare(original.copy(dayOverrides=pausedPlan.after),original,early,zone) { 1 }
        val restoredPlan=DayOverrideRanges.preview(scope.copy(type=ScheduleType.DAILY),paused.dayOverrides,date,date.plusDays(2),RangeAdjustmentAction.RESTORE_SKIPS)
        val restored=SchedulePreparation.prepare(paused.copy(dayOverrides=restoredPlan.after),paused,later,zone) { 1 }
        assertEquals(3,restored.dayOverrideBarriers.size);assertTrue(restored.dayOverrideBarriers.values.all { it==later.toEpochMilli()+1 })
        val detail=EventDetails(restored,listOf(ReminderRule(2,1,-30),ReminderRule(3,1,0)))
        val planned=OccurrencePlanner().plan(listOf(detail),early,date.plusDays(3).atStartOfDay(zone).toInstant(),zone)
        assertEquals(5,planned.size);assertEquals(date.atTime(9,0).atZone(zone).toInstant().toEpochMilli(),planned.first().triggerAtEpochMillis)
    }
}
