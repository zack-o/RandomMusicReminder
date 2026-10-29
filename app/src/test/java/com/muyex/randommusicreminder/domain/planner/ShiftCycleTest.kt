package com.muyex.randommusicreminder.domain.planner

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ShiftCycleTest {
    @Test fun sixDayCycleCrossesMonthsYearsAndLeapDayWithoutReset() {
        val cycle=ShiftCycle.template();val anchor=LocalDate.of(2027,12,30)
        cycle.validate()
        assertNull(cycle.dayIndex(anchor,anchor.minusDays(1)))
        for(day in 0..500) assertEquals(day % 6,cycle.dayIndex(anchor,anchor.plusDays(day.toLong())))
        assertEquals("夜班",cycle.dayOn(anchor,LocalDate.of(2028,1,1))?.label)
    }
    @Test fun restDayCanIndependentlyRemindWithoutChangingCyclePhase() {
        val initial=ShiftCycle.template("白夜休休")
        val changed=initial.copy(days=initial.days.mapIndexed { index, day -> if(index==2) day.copy(enabled=true,times=listOf(ShiftTime(LocalTime.of(9,0)))) else day })
        changed.validate()
        val anchor=LocalDate.of(2030,1,1)
        assertEquals(true,changed.dayOn(anchor,anchor.plusDays(2))?.enabled)
        assertEquals(false,changed.dayOn(anchor,anchor.plusDays(3))?.enabled)
        assertEquals("白班",changed.dayOn(anchor,anchor.plusDays(4))?.label)
    }
    @Test fun supportsNextDayAndRejectsInvalidOrDuplicateTimes() {
        val night=ShiftDay("夜班",times=listOf(ShiftTime(LocalTime.of(19,30)),ShiftTime(LocalTime.of(8,0),nextDay=true)))
        ShiftCycle(listOf(night)).validate()
        assertThrows(IllegalArgumentException::class.java) { ShiftCycle(emptyList()).validate() }
        assertThrows(IllegalArgumentException::class.java) { ShiftCycle(List(61){night}).validate() }
        assertThrows(IllegalArgumentException::class.java) { ShiftCycle(listOf(night.copy(times=night.times+night.times.first().copy(key="different")))).validate() }
        assertThrows(IllegalArgumentException::class.java) { ShiftCycle(listOf(night.copy(enabled=true,times=emptyList()))).validate() }
    }
    @Test fun persistenceKeysDoNotChangePortableDuplicateDetection() {
        assertEquals(ShiftCycle.template().fingerprint(),ShiftCycle.template().fingerprint())
        assertNotEquals(ShiftCycle.template("白夜休休").fingerprint(),ShiftCycle.template("白白夜夜休休").fingerprint())
    }
    @Test fun reorderingTimesDoesNotCreateAnApparentlyDifferentCycle() {
        val day=ShiftDay("交接",times=listOf(ShiftTime(LocalTime.of(8,0)),ShiftTime(LocalTime.of(20,0))))
        assertEquals(ShiftCycle(listOf(day)).fingerprint(),ShiftCycle(listOf(day.copy(times=day.times.reversed()))).fingerprint())
    }
    @Test fun shortestAndLongestCyclesKeepTheirCalendarPhase() {
        val anchor=LocalDate.of(2030,12,31)
        val daily=ShiftCycle.template("白")
        daily.validate()
        assertEquals(0,daily.dayIndex(anchor,anchor.plusDays(60)))
        val longest=ShiftCycle(List(60) { index -> ShiftDay("班次${index+1}",times=listOf(ShiftTime(LocalTime.of(8,0)))) })
        longest.validate()
        assertEquals(59,longest.dayIndex(anchor,anchor.plusDays(59)))
        assertEquals(0,longest.dayIndex(anchor,anchor.plusDays(60)))
    }
    @Test fun rejectsAllDisabledCyclesAndSharedPersistenceKeys() {
        assertThrows(IllegalArgumentException::class.java) { ShiftCycle.template("休休").validate() }
        val slot=ShiftTime(LocalTime.of(8,0))
        val day=ShiftDay("白班",times=listOf(slot))
        assertThrows(IllegalArgumentException::class.java) { ShiftCycle(listOf(day,day)).validate() }
        assertThrows(IllegalArgumentException::class.java) {
            ShiftCycle(listOf(day.copy(times=List(9) { ShiftTime(LocalTime.of(it,0)) }))).validate()
        }
    }
}
