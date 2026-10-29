package com.muyex.randommusicreminder.domain.haptics

enum class ReminderOutputMode(val label: String, val audio: Boolean, val vibration: Boolean) {
    MUSIC("音乐",true,false), VIBRATION("仅震动",false,true), MUSIC_AND_VIBRATION("音乐加震动",true,true), NOTIFICATION("仅通知",false,false);
    companion object {
        fun merge(values: List<ReminderOutputMode>): ReminderOutputMode = entries.first { mode ->
            mode.audio == values.any { it.audio } && mode.vibration == values.any { it.vibration }
        }
    }
}

enum class HapticPreset(val label: String) { SOFT("轻点"), PULSE("双脉冲"), URGENT("三连提示") }
enum class HapticStrength(val label: String, val scale: Double) { LIGHT("轻",0.55), STANDARD("标准",0.8), STRONG("明显",1.0) }
data class HapticSettings(val preset: HapticPreset=HapticPreset.PULSE, val strength: HapticStrength=HapticStrength.STANDARD,
    val repeatGapMillis: Int=1000, val useMusicRhythm: Boolean=false) {
    fun validate() { require(repeatGapMillis in 500..5000) { "震动循环间隔须为500～5000毫秒" } }
}
