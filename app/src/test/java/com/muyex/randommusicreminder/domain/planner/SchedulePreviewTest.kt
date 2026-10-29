package com.muyex.randommusicreminder.domain.planner

import com.muyex.randommusicreminder.domain.calendar.*
import com.muyex.randommusicreminder.domain.model.*
import com.muyex.randommusicreminder.domain.tasks.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class SchedulePreviewTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val date=LocalDate.of(2030,1,1)
    private val now=date.atStartOfDay(zone).toInstant()
    private val engine=SchedulePreviewEngine()
    private fun details()=EventDetails(ReminderEvent(1,"事项预览",scheduleType=ScheduleType.DAILY,localDate=date,localTime=LocalTime.of(9,0,55)),
        listOf(ReminderRule(2,1,-30),ReminderRule(3,1,0)))
    private fun millis(day:LocalDate=date,hour:Int=9,minute:Int=0,second:Int=55)=day.atTime(hour,minute,second).atZone(zone).toInstant().toEpochMilli()
    private fun snapshot(data:EventDetails)=PreviewSnapshot(stored=data)
    private fun random()=details().let { it.copy(event=it.event.copy(timingMode=TimingMode.RANDOM_WINDOW,localTime=LocalTime.of(8,0),
        windowEndTime=LocalTime.of(8,10),windowBackupEnabled=true,createdAtEpochMillis=now.toEpochMilli(),randomSeed=71)) }

    @Test fun fixedPreviewMatchesActualPlannerAndKeepsSecondsAndAdvanceTimes() {
        val data=details();val preview=engine.preview(data,snapshot(data),now,zone)
        val actual=OccurrencePlanner().plan(listOf(data),now,now.plusSeconds(90L*86400),zone).take(14)
        assertEquals(actual.map { it.triggerAtEpochMillis },preview.items.map { it.atEpochMillis })
        assertEquals(actual.map { it.id },preview.items.map { it.id });assertTrue(preview.hasMore)
        assertEquals(millis(hour=8,minute=30),preview.items.first().atEpochMillis)
    }
    @Test fun unsavedDraftUsesTemporaryIdentitiesAndDoesNotMutateInput() {
        val data=details().let { it.copy(event=it.event.copy(id=0),rules=it.rules.map { r->r.copy(id=0,eventId=0) }) }
        val preview=engine.preview(data,PreviewSnapshot(),now,zone)
        assertEquals(14,preview.items.size);assertEquals(0L,data.event.id);assertTrue(data.rules.all { it.id==0L })
    }
    @Test fun oldRuleIdentityIsRecoveredByOffsetAndTerminalRecordDoesNotReplay() {
        val stored=details();val original=OccurrencePlanner().plan(listOf(stored),now,now.plusSeconds(86400),zone).last()
        val old=ScheduledOccurrence(original.id,1,3,date,original.triggerAtEpochMillis,state=OccurrenceState.COMPLETED)
        val draft=stored.copy(rules=stored.rules.map { it.copy(id=0) })
        val preview=engine.preview(draft,PreviewSnapshot(stored,listOf(old)),now,zone)
        assertFalse(preview.items.any { it.id==original.id })
        assertTrue(preview.omissions.any { it.date==date && it.reason.contains("已处理") })
    }
    @Test fun completedTaskSuppressesOnlyItsDateAndDoesNotRemoveNextCycle() {
        val data=details();val state=mapOf(TaskIdentity(1,TaskIdentity.day(date)) to TaskState(TaskActionType.DONE,0))
        val preview=engine.preview(data,PreviewSnapshot(data,taskStates=state),now,zone)
        assertEquals(date.plusDays(1),preview.items.first().baseDate)
        assertTrue(preview.omissions.any { it.reason.contains("已完成") })
    }
    @Test fun shiftNextDayAndAdvanceKeepOriginalStartingDateAndEndDate() {
        val cycle=ShiftCycle(listOf(ShiftDay("夜班",times=listOf(ShiftTime(LocalTime.of(0,10),true)))))
        val data=details().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.SHIFT,shiftCycle=cycle,endDate=date)) }
        val preview=engine.preview(data,snapshot(data),now,zone)
        assertEquals(2,preview.items.size);assertTrue(preview.items.all { it.baseDate==date })
        assertEquals(millis(hour=23,minute=40,second=0),preview.items.first().atEpochMillis)
        assertTrue(preview.items.first().reason.contains("次日 00:10"))
    }
    @Test fun closedShiftDaysHaveReasonsButDoNotAdvancePhase() {
        val data=details().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.SHIFT,shiftCycle=ShiftCycle.template("白休"))) }
        val preview=engine.preview(data,snapshot(data),now,zone)
        assertEquals(date.plusDays(2),preview.items[2].baseDate)
        assertTrue(preview.omissions.any { it.date==date.plusDays(1) && it.reason.contains("关闭提醒") })
    }
    @Test fun customCalendarRestFiltersOnlyOptedInEventsAndAdvanceUsesBaseDate() {
        val base=details().let { it.copy(event=it.event.copy(localTime=LocalTime.of(0,10)),rules=listOf(ReminderRule(2,1,-30))) }
        val custom=mapOf(date.plusDays(1) to CalendarDayType.REST)
        val opted=base.copy(event=base.event.copy(holidayPolicy=HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS))
        val preview=engine.preview(opted,PreviewSnapshot(opted,overrides=custom),now,zone)
        assertFalse(preview.items.any { it.baseDate==date.plusDays(1) })
        assertTrue(preview.omissions.any { it.reason.contains("自定义休息日") })
        assertTrue(engine.preview(base,PreviewSnapshot(base,overrides=custom),now,zone).items.any { it.baseDate==date.plusDays(1) })
    }
    @Test fun workdayPolicyCanReplaceWeekdaysButCannotExpandMonthlyDates() {
        val saturday=LocalDate.of(2026,5,9)
        val weekly=details().event.copy(localDate=LocalDate.of(2026,1,1),scheduleType=ScheduleType.WEEKLY,weekdaysMask=WeekdayMask.from(setOf(DayOfWeek.MONDAY)),holidayPolicy=HolidayPolicy.MAINLAND_WORKDAYS)
        assertTrue(ScheduleDateRules.evaluate(weekly,saturday).included)
        assertTrue(ScheduleDateRules.evaluate(weekly,saturday).reason.contains("补班"))
        assertFalse(ScheduleDateRules.evaluate(weekly.copy(scheduleType=ScheduleType.MONTHLY),saturday,CalendarDayType.WORK).included)
    }
    @Test fun monthlyLastDayDoesNotDriftAndPreviewHasExplicitHorizon() {
        val data=details().let { it.copy(event=it.event.copy(scheduleType=ScheduleType.MONTHLY,localDate=LocalDate.of(2030,1,31),missingDayPolicy=MissingDayPolicy.LAST_DAY),rules=listOf(it.rules.last())) }
        val preview=engine.preview(data,snapshot(data),now,zone)
        assertEquals(listOf(LocalDate.of(2030,1,31),LocalDate.of(2030,2,28),LocalDate.of(2030,3,31)),preview.items.map { it.baseDate })
        assertEquals(now.plusSeconds(90L*86400).toEpochMilli(),preview.untilExclusive)
    }
    @Test fun unknownHolidayYearHasExplicitWarningButNonePolicyDoesNot() {
        val data=details();assertFalse(engine.preview(data,snapshot(data),now,zone).warnings.any { it.contains("未收录") })
        val opted=data.copy(event=data.event.copy(holidayPolicy=HolidayPolicy.MAINLAND_WORKDAYS))
        assertTrue(engine.preview(opted,snapshot(opted),now,zone).warnings.any { it.contains("未收录") })
    }
    @Test fun randomPreviewShowsRangeRatherThanInventingSelectedMinuteAndDoesNotAllocateSeed() {
        val data=random().let { it.copy(event=it.event.copy(id=0,randomSeed=0),rules=it.rules.map { r->r.copy(id=0,eventId=0) }) }
        val preview=engine.preview(data,PreviewSnapshot(),now,zone)
        assertEquals(millis(hour=8,second=0),preview.items.first().atEpochMillis)
        assertEquals(millis(hour=8,minute=10,second=0),preview.items.first().endEpochMillis)
        assertEquals(0L,data.event.randomSeed);assertEquals(14,preview.items.size)
        assertTrue(preview.warnings.any { it.contains("不会锁定") })
    }
    @Test fun randomProcessedMainKeepsOnlyPendingBackup() {
        val data=random()
        val main=OccurrencePlanner().plan(listOf(data),now,now.plusSeconds(86400),zone).first { !it.windowBackup }
        // A main chosen at the last minute has no separate backup, use a stable seed with two points.
        assertTrue(main.triggerAtEpochMillis<checkNotNull(main.windowEndAtEpochMillis))
        val old=ScheduledOccurrence(main.id,1,main.reminderRuleId,date,main.triggerAtEpochMillis,state=OccurrenceState.COMPLETED,selectedLocalMinute=main.selectedLocalMinute)
        val preview=engine.preview(data,PreviewSnapshot(data,listOf(old)),now,zone)
        assertEquals("窗口末固定补充",preview.items.first().label);assertNull(preview.items.first().endEpochMillis)
    }
    @Test fun reopenedStartedRandomWindowDoesNotReappear() {
        val data=random();val state=TaskState(TaskActionType.REOPEN,millis(hour=8,minute=1,second=0))
        val preview=engine.preview(data,PreviewSnapshot(data,taskStates=mapOf(TaskIdentity(1,TaskIdentity.day(date)) to state)),now,zone)
        assertFalse(preview.items.any { it.baseDate==date })
        assertTrue(preview.omissions.any { it.reason.contains("不补排") })
    }
    @Test fun disabledAndEndedEventsHaveHonestEmptyStates() {
        val data=details();val paused=engine.preview(data.copy(event=data.event.copy(enabled=false)),snapshot(data),now,zone)
        assertTrue(paused.paused);assertTrue(paused.items.isEmpty())
        val ended=data.copy(event=data.event.copy(endDate=date.minusDays(1),localDate=date.minusDays(2)))
        val result=engine.preview(ended,snapshot(ended),now,zone)
        assertTrue(result.items.isEmpty());assertTrue(result.omissions.any { it.reason.contains("结束日期") })
    }
    @Test fun dstGapUsesSameRealInstantAsPlannerAndExplainsAdjustment() {
        val dst=ZoneId.of("America/New_York");val day=LocalDate.of(2030,3,10)
        val data=details().let { it.copy(event=it.event.copy(localDate=day,localTime=LocalTime.of(2,30),scheduleType=ScheduleType.ONCE),rules=listOf(it.rules.first().copy(offsetMinutes=-60))) }
        val preview=engine.preview(data,snapshot(data),day.atStartOfDay(dst).toInstant(),dst)
        assertEquals(day.atTime(3,30).atZone(dst).minusHours(1).toInstant().toEpochMilli(),preview.items.single().atEpochMillis)
        assertTrue(preview.items.single().reason.contains("时区跳时"))
        val shift=data.copy(event=data.event.copy(scheduleType=ScheduleType.SHIFT,endDate=day,
            shiftCycle=ShiftCycle(listOf(ShiftDay("白班",times=listOf(ShiftTime(LocalTime.of(2,30))))))))
        assertTrue(engine.preview(shift,snapshot(shift),day.atStartOfDay(dst).toInstant(),dst).items.single().reason.contains("时区跳时"))
    }
    @Test fun sharedPreparationPreservesSeedAndRestoreBoundaryAndRevisesChangedTiming() {
        val stored=random().event.copy(randomSeed=123,timingRevision=4,resumedAfterEpochMillis=100,completionTrackingStartedAt=200,trackCompletion=true)
        var calls=0
        val unchanged=SchedulePreparation.prepare(stored.copy(randomSeed=0,resumedAfterEpochMillis=null),stored,now,zone) { calls++;9 }
        assertEquals(123L,unchanged.randomSeed);assertEquals(4L,unchanged.timingRevision);assertEquals(100L,unchanged.resumedAfterEpochMillis)
        assertEquals(200L,unchanged.completionTrackingStartedAt);assertEquals(0,calls)
        val changed=SchedulePreparation.prepare(stored.copy(localTime=LocalTime.of(7,50)),stored,now,zone) { error("must retain seed") }
        assertEquals(5L,changed.timingRevision)
    }
    @Test fun startedNewRandomWindowIsSkippedOrLimitedToExplicitRemainingMinutes() {
        val current=Instant.ofEpochMilli(millis(hour=8,minute=5,second=32))
        val data=random().let { it.copy(event=it.event.copy(id=0,randomSeed=0),rules=it.rules.map { rule->rule.copy(id=0,eventId=0) }) }
        val skipped=engine.preview(data,PreviewSnapshot(),current,zone)
        assertFalse(skipped.items.any { it.baseDate==date })
        assertTrue(skipped.omissions.any { it.date==date && it.reason.contains("已经开始") })
        val remaining=data.copy(event=data.event.copy(windowFirstNotBeforeEpochMillis=current.toEpochMilli()))
        val preview=engine.preview(remaining,PreviewSnapshot(),current,zone)
        assertEquals(millis(hour=8,minute=6,second=0),preview.items.first().atEpochMillis)
        assertEquals(millis(hour=8,minute=10,second=0),preview.items.first().endEpochMillis)
    }
    @Test fun restorationBoundarySkipsEarlierFixedPointsAndStartedRandomWindows() {
        val boundary=millis(hour=8,minute=5,second=0)
        val data=details().let { it.copy(event=it.event.copy(resumedAfterEpochMillis=boundary)) }
        assertEquals(millis(hour=8,minute=30),engine.preview(data,snapshot(data),now,zone).items.first().atEpochMillis)
        val window=random().let { it.copy(event=it.event.copy(resumedAfterEpochMillis=boundary)) }
        assertFalse(engine.preview(window,snapshot(window),now,zone).items.any { it.baseDate==date })
    }
    @Test fun crossYearAdvanceChecksUnknownHolidaysOnTaskDateEvenWhenTriggerYearIsCovered() {
        val triggerDay=LocalDate.of(2026,1,1)
        val data=details().let { it.copy(event=it.event.copy(localDate=LocalDate.of(2027,1,1),scheduleType=ScheduleType.ONCE,
            holidayPolicy=HolidayPolicy.SKIP_OFFICIAL_HOLIDAYS),rules=listOf(ReminderRule(2,1,-365*1440))) }
        val preview=engine.preview(data,snapshot(data),triggerDay.atStartOfDay(zone).toInstant(),zone)
        assertEquals(1,preview.items.size)
        assertTrue(preview.warnings.any { it.contains("事项日期") && it.contains("未收录") })
    }
}
