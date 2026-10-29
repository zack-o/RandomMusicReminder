package com.muyex.randommusicreminder.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class AlarmRescheduleReason {
    BOOT_COMPLETED,
    PACKAGE_REPLACED,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    EXACT_ALARM_PERMISSION_CHANGED,
    HORIZON_REFRESH,
}

fun interface AlarmRescheduleHandler {
    suspend fun rescheduleAll(reason: AlarmRescheduleReason)
}

object AlarmRescheduleRegistry {
    @Volatile
    private var handler: AlarmRescheduleHandler? = null

    fun install(handler: AlarmRescheduleHandler) {
        this.handler = handler
    }

    fun clear() {
        handler = null
    }

    internal fun current(): AlarmRescheduleHandler? = handler
}

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> AlarmRescheduleReason.BOOT_COMPLETED
            Intent.ACTION_MY_PACKAGE_REPLACED -> AlarmRescheduleReason.PACKAGE_REPLACED
            Intent.ACTION_TIME_CHANGED -> AlarmRescheduleReason.TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> AlarmRescheduleReason.TIMEZONE_CHANGED
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                AlarmRescheduleReason.EXACT_ALARM_PERMISSION_CHANGED
            ACTION_REFRESH_HORIZON -> AlarmRescheduleReason.HORIZON_REFRESH
            else -> return
        }
        val handler = AlarmRescheduleRegistry.current() ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // BroadcastReceiver 的异步处理窗口有限，重排只应执行读取与 AlarmManager 注册。
                withTimeout(9_000L) {
                    handler.rescheduleAll(reason)
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_HORIZON =
            "com.muyex.randommusicreminder.action.REFRESH_SCHEDULE_HORIZON"
    }
}
