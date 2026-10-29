package com.muyex.randommusicreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.muyex.randommusicreminder.playback.ReminderNotificationFactory
import com.muyex.randommusicreminder.playback.ReminderPlaybackService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntentContract.ACTION_FIRE) return
        val request = AlarmIntentContract.requestFrom(intent) ?: return

        try {
            ContextCompat.startForegroundService(
                context,
                ReminderPlaybackService.startIntent(context, request),
            )
        } catch (_: RuntimeException) {
            // 后台启动被系统拦截时至少保留可点击的高优先级通知，避免提醒完全丢失。
            ReminderNotificationFactory(context).showStartFailure(request)
        }
    }
}
