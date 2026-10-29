package com.muyex.randommusicreminder.domain.haptics

import com.muyex.randommusicreminder.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class HapticRulesTest {
    @Test fun previewCannotInterruptAlarmAndOldOwnerCannotCancelNewOwner() {
        val ownership=HapticOwnership();val preview=Any();val alarm=Any();var cancels=0
        assertTrue(ownership.claim(preview,false) {})
        assertTrue(ownership.claim(alarm,true) {})
        ownership.release(preview) { cancels++ };assertEquals(0,cancels)
        assertFalse(ownership.claim(preview,false) { fail("预览不应抢占提醒") })
        ownership.release(alarm) { cancels++ };assertEquals(1,cancels)
        assertTrue(ownership.claim(preview,false) {})
    }
    @Test fun outputUnionDoesNotTurnVibrationOrNotificationIntoAudio() {
        assertEquals(ReminderOutputMode.VIBRATION,ReminderOutputMode.merge(listOf(ReminderOutputMode.VIBRATION,ReminderOutputMode.NOTIFICATION)))
        assertEquals(ReminderOutputMode.NOTIFICATION,ReminderOutputMode.merge(emptyList()))
        assertEquals(ReminderOutputMode.MUSIC_AND_VIBRATION,ReminderOutputMode.merge(listOf(ReminderOutputMode.MUSIC,ReminderOutputMode.VIBRATION)))
        assertEquals(ReminderOutputMode.MUSIC,ReminderOutputMode.merge(listOf(ReminderOutputMode.MUSIC,ReminderOutputMode.NOTIFICATION)))
    }
    @Test fun mergedAudioAndVibrationUseOnlyEventsRequestingTheirChannel() {
        fun item(id:String,mode:ReminderOutputMode)=PlaybackOccurrence(id,id.toLong(),"事项$id",AlertMode.GENTLE,30000,null,0,MusicPoolMode.CUSTOM,mode)
        val routed=OutputRouting.select(listOf(item("1",ReminderOutputMode.NOTIFICATION),item("2",ReminderOutputMode.VIBRATION),item("3",ReminderOutputMode.MUSIC)))
        assertEquals(3L,routed.audio?.eventId);assertEquals(2L,routed.vibration?.eventId)
        assertEquals(ReminderOutputMode.MUSIC_AND_VIBRATION,routed.mode)
    }
    @Test fun everyPresetMeetsPulseAndDutyLimitsAndAddsAnOffInterval() {
        HapticPreset.entries.forEach { preset ->
            val pattern=RhythmPattern.preset(preset);pattern.validate()
            val waveform=pattern.waveform(HapticSettings(repeatGapMillis=500),true)
            assertEquals(pattern.durationMillis+500L,waveform.timings.sum())
            assertTrue(waveform.timings.last()>=500)
            assertEquals(0,waveform.amplitudes.last())
        }
    }
    @Test fun devicesWithoutAmplitudeControlUseExplicitBinaryShortPulses() {
        val pattern=RhythmPattern.preset(HapticPreset.URGENT)
        val light=pattern.waveform(HapticSettings(strength=HapticStrength.LIGHT),false)
        val strong=pattern.waveform(HapticSettings(strength=HapticStrength.STRONG),false)
        assertTrue(light.binaryFallback);assertTrue(light.amplitudes.all { it in setOf(0,-1) })
        val lightOn=light.timings.zip(light.amplitudes).filter { it.second!=0 }.sumOf { it.first }
        val strongOn=strong.timings.zip(strong.amplitudes).filter { it.second!=0 }.sumOf { it.first }
        assertTrue(lightOn<strongOn)
    }
    @Test fun unsafeOverlapsLongPulsesAndDutyCycleAreRejected() {
        val invalid=listOf(RhythmPattern(1000,listOf(HapticPulse(0,161,100))),
            RhythmPattern(1000,listOf(HapticPulse(0,100,100),HapticPulse(120,40,100))),
            RhythmPattern(500,listOf(HapticPulse(0,100,100),HapticPulse(140,100,100),HapticPulse(280,100,100))))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { it.validate() } }
        assertThrows(IllegalArgumentException::class.java) { HapticSettings(repeatGapMillis=499).validate() }
        assertThrows(IllegalArgumentException::class.java) { HapticSettings(repeatGapMillis=5001).validate() }
    }
    @Test fun energyChangesBecomeSpacedBoundedPulses() {
        val pattern=EnergyRhythm.fromRms(List(1000) { if(it%25==0) 0.7 else 0.001 })
        pattern.validate();assertEquals(20000,pattern.durationMillis)
        assertTrue(pattern.pulses.size>=20)
        assertTrue(pattern.pulses.zipWithNext().all { (first,next) -> next.startMillis-first.startMillis>=200 })
    }
    @Test fun silenceConstantEnergyAndMalformedFramesDoNotInventRhythm() {
        listOf(List(500){0.0},List(500){0.4},List(500){Double.NaN},List(499){0.4},List(500){if(it==489 || it==499) 0.7 else 0.001}).forEach { frames ->
            assertThrows(IllegalArgumentException::class.java) { EnergyRhythm.fromRms(frames) }
        }
    }
}
