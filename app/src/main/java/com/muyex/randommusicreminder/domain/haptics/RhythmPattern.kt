package com.muyex.randommusicreminder.domain.haptics

data class HapticPulse(val startMillis: Int, val durationMillis: Int, val amplitude: Int)
data class HapticWaveform(val timings: List<Long>, val amplitudes: List<Int>, val binaryFallback: Boolean)

data class RhythmPattern(val durationMillis: Int, val pulses: List<HapticPulse>) {
    fun validate() {
        require(durationMillis in 500..30000 && pulses.size in 1..200) { "震动节奏时长或脉冲数量无效" }
        var end = -40
        pulses.forEach { pulse ->
            require(pulse.startMillis >= end+40 && pulse.durationMillis in 20..160 && pulse.amplitude in 1..255 &&
                pulse.startMillis.toLong()+pulse.durationMillis <= durationMillis) { "震动脉冲超出限制或相互重叠" }
            end=pulse.startMillis+pulse.durationMillis
        }
        require(pulses.sumOf { it.durationMillis }.toLong()*100 <= durationMillis.toLong()*35) { "震动占空比超过35%" }
    }

    fun waveform(settings: HapticSettings, amplitudeControl: Boolean): HapticWaveform {
        validate();settings.validate()
        val times=mutableListOf<Long>();val amps=mutableListOf<Int>();var cursor=0
        pulses.forEach { pulse ->
            times+=(pulse.startMillis-cursor).toLong();amps+=0
            val duration=if(amplitudeControl) pulse.durationMillis else (pulse.durationMillis*settings.strength.scale).toInt().coerceAtLeast(20)
            times+=duration.toLong();amps+=if(amplitudeControl) (pulse.amplitude*settings.strength.scale).toInt().coerceIn(1,255) else -1
            cursor=pulse.startMillis+duration
        }
        times+=(durationMillis-cursor+settings.repeatGapMillis).toLong();amps+=0
        return HapticWaveform(times,amps,!amplitudeControl)
    }

    companion object {
        fun preset(value: HapticPreset): RhythmPattern = when(value) {
            HapticPreset.SOFT -> RhythmPattern(800,listOf(HapticPulse(0,40,100)))
            HapticPreset.PULSE -> RhythmPattern(1000,listOf(HapticPulse(0,80,180),HapticPulse(260,80,180)))
            HapticPreset.URGENT -> RhythmPattern(1200,listOf(HapticPulse(0,100,220),HapticPulse(270,100,220),HapticPulse(540,100,220)))
        }
    }
}
