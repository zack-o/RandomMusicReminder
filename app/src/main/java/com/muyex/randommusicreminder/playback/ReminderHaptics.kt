package com.muyex.randommusicreminder.playback

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.muyex.randommusicreminder.BuildConfig
import com.muyex.randommusicreminder.domain.haptics.*

interface HapticOutput {
    val capability: String
    fun start(pattern: RhythmPattern, settings: HapticSettings): String
    fun stop()
}

object ReminderHaptics {
    internal var testFactory: ((Context)->HapticOutput)?=null
    private val ownership=HapticOwnership()
    internal fun claim(token: Any, alarm: Boolean, action: ()->Unit)=ownership.claim(token,alarm,action)
    internal fun release(token: Any, action: ()->Unit)=ownership.release(token,action)
    fun create(context: Context, alarm: Boolean=false): HapticOutput = if(BuildConfig.DEBUG) testFactory?.invoke(context) ?: AndroidHapticOutput(context,alarm) else AndroidHapticOutput(context,alarm)
}

private class AndroidHapticOutput(context: Context, private val alarm: Boolean): HapticOutput {
    private val token=Any()
    private val vibrator: Vibrator?=if(Build.VERSION.SDK_INT>=31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    override val capability: String get()=when {
        vibrator?.hasVibrator()!=true -> "此设备没有可用震动马达，将保留无声通知"
        !vibrator.hasAmplitudeControl() -> "不支持振幅调节，使用短脉冲；轻/标准/明显改为脉冲时长，触感受系统设置影响"
        else -> "支持振幅调节；触感仍受系统震动设置和马达影响"
    }
    override fun start(pattern: RhythmPattern, settings: HapticSettings): String {
        val device=vibrator?.takeIf { it.hasVibrator() } ?: return capability
        return try {
            val waveform=pattern.waveform(settings,device.hasAmplitudeControl())
            val effect=VibrationEffect.createWaveform(waveform.timings.toLongArray(),waveform.amplitudes.toIntArray(),0)
            val started=ReminderHaptics.claim(token,alarm) {
                if(Build.VERSION.SDK_INT>=33) device.vibrate(effect,VibrationAttributes.Builder().setUsage(if(alarm) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_TOUCH).build())
                else device.vibrate(effect,AudioAttributes.Builder().setUsage(if(alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
            if(!started) return "当前有事项正在震动，请停止后再试震"
            if(waveform.binaryFallback) "已请求短脉冲震动（无振幅调节）" else "已请求震动，触感受系统设置影响"
        } catch(_: RuntimeException) { "震动未能启动，保留通知；请检查系统震动设置" }
    }
    override fun stop() { ReminderHaptics.release(token) { runCatching { vibrator?.cancel() } } }
}
