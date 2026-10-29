package com.muyex.randommusicreminder.domain.haptics

object EnergyRhythm {
    const val FRAME_MILLIS=20
    const val ALGORITHM_VERSION=1
    fun fromRms(frames: List<Double>): RhythmPattern {
        require(frames.size in 500..1500 && frames.all { it.isFinite() && it in 0.0..1.0 }) { "分析片段须为10～30秒有效PCM能量" }
        val peak=frames.max()
        require(peak>=0.01) { "片段过静，请选择其他片段或使用标准预设" }
        val pulses=mutableListOf<HapticPulse>()
        var previous=-200
        frames.forEachIndexed { index, energy ->
            val baseline=frames.subList((index-5).coerceAtLeast(0),index).average().takeIf(Double::isFinite) ?: energy
            val onset=energy-baseline
            val start=index*FRAME_MILLIS
            if(start-previous>=200 && energy>=peak*0.18 && onset>=maxOf(0.008,peak*0.10)) {
                pulses+=HapticPulse(start,60,(70+energy/peak*130).toInt().coerceIn(70,200));previous=start
            }
        }
        val kept=pulses.filter { it.startMillis+it.durationMillis<=frames.size*FRAME_MILLIS }
        require(kept.size>=2) { "未找到足够明显的能量变化，请换片段或使用标准预设" }
        return RhythmPattern(frames.size*FRAME_MILLIS,kept).also { it.validate() }
    }
}
